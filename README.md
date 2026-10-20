<h1 align="center">FH4Droid</h1>

<p align="center">Forza Horizon (2012) on Android through static recompilation.<br>An Android-focused fork of Pinyon Shift, maintained by XHYN-PH.</p>

<p align="center">
  <a href="https://github.com/XHYN-PH/fh4droid/releases">Download APK</a> ·
  <a href="https://youtube.com/@xhyn_ph">YouTube</a> ·
  <a href="https://t.me/xhynports">Telegram</a>
</p>

**This fork focuses on Android only.** Windows, Linux and macOS code inherited from upstream remains in the source for shared runtime and build tooling. Desktop releases and desktop support are outside this fork's focus.

**Current testing release: 0.6.0-r1.** This is the original Xbox 360 **Forza Horizon (2012)**. This build has not been tested on a physical Android device. Expect crashes, graphics issues and performance problems while testing; no frame-rate claim is made for this release.

**You must provide your own legally obtained, complete extracted game files.** The APK contains the recompiled executable code but does not include the game's media assets, disc image or title updates. No game-download links are provided.

## Features

- Native ARM64 execution through Pinyon Shift and ShiftGlue static recompilation.
- Vulkan rendering, with upstream graphics options and Android presets.
- Touch controls and physical controller support inherited from upstream.
- An extracted-folder importer: install the APK, choose your game folder and play without compiling on the phone.
- Bundled Turnip Gen8 V37 by StevenMXZ, with its notice, for supported Adreno GPUs.
- A separate FH4Droid app identity, `ph.xhyn.fh4droid`, so it installs alongside upstream Pinyon Shift.
- 16 KB-aligned native libraries and dynamic physical-memory mapping. Device testing on both 4 KB and 16 KB kernels is pending.

These are inherited runtime features plus this fork's Android packaging changes. This release does not claim better performance than upstream.

## Requirements

| Item | Requirement |
|---|---|
| Android | Android 13 or newer, 64-bit (API 33+) |
| CPU | ARM64 with ARMv8.2 support |
| Graphics | Vulkan 1.3-capable GPU and driver |
| Device target | A high-end phone or handheld; upstream targets Snapdragon 8 Gen 2 or newer |
| Storage | About 8 GB free for the imported game, plus space for the APK and the original folder |
| Game | Forza Horizon (2012), Xbox 360 USA retail base edition, serial `MS-2505`, title ID `4D5309C9` |

Compatibility depends on the phone, Android version and graphics driver. Other GPUs use the system driver unless a compatible driver is selected. A matching specification does not guarantee that this untested build will work on a particular device.

## Getting started

1. Download `FH4Droid-0.6.0-r1-arm64.apk` from [Releases](https://github.com/XHYN-PH/fh4droid/releases) and install it.
2. Extract your own matching game on a PC first. The folder must contain `default.xex` and all the game's media files; the three executable modules alone are not enough to play.
3. Copy the complete extracted folder to the phone.
4. Open **FH4Droid**, tap **Choose folder**, and select that folder.
5. Wait for the import to finish, then tap **Play**.

The importer copies the files into the app, so the original folder and imported copy both use storage until you remove the original yourself. Selecting an ISO directly is not supported by this import flow.

This is a **base-game build**, not a title-update-v4 build. A save already used with title update v4 cannot be loaded by the base build. Back up saves and use a separate test profile. The separate app ID does not automatically migrate game files or saves from Pinyon Shift.

## Testing status

The APK passed signature, ARM64 dependency, ZIP alignment and 16 KB ELF alignment checks. Android tooling tests passed 22 checks, with one Windows-only check skipped on Linux. Gameplay, thermal behavior, frame rate and device compatibility still need physical-device testing.

See the [0.6.0-r1 release notes](docs/releases/fh4droid-0.6.0-r1.md) for the exact APK checksum, build source and limitations.

## Reporting a problem

Include your phone model, GPU, Android version, graphics driver, graphics preset and the steps that cause the problem. Include relevant app logs and a screenshot or short video for rendering issues. Remove personal information before sharing a report. Do not attach game files, saves or signing keys.

## Source and credits

The source tree contains the launcher, Android interface, host runtime, renderer, build tools and configuration. Original game modules, generated translations, game assets, compiled libraries and signing keys are excluded from Git. The APK release contains compiled translations of the game executable; importing assets separately does not change that.

- [arcanite24/Pinyon Shift](https://github.com/arcanite24/pinyon-shift) for the original Forza Horizon recompilation project, runtime, renderer, launcher and Android port.
- [arcanite24/ShiftGlue](https://github.com/arcanite24/shiftglue-sdk), built on ReXGlue and the work of the wider Xbox 360 recompilation community, for translation and runtime support.
- StevenMXZ and Mesa contributors for Turnip Gen8 V37. See the [driver notice](android/drivers/turnip-gen8-v37/NOTICE.txt).
- SDL, libadrenotools, FidelityFX and other dependency authors. See [third-party notices](THIRD_PARTY_NOTICES.md).
- XHYN-PH for this fork's Android identity, packaging and release maintenance.

Independently authored project code is distributed under the [BSD 3-Clause License](LICENSE). Third-party components retain their own licenses. Forza Horizon, Xbox, Microsoft, Turn 10 Studios and Playground Games belong to their respective owners. This unofficial project is not affiliated with or endorsed by them. See [legal and distribution notes](docs/LEGAL.md).

[Android build documentation](docs/ANDROID.md) · [Original upstream README](README_UPSTREAM.md) · [XHYN IV](https://github.com/XHYN-PH/XHYN-IV-recomp) · [XHYN V Reborn](https://github.com/XHYN-PH/XHYN-V-reborn)
