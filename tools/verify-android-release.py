#!/usr/bin/env python3
"""Verify the final signed APK against source, NDK and a known update APK.

This checks the shipped bytes. It does not label phone gameplay as tested.
Keep its JSON beside each privately built APK; no game/signing input is copied.
"""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import tempfile
import zipfile
import pinyon_android as android

ROOT=pathlib.Path(__file__).resolve().parents[1]

def run(*command):
    result=subprocess.run(list(map(str,command)),capture_output=True,text=True,timeout=120)
    if result.returncode:raise ValueError(f"{pathlib.Path(str(command[0])).name} failed: {result.stderr or result.stdout}")
    return result.stdout

def require(ok,message):
    if not ok:raise ValueError(message)

def package_identity(tools,apk):
    text=run(tools.aapt2,"dump","badging",apk)
    match=re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",text)
    require(match is not None,"APK identity is missing")
    return match.groups(),text

def certificate(tools,apk):
    text=run(tools.apksigner,"verify","--verbose","--print-certs",apk)
    match=re.search(r"Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]+)",text)
    require(match is not None and "Number of signers: 1" in text,"APK signer is missing or ambiguous")
    return match.group(1).lower()

def defined(tools,path):
    text=run(tools.llvm_bin/android._exe("llvm-nm"),"--dynamic","--defined-only",path)
    return {line.split()[-1].split("@")[0] for line in text.splitlines() if line.split()}

