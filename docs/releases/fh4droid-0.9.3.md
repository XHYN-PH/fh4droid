# FH4Droid 0.9.3

FH4Droid 0.9.3 uses Pinyon Shift 0.9.1. It targets Android 13 or newer on ARM64 devices with ARMv8.2-A and a compatible Vulkan driver. Use your own supported Xbox 360 USA base-game files.

## What changed

- **Launch from ISO.** The game reads the ISO through the file Android already opened. This addresses the startup error where the runtime could read a selected disc image during setup but could not map it for play.
- **Upscaling filter.** Choose **Settings → Graphics → Upscaling filter → Off (nearest)** to scale the image to the display without smoothing. It takes effect live. Render resolution and FXAA are separate settings.
- **Settings feedback.** FSR presets, screen modes, MSAA, touch style and steering update their displayed values as soon as they save. Search includes advanced settings and audio actions. **Shader workers** controls pre-play preparation.

## Verification

Cloud validation exercised 232 choices across 55 settings and passed 25 Android UI tests. Native disc tests covered descriptor reads, truncated images and 2,500 malformed fixtures. A software Vulkan readback verified the difference between Bilinear and nearest sampling. The signed APK was checked against 0.9.2 for upgrade identity. ISO launch and visual quality on Android hardware still need a phone test.
