#!/usr/bin/env python3
"""Replay Android SDK patches from pinned Git objects and compare every byte."""
import pathlib
import subprocess
import tempfile

ROOT=pathlib.Path(__file__).resolve().parents[1]
SDK=ROOT/"thirdparty/shiftglue-sdk"

def main():
    patches=sorted((ROOT/"cmake/patches").glob("shiftglue-fh4droid-*.patch"))
    paths=set();added=set()
    for patch in patches:
        for part in patch.read_text().split("diff --git ")[1:]:
            name=part.splitlines()[0].split(" b/",1)[1]
            if pathlib.PurePosixPath(name).is_absolute() or ".." in pathlib.PurePosixPath(name).parts:
                raise ValueError("Unsafe SDK patch path")
            paths.add(name)
            if "\n--- /dev/null\n" in part:added.add(name)
    if not patches or not paths:raise ValueError("Android SDK patches are missing")
    with tempfile.TemporaryDirectory(prefix="fh4droid-sdk-replay-") as temporary:
        stage=pathlib.Path(temporary)
        for name in sorted(paths):
            source=SDK/name;repo=SDK;relative=pathlib.Path(name)
            for parent in source.parents:
                if parent==SDK:break
                if (parent/".git").exists():repo=parent;relative=source.relative_to(parent);break
            original=subprocess.run(["git","-C",str(repo),"show","HEAD:"+relative.as_posix()],capture_output=True)
            if original.returncode:
                if name not in added:raise ValueError("Pinned SDK source is missing: "+name)
                continue
            destination=stage/name;destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(original.stdout)
        for patch in patches:
            result=subprocess.run(["git","apply",str(patch)],cwd=stage,capture_output=True,text=True)
            if result.returncode:raise ValueError(patch.name+": "+result.stderr)
        mismatch=[name for name in sorted(paths) if not (stage/name).is_file() or not (SDK/name).is_file() or (stage/name).read_bytes()!=(SDK/name).read_bytes()]
        if mismatch:raise ValueError("SDK differs from replay: "+", ".join(mismatch))
    print(f"PASS: {len(paths)} SDK files reproduce exactly from {len(patches)} patches")
    return 0

if __name__=="__main__":raise SystemExit(main())
