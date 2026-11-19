#!/usr/bin/env python3
"""Prepare controlled Android experiments or compare matching route captures.

Plans are overlays for private copied test state, never automatic edits to a
player's files. Results require per-frame CSVs plus explicit device, route,
seed, cache and thermal provenance. Missing evidence is not a passing result.
"""
import argparse
import importlib.util
import json
import math
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
        "gpu-pass-timings":{"fh1_native_gpu_profile":"true"},
        "thread-waits":{"perf_thread_cpu":'"Guest;GPU;Vulkan;XMA Decoder;Audio"',
                        "perf_critical_path_trace":"true"},
        "gpu-idle-low-spin":{"gpu_idle_spin_count":"0"},
        "iso-streaming":{},
        "folder-streaming":{},
        "shadows-off":{"pinyon_shift_fh1_shadows":"false"},
        "reflections-half":{"pinyon_shift_fh1_env_map_rate":"0.5"},
        "reflections-full":{"pinyon_shift_fh1_env_map_rate":"1"},
        "paint-system-driver":{"android_gpu_driver":'""'},
        "paint-compatibility-renderer":{"vulkan_fh1_native_executor":"false"},
        "audio-guest-output":{"pinyon_shift_native_audio_output":"false"},
        "audio-original-mixer":{"pinyon_shift_fmod_stereo":"false"},
        "audio-original-eq":{"pinyon_shift_fmod_native_parameq":"false"},
        "audio-inline-decode":{"xma_decode_kicks_inline":"true"},
        "audio-full-update-rate":{"pinyon_shift_audio_update_interval":"1"},
        "audio-051-style":{"pinyon_shift_native_audio_output":"false",
                           "pinyon_shift_fmod_stereo":"false",
                           "pinyon_shift_fmod_native_parameq":"false",
                           "pinyon_shift_audio_update_interval":"1",
                           "xma_decode_kicks_inline":"true"},
        "bloom-off":{"disable_bloom":"true"},
        "depth-of-field-on":{"disable_depth_of_field":"false"},
        "motion-blur-on":{"disable_motion_blur":"false"},
        "scale-50":{"fh1_render_scale_percent":"50"},
        "scale-67":{"fh1_render_scale_percent":"67"},
        "scale-85":{"fh1_render_scale_percent":"85"},
        "scale-90":{"fh1_render_scale_percent":"90"},
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
        "routes":{
            "free-roam":"Use the same save, car, route and camera. Capture the first minute and a warmed 15-minute pass.",
            "race":"Use the same event, grid and car. Keep the race start and finish markers in both runs.",
            "showroom":"Use the same WRX STI, paint, view and exposure. Capture before switching each driver or renderer.",
            "impact":"Repeat a wall impact, sustained scrape and contact with another car. Record chase and cockpit sound with the same output device.",
        },
        "manual_variants":{"iso-streaming":"Select the local ISO in Game files.",
                           "folder-streaming":"Use the matching extracted game files.",
                           "gpu-pass-timings":"Enable Detailed GPU timings for the diagnostic launch.",
                           "paint-system-driver":"Select and test the system driver before launching.",
                           "audio-051-style":"Use the Audio compatibility switch or apply this overlay to copied test state."},
        "instructions":"Use copied save/cache state and the same route, driver and initial thermal severity. Never clear the live cache or saves. Test visual quality and audible audio separately. Revert experiments that regress correctness. 40 FPS needs an appropriate display cadence. No automatic device qualification is implied.",
        "result_format":{"runs":[{"case":"baseline","csv":"capture.csv","device":"model / GPU","driver":"version","build":"source commit","route":"route ID","seed_sha256":"64 hex characters","cache":"warm","storage":"iso","display_hz":120,"target_fps":30,"render_scale_percent":75,"thermal_start":0,"thermal_end":0,"host_menu":False}]}}
    (directory/"plan.json").write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
    return result

