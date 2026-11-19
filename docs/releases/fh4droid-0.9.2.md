# FH4Droid 0.9.2

This release uses Pinyon Shift 0.9.1. It targets Android 13 or newer on ARM64 devices with ARMv8.2-A and a compatible Vulkan driver. Use your own supported Xbox 360 USA base-game files.

## What changed

- The four home cards match the Settings category cards. Settings opens one category at a time, with help on the right and LB/RB to switch categories. Controls opens the same hub from either menu.
- The full-screen touch editor has movable tools, an Add button picker for hidden controls, and opacity for each control. Test mode matches the invisible look stick's in-game touch area.
- Save ZIPs can contain up to 4 GiB of expanded save data. Restoring a selected backup can return a profile to its earlier empty state. The game can recover interrupted save swaps while preserving installed DLC.
- Detailed session snapshots run through **More → Problem reports → Play with diagnostics**. Report writes run off the game activity's UI thread. Performance comparisons flag slow frames at both p95 and p99.
- Play waits for the known graphics pipelines and Vulkan cache. Startup explains whether a cache storage problem or a shader preparation failure blocked play.
- **Game files → Installation** checks the disc image and local copies of the files the launcher and game need. Driver selection is under **More** and graphics cache management is under **Game files → Storage**.

## Verification

Cloud validation covers controller navigation, Android UI rendering, save import and interruption recovery, source checks, software Vulkan, and APK signing and upgrade identity. The cloud runner has no Android emulator or phone. Gameplay on Android hardware still needs a device test.
