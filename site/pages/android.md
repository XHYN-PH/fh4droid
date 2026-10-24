---
title: FH4Droid on Android
section: Playing
order: 2
description: FH4Droid launcher, game files, settings, controls and troubleshooting on Android.
---


FH4Droid focuses on the Android port of Forza Horizon (2012), Xbox 360. The 0.6.5-r4 shell and renderer changes use the fresh source and have not been gameplay-tested on a physical device. Upstream measurements describe their named devices and builds; they are not benchmarks for this build.

## Launcher

The launcher uses the supplied driving background, the transparent logo at the upper right and a red-to-orange accent. The line beneath Ready to drive shows saved resolution, frame limit and driver. Menu animations respect Android's animator setting and can be disabled in Settings → Display. There is no continuous launcher animation during gameplay.

Settings groups Graphics, Display, Audio, Controls and Gameplay. Changes save automatically. Restart-labelled options apply on the next launch. Drivers are tested in a fresh helper process before activation and again before Play. A native driver crash or timeout leaves the launcher separate from that process. Recovery offers the system driver; a successful preflight does not prove rendering compatibility.

The launcher scrolls on short screens and stacks its main buttons on narrow screens or with larger text. It recalculates navigation when the window size or orientation changes. Keyboard/controller focus has a visible outline. Settings presets and MSAA choices are written in one atomic transaction; a failed write leaves the displayed value unchanged. Missing shadows/filtering settings show the Android runtime defaults rather than a different menu default. Quoted advanced settings decode TOML escapes, including quotes, backslashes and Unicode, so driver preflight receives the saved string values.

Preflight and gameplay share the custom-driver loader: both copy into internal storage, rename packaged library identities, and use the saved driver environment options. A failed custom-driver load is rejected rather than silently passing as the system driver. Bundled driver updates compare content before replacement, even when file sizes match.

## Game files

Use Game files → Choose game ISO or Choose extracted game folder, with the player's own supported USA base-game dump. The ISO stays in place with persistent document permission and a seekable descriptor held by the game process. The game mounts the SDK's read-only disc-image device. Only executables and host UI/audio support files (about 100 MB, allow 150 MB free) are copied locally. Keep the ISO accessible while playing. The bounded Java inspector and native reader reject truncated directories and cycles.

Folder import compares all catalogued paths and sizes, verifies the executables before replacing an installation, verifies SHA-256 on copied/resumed files and checks space for the files still needing a copy. A partial import remains marked incomplete. Check installed game files performs a complete content check; it can take several minutes.

Import/check screens show current-file progress and offer cooperative cancellation between reads. Completed files are kept; choose the same source to resume a stopped folder or Wi-Fi copy. Successful folder copies clear the previous ISO selection only after the copy finishes. Cancellation may wait for a file provider or network read to return. In ISO mode, the existing content check covers the disc image; it does not also check the locally cached support files.

For PC sharing, choose Game files → Copy from PC over Wi-Fi. The original paired local-network workflow remains available. File-replacing overlays work in ISO mode, but mods which add files to directory enumerations should use the complete extracted folder.

## Rendering resolution

Graphics offers 50%, 75% and 100% raster resolution. Native attachment dimensions, viewport/scissor coordinates, transfers and source reads scale together. Resolves retain the guest texture layout; guest code and game timing do not change. The default is 100%.

Fractional resolutions use separate shader/pipeline cache directories. The existing precompiled SPIR-V pack is used at 100%; lower resolutions translate their own variants and reuse their caches on subsequent launches. No FPS gain has been established for this build. Compare the same route, captures and thermal conditions before recommending a preset. Bilinear remains the inexpensive default output filter; sharper filters add GPU work.

## Controls

Touch steering defaults to separate left and right arrow buttons. Holding both gives neutral steering; sliding between them changes direction, and moving outside them releases steering. Accelerator and brake use pedal pictograms. The other controller buttons keep their existing mappings, with drawn symbols instead of game-font glyphs, so touch icons do not become question marks when a font glyph is unavailable. Touch drawing does not require the game's font archive.

