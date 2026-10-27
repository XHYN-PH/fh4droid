---
title: FH4Droid on Android
section: Playing
order: 2
description: FH4Droid launcher, game files, settings, controls and troubleshooting on Android.
---


FH4Droid focuses on the Android port of Forza Horizon (2012), Xbox 360. The 0.6.8-r7 shell and renderer changes use the fresh source and have not been gameplay-tested on a physical device. Upstream measurements describe their named devices and builds; they are not benchmarks for this build.

## r7 changes and limits

Android 12 or newer is required. Snapdragon 845/Adreno 630, Mali-G52 and Xclipse need device qualification; an Android 10 stock phone does not meet this OS floor. Start Mali/Xclipse with the system Vulkan driver. Turnip controls are Adreno-only, and PanVK is not a universal Mali fallback.

View changes use host-time hysteresis and discard obsolete queued requests. The reported sudden shrinking/sidebars needs transition testing on the phone; the white-car overexposure remains unresolved. The probe checks 8-bit/10-bit/float16 clear and readback with both render-pass paths, which does not test the paint shaders.

Save management supports Normal/Modded/Both archives and reversible restore. Offline Profile edits local name/picture without changing XUID. Phone JPEG rotation and mirroring are applied before the bounded centre crop. Xenia ZIP import preserves its source XUID, keeps existing profiles, backs up settings and refuses detected TU4 saves in this base build. This is separate from Xbox LIVE, which is not provided.

Named graphics profiles and a manually marked working snapshot support recovery. Resolution, MSAA and screen mode distinguish running and next-launch values; presets show changes before applying. Budget preview targets 30 FPS at 50% with one shader worker, without a measured 4 GB performance claim. Native/classic renderer options remain experimental. Camera comfort options use existing native hooks.

Interface offers separate app/game languages: English or Filipino core menus (detailed help stays English) and game choices limited to archives present in the owned files. Controls hand off between keyboard, gamepad and touch, with Android menu hints and A/B navigation. Use my music player turns off game music at next launch while preserving engine/effects.

