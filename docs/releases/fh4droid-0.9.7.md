# FH4Droid 0.9.7

This update fixes the sharp rectangle in the road blur around the car. The affected asphalt now uses the same camera motion as the surrounding road, and the car keeps its own blur.

Build 917 uses Pinyon Shift 0.9.1. It requires Android 13 or later, an ARMv8.2-A CPU, a compatible Vulkan driver, and your own supported USA Xbox 360 base-game files. Install the APK over 0.9.6 to update your existing installation.

## Verification

Software Vulkan readback confirmed that the translated game shader gives the affected road pixels the same motion as the surrounding road. The signed ARM64 APK passed the release checks for the signing certificate, alignment, and native dependencies. A gameplay check on a phone remains pending.

[Full changelog](https://github.com/XHYN-PH/fh4droid/blob/v0.9.7/CHANGELOG.md) · [Source and build guide](https://github.com/XHYN-PH/fh4droid/blob/v0.9.7/docs/releases/fork-handoff.md)