def verify(apk,baseline):
    tools=android.Tools();identity,badging=package_identity(tools,apk);old,_=package_identity(tools,baseline)
    expected_version,expected_code=android.release_version()
    require(identity==(android.PACKAGE,str(expected_code),expected_version),"APK version/package does not match current source")
    require(identity[0]==old[0] and int(identity[1])>int(old[1]),"Update must retain the package and increase versionCode")
    require(f"minSdkVersion:'{android.CONFIG['min_sdk']}'" in badging,"Incorrect minimum Android API")
    signer=certificate(tools,apk);require(signer==certificate(tools,baseline),"Release signing certificate changed")
    run(tools.zipalign,"-c","-P","16","4",apk)
    source=run("git","-C",ROOT,"rev-parse","HEAD").strip()
    require(not run("git","-C",ROOT,"status","--porcelain","--ignore-submodules=dirty").strip(),"Source must be committed before release verification")
    readelf=tools.llvm_bin/android._exe("llvm-readelf")
    nm=tools.llvm_bin/android._exe("llvm-nm")
    libraries={};library_records={}
    with zipfile.ZipFile(apk) as archive,tempfile.TemporaryDirectory(prefix="fh4droid-apk-check-") as temporary:
        names=archive.namelist()
        require(len(names)==len(set(names)),"Duplicate APK entries")
        forbidden={".keystore",".jks",".xex",".xexp",".iso",".zip",".cpp",".o",".pdb"}
        for name in names:
            path=pathlib.PurePosixPath(name)
            require(not path.is_absolute() and ".." not in path.parts,"Unsafe APK entry")
            require(path.suffix.lower() not in forbidden,"Private/source payload in APK: "+name)
        require(archive.read("classes.dex").startswith(b"dex\n"),"DEX payload is missing")
        metadata=json.loads(archive.read("assets/pinyon_shift_build.json"))
        require(metadata["pinyon_shift_commit"]==source and metadata["pinyon_shift_dirty"]=="false","APK provenance does not match committed source")
        require(metadata["version"]==identity[2] and metadata["min_sdk"]==android.CONFIG["min_sdk"],"Build metadata identity differs")
        pin=json.loads((ROOT/"config/release-toolchain.json").read_text())["rexglue"]["revision"]
        require(metadata["rexglue_commit"]==pin,"APK uses another SDK revision")
        require(metadata["android_sdk_patches"]=={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (ROOT/"cmake/patches").glob("shiftglue-fh4droid-*.patch")},"SDK patch provenance differs")
        require(metadata.get("validation_layer") is False,"Validation layer must not ship in the normal release")
        for asset in (ROOT/"android/art").rglob("*"):
            if asset.is_file():require(archive.read("assets/"+asset.relative_to(ROOT/"android/art").as_posix())==asset.read_bytes(),"APK artwork differs: "+asset.name)
        for catalog,asset in [("supported-extracted-ms-2505.json","supported-game.json"),("supported-dlc.json","supported-dlc.json"),("supported-dumps.json","supported-dumps.json")]:
            require(archive.read("assets/"+asset)==(ROOT/"config"/catalog).read_bytes(),"APK catalog differs: "+asset)
        xml=run(tools.aapt2,"dump","xmltree","--file","AndroidManifest.xml",apk)
        orientations=[line for line in xml.splitlines() if "screenOrientation" in line]
        require(orientations and all(line.rstrip().endswith("=0") for line in orientations),"An activity can rotate out of landscape")
        require(not re.search(r"android:debuggable[^\n]*=(?:true|0xffffffff)",xml),"Release APK is debuggable")
        for name in names:
            if not name.startswith("lib/") or not name.endswith(".so"):continue
            require(name.startswith("lib/arm64-v8a/"),"Unexpected APK ABI")
            data=archive.read(name);path=pathlib.Path(temporary)/pathlib.Path(name).name;path.write_bytes(data);libraries[path.name]=path
            require("AArch64" in run(readelf,"-h",path),"Non-ARM64 native library")
            segments=run(readelf,"-lW",path).splitlines();alignments=[int(line.split()[-1],16) for line in segments if line.strip().startswith("LOAD")]
            require(alignments and min(alignments)>=16384,"ELF alignment below 16 KiB: "+path.name)
            library_records[path.name]={"sha256":hashlib.sha256(data).hexdigest(),"bytes":len(data),"min_load_alignment":min(alignments)}
        expected=set(android.NATIVE_LIBRARIES+android.ADRENOTOOLS_HOOKS+("libc++_shared.so",))
        require(set(libraries)==expected,"Unexpected/missing native library set")
        require(metadata["executable_sha256"].lower()==library_records["libmain.so"]["sha256"],"Main hash describes different bytes")
        require(identity[2].encode() in libraries["libmain.so"].read_bytes(),"Native game library contains another build version")
        android.android_module_check.verify(list(libraries.values()),readelf,nm)
        symbols={name:defined(tools,path) for name,path in libraries.items()}
        require("rex_android_preparation_progress" in symbols["librexgpu-fh1.so"],"GPU preparation bridge is missing")
        for cls,library in [("PinyonShiftActivity","libmain.so"),("DriverProbeActivity","libfh4droid_android.so"),("ContentNative","libfh4droid_android.so")]:
            text=(ROOT/"android/java/studio/deimos/pinyonshift"/(cls+".java")).read_text()
            for method in re.findall(r"\bnative\s+[\w\[\]]+\s+(\w+)\s*\(",text):
                require(f"Java_studio_deimos_pinyonshift_{cls}_{method}" in symbols[library],"JNI export is missing: "+method)
        # Resolve strong imports against the actual shipped dependency graph
        # and API-level NDK stubs, ignoring linker scripts and weak imports.
        sysroot=tools.llvm_bin.parent/"sysroot/usr/lib/aarch64-linux-android"/str(android.CONFIG["min_sdk"])
        stubs={}
        for name,path in libraries.items():
            needed=re.findall(r"Shared library: \[([^\]]+)\]",run(readelf,"-dW",path))
            available=set(symbols[name])
            for dependency in needed:
                if dependency in symbols:available.update(symbols[dependency]);continue
                stub=sysroot/dependency
                require(stub.is_file() and stub.read_bytes()[:4]==b"\x7fELF","Native dependency is unavailable at minimum API: "+dependency)
                if dependency not in stubs:stubs[dependency]=defined(tools,stub)
                available.update(stubs[dependency])
            unresolved=[]
            for line in run(readelf,"--dyn-syms","--wide",path).splitlines():
                fields=line.split()
                if len(fields)>=8 and fields[6]=="UND" and fields[4]=="GLOBAL":
                    symbol=fields[7].split("@")[0]
                    if symbol not in available:unresolved.append(symbol)
            require(not unresolved,"Unresolved strong imports in "+name+": "+", ".join(unresolved))
    return {"schema":"fh4droid.android-release-verification.v1","apk":apk.name,"apk_sha256":hashlib.sha256(apk.read_bytes()).hexdigest(),"apk_bytes":apk.stat().st_size,
        "version":identity[2],"version_code":int(identity[1]),"application_id":identity[0],"upgrade_from":old[2],"certificate_sha256":signer,"source_commit":source,
        "min_sdk":android.CONFIG["min_sdk"],"cpu_baseline":metadata["cpu_baseline"],"libraries":library_records,
        "signature_verified":True,"alignment_verified":True,"module_registry_verified":True,"jni_verified":True,"strong_native_imports_resolved":True,
        "physical_device_tested":False,"gameplay_tested":False}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("apk",type=pathlib.Path);parser.add_argument("--baseline",type=pathlib.Path,required=True);parser.add_argument("--output",type=pathlib.Path,required=True);args=parser.parse_args()
    result=verify(args.apk,args.baseline);args.output.write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8");print(json.dumps({key:value for key,value in result.items() if key!="libraries"},indent=2));return 0
if __name__=="__main__":raise SystemExit(main())
