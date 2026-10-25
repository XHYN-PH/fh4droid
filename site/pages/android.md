---
title: FH4Droid on Android
section: Playing
order: 2
description: FH4Droid launcher, game files, settings, controls and troubleshooting on Android.
---


FH4Droid focuses on the Android port of Forza Horizon (2012), Xbox 360. The 0.6.6-r5 shell and renderer changes use the fresh source and have not been gameplay-tested on a physical device. Upstream measurements describe their named devices and builds; they are not benchmarks for this build.

## Launcher

The compact launcher puts Play and the main actions at the lower left, with brighter branding and a settings summary. Pages open on the right on wide screens and fill the window on narrow screens or with large text. In-game pages use a right-side drawer with a sticky Resume action. Back returns through the page stack and restores scroll and focus. Menu scale is adjustable from 70% to 105%; optional transitions honour Android's animator setting.

Settings has categories, search and pinned favourites. Rows distinguish active runtime values from saved next-launch changes. Section resets require confirmation, and Undo restores the last section change. Presets write as one atomic transaction; failed writes leave the displayed value unchanged. Advanced quoted settings decode TOML escapes before preflight.

Drivers are tested in a fresh helper process before activation and before Play. Results show device/API information, required feature checks and a small render/readback test. Preflight and gameplay share the custom loader and saved environment. Failed custom loads do not silently select the system driver. Readable imported-driver names and confirmed removal are available; removing the active imported driver selects System. A successful probe does not establish gameplay compatibility.

Graphics cache management lists only recognized shader and raster caches, and requires the game to be closed before deletion. It never selects saves, imported game files or the Rally adapter. First visits after clearing can pause while graphics are prepared.

## Game files

Use Game files → Choose game ISO or Choose extracted game folder, with the player's own supported USA base-game dump. The ISO stays in place with persistent document permission and a seekable descriptor held by the game process. The game mounts the SDK's read-only disc-image device. Only executables and host UI/audio support files (about 100 MB, allow 150 MB free) are copied locally. Keep the ISO accessible while playing. The bounded Java inspector and native reader reject truncated directories and cycles.

Folder import compares all catalogued paths and sizes, verifies the executables before replacing an installation, verifies SHA-256 on copied/resumed files and checks space for the files still needing a copy. A partial import remains marked incomplete. Check installed game files performs a complete content check; it can take several minutes.

Import/check screens show current-file progress and offer cooperative cancellation between reads. Completed files are kept; choose the same source to resume a stopped folder or Wi-Fi copy. Successful folder copies clear the previous ISO selection only after the copy finishes. Cancellation may wait for a file provider or network read to return. In ISO mode, the existing content check covers the disc image; it does not also check the locally cached support files.

For PC sharing, choose Game files → Copy from PC over Wi-Fi. The original paired local-network workflow remains available. File-replacing overlays work in ISO mode, but mods which add files to directory enumerations should use the complete extracted folder.

## Rendering resolution

Graphics offers raster resolution from 25% to 100% in one-percent steps, custom frame limits, FSR output profiles, screen modes and graphics presets. Presets coordinate related values and clear incompatible settings from the previous preset. The default raster scale is 100%.

Native attachment dimensions, viewport/scissor, depth/colour transfers and source reads scale together. Guest resolve destinations retain their original layout. Fractional resolutions use separate raster-v3 shader/pipeline caches; the precompiled SPIR-V pack is used only at 100%. Shader preparation is scheduled with bounded concurrency, and detailed GPU timestamp profiling is opt-in.

A software Vulkan readback suite checked 953,854 colour/depth values across every supported scale, including generic and specialized paths. Vulkan 1.2 extension and 1.3 core probe paths passed on that software driver. These checks establish neither phone-driver compatibility nor sustained FPS. No performance gain is claimed. Compare the same route, captures, duration and thermal conditions before recommending a preset.

## Controls

Touch steering defaults to separate left and right arrow buttons. Holding both gives neutral steering; sliding between them changes direction, and moving outside them releases steering. Every control has drawn artwork: pedals, handbrake, shifting, rewind, clutch, camera, map and pause. Small vector Xbox hints keep menu prompts recognizable; action symbols assume the game's default bindings and do not remap buttons. The right look stick, four D-pad directions and L3/R3 stick clicks are also editable. D-pad presses stay separate from steering, and the look stick works while holding steering and a pedal. Touch drawing and its button hints do not require the game's font archive.

Controls opens a full-screen touch editor with a movable, collapsible toolbar and Control, Layout, Driving and Appearance tabs. Drag controls or use position/size sliders and accessible nudge buttons. Saving is atomic; cancelling keeps the previous layout. The editor and gameplay use the same vector artwork and safe-area geometry.

**Steering: Arrows / Stick** is available both in Touch settings and the editor. Switching preserves positions for both. The left and right sticks each offer floating, fixed and invisible modes. Invisible sticks use the corresponding lower screen region; visible buttons take priority. The independent D-pad and look stick remain available. Older layouts retain their positions, including the hotfix's separate D-pad and steering controls.

