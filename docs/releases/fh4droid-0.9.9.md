# FH4Droid 0.9.9

Build 919 fixes an Android settings save issue, improves driver recovery, and expands diagnostics for graphics problems. Install the signed APK over 0.9.8 to update. It uses Pinyon Shift 0.9.1 with selected ShiftGlue changes and requires Android 13 or later, an ARMv8.2-A CPU, a compatible Vulkan driver and your supported USA base-game files.

## Fixes and refinements

- Android saves and restores settings at the top level of the TOML file. A custom section can no longer absorb settings added by the launcher. Existing comments, nested values and multiline notes are preserved.
- A failed driver test can offer the last driver that reached gameplay as a one-time retry. The driver selected in Settings is kept.
- Repeated Vulkan draw errors are limited so the original device-loss error remains visible in logs. Frame dumps coordinate with the GPU recorder to avoid corrupting capture files. [Log cap](https://github.com/arcanite24/shiftglue-sdk/commit/f6ada6bb) · [Frame-dump fix](https://github.com/arcanite24/shiftglue-sdk/commit/0aaca257)
- Controller shortcuts track which device is holding a chord. Duplicate input sources trigger the menu once per press. [Controller fix](https://github.com/arcanite24/shiftglue-sdk/commit/49d263937)
- **Match display refresh** chooses a compatible rate up to 60 Hz in Battery Saver. If no compatible mode is available, Android can choose the rate. Problem reports show the FPS target, requested refresh rate and observed display mode.
- **Advanced → Earlier geometry uploads** is an optional renderer experiment. It schedules eligible vertex and index uploads before a render pass to reduce interruptions. The effect depends on the GPU and driver. [Upload scheduling patch](https://github.com/arcanite24/shiftglue-sdk/commit/ab53bece0)

[Full changelog](../../CHANGELOG.md) · [Source and build guide](fork-handoff.md)
