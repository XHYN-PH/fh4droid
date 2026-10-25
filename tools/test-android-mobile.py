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
