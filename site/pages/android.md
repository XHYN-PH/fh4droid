---
title: FH4Droid on Android
section: Playing
order: 2
description: FH4Droid launcher, game files, settings, controls and troubleshooting on Android.
---


FH4Droid focuses on the Android port of Forza Horizon (2012), Xbox 360. The 0.6.1-r2 shell and renderer changes are implemented but have not been gameplay-tested on a physical device. Upstream measurements describe their named devices and builds; they are not benchmarks for this build.

## Launcher

The launcher uses the supplied driving background, the transparent logo at the upper right and a red-to-orange accent. The line beneath Ready to drive shows saved resolution, frame limit and driver. Menu animations respect Android's animator setting and can be disabled in Settings → Display. There is no continuous launcher animation during gameplay.

Settings groups Graphics, Display, Audio, Controls and Gameplay. Changes save automatically. Restart-labelled options apply on the next launch. Drivers are tested in a fresh helper process before activation and again before Play. A native driver crash or timeout leaves the launcher separate from that process. Recovery offers the system driver; a successful preflight does not prove rendering compatibility.

## Game files

Use Game files → Choose game ISO or Choose extracted game folder, with the player's own supported USA base-game dump. The ISO stays in place with persistent document permission and a seekable descriptor held by the game process. The game mounts the SDK's read-only disc-image device. Only executables and host UI/audio support files (about 100 MB, allow 150 MB free) are copied locally. Keep the ISO accessible while playing. The bounded Java inspector and native reader reject truncated directories and cycles.

Folder import compares all catalogued paths and sizes, verifies the executables before replacing an installation, verifies SHA-256 on copied/resumed files and checks space for the files still needing a copy. A partial import remains marked incomplete. Check installed game files performs a complete content check; it can take several minutes.

For PC sharing, choose Game files → Copy from PC over Wi-Fi. The original paired local-network workflow remains available. File-replacing overlays work in ISO mode, but mods which add files to directory enumerations should use the complete extracted folder.

## Rendering resolution

Graphics offers 50%, 75% and 100% raster resolution. Native attachment dimensions, viewport/scissor coordinates, transfers and source reads scale together. Resolves retain the guest texture layout; guest code and game timing do not change. The default is 100%.

Fractional resolutions use separate shader/pipeline cache directories. The existing precompiled SPIR-V pack is used at 100%; lower resolutions translate their own variants and reuse their caches on subsequent launches. No FPS gain has been established for this build. Compare the same route, captures and thermal conditions before recommending a preset. Bilinear remains the inexpensive default output filter; sharper filters add GPU work.

## Controls

Controls opens a landscape touch editor with normalized positions, resizing, hiding, opacity and reset. Saving writes the layout atomically; cancelling keeps the previous layout. Physical controller detection can hide the touch pad. Touch cancellation, menu opening and controller changes release held touch inputs.

Tap ☰ or Android Back during play for Resume, Settings, touch editing, problem reports and Quit. The host menu captures guest input and signals the same system-UI state as native host settings. Advanced game settings opens the original host menu, including controller mapping and other existing features.

## Saves

Saves exports the standard base-game user profile as a save-only ZIP. Imports validate the entire archive in staging, reject path escapes/duplicates and retain the previous user tree before an atomic replacement. Restore keeps the current tree so the operation can be reversed. Quit the game before save management. Separate modded/cheat profiles remain available through Advanced game settings. Base-game and TU4 saves are incompatible.

## Reports and recovery

Reports → Play with logs enables native performance logging for that session. Share latest problem report exports bounded recent logs, device and settings details, the driver preflight result and Android exit reasons for the game process. Intentional driver-probe termination is excluded. Normal play leaves detailed performance reporting off. Reports contain no saves or game media; review them before sharing.

The launcher and game run in different processes. A session marker distinguishes a normal exit from an interrupted game; recovery offers a system-driver launch or a report. Android low-memory kills and native crashes are different failure causes and should not be treated as proof of a driver problem.

## Build and implementation notes

The pinned ShiftGlue SDK remains at 79d3e6f. Reviewed runtime changes and regenerated Vulkan helpers are stored in cmake/patches and applied idempotently before configuring the source SDK. This avoids a submodule commit unavailable from the upstream remote. Regenerate helpers with compile-fh1-native.sh (SPIRV_ONLY=1 and GLSLANG set), then run tools/update-android-sdk-patch.py after reviewing the SDK worktree.

The ARMv8.2 baseline, Android 13 minimum and 16 KiB native/ZIP alignment requirements are unchanged. Run tools/test-android-mobile.py and the renderer/parser host tests before packaging. No physical-device or sustained performance claim follows from compilation or SPIR-V validation.

[Player setup and limitations](https://github.com/XHYN-PH/fh4droid/blob/main/docs/releases/fh4droid-0.6.1-r2.md). [Upstream Android research](https://github.com/arcanite24/pinyon-shift/blob/dev/docs/ANDROID_PORT_BACKLOG.md).
