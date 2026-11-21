# FH4Droid 0.9.8

Build 918 adds a system-driver startup fix, steadier display timing and better information when a graphics or settings issue occurs. Install the signed APK over 0.9.7 to update. It uses Pinyon Shift 0.9.1 with selected ShiftGlue changes and requires Android 13 or later, an ARMv8.2-A CPU, compatible Vulkan support and your supported USA base-game files.

## Drivers and display

- Fix the empty clear value that could crash some stock Adreno and Mali drivers at first paint. **Test driver** now clears and presents a real Android surface. [ShiftGlue presenter fix](https://github.com/arcanite24/shiftglue-sdk/commit/23c4d57f04aa864836c5d13cf18f806326c4301b)
- Automatic can try the last driver that reached gameplay once after repeated startup crashes. Drivers with different Vulkan cache UUIDs keep separate pipeline data.
- **Match display refresh** requests an evenly divisible mode at the same panel resolution. Game timing follows compatible display refresh intervals. [Display mode patch](https://github.com/arcanite24/pinyon-shift/pull/1104) · [Display timing and thread names](https://github.com/arcanite24/shiftglue-sdk/pull/18)
- Choose **Snapdragon GSR 1** under **Upscaling filter** to try a spatial upscaler on reduced resolutions. [ShiftGlue GSR integration](https://github.com/arcanite24/shiftglue-sdk/commit/b51a204b1c3aceaaf0d48f933ee21d04a28bee6e)
- **Mali texture compression** is an opt-in for GPUs that support ASTC and lack BC. It saves texture memory and can change the appearance of translucent edges. [ASTC transcode](https://github.com/arcanite24/shiftglue-sdk/pull/13)
- Limit PowerVR's reported sampler count and use a compatible shader path. Some terrain artifacts still need investigation on Pixel 10. [PowerVR patch](https://github.com/arcanite24/shiftglue-sdk/pull/15)

## Settings and problem reports

- Android validates changes with the same TOML parser the game uses. Startup can recover the last valid settings saved by the app and preserve the unreadable file for inspection.
- See which values are running, which are saved for the next launch and when a menu is using original proportions. The in-game Advanced resolution control uses the same one-percent steps as the launcher.
- **Problem reports** can start a session with mods off or compare the selected Turnip driver with its defaults, system memory only, or no preamble only. A capture for car paint or glass records the chosen driver and current settings and can include a game frame.
- Reports retain startup checkpoints and warnings across rotated runtime logs. The Mods page shows manifest problems on affected mods.

[Full changelog](../../CHANGELOG.md) · [Source and build guide](fork-handoff.md)
