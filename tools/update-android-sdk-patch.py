#!/usr/bin/env python3
"""Record the reviewed SDK worktree as reproducible patches for the pinned SDK.

Regenerate Vulkan headers first with compile-fh1-native.sh. Mark any newly
added SDK source with git add -N before invoking this script. No commit or
network operation is performed here.
"""
import pathlib
import subprocess
root=pathlib.Path(__file__).resolve().parents[1]
sdk=root/"thirdparty/shiftglue-sdk"
pin="79d3e6fae4b79e00def48a595c80f0eaa47fd2b6"
if subprocess.check_output(["git","-C",str(sdk),"rev-parse","HEAD"],text=True).strip()!=pin:
    raise SystemExit("Review the SDK pin before regenerating Android patches.")
diff=subprocess.check_output(["git","-C",str(sdk),"diff","--binary"])
parts=[b"diff --git "+p for p in diff.split(b"diff --git ")[1:]]
runtime=[];groups=[];group=b""
for part in parts:
    if b"src/graphics/shaders/vulkan_spirv/" not in part.splitlines()[0]:
        runtime.append(part);continue
    if group and len(group)+len(part)>4*1024*1024:
        groups.append(group);group=b""
    group+=part
if group:groups.append(group)
directory=root/"cmake/patches";directory.mkdir(exist_ok=True)
for p in directory.glob("shiftglue-fh4droid-*.patch"):p.unlink()
files={"shiftglue-fh4droid-runtime.patch":b"".join(runtime)}
files.update({f"shiftglue-fh4droid-spirv-{i+1}.patch":g for i,g in enumerate(groups)})
for name,data in files.items():
    if len(data)>5*1024*1024:raise SystemExit("Patch exceeds the repository size policy: "+name)
    (directory/name).write_bytes(data)
    print(name,len(data),"bytes")
