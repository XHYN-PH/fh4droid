#!/usr/bin/env python3
"""Compile Android Java and run the storage/ISO/layout tests on the host JVM."""
import pathlib
import subprocess
import sys
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parent))
import pinyon_android

root=pinyon_android.ROOT
tools=pinyon_android.Tools()
out=root/".local/android/mobile-tests"
out.mkdir(parents=True,exist_ok=True)
sources=sorted((root/"android/java").rglob("*.java"))
sources+=sorted((pinyon_android.rexsdk_dir()/"thirdparty/sdl3/android-project/app/src/main/java").rglob("*.java"))
tests=sorted((root/"tests/android").glob("*Test.java"))
sources+=tests
subprocess.run([str(tools.javac),"--release","8","-nowarn","-classpath",str(tools.android_jar),
    "-encoding","UTF-8","-d",str(out),*map(str,sources)],check=True,timeout=180)
java=tools.javac.with_name("java")
import os
for test in tests:
    subprocess.run([str(java),"-cp",str(out)+os.pathsep+str(tools.android_jar),
        "studio.deimos.pinyonshift."+test.stem],check=True,timeout=60)

# Verify the actual POSIX lock protocol across Java and native writers,
# including stale native snapshots. No game files or Android device needed.
import shutil, tempfile
cxx=shutil.which("c++")
if os.name=="posix" and cxx:
    root_settings=out/"root-settings-test"
    subprocess.run([cxx,"-std=c++23","-O2","-UNDEBUG","-I"+str(root),
        "-I"+str(pinyon_android.rexsdk_dir()/"thirdparty/tomlplusplus/include"),
        str(root/"tests/config/android_root_settings_test.cpp"),"-o",str(root_settings)],
        check=True,timeout=120)
    subprocess.run([str(root_settings)],check=True,timeout=30)
    native=out/"host-config-test"
    subprocess.run([cxx,"-std=c++23","-O2","-UNDEBUG","-I"+str(root/"src"),
        str(root/"tests/config/host_config_test.cpp"),str(root/"src/config/host_config.cpp"),
        str(root/"src/platform/host_platform.cpp"),"-o",str(native)],check=True,timeout=120)
    subprocess.run([str(native)],check=True,timeout=30)
    with tempfile.TemporaryDirectory(prefix="fh4droid-native-java-") as temporary:
        config=pathlib.Path(temporary)/"settings.toml"
        config.write_text("unrelated = 77\n",encoding="utf-8")
        commands=[
            [str(java),"-cp",str(out)+os.pathsep+str(tools.android_jar),
             "studio.deimos.pinyonshift.ConfigConcurrencyTest",str(config),"java_","100"],
            [str(native),"--write-many",str(config),"native_","100"],
        ]
        children=[subprocess.Popen(command) for command in commands]
        try:
            for child in children:
                if child.wait(timeout=30):raise RuntimeError("config writer failed")
        finally:
            for child in children:
                if child.poll() is None:child.kill();child.wait()
        values=dict(line.split(" = ",1) for line in config.read_text().splitlines() if " = " in line)
        assert values.get("unrelated")=="77"
        for prefix in ("java_","native_"):
            for i in range(100):assert values.get(prefix+str(i))==str(i),(prefix,i)
        print("PASS: 201 concurrent native/Java settings checks")
else:
    print("SKIP: POSIX native/Java settings coordination requires a C++23 compiler")
