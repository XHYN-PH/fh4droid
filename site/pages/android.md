---
title: FH4Droid on Android
section: Playing
order: 2
description: FH4Droid launcher, game files, settings, controls and troubleshooting on Android.
---


FH4Droid focuses on the Android port of Forza Horizon (2012), Xbox 360. The current source integrates Pinyon Shift 0.9.1 and retains the race-grid launcher, Android input, and save management from 0.8.2-ui-polish-test5. Upstream measurements describe their named devices and builds. This integration awaits Android gameplay testing.

## Current changes and limits

Pinyon Shift 0.9.1 updates the build tooling and includes 0.9.0 runtime changes, such as faster file lookups and changes to audio scheduling. Upstream frame generation targets desktop platforms. FH4Droid continues to use its own Android raster resolution setting.

Android 13 or newer is required. Snapdragon 845/Adreno 630, Mali-G52 and Xclipse need device qualification; an Android 10 stock phone does not meet this OS floor. Start Mali/Xclipse with the system Vulkan driver. Turnip controls are Adreno-only, and PanVK is not a universal Mali fallback.

View changes use host-time hysteresis and discard obsolete queued requests. The reported sudden shrinking/sidebars needs transition testing on the phone; the white-car overexposure remains unresolved. The probe checks 8-bit/10-bit/float16 clear and readback with both render-pass paths, which does not test the paint shaders.

