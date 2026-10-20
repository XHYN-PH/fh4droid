<p align="center">
  <img src=".github/fh4droid-logo.png" width="420" alt="FH4Droid logo">
</p>

<h1 align="center">FH4Droid</h1>

<p align="center">Forza Horizon (2012) for Android.<br>Based on Pinyon Shift and maintained by XHYN-PH.</p>

<p align="center">
  <a href="https://github.com/XHYN-PH/fh4droid/releases">Download APK</a> ·
  <a href="https://youtube.com/@xhyn_ph">YouTube</a> ·
  <a href="https://t.me/xhynports">Telegram</a>
</p>

**FH4Droid focuses on Android only.** It brings the original Xbox 360 **Forza Horizon (2012)** to compatible phones and handhelds.

**Current testing release: 0.6.0-r1.** This build has not been tested on a physical Android device. Expect possible crashes, graphics issues and slowdowns.

**You must provide your own legally obtained game files.** You need the complete extracted folder from the USA Xbox 360 release. Game files and game-download links are not provided here.

## Features

- Touch controls and physical controller support.
- Adjustable graphics settings and Android graphics presets.
- Import your game folder directly in the app.
- A bundled graphics driver for supported Adreno devices.
- Installs separately from the original Pinyon Shift app.

## Requirements

| Item | Requirement |
|---|---|
| Android | Android 13 or newer on a compatible 64-bit phone or handheld |
| Recommended device | Snapdragon 8 Gen 2 or newer |
| Graphics | Vulkan 1.3 support |
| Storage | About 8 GB free for the imported game, plus room for the original folder and APK |
| Game | Forza Horizon (2012), Xbox 360 USA retail version, without Title Update 4 |

Support and performance depend on the phone and graphics driver. Other devices are untested.

## Getting started

1. Download and install the APK from [Releases](https://github.com/XHYN-PH/fh4droid/releases).
2. Extract your own game on a PC first. Use the complete folder, including `default.xex` and all other game files.
3. Copy the complete extracted folder to the phone.
4. Open **FH4Droid**, tap **Choose folder**, and select that folder.
5. Wait for the import to finish, then tap **Play**.

The app makes its own copy of the game files, so you need space for both copies during import. Extract an ISO before choosing the folder; the app cannot import an ISO directly.

**Title Update 4 is not needed for this release.** Saves already used with Update 4 will not work with this APK. Back up your saves and use a separate test profile. Game files and saves from the original Pinyon Shift app are not transferred automatically.

See the [release notes](docs/releases/fh4droid-0.6.0-r1.md) for setup and known issues.

## Reporting a problem

Include your phone model, Android version, graphics settings and what happened. Add a screenshot or short video for visual problems, and app logs if available. Remove personal information before sharing a report. Do not attach game files or saves.

## Credits

- [arcanite24/Pinyon Shift](https://github.com/arcanite24/pinyon-shift) for the original project and Android port.
- [arcanite24/ShiftGlue](https://github.com/arcanite24/shiftglue-sdk), ReXGlue and their contributors.
- StevenMXZ and Mesa contributors for the bundled Turnip graphics driver. See the [driver notice](android/drivers/turnip-gen8-v37/NOTICE.txt).
- SDL, libadrenotools, FidelityFX and other contributors. See [third-party notices](THIRD_PARTY_NOTICES.md).
- XHYN-PH for this Android fork and its releases.

This unofficial project is not affiliated with Microsoft, Xbox, Turn 10 Studios or Playground Games. Forza Horizon belongs to its respective rights holders. Project code uses the [BSD 3-Clause License](LICENSE); other components retain their own licenses. See [legal notes](docs/LEGAL.md).

[Developer documentation](docs/ANDROID.md) · [Original project README](README_UPSTREAM.md) · [XHYN IV](https://github.com/XHYN-PH/XHYN-IV-recomp) · [XHYN V Reborn](https://github.com/XHYN-PH/XHYN-V-reborn)
