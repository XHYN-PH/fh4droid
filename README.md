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

**Testing build: 0.6.1-r2.** This build has not been tested on a physical Android device. Expect possible crashes, graphics issues and slowdowns.

**You must provide your own legally obtained game files.** Use the supported USA Xbox 360 base-game ISO or complete extracted folder. Game files and game-download links are not provided here.

## Features

- A new launcher with the driving background, transparent logo, red-to-orange theme and optional animations.
- Current graphics settings beneath **Ready to drive**, plus an in-game menu.
- Graphics, display, audio, controls and gameplay settings, with restart labels.
- 50%, 75% and 100% rendering resolution options.
- Movable, resizable touch controls, adjustable opacity and automatic hiding with a controller.
- Save export, import, backups and restoration from your phone.
- A graphics-driver test before launch, system-driver recovery and shareable problem reports.
- Direct ISO loading or verified game-folder import with resume and repair.
- Installs separately from the original Pinyon Shift app.

## Requirements

| Item | Requirement |
|---|---|
| Android | Android 13 or newer on a compatible 64-bit phone or handheld |
| Recommended device | Snapdragon 8 Gen 2 or newer |
| Graphics | Vulkan 1.3 support |
| Storage | ISO: room for your ISO plus about 150 MB of support files. Folder import: about 8 GB plus the original folder and APK |
| Game | Forza Horizon (2012), Xbox 360 USA retail version, without Title Update 4 |

Support and performance depend on the phone and graphics driver. Other devices are untested.

## Getting started

1. Install the APK, then open **FH4Droid → Game files**.
2. Choose your supported game **ISO**, or choose the **complete extracted folder**.
3. Wait for the game-file checks and any copying to finish.
4. Open **Settings** to choose graphics, audio and controls. Restart labels show what applies on the next launch.
5. Tap **Play**. The app tests the selected graphics driver before starting.

Keep the ISO accessible while playing. ISO mode caches only the menu and audio configuration files; it does not extract the whole game. Use folder mode for mods that add new files.

While playing, tap **☰** or press Android Back for the in-game menu. Choose **Advanced game settings** for the full game settings and controller mapping. **Controls** on the launcher opens the touch editor.

Use **Saves** to export or restore the standard base-game profile while the game is closed. Mods or cheats can use separate profiles managed in Advanced game settings. Use **Reports → Play with logs** when reproducing a problem, then **Share latest problem report**.

The new rendering resolutions, ISO path and menus still need physical-device gameplay testing. First visits to a scene may pause while graphics are prepared. Performance targets are not guaranteed frame rates.

**Title Update 4 is not needed for this release.** Saves already used with Update 4 will not work with this APK. Back up your saves and use a separate test profile. Game files and saves from the original Pinyon Shift app are not transferred automatically.

See the [release notes](docs/releases/fh4droid-0.6.1-r2.md) for setup and known issues.

## Reporting a problem

Include your phone model, Android version, graphics settings and what happened. Add a screenshot or short video for visual problems, and app logs if available. Remove personal information before sharing a report. Do not attach game files or saves.

## Credits

- [arcanite24/Pinyon Shift](https://github.com/arcanite24/pinyon-shift) for the original project and Android port.
- [arcanite24/ShiftGlue](https://github.com/arcanite24/shiftglue-sdk), ReXGlue and their contributors.
- StevenMXZ and Mesa contributors for the bundled Turnip graphics driver. See the [driver notice](android/drivers/turnip-gen8-v37/NOTICE.txt).
- SDL, libadrenotools, FidelityFX and other contributors. See [third-party notices](THIRD_PARTY_NOTICES.md).
- XHYN-PH for this Android fork and its releases.
- [XHYN IV](https://github.com/XHYN-PH/XHYN-IV-recomp), [FH2 Recompiled](https://github.com/Gabrick75/fh2-recompiled) and the NFS Android ports for workflow references.

This unofficial project is not affiliated with Microsoft, Xbox, Turn 10 Studios or Playground Games. Forza Horizon belongs to its respective rights holders. Project code uses the [BSD 3-Clause License](LICENSE); other components retain their own licenses. See [legal notes](docs/LEGAL.md).

[Developer documentation](docs/ANDROID.md) · [Original project README](README_UPSTREAM.md) · [XHYN IV](https://github.com/XHYN-PH/XHYN-IV-recomp) · [XHYN V Reborn](https://github.com/XHYN-PH/XHYN-V-reborn)