The unreleased follow-up gives map signals a separate continuous grace interval, so a brief map sample cannot inherit an older camera-gap timer. A sampling gap longer than one second preserves the committed framing and starts fresh transition evidence. The stall watchdog excludes background suspension and grants a new grace interval on resume. Optional detailed GPU profiling skips timestamp commands when the selected queue does not support them. These changes pass host regression and Android compilation checks; phone transition testing remains necessary. See the [pre-UI audit](https://github.com/XHYN-PH/fh4droid/blob/main/docs/releases/fh4droid-pre-ui-audit-2026-10-27.md) for evidence and unresolved items.

Save management supports Normal/Modded/Both archives and reversible restore. To use folder sync, enable **Save folder sync** under **Settings → Gameplay**, then point your sync app at `Android/media/ph.xhyn.fh4droid/sync`. The setting applies at the next launch and syncs the normal save and achievements. Offline Profile edits local name/picture without changing XUID. Phone JPEG rotation and mirroring are applied before the bounded centre crop. Xenia ZIP import preserves its source XUID, keeps existing profiles, backs up settings and refuses detected TU4 saves in this base build. This is separate from Xbox LIVE, which is not provided.

Named graphics profiles and a manually marked working snapshot support recovery. Resolution, MSAA and screen mode distinguish running and next-launch values; presets show changes before applying. Budget preview targets 30 FPS at 50% with one shader worker, without a measured 4 GB performance claim. Native/classic renderer options remain experimental. Camera comfort options use existing native hooks.

Interface offers separate app/game languages: English or Filipino core menus (detailed help stays English) and game choices limited to archives present in the owned files. Controls hand off between keyboard, gamepad and touch, with Android menu hints and A/B navigation. Use my music player turns off game music at next launch while preserving engine/effects.

Reports refresh live state every five seconds and on pause/view changes, prioritize the current CSV and identify truncation. More → Mark a visual issue records a frame/time/view marker for an external screenshot. Latest frame time, RAM and thermal severity are optional FPS details; severity is not temperature. See the [complete review](https://github.com/XHYN-PH/fh4droid/blob/main/docs/releases/fh4droid-0.6.8-r7-review.md) for all 70 dispositions and remaining device tests, and [release notes](https://github.com/XHYN-PH/fh4droid/blob/main/docs/releases/fh4droid-0.6.8-r7.md) for opt-in validation packaging. The diagnostic path queries extensions from the selected layer, enables validation features explicitly and clears layer-only state on fallback.

## Launcher

The app uses fixed landscape orientation for the launcher, local import, touch editor, driver probe and game. SDL window requests retain the landscape lock.

The launcher uses the approved B dashboard: Play on the left, with Settings, Controls, Game files and More in a two-by-two grid on the right. The supplied transparent logo and road background stay in place. Play uses an ivory card; the focused action has a pink outline. On narrow landscape screens or with larger text, Play stacks above that grid. Directional controller movement follows the visible card positions. Local profile is in the header. More opens a six-card management grid for Mods & DLC, Graphics drivers, Save backups, Achievements, Problem reports and About. Cheats remain available in the Mods tab of Mods & DLC.

Settings, controls, files and profile open matching category pages. Detailed settings, imports, backups and driver choices use the same dark surfaces, thin icons and pink focus feedback. Back returns through open tools to their category and restores scroll and focus. The footer retains plain YouTube, Telegram and GitHub links, controller hints and the installed package version/build. Large text uses a two-column management grid and a two-line footer on compact screens.

Focus settles over 160 ms; page navigation slides and fades over 220 ms, with reverse movement when returning from a tool. Scrolling follows controller focus. Motion stops when settled and honours both the app Animations preference and Android's animator setting. UI scale remains adjustable from 70% to 105%. In-game pages keep their right-side drawer and Resume action; the shared card palette also applies there.

The race-grid home and management page are rendered by the same Android view code used for their approved previews. Native-render tests compare their pixels at the reference viewport, density, font size and fixture state. These cloud checks do not qualify a physical controller, phone GPU or installed APK; the package must be compared at matching display settings when one is built.

Settings has categories, search and pinned favourites. Rows distinguish active runtime values from saved next-launch changes. Section resets require confirmation, and Undo restores the last section change. Presets write as one atomic transaction; failed writes leave the displayed value unchanged. Advanced quoted settings decode TOML escapes before preflight.

Drivers are tested in a fresh helper process before activation and before Play. Results show device/API information, required feature checks and a small render/readback test. Preflight and gameplay share the custom loader and saved environment. Failed custom loads do not silently select the system driver. The Drivers page lists radio cards plus Import, Test and Remove actions. Readable imported-driver names and confirmed removal are available; removing the active imported driver selects System. A successful probe does not establish gameplay compatibility.

Graphics cache management lists only recognized shader and raster caches, and requires the game to be closed before deletion. It never selects saves, imported game files or the Rally adapter. First visits after clearing can pause while graphics are prepared.

## Game files

Use Game files → Choose game ISO or Choose extracted game folder, with the player's own supported USA base-game dump. The ISO stays in place with persistent document permission and a seekable descriptor held by the game process. The game mounts the SDK's read-only disc-image device. Only executables and host UI/audio support files (about 100 MB, allow 150 MB free) are copied locally. Keep the ISO accessible while playing. The bounded Java inspector and native reader reject truncated directories and cycles.

Folder import compares all catalogued paths and sizes, verifies the executables before replacing an installation, verifies SHA-256 on copied/resumed files and checks space for the files still needing a copy. A partial import remains marked incomplete. Check installed game files performs a complete content check; it can take several minutes.

Import/check screens show current-file progress and offer cooperative cancellation between reads. Completed files are kept; choose the same source folder to resume a stopped copy. Successful folder copies clear the previous ISO selection only after the copy finishes. Cancellation may wait for a file-provider read to return. In ISO mode, the existing content check covers the disc image; it does not also check the locally cached support files.

The Android app imports local files. PC discovery, pairing, Wi-Fi copying and network APK updates have been removed. Copy your own extracted folder to the device over USB before selecting it; install app updates through Android’s installer. File-replacing overlays work in ISO mode, but mods which add files to directory enumerations should use the complete extracted folder.

## Source polish follow-up

The user confirmed that upstream test 2 starts and drives. The 12 November source follow-up fixes stale native configuration after a failed reload, commits hardware defaults with their completion marker, preserves interrupted-session reporting on forced closes, and improves damaged-mod recovery. Ordinary mod imports strip the internal built-in marker; installed built-ins open their details, and the content manager uses the shared controller navigation. These changes are source-only until the next APK is built. See the [full source and Android audit](https://github.com/XHYN-PH/fh4droid/blob/main/docs/releases/fh4droid-polish-audit-2026-11-12.md) for the original findings. The [performance and recovery follow-up](https://github.com/XHYN-PH/fh4droid/blob/main/docs/releases/fh4droid-performance-polish-2026-11-12.md) retains all 36 performance-first choices, implemented changes and remaining device qualification.

## Startup checks

The first 0.8.2 upstream test APK aborted while loading XMedia, before the title screen. Its refreshed main-only generated tree omitted the callback that registers SpeechFacade and XMediaFacade, even though both libraries were packaged. Test 2 restores that metadata. Configuration now rejects a missing or disconnected module registry, and packaging requires the linked registry and both libraries' registration exports. A host smoke test exercises the generated entrypoint callback and all 50,935 facade mappings with the matching SDK. This covers the reported startup failure; Android gameplay still requires device testing.

## Mods, DLC and prepared shaders

Open More → Mods, DLC and shaders while the game is closed. Import one mod ZIP or folder, enable it, and adjust its priority or manifest options. Earlier enabled mods win file conflicts. Festival pacing and Hidden cars are included as optional installs. Database edits rebuild a separate overlay and use the modded profile. Disable and remove an installed mod before replacing it. Archive-member patches need PC preparation; native plugins need a compatible Android ARM64 build.

The DLC tab accepts original Marketplace packages, folders and ZIPs nested at most three levels. It checks the supported catalog, raw package hash, extracted payload and licence header. Imports start disabled. Unsupported inputs are reported individually; completed imports survive cancellation. Enable dependencies first. UDLC links explain the compatible inputs; no content is downloaded automatically. Rally requires a verified prepared adapter folder from the matching upstream PC tools before it can be enabled. This APK targets the supported USA base game, without title update v4.

Content discovery runs on a worker and preserves the open detail page, scroll and focus after changes. Invalid options do not hide mod recovery. Durable publication journals recover interrupted mod/DLC/Rally changes before import or Play. Space checks cover expansion/staging, and unknown-size copies check free space while reading. Picker names remain readable in import results.

The Shaders tab imports a compatible prepared `.xpso` pipeline list from the player's files. Preparation can run before play or during the title screen. Import checks structure and version; the renderer validates shader hashes. It cannot eliminate all shader-compilation pauses. A small in-game status shows actual cached-pipeline preparation counts; reports retain failures/cancellation. Automatic shader concurrency uses one worker on low-memory/four-core devices and up to two otherwise. Background preparation retains its lower priority.

The game menu opens upstream's achievement screen with categories, counted progress, nearest unlocks, tracking and notifications. The launcher keeps its offline summary. Live chase-camera controls and the 80–200% global FOV setting are in Settings. Graceful Quit to launcher requests the upstream shutdown path. None of these features supplies Xbox LIVE service access.

## Rendering resolution

Graphics offers raster resolution from 25% to 100% in one-percent steps, custom frame limits, FSR output profiles, screen modes and graphics presets. Presets coordinate related values and clear incompatible settings from the previous preset. The default raster scale is 100%.

Native attachment dimensions, viewport/scissor, depth/colour transfers and source reads scale together. Guest resolve destinations retain their original layout. Fractional resolutions use separate raster-v3 shader/pipeline caches; the precompiled SPIR-V pack is used only at 100%. Shader preparation is scheduled with bounded concurrency, and detailed GPU timestamp profiling is opt-in.

A software Vulkan readback suite checked 953,854 colour/depth values across every supported scale, including generic and specialized paths. Vulkan 1.2 extension and 1.3 core probe paths passed on that software driver. These checks establish neither phone-driver compatibility nor sustained FPS. No performance gain is claimed. Compare the same route, captures, duration and thermal conditions before recommending a preset.

Settings keep explanations in a right-hand panel at both tested landscape sizes. Focus updates the description, and Left/Right adjusts choice rows. Render resolution changes by 1% and is saved for the next launch. Search/favourites and reset/undo remain available. The game menu uses a small icon at the upper right, text-only actions and single-tap activation. Profile actions share a compact grid; achievements show six entries per page with filters and search.

Automatic driver selection keeps the system driver on Adreno 730 (Snapdragon 8 Gen 1 and 8+ Gen 1) following reports of choppy rendering with newer custom drivers. This is a conservative selection policy, not a measured performance improvement. Other imported drivers remain selectable. Camera-dependent engine audio and random stutter/crash reports still require matching diagnostics and phone testing; full dynamic range changes output compression, not the camera's audio perspective.

## Controls

Touch steering defaults to separate left and right arrow buttons. Holding both gives neutral steering; sliding between them changes direction, and moving outside them releases steering. Every control has a text action name or controller button code: gas, brake, handbrake, shifting, rewind, clutch, camera, map and pause. Small button/action captions keep menu prompts recognizable; action names assume the game's default bindings and do not remap buttons. The right look stick, four D-pad directions and L3/R3 stick clicks are also editable. D-pad presses stay separate from steering, and the look stick works while holding steering and a pedal. Touch drawing and its button hints do not require the game's font archive.

Controls opens a full-window touch canvas. Properties opens a separate full-window view with Control, Layout, Driving and Appearance tabs. Drag or pinch controls, or set size/position with sliders. Hide tools clears the canvas for placement; Show tools restores Save/Cancel and Test. Test highlights touches locally without sending inputs to the game. Saving is atomic; cancelling keeps the previous layout. Hidden controls remain selectable under Properties. Edit offers bounded undo/redo, overlap/small-target checks and Hide tools. Back/Cancel asks before discarding a changed draft. Safe-area data refreshes before native rendering as well as input.

The editor and native overlay use the same antialiased PNG atlas for text action names and optional controller-code badges. Pedal targets have translucent backing and pink pressed feedback. The moving stick knob stays unlabelled. Existing positions, mappings and hit geometry are preserved. Text names assume the default in-game controller bindings; remapping updates the displayed action identity. Detected menu/photo contexts keep Confirm/Back and both analog sticks. The pressed-button popup is removed.

The editor canvas fills the window. Edit offers gridlines, optional snapping and square grid spacing of 16, 24 or 32 dp. Snapping is relative to the safe-area origin and clamps the entire control within safe bounds. Grid settings persist between editor sessions. Hide tools frees the canvas; Test suppresses the grid and keeps touches local to the editor. Properties also fills the window. These changes are checked with host placement tests and native Android rendering; phone touch testing remains necessary.

New layouts start with the compact Modern racing preset. Existing layouts keep their saved positions and visibility. Apply Modern racing, Full controller or a handed preset explicitly to replace positions. Steering has Arrows and Stick choices; floating, fixed and invisible modes remain available for each stick. The optional manual group shows clutch and both gear controls together.

**Steering: Arrows / Stick** is available both in Touch settings and the editor. Switching preserves positions for both. The left and right sticks each offer floating, fixed and invisible modes. Invisible sticks use the corresponding lower screen region; visible buttons take priority. The independent D-pad and look stick remain available. Older layouts retain their positions, including the hotfix's separate D-pad and steering controls.

Local named layouts, left/right-handed templates, compact driving and full-controller templates help configure the pad. Full controller selects stick steering and Xbox labels; driving templates use arrows and racing artwork. Switching between driving and controller styles remembers each style separately. Layout document import/export is not provided.

Held controls remain visible. Global and per-control idle timers control fading; controller auto-hide is optional. Cancellation, menus, controller changes and lifecycle suspension release touch input. Driving options include immediate/120 ms/240 ms arrow response, optional sliding pedals, high contrast, button/action captions and touch haptics. Action names assume default game bindings and do not remap buttons.

Controller calibration applies deadzones, curves and trigger ranges to physical SDL controllers, without changing touch or wheel input. Optional phone tilt has corrected steering direction, an invert option, sensitivity, deadzone and centre calibration; deliberate stick/controller steering takes priority. Phone rumble has its own strength and stops when paused or menus capture input. These features await physical-device validation.

Phone tilt is independent of the Arrows / Stick layout choice. If tilt is enabled, holding only the accelerator still permits phone steering. Turn it off under Phone for arrows-only steering. Holding a steering arrow or touching the steering stick takes priority over tilt; a physical controller suppresses phone steering. Host regressions cover accelerator-only input, dragging a pedal over an arrow, and simultaneous accelerator/arrow/tilt input.

Achievements shows current progress with cached artwork. The trainer operates on a separate modded profile; enabling it requires a restart. Opt-in sustained mode requests supported Android performance mode and temporarily caps a severe thermal state at 30 FPS, or keeps a lower selected limit. It restores the saved target after a minute at light-or-lower severity without rewriting the preset. Adreno experiments are gated to matching hardware and provide a reset; they can reduce stability.

Android Back opens a five-action pause drawer: Resume, Settings, Controls, More and Quit. Settings use six categories: Graphics, Audio, Controls, Interface, Gameplay and Advanced. Controls separates touch, phone and physical-controller options. Search/favourites and section Reset/Undo are secondary tools. Advanced → Original game settings opens the original host menu for controller mapping and other existing features. Android photo capture and minimap repositioning are omitted.

## Android resource policy

Automatic texture reserve is 64 MiB on low-RAM/up-to-4-GiB devices, 128 MiB up to 6 GiB, and 256 MiB otherwise. Explicit choices are retained. It is an initial reserve, not a hard memory limit or a smaller guest address space. The optional Budget preview selects 512-pixel textures; 256 remains an explicit choice. Balanced preserves original textures. These alternatives need phone quality/performance comparisons.

Advanced → Extended comparison capture raises the diagnostic CSV bound from 16 to 64 MiB for long tests. Report exports keep a bounded tail and identify truncation; ordinary launches keep detailed CSV/GPU profiling off.

Surface refresh hints follow the lower active game/presentation cap. Real memory pressure still trims reloadable graphics resources; merely hiding the window no longer forces a purge. Normal Android diagnostics sample every five seconds; explicit comparisons use one-second Android samples plus the per-frame CSV. Automatic resource settings show their effective value without falsely calling it a pending restart.

The selected-driver probe exposes BC and fragment-shading-rate availability. Coarse shading and prepared immersive-camera controls explain unavailable dependencies. Quit displays the backup/closing state and rejects repeated requests; only native completion marks a clean exit. Reports include the last host action and content transaction phase.

## Saves

Saves exports Normal, Modded or Both base-game save trees. Imports validate the entire archive in staging, reject path escapes/duplicates and retain selected previous trees before replacement. Multi-tree failures roll back and Restore keeps the current trees so the operation is reversible. Installed DLC payloads, disabled packages and licence headers are excluded from exports and preserved during import/restore. Older archives containing DLC are rejected before save replacement; re-export saves with this version. Legacy normal-only save archives remain readable. Quit the game before save management. The page shows backup status and configured profile selection. Base-game and TU4 saves are incompatible; guided Xenia import refuses detected TU4 data for this APK.

## Reports and recovery

Settings → Advanced → Record a performance comparison launches a session with bounded native CSV logging. Drive the same route for each configuration, then use More → Diagnostics / Reports (or the in-game action). Normal play keeps detailed profiling off.

Exports retain CSV headers and whole rows. The report index identifies file size/truncation, originating session and source commit, and separates the latest session from older history and shared logs. A session from an older APK is not labelled current after an update. Thermal values are named severity levels, not temperatures, and normal self-exits are distinguished from crashes and low-memory kills. Saved, launch and live settings have separate snapshots; phone FPS/thermal samples carry session/build provenance.

The report analyzer, `tools/analyze-android-report.py REPORT.zip --target-fps 30 --skip-seconds 10 --duration 60`, summarizes an explicit capture window. Menus/loading remain included unless the selected window excludes them. Audio-counter overlap is correlation, not a diagnosis. Truncated captures describe their exported tail, not the whole run.

Repeated export taps are blocked during preparation. Each share uses its own completed ZIP. Reports include device/settings/driver data and bounded logs, not saves or game media; review them before sharing.

The launcher and game run in different processes. A session marker distinguishes a normal exit from an interrupted game; recovery offers a system-driver launch or a report. Android low-memory kills and native crashes are different failure causes and should not be treated as proof of a driver problem.

An intentional native window close records a clean exit before the runtime's hard termination. Finishing the game activity also records that intent before SDL teardown, so a normal quit does not depend on Java cleanup running after native shutdown.

## Build and implementation notes

The pinned ShiftGlue SDK is 537b2b64, matching upstream 0.9.1. Reviewed runtime changes and regenerated Vulkan helpers are stored in cmake/patches and applied idempotently before configuring the source SDK. This avoids a submodule commit unavailable from the upstream remote. Regenerate helpers with compile-fh1-native.sh (SPIRV_ONLY=1 and GLSLANG set), then run tools/update-android-sdk-patch.py after reviewing the SDK worktree.

The current minimum is Android 13/API 33 with an ARMv8.2-A CPU, matching Pinyon Shift. The launcher checks for LSE atomics and RDM before loading native libraries. Vulkan 1.1/1.2 compatibility, Mali/Immortalis and Samsung Xclipse remain experimental. Packaging records the CPU baseline from the native build. Run `tools/test-android-mobile.py` and the renderer and parser host tests before packaging. The APK and native libraries must support 16 KiB page alignment.

A reviewed generated-code snapshot can be built with `PINYON_SHIFT_FROZEN_CODEGEN=ON`; this mode requires the complete generated modules and stamp, but not the standalone generator. It does not regenerate code when game inputs or generator settings change. Keep generated game code private and use the matching source/SDK revisions.

CI also checks shared touch geometry, runs the Java mobile suite with the pinned platform/JDK, and compiles the Android driver probe and native UI bridge without game-generated source. The independent input-calibration and shared-driver loader patches are retained when shader patches are regenerated.

The docs workflow builds a site artifact from main or the published release tag. This fork has no configured GitHub Pages site; an owner can enable Pages with the Actions source and set the repository variable `FH4DROID_PAGES_ENABLED=true` to deploy it. Android APK publication does not depend on website deployment.

[Player setup and limitations](https://github.com/XHYN-PH/fh4droid/blob/main/docs/releases/fh4droid-0.6.8-r7.md). [Upstream Android research](https://github.com/arcanite24/pinyon-shift/blob/dev/docs/ANDROID_PORT_BACKLOG.md).

### Fractional-resolution lines

A software Vulkan regression reproduced a band-boundary clear bug: a post-resolve clear rounded the bottom of an intermediate band up, erasing a reduced-resolution texel still used by the next band. Intermediate clears now retain that shared row. The final band copy also calculates its extent from rounded endpoints, preventing a one-row attachment overrun at scales such as 59%. The fixture checks three successive band resolves at every integer resolution from 25% through 100% with the shipped resolve SPIR-V. Old clear bounds corrupt 13,600 pixels; corrected bounds pass 4,377,600 tiled-resolve pixels, in addition to 953,854 existing colour/depth resolve and transfer checks. Another 972,800 colour values cover guest 2×/4× MSAA using single-sample or 4× host surfaces, including sample averaging, at every supported scale. These are correctness checks on llvmpipe, not phone performance measurements. The reported phone artifact still needs an on-device retest; screen filters and physical display behavior are not covered by this fixture.

## HDR calibration

Keep brightness and the display mode fixed, and turn adaptive brightness off
while calibrating. **SETTINGS > DISPLAY > HDR CALIBRATION** shows the selected
signal levels; Android and the display may tone-map them before they reach
the panel. If the peak square never disappears, keep the existing peak
pending a measured calibration. Patterns replace the guest image and HUD;
HDR COMPARE is suspended while they are shown and resumes when you leave calibration.

HDR10 and calibration chart visibility were checked on the Odin 2 Portal with SYSTEM and Turnip at fixed brightness and refresh. The captured paper-white chart is identical with compare on or off; this does not measure panel luminance. The RedMagic Astra's
calibration remains unqualified ([#566](https://github.com/arcanite24/pinyon-shift/issues/566));
there is no measured Astra preset. The reported luminance is not a panel
measurement. The [Android reference](https://github.com/arcanite24/pinyon-shift/blob/dev/docs/ANDROID.md#hdr-calibration)
describes the driver comparison, display diagnostics and measurements needed
to qualify it.
