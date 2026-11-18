"""Reject an Android library set that cannot register the game's facade code."""
from pathlib import Path
import subprocess


def inspect(tool: Path, *args: str | Path) -> str:
    result = subprocess.run([str(tool), *map(str,args)], capture_output=True, text=True)
    if result.returncode:
        raise ValueError(f"Module check failed: {tool.name}: {result.stderr.strip()}")
    return result.stdout


def verify(libraries: list[Path], readelf: Path, nm: Path) -> None:
    files = {p.name: p for p in libraries}
    main = files.get("libmain.so")
    if main is None:
        raise ValueError("Game library is missing")
    symbols = inspect(readelf, "--symbols", "--wide", main)
    image = callback = None
    for line in symbols.splitlines():
        fields = line.split()
        if len(fields) < 8 or fields[6] == "UND":
            continue
        if fields[3] == "OBJECT" and fields[7] == "PPCImageConfig":
            image = int(fields[1], 16)
        if fields[3] == "FUNC" and "RegisterRecompiledModules" in fields[7]:
            callback = int(fields[1], 16)
    if image is None or callback is None:
        raise ValueError("Game module registry is missing; rebuild from a complete generated project")
    # The entrypoint connection is checked by the consumer CMake guard.
    # PPCImageConfig can be initialized by a C++ constructor in .bss, so its
    # on-disk pointer bytes/relocations alone cannot establish that connection.
    for module in ("SpeechFacade", "XMediaFacade"):
        name = f"libpinyon_shift_{module}_default.so"
        if name not in files:
            raise ValueError(f"{module} library is missing")
        symbols = inspect(nm, "--dynamic", "--defined-only", files[name])
        names = {line.split()[-1] for line in symbols.splitlines() if line.split()}
        if not {"ReXModule_Register", "ReXModule_GetImageInfo"} <= names:
            raise ValueError(f"{module} registration exports are missing")