Reports refresh live state every five seconds and on pause/view changes, prioritize the current CSV and identify truncation. More → Mark a visual issue records a frame/time/view marker for an external screenshot. Latest frame time, RAM and thermal severity are optional FPS details; severity is not temperature. See the [complete review](https://github.com/XHYN-PH/fh4droid/blob/main/docs/releases/fh4droid-0.6.8-r7-review.md) for all 70 dispositions and remaining device tests, and [release notes](https://github.com/XHYN-PH/fh4droid/blob/main/docs/releases/fh4droid-0.6.8-r7.md) for opt-in validation packaging. The diagnostic path queries extensions from the selected layer, enables validation features explicitly and clears layer-only state on fallback.

## Launcher

The app uses fixed landscape orientation for the launcher, local import, touch editor, driver probe and game. SDL window requests retain the landscape lock.

The compact launcher puts Play and the main actions at the lower left, with brighter branding and a settings summary. Pages open on the right on wide screens and fill the window on narrow screens or with large text. In-game pages use a right-side drawer with a sticky Resume action. Back returns through the page stack and restores scroll and focus. Menu scale is adjustable from 70% to 105%; optional transitions honour Android's animator setting.

The main menu displays the supplied transparent FH4Droid logo and one primary Play action, with Settings and Controls beneath it. Game files, Saves and More remain quiet secondary shortcuts.

The launcher footer uses text-only YouTube, Telegram and GitHub links on the left. The right footer reads the installed Android package version and build number and shows game-file readiness. Secondary shortcuts use the same lightweight text treatment; focus feedback and generous touch targets remain available.

Settings has categories, search and pinned favourites. Rows distinguish active runtime values from saved next-launch changes. Section resets require confirmation, and Undo restores the last section change. Presets write as one atomic transaction; failed writes leave the displayed value unchanged. Advanced quoted settings decode TOML escapes before preflight.

Drivers are tested in a fresh helper process before activation and before Play. Results show device/API information, required feature checks and a small render/readback test. Preflight and gameplay share the custom loader and saved environment. Failed custom loads do not silently select the system driver. Readable imported-driver names and confirmed removal are available; removing the active imported driver selects System. A successful probe does not establish gameplay compatibility.

Graphics cache management lists only recognized shader and raster caches, and requires the game to be closed before deletion. It never selects saves, imported game files or the Rally adapter. First visits after clearing can pause while graphics are prepared.

## Game files

Use Game files → Choose game ISO or Choose extracted game folder, with the player's own supported USA base-game dump. The ISO stays in place with persistent document permission and a seekable descriptor held by the game process. The game mounts the SDK's read-only disc-image device. Only executables and host UI/audio support files (about 100 MB, allow 150 MB free) are copied locally. Keep the ISO accessible while playing. The bounded Java inspector and native reader reject truncated directories and cycles.

Folder import compares all catalogued paths and sizes, verifies the executables before replacing an installation, verifies SHA-256 on copied/resumed files and checks space for the files still needing a copy. A partial import remains marked incomplete. Check installed game files performs a complete content check; it can take several minutes.

Import/check screens show current-file progress and offer cooperative cancellation between reads. Completed files are kept; choose the same source folder to resume a stopped copy. Successful folder copies clear the previous ISO selection only after the copy finishes. Cancellation may wait for a file-provider read to return. In ISO mode, the existing content check covers the disc image; it does not also check the locally cached support files.

The Android app imports local files. PC discovery, pairing, Wi-Fi copying and network APK updates have been removed. Copy your own extracted folder to the device over USB before selecting it; install app updates through Android’s installer. File-replacing overlays work in ISO mode, but mods which add files to directory enumerations should use the complete extracted folder.

## Rendering resolution

Graphics offers raster resolution from 25% to 100% in one-percent steps, custom frame limits, FSR output profiles, screen modes and graphics presets. Presets coordinate related values and clear incompatible settings from the previous preset. The default raster scale is 100%.

Native attachment dimensions, viewport/scissor, depth/colour transfers and source reads scale together. Guest resolve destinations retain their original layout. Fractional resolutions use separate raster-v3 shader/pipeline caches; the precompiled SPIR-V pack is used only at 100%. Shader preparation is scheduled with bounded concurrency, and detailed GPU timestamp profiling is opt-in.

A software Vulkan readback suite checked 953,854 colour/depth values across every supported scale, including generic and specialized paths. Vulkan 1.2 extension and 1.3 core probe paths passed on that software driver. These checks establish neither phone-driver compatibility nor sustained FPS. No performance gain is claimed. Compare the same route, captures, duration and thermal conditions before recommending a preset.

## Controls

Touch steering defaults to separate left and right arrow buttons. Holding both gives neutral steering; sliding between them changes direction, and moving outside them releases steering. Every control has drawn artwork: pedals, handbrake, shifting, rewind, clutch, camera, map and pause. Small button/action captions keep menu prompts recognizable; action symbols assume the game's default bindings and do not remap buttons. The right look stick, four D-pad directions and L3/R3 stick clicks are also editable. D-pad presses stay separate from steering, and the look stick works while holding steering and a pedal. Touch drawing and its button hints do not require the game's font archive.

Controls opens a full-window touch canvas. Properties opens a separate full-window view with Control, Layout, Driving and Appearance tabs. Drag or pinch controls, or set size/position with sliders. Hide tools clears the canvas for placement; Show tools restores Save/Cancel and Test. Test highlights touches locally without sending inputs to the game. Saving is atomic; cancelling keeps the previous layout. Hidden controls remain selectable under Properties. Edit offers bounded undo/redo, overlap/small-target checks and Hide tools. Back/Cancel asks before discarding a changed draft. Safe-area data refreshes before native rendering as well as input.

The editor and native overlay use the exact same antialiased PNG atlas for all 20 icons, classic letters and button/action captions. Modern symbols use thin linework on translucent controls. Accelerator, brake and clutch have shaded pedal faces with fine grip details and slim bevels; their artwork replaces the extra rectangular keycaps. Pressed pedals warm to the orange accent, while steering arrows use circular pads. All symbols are rendered from square SVGs with their proportions preserved. The camera uses a circular lens. Separate button-code badges sit above the symbols and short action labels below them. The Modern racing preset groups map, camera and pause at the upper right, with rewind and handbrake above the pedals. It adapts the spacing to the screen aspect ratio so pedal and steering clusters stay separated on tablets and wide phones. Existing custom positions stay in place until a preset is applied. Always visible works before the first tap. Separate turn-in/return rates and steering lock control the arrows; opposed arrows cancel immediately. Output amount bars show pedal and arrow values. Icon opacity is independent of backing opacity. Normal, pressed and held states use the same artwork. Optional pressed-button names briefly identify touched controls at the top of the screen. Digital touch remapping updates artwork/captions and hit bounds; action names still assume the game's default controller bindings. Detected menus keep their original Confirm/Back actions.

New layouts start with the compact Modern racing preset. Existing layouts keep their saved positions and visibility. Apply Modern racing, Full controller or a handed preset explicitly to replace positions. Steering has Arrows and Stick choices; floating, fixed and invisible modes remain available for each stick. The optional manual group shows clutch and both gear controls together.

**Steering: Arrows / Stick** is available both in Touch settings and the editor. Switching preserves positions for both. The left and right sticks each offer floating, fixed and invisible modes. Invisible sticks use the corresponding lower screen region; visible buttons take priority. The independent D-pad and look stick remain available. Older layouts retain their positions, including the hotfix's separate D-pad and steering controls.

Local named layouts, left/right-handed templates, compact driving and full-controller templates help configure the pad. Full controller selects stick steering and Xbox labels; driving templates use arrows and racing artwork. Switching between driving and controller styles remembers each style separately. Layout document import/export is not provided.

Held controls remain visible. Global and per-control idle timers control fading; controller auto-hide is optional. Cancellation, menus, controller changes and lifecycle suspension release touch input. Driving options include immediate/120 ms/240 ms arrow response, optional sliding pedals, high contrast, button/action captions and touch haptics. Action icons assume default game bindings and do not remap buttons.

Controller calibration applies deadzones, curves and trigger ranges to physical SDL controllers, without changing touch or wheel input. Optional phone tilt has sensitivity, deadzone and centre calibration; deliberate stick/controller steering takes priority. Phone rumble has its own strength and stops when paused or menus capture input. These features await physical-device validation.

Achievements shows current progress with cached artwork. The trainer operates on a separate modded profile; enabling it requires a restart. Opt-in sustained mode requests supported Android performance mode and temporarily caps a severe thermal state at 30 FPS, or keeps a lower selected limit. It restores the saved target after a minute at light-or-lower severity without rewriting the preset. Adreno experiments are gated to matching hardware and provide a reset; they can reduce stability.

Android Back opens a five-action pause drawer: Resume, Settings, Controls, More and Quit. Settings use six categories: Graphics, Controls, Audio, Interface, Gameplay and Advanced. Controls separates touch, phone and physical-controller options. Search/favourites and section Reset/Undo are secondary tools. Advanced → Original game settings opens the original host menu for controller mapping and other existing features. Android photo capture and minimap repositioning are omitted.

## Saves

Saves exports Normal, Modded or Both base-game save trees. Imports validate the entire archive in staging, reject path escapes/duplicates and retain selected previous trees before replacement. Multi-tree failures roll back and Restore keeps the current trees so the operation is reversible. Legacy normal-only archives remain readable. Quit the game before save management. The page shows backup status and configured profile selection. Base-game and TU4 saves are incompatible; guided Xenia import refuses detected TU4 data for this APK.

## Reports and recovery

Settings → Advanced → Record a performance comparison launches a session with bounded native CSV logging. Drive the same route for each configuration, then use More → Report a problem (or the in-game action). Normal play keeps detailed profiling off.

Exports retain CSV headers and whole rows. The report index identifies file size/truncation, originating session and source commit, and separates the latest session from older history and shared logs. A session from an older APK is not labelled current after an update. Thermal values are named severity levels, not temperatures, and normal self-exits are distinguished from crashes and low-memory kills. Saved, launch and live settings have separate snapshots; phone FPS/thermal samples carry session/build provenance.

The report analyzer, `tools/analyze-android-report.py REPORT.zip --target-fps 30 --skip-seconds 10 --duration 60`, summarizes an explicit capture window. Menus/loading remain included unless the selected window excludes them. Audio-counter overlap is correlation, not a diagnosis. Truncated captures describe their exported tail, not the whole run.

Repeated export taps are blocked during preparation. Each share uses its own completed ZIP. Reports include device/settings/driver data and bounded logs, not saves or game media; review them before sharing.

The launcher and game run in different processes. A session marker distinguishes a normal exit from an interrupted game; recovery offers a system-driver launch or a report. Android low-memory kills and native crashes are different failure causes and should not be treated as proof of a driver problem.

An intentional native window close records a clean exit before the runtime's hard termination. Finishing the game activity also records that intent before SDL teardown, so a normal quit does not depend on Java cleanup running after native shutdown.

## Build and implementation notes

The pinned ShiftGlue SDK remains at 79d3e6f. Reviewed runtime changes and regenerated Vulkan helpers are stored in cmake/patches and applied idempotently before configuring the source SDK. This avoids a submodule commit unavailable from the upstream remote. Regenerate helpers with compile-fh1-native.sh (SPIRV_ONLY=1 and GLSLANG set), then run tools/update-android-sdk-patch.py after reviewing the SDK worktree.

The current minimum is Android 12/API 31 and the native CPU baseline is ARMv8-A. Vulkan 1.1/1.2 capability paths, Mali/Immortalis and Samsung Xclipse are experimental; a GPU name or API version does not qualify gameplay. 16 KiB native/ZIP alignment remains required. Run tools/test-android-mobile.py and the renderer/parser host tests before packaging. No physical-device or sustained performance claim follows from compilation or SPIR-V validation.

A reviewed generated-code snapshot can be built with `PINYON_SHIFT_FROZEN_CODEGEN=ON`; this mode requires the complete generated modules and stamp, but not the standalone generator. It does not regenerate code when game inputs or generator settings change. Keep generated game code private and use the matching source/SDK revisions.

CI also checks shared touch geometry, runs the Java mobile suite with the pinned platform/JDK, and compiles the Android driver probe and native UI bridge without game-generated source. The independent input-calibration and shared-driver loader patches are retained when shader patches are regenerated.

The docs workflow builds a site artifact from main or the published release tag. This fork has no configured GitHub Pages site; an owner can enable Pages with the Actions source and set the repository variable `FH4DROID_PAGES_ENABLED=true` to deploy it. Android APK publication does not depend on website deployment.

[Player setup and limitations](https://github.com/XHYN-PH/fh4droid/blob/main/docs/releases/fh4droid-0.6.8-r7.md). [Upstream Android research](https://github.com/arcanite24/pinyon-shift/blob/dev/docs/ANDROID_PORT_BACKLOG.md).

### Fractional-resolution lines

A software Vulkan regression reproduced a band-boundary clear bug: a post-resolve clear rounded the bottom of an intermediate band up, erasing a reduced-resolution texel still used by the next band. Intermediate clears now retain that shared row. The final band copy also calculates its extent from rounded endpoints, preventing a one-row attachment overrun at scales such as 59%. The fixture checks three successive band resolves at every integer resolution from 25% through 100% with the shipped resolve SPIR-V. Old clear bounds corrupt 13,600 pixels; corrected bounds pass 4,377,600 tiled-resolve pixels, in addition to 953,854 existing colour/depth resolve and transfer checks. Another 972,800 colour values cover guest 2×/4× MSAA using single-sample or 4× host surfaces, including sample averaging, at every supported scale. These are correctness checks on llvmpipe, not phone performance measurements. The reported phone artifact still needs an on-device retest; screen filters and physical display behavior are not covered by this fixture.