def scale_trials(results):
    """Choose a measured next-launch scale for each matching route and FPS cap."""
    groups = {}
    provenance = ("device", "driver", "build", "route", "seed_sha256", "cache", "storage",
                  "display_hz", "thermal_start", "thermal_end", "target_fps")
    for result in results:
        run = result["provenance"]
        if run["case"] != "baseline" and not run["case"].startswith("scale-"):
            continue
        target, scale = run.get("target_fps"), run.get("render_scale_percent")
        if type(target) is not int or target not in (30, 40, 60, 120):
            continue
        if type(scale) is not int or not 25 <= scale <= 100:
            continue
        group = tuple(run.get(key) for key in provenance)
        if any(value is None for value in group):
            continue
        frames = result["summary"]["frames"]
        if frames["measured_duration_seconds"] < 60:
            continue
        times = frames["frame_time_us"]
        budget = 1_000_000 / target
        ready = times["p95"] <= budget * 1.05 and times["p99"] <= budget * 1.25
        groups.setdefault(group, {}).setdefault(scale, []).append(ready)
    trials = []
    for group, scales in groups.items():
        if len(scales) < 2:
            continue
        stable = [scale for scale, samples in scales.items() if all(samples)]
        trials.append({**dict(zip(provenance, group)), "tested_percent": sorted(scales),
                       "suggested_next_launch_percent": max(stable) if stable else None})
    return trials


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
        if "storage" in run and run["storage"] not in ("iso", "linked-folder", "copied-folder"):
            raise ValueError("Invalid game storage mode")
        if "display_hz" in run and (type(run["display_hz"]) not in (int, float)
                                    or not math.isfinite(run["display_hz"]) or run["display_hz"] <= 0):
            raise ValueError("Invalid display refresh")
        capture=pathlib.Path(run["csv"])
        if not capture.is_absolute():capture=manifest.parent/capture
        if capture.stat().st_size>64*1024*1024:raise ValueError("Capture exceeds 64 MB")
        results.append({"provenance":run,"summary":summary.summarize(capture)})
    comparisons=[]
    for result in results:
        run=result["provenance"]
        if run["case"]=="baseline":continue
        match = ["device", "route", "seed_sha256", "cache", "thermal_start", "display_hz"]
        if run["case"] != "paint-system-driver":
            match.append("driver")
        if run["case"] not in ("iso-streaming", "folder-streaming"):
            match.append("storage")
        if not run["case"].startswith("target-"):
            match.append("target_fps")
        if not run["case"].startswith("scale-"):
            match.append("render_scale_percent")
        references = [item for item in results if item["provenance"]["case"] == "baseline"
                      and all(item["provenance"].get(key) == run.get(key) for key in match)]
        if not references:comparisons.append({"case":run["case"],"status":"unqualified","reason":"No matching baseline"});continue
        reference=references[0];a=reference["summary"];b=result["summary"]
        duration=a["frames"]["measured_duration_seconds"];other=b["frames"]["measured_duration_seconds"]
        if duration<60 or abs(other-duration)/duration>.10 or abs(run["thermal_end"]-reference["provenance"]["thermal_end"])>1:
            comparisons.append({"case":run["case"],"status":"unqualified","reason":"Short or unequal duration, or unmatched thermal state"});continue
        summary.add_comparison(b,a)
        delta=b["comparison"]["metrics"]["p95_frame_time_us"]["delta_percent"]
        reference_p99 = a["frames"]["frame_time_us"]["p99"]
        p99_delta = (b["frames"]["frame_time_us"]["p99"] / reference_p99 - 1) * 100 if reference_p99 else None
        regressed = delta > threshold or p99_delta is not None and p99_delta > threshold
        comparisons.append({"case":run["case"],"status":"regression" if regressed else "within-threshold",
                            "p95_delta_percent":delta,"p99_delta_percent":p99_delta})
    return {"schema":"fh4droid.performance-suite.v1","runs":results,"comparisons":comparisons,
            "scale_trials":scale_trials(results),"qualified_devices":[],"note":"Timing comparisons do not qualify visuals, audio, long-session stability or a GPU family. A/A repeats establish measurement noise before interpreting small changes."}

def main():
    parser=argparse.ArgumentParser(description=__doc__);sub=parser.add_subparsers(dest="action",required=True)
    create=sub.add_parser("plan");create.add_argument("directory",type=pathlib.Path)
    analyze=sub.add_parser("compare");analyze.add_argument("manifest",type=pathlib.Path);analyze.add_argument("--threshold",type=float,default=5);analyze.add_argument("--output",type=pathlib.Path,required=True)
    args=parser.parse_args()
    if args.action=="plan":result=plan(args.directory);print(f"Prepared {len(result['cases'])} experiment overlays; no device results yet.")
    else:args.output.write_text(json.dumps(compare(args.manifest,args.threshold),indent=2)+"\n",encoding="utf-8")
    return 0
if __name__=="__main__":raise SystemExit(main())
