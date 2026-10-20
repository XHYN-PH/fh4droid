# FH4Droid 0.6.0-r1 — Android test release

An Android-focused fork of Pinyon Shift 0.6.0 for **Forza Horizon (2012), the original Xbox 360 game**. This is a test prerelease. It has not been tested on a physical Android device.

## Install

1. Install `FH4Droid-0.6.0-r1-arm64.apk` and open **FH4Droid**.
2. Tap **Choose folder** and select your complete extracted game folder, containing `default.xex` and all the game's media files. Extract an ISO first.
3. Let the files finish importing, then tap **Play**.

No compilation is needed on the phone. You must supply your own matching USA retail game assets: title ID `4D5309C9`, serial `MS-2505`. The APK contains recompiled executable code; it does not include the game's remaining media assets, disc image or title updates.

This is the base-game build, not title update v4. A save already used with title update v4 cannot be loaded by the base build. Back up existing saves and use a separate test profile.

Keep about 8 GB free for the imported files. Importing a folder already on the phone makes another copy inside the app; both copies use space until you remove the original yourself.

## Requirements

- Android 13+ (API 33), ARM64 and an ARMv8.2 CPU.
- A Vulkan 1.3-capable GPU and driver.
- Upstream targets Snapdragon 8 Gen 2 or newer. Device compatibility and speed remain unverified for this build.

## Included

- FH4Droid app name and separate application ID, `ph.xhyn.fh4droid`.
- Upstream native ARM64 runtime, Vulkan renderer, controller support and touch controls.
- Extracted-folder importer.
- Original Turnip Gen8 V37 by StevenMXZ, with its notice and verified archive checksum. Automatic selection applies on supported Adreno GPUs; other GPUs use the system driver.
- 16 KB-aligned native libraries and dynamic physical-memory mapping. The build does not force a 4 KB-page kernel. Testing on both page sizes is pending.

The runtime and renderer are from [Pinyon Shift](https://github.com/arcanite24/pinyon-shift) and [ShiftGlue](https://github.com/arcanite24/shiftglue-sdk). No performance improvement over upstream is claimed. This fork focuses on Android only.

## Verification and limitations

Signature, ARM64 library contents, required native dependencies, 16 KB ELF alignment and APK ZIP alignment passed validation. Android tooling tests passed 22 checks with one Windows-only check skipped on Linux. The documentation built 37 pages and passed the Markdown link check.

Compilation and packaging checks do not verify gameplay, graphics correctness, thermal behavior or frame rate. Those need physical-device testing.

APK application version: `0.6.0` (version code `600`). The `r1` suffix identifies this fork's release.

Build source commit: `fb2aa3d1f35e8815521786103c1f1be75bf22e96`.

Upstream base: `9331e453e9850d0fcc1c1606dee95db834a9d317`.

ShiftGlue SDK: `79d3e6fae4b79e00def48a595c80f0eaa47fd2b6`.

APK SHA-256:

```text
a03c1a728ad1993b05fc50aa485c55dfeed9a14c8970771b478988417777a4e8
```

Signing certificate SHA-256:

```text
113ab182105837ea5776d8efbf8cc84281a5708a630f9a241945d3c7feb5e075
```

For bug reports, include the device, Android version, GPU driver, graphics preset, relevant logs and the action that triggered the problem. Remove personal information and do not attach game files or saves.