Local named layouts, left/right-handed templates, compact driving and full-controller templates help configure the pad. Switching between driving and controller styles remembers each style separately. Layout document import/export is not provided.

Held controls remain visible. Global and per-control idle timers control fading; controller auto-hide is optional. Cancellation, menus, controller changes and lifecycle suspension release touch input. Driving options include immediate/120 ms/240 ms arrow response, optional sliding pedals, high contrast, drawn Xbox prompt hints and touch haptics. Action icons assume default game bindings and do not remap buttons.

Controller calibration applies deadzones, curves and trigger ranges to physical SDL controllers, without changing touch or wheel input. Optional phone tilt has sensitivity, deadzone and centre calibration; deliberate stick/controller steering takes priority. Phone rumble has its own strength and stops when paused or menus capture input. These features await physical-device validation.

Achievements shows current progress with cached artwork. The trainer operates on a separate modded profile; enabling it requires a restart. Opt-in sustained mode requests supported Android performance mode and temporarily caps a severe thermal state at 30 FPS, or keeps a lower selected limit. It restores the saved target after a minute at light-or-lower severity without rewriting the preset. Adreno experiments are gated to matching hardware and provide a reset; they can reduce stability.

Tap ☰ or Android Back for the in-game drawer. Advanced game settings opens the original host menu for controller mapping and other existing features. Android photo capture and minimap repositioning are omitted.

## Saves

Saves exports the standard base-game user profile as a save-only ZIP. Imports validate the entire archive in staging, reject path escapes/duplicates and retain the previous user tree before an atomic replacement. Restore keeps the current tree so the operation can be reversed. Quit the game before save management. Separate modded/cheat profiles remain available through Advanced game settings. Base-game and TU4 saves are incompatible.

## Reports and recovery

Settings → Performance → Record a performance comparison launches a session with bounded native CSV logging. Drive the same route for each configuration, then use More → Report a problem (or the in-game action). Normal play keeps detailed profiling off.

Exports retain CSV headers and whole rows. The report index identifies file size/truncation, originating session and source commit, and separates the latest session from older history and shared logs. A session from an older APK is not labelled current after an update. Thermal values are named severity levels, not temperatures, and normal self-exits are distinguished from crashes and low-memory kills. Saved, launch and live settings have separate snapshots; phone FPS/thermal samples carry session/build provenance.

The report analyzer, `tools/analyze-android-report.py REPORT.zip --target-fps 30 --skip-seconds 10 --duration 60`, summarizes an explicit capture window. Menus/loading remain included unless the selected window excludes them. Audio-counter overlap is correlation, not a diagnosis. Truncated captures describe their exported tail, not the whole run.

Repeated export taps are blocked during preparation. Each share uses its own completed ZIP. Reports include device/settings/driver data and bounded logs, not saves or game media; review them before sharing.

The launcher and game run in different processes. A session marker distinguishes a normal exit from an interrupted game; recovery offers a system-driver launch or a report. Android low-memory kills and native crashes are different failure causes and should not be treated as proof of a driver problem.

An intentional native window close records a clean exit before the runtime's hard termination. Finishing the game activity also records that intent before SDL teardown, so a normal quit does not depend on Java cleanup running after native shutdown.

## Build and implementation notes

The pinned ShiftGlue SDK remains at 79d3e6f. Reviewed runtime changes and regenerated Vulkan helpers are stored in cmake/patches and applied idempotently before configuring the source SDK. This avoids a submodule commit unavailable from the upstream remote. Regenerate helpers with compile-fh1-native.sh (SPIRV_ONLY=1 and GLSLANG set), then run tools/update-android-sdk-patch.py after reviewing the SDK worktree.

The ARMv8.2 baseline, Android 13 minimum and 16 KiB native/ZIP alignment requirements are unchanged. Run tools/test-android-mobile.py and the renderer/parser host tests before packaging. No physical-device or sustained performance claim follows from compilation or SPIR-V validation.

A reviewed generated-code snapshot can be built with `PINYON_SHIFT_FROZEN_CODEGEN=ON`; this mode requires the complete generated modules and stamp, but not the standalone generator. It does not regenerate code when game inputs or generator settings change. Keep generated game code private and use the matching source/SDK revisions.

CI also checks shared touch geometry, runs the Java mobile suite with the pinned platform/JDK, and compiles the Android driver probe and native UI bridge without game-generated source. The independent input-calibration and shared-driver loader patches are retained when shader patches are regenerated.

[Player setup and limitations](https://github.com/XHYN-PH/fh4droid/blob/main/docs/releases/fh4droid-0.6.6-r5.md). [Upstream Android research](https://github.com/arcanite24/pinyon-shift/blob/dev/docs/ANDROID_PORT_BACKLOG.md).
