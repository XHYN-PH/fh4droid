#!/usr/bin/env python3
"""Prepare controlled Android experiments or compare matching route captures.

Plans are overlays for private copied test state, never automatic edits to a
player's files. Results require per-frame CSVs plus explicit device, route,
seed, cache and thermal provenance. Missing evidence is not a passing result.
"""
import argparse
import importlib.util
import json
import pathlib
import re

ROOT=pathlib.Path(__file__).resolve().parents[1]
SPEC=importlib.util.spec_from_file_location("performance_summary",ROOT/"tools/summarize-performance.py")
summary=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(summary)
BASE={"pinyon_shift_fh1_render_fps_limit":"30","host_present_fps_limit":"0",
      "fh1_render_scale_percent":"75","fh1_msaa_single_sample":"true","fh1_msaa_2x":"false",
      "present_effect":'"bilinear"',"vulkan_texture_max_size":"0",
      "pinyon_shift_fh1_shadows":"true","pinyon_shift_fh1_env_map_rate":"0.25",
      "disable_bloom":"false","disable_depth_of_field":"true","disable_motion_blur":"true",
      "vulkan_pipeline_creation_threads":"2","vulkan_aot_prewarm_threads":"2",
      "vulkan_texture_memory_prealloc_mb":"256","vulkan_pipeline_prewarm_background":"false",
      "vulkan_force_bc_decode":"false","host_present_sleep_spin":"true"}

def cases():
    out={"baseline":dict(BASE)}
    changes={
        "texture-512":{"vulkan_texture_max_size":"512"},
        "texture-256":{"vulkan_texture_max_size":"256"},
        "pool-64":{"vulkan_texture_memory_prealloc_mb":"64"},
        "pool-128":{"vulkan_texture_memory_prealloc_mb":"128"},
        "shader-one":{"vulkan_pipeline_creation_threads":"1","vulkan_aot_prewarm_threads":"1"},
        "warmup-background":{"vulkan_pipeline_prewarm_background":"true"},
        "shadows-off":{"pinyon_shift_fh1_shadows":"false"},
        "reflections-half":{"pinyon_shift_fh1_env_map_rate":"0.5"},
        "bloom-off":{"disable_bloom":"true"},
        "depth-of-field-on":{"disable_depth_of_field":"false"},
        "motion-blur-on":{"disable_motion_blur":"false"},
        "scale-50":{"fh1_render_scale_percent":"50"},
        "scale-67":{"fh1_render_scale_percent":"67"},
        "scale-100":{"fh1_render_scale_percent":"100"},
        "upscale-cas":{"present_effect":'"cas"'},
        "upscale-fsr1":{"present_effect":'"fsr"'},
        "forced-bc-decode":{"vulkan_force_bc_decode":"true"},
        "pacing-no-spin":{"host_present_sleep_spin":"false","host_present_fps_limit":"30"},
        "pacing-spin":{"host_present_sleep_spin":"true","host_present_fps_limit":"30"},
        "target-40":{"pinyon_shift_fh1_render_fps_limit":"40"},
        "target-60":{"pinyon_shift_fh1_render_fps_limit":"60"},
    }
    for name,values in changes.items():out[name]=BASE|values
    return out

def plan(directory):
    directory.mkdir(parents=True,exist_ok=True)
    matrix=cases()
    if (directory/"plan.json").exists() or any((directory/(name+".toml")).exists() for name in matrix):raise ValueError("Refusing to overwrite an existing experiment plan")
    for name,values in matrix.items():
        target=directory/(name+".toml")
        if target.exists():raise ValueError("Refusing to overwrite an existing plan: "+str(target))
        target.write_text("# Apply only to copied benchmark state.\n"+"".join(f"{key} = {value}\n" for key,value in values.items()),encoding="utf-8")
    result={"schema":"fh4droid.performance-plan.v1","cases":list(matrix),
        "device_matrix":["Snapdragon 845 / Adreno 630","Mali-G52","Xclipse","Adreno 750 reference","Adreno 825/840 regression"],
        "required_runs":["baseline A/A repeat","cold cache","warm cache","15-minute sustained driving","background/resume","map/garage/photo/driving transitions"],
        "instructions":"Use copied save/cache state and the same route, driver and initial thermal severity. Never clear the live cache or saves. Test visual quality and audible audio separately. Revert experiments that regress correctness. 40 FPS needs an appropriate display cadence. No automatic device qualification is implied.",
        "result_format":{"runs":[{"case":"baseline","csv":"capture.csv","device":"model / GPU","driver":"version","build":"source commit","route":"route ID","seed_sha256":"64 hex characters","cache":"warm","thermal_start":0,"thermal_end":0,"host_menu":False}]}}
    (directory/"plan.json").write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
    return result