Controls opens a landscape touch editor with normalized positions, resizing, hiding, opacity and reset. Steering selects Arrows or the original Stick; switching keeps the saved positions for both. Older layouts retain their button positions, with arrows enabled by default. Extended layouts from the reference hotfix keep their steering-button positions separately from the D-pad positions, and retain an existing stick selection. The editor uses the same icon paths as gameplay and shows the display safe area. Saving writes the layout atomically; cancelling keeps the previous layout. Reset restores arrows and keeps the controller auto-hide switch in sync.

Physical controller detection can hide the touch pad. Otherwise, controls stay visible while a finger is held and fade after 20 seconds without input after release. Touch cancellation, menu opening and controller changes release held touch inputs. Native input tests and host-JVM layout tests cover these transitions; physical-device gameplay and contrast checks remain pending.

The touch editor groups tools into Placement, Driving and Appearance, and scrolls when text or window size needs more space. It displays position, size, opacity and steering response; position updates while dragging. Select control and Move buttons allow editing without dragging, with spoken value feedback. Driving offers immediate, 120 ms and 240 ms arrow response. Immediate remains the default; opposite arrows always give neutral steering. Optional sliding pedals start at half input, increase when dragged up and decrease when dragged down. High contrast raises touch-icon visibility, and optional touch vibration follows Android's haptic preference. Appearance shows both the chosen opacity and the effective high-contrast opacity.

Tap ☰ or Android Back during play for Resume, Settings, touch editing, problem reports and Quit. The host menu captures guest input and signals the same system-UI state as native host settings. Advanced game settings opens the original host menu, including controller mapping and other existing features.

## Saves

Saves exports the standard base-game user profile as a save-only ZIP. Imports validate the entire archive in staging, reject path escapes/duplicates and retain the previous user tree before an atomic replacement. Restore keeps the current tree so the operation can be reversed. Quit the game before save management. Separate modded/cheat profiles remain available through Advanced game settings. Base-game and TU4 saves are incompatible.

## Reports and recovery

Reports → Play with logs enables native performance logging for that session. Share latest problem report exports bounded recent logs, device and settings details, the driver preflight result and Android exit reasons for the game process. Repeated export taps are blocked while a report is prepared. Each share points to a separate completed ZIP, so a later export does not rewrite a report another app is reading. Intentional driver-probe termination is excluded. Normal play leaves detailed performance reporting off. Reports contain no saves or game media; review them before sharing.

The launcher and game run in different processes. A session marker distinguishes a normal exit from an interrupted game; recovery offers a system-driver launch or a report. Android low-memory kills and native crashes are different failure causes and should not be treated as proof of a driver problem.

An intentional native window close records a clean exit before the runtime's hard termination. Finishing the game activity also records that intent before SDL teardown, so a normal quit does not depend on Java cleanup running after native shutdown.

## Build and implementation notes

The pinned ShiftGlue SDK remains at 79d3e6f. Reviewed runtime changes and regenerated Vulkan helpers are stored in cmake/patches and applied idempotently before configuring the source SDK. This avoids a submodule commit unavailable from the upstream remote. Regenerate helpers with compile-fh1-native.sh (SPIRV_ONLY=1 and GLSLANG set), then run tools/update-android-sdk-patch.py after reviewing the SDK worktree.

The ARMv8.2 baseline, Android 13 minimum and 16 KiB native/ZIP alignment requirements are unchanged. Run tools/test-android-mobile.py and the renderer/parser host tests before packaging. No physical-device or sustained performance claim follows from compilation or SPIR-V validation.

A reviewed generated-code snapshot can be built with `PINYON_SHIFT_FROZEN_CODEGEN=ON`; this mode requires the complete generated modules and stamp, but not the standalone generator. It does not regenerate code when game inputs or generator settings change. Keep generated game code private and use the matching source/SDK revisions.

CI also checks shared touch geometry, runs the Java mobile suite with the pinned platform/JDK, and compiles the Android driver probe and native UI bridge without game-generated source. The separately maintained shared-driver loader patch is retained when shader patches are regenerated.

[Player setup and limitations](https://github.com/XHYN-PH/fh4droid/blob/main/docs/releases/fh4droid-0.6.5-r4.md). [Upstream Android research](https://github.com/arcanite24/pinyon-shift/blob/dev/docs/ANDROID_PORT_BACKLOG.md).
