#!/usr/bin/env python3
"""Run graphics preset and screen-mode transactions on a host JDK."""
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
output = root / ".local/android/graphics-tests"
output.mkdir(parents=True, exist_ok=True)
java = shutil.which("java")
if not java:
    raise SystemExit("A JDK is required to run graphics settings checks.")
javac = shutil.which("javac")
compiler = [javac] if javac else [java, "com.sun.tools.javac.Main"]
subprocess.run(compiler + ["--release", "8", "-encoding", "UTF-8", "-d", str(output),
    str(root / "android/java/studio/deimos/pinyonshift/GraphicsProfiles.java"),
    str(root / "tests/android/GraphicsProfilesTest.java")], check=True)
subprocess.run([java, "-cp", str(output),
    "studio.deimos.pinyonshift.GraphicsProfilesTest"], check=True)