def compare(manifest,threshold=5):
    if not 0<=threshold<=100:raise ValueError("Regression threshold must be 0–100 percent")
    data=json.loads(manifest.read_text(encoding="utf-8"));runs=data.get("runs",[])
    if not runs:raise ValueError("No measured runs supplied")
    results=[]
    for run in runs:
        for key in ("case","device","driver","build","route","seed_sha256","cache"):
            if not isinstance(run.get(key),str) or not run[key].strip():raise ValueError("Missing run provenance: "+key)
        if not re.fullmatch("[a-fA-F0-9]{64}",run["seed_sha256"]):raise ValueError("Invalid seed digest")
        if run["cache"] not in ("cold","warm"):raise ValueError("Cache state must be cold or warm")
        if run.get("host_menu") is not False:raise ValueError("Gameplay comparisons must exclude host menus")
        for key in ("thermal_start","thermal_end"):
            if type(run.get(key)) is not int or not 0<=run[key]<=6:raise ValueError("Invalid thermal severity")
        capture=pathlib.Path(run["csv"])
        if not capture.is_absolute():capture=manifest.parent/capture
        if capture.stat().st_size>64*1024*1024:raise ValueError("Capture exceeds 64 MB")
        results.append({"provenance":run,"summary":summary.summarize(capture)})
    comparisons=[]
    for result in results:
        run=result["provenance"]
        if run["case"]=="baseline":continue
        references=[item for item in results if item["provenance"]["case"]=="baseline" and all(item["provenance"][key]==run[key] for key in ("device","driver","route","seed_sha256","cache","thermal_start"))]
        if not references:comparisons.append({"case":run["case"],"status":"unqualified","reason":"No matching baseline"});continue
        reference=references[0];a=reference["summary"];b=result["summary"]
        duration=a["frames"]["measured_duration_seconds"];other=b["frames"]["measured_duration_seconds"]
        if duration<60 or abs(other-duration)/duration>.10 or abs(run["thermal_end"]-reference["provenance"]["thermal_end"])>1:
            comparisons.append({"case":run["case"],"status":"unqualified","reason":"Short or unequal duration, or unmatched thermal state"});continue
        summary.add_comparison(b,a)
        delta=b["comparison"]["metrics"]["p95_frame_time_us"]["delta_percent"]
        comparisons.append({"case":run["case"],"status":"regression" if delta>threshold else "within-threshold","p95_delta_percent":delta})
    return {"schema":"fh4droid.performance-suite.v1","runs":results,"comparisons":comparisons,
            "qualified_devices":[],"note":"Timing comparisons do not qualify visuals, audio, long-session stability or a GPU family. A/A repeats establish measurement noise before interpreting small changes."}

def main():
    parser=argparse.ArgumentParser(description=__doc__);sub=parser.add_subparsers(dest="action",required=True)
    create=sub.add_parser("plan");create.add_argument("directory",type=pathlib.Path)
    analyze=sub.add_parser("compare");analyze.add_argument("manifest",type=pathlib.Path);analyze.add_argument("--threshold",type=float,default=5);analyze.add_argument("--output",type=pathlib.Path,required=True)
    args=parser.parse_args()
    if args.action=="plan":result=plan(args.directory);print(f"Prepared {len(result['cases'])} experiment overlays; no device results yet.")
    else:args.output.write_text(json.dumps(compare(args.manifest,args.threshold),indent=2)+"\n",encoding="utf-8")
    return 0
if __name__=="__main__":raise SystemExit(main())
