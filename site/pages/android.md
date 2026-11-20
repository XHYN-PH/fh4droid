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

Diagnostic runs refresh live state every five seconds and on pause or view changes, prioritize the current CSV and identify truncation. More → Mark a visual issue records a frame/time/view marker for an external screenshot. Latest frame time, RAM and thermal severity are optional FPS details; severity is not temperature. See the [complete review](https://github.com/XHYN-PH/fh4droid/blob/main/docs/releases/fh4droid-0.6.8-r7-review.md) for all 70 dispositions and remaining device tests, and [release notes](https://github.com/XHYN-PH/fh4droid/blob/main/docs/releases/fh4droid-0.6.8-r7.md) for opt-in validation packaging. The diagnostic path queries extensions from the selected layer, enables validation features explicitly and clears layer-only state on fallback.

## Launcher

The app uses fixed landscape orientation for the launcher, local import, touch editor, driver probe and game. SDL window requests retain the landscape lock.

The launcher has a tall Play card on the left, with Settings, Controls, Game files and More in a two-by-two grid on the right. The cards, text and gaps scale to the area inside the cutout and system bars. The Home footer has its own row and a gap above it. On short screens, the dashboard scrolls between the header and footer. Option screens use the space below the header. Directional controller movement follows the cards. LB and RB switch settings categories and cycle content and achievement tabs. Local profile is in the header. More opens Mods & DLC, Graphics drivers, Save backups, Achievements, Problem reports and About. Cheats are available in the Mods tab of Mods & DLC.

The main Controls card and the Controls category in Settings open the same hub. Game files links to Installation and Storage. Graphics drivers live under More and graphics cache management lives under Game files → Storage. Detailed settings, imports, backups and driver choices use the same dark surfaces, thin icons and pink focus feedback. Back returns through open tools to their category and restores scroll and focus. The footer shows YouTube, Telegram and GitHub links, controller hints and the installed version and build. The links move to a second line when the footer needs more width. Narrow layouts use two columns in the management grid.

Home raises the Play card and staggers the entrance of the two rows beside it. Launcher pages slide across as you open them or go back. The in-game drawer slides in from the right and fades into view. It slides back to the right when you close it. Pages inside the drawer slide as you move forward or back. The app’s **Animations** setting and Android’s animator setting control these effects. Scrolling follows controller focus. UI scale remains adjustable from 70% to 105%.

Open **More → About** for the installed version and build, Android and game-file requirements, project credits, and links to Pinyon Shift, ShiftGlue and the third-party notices. The page also links to the FH4Droid repository, YouTube, Telegram and the control artwork licences.

The race-grid home and management page are rendered by the same Android view code used for their approved previews. Native-render tests compare their pixels at the reference viewport, density, font size and fixture state. These cloud checks do not qualify a physical controller, phone GPU or installed APK; the package must be compared at matching display settings when one is built.

Settings opens a six-category chooser in the launcher and in the game menu. Each category opens its own page with options on the left and explanations on the right. Back returns to the chooser, and opening Search gives a separate page for settings and pinned favourites. Typing a search includes advanced options and audio setup actions. Rows distinguish active runtime values from saved next-launch changes. Section resets require confirmation, and Undo restores the last section change. Presets write as one atomic transaction; failed writes leave the displayed value unchanged. Advanced quoted settings decode TOML escapes before preflight.

Drivers are tested in a fresh helper process before activation and before Play. Results show device/API information, required feature checks and a small render/readback test. Preflight and gameplay share the custom loader and saved environment. Failed custom loads do not silently select the system driver. The Drivers page lists radio cards plus Import, Test and Remove actions. Readable imported-driver names and confirmed removal are available; removing the active imported driver selects System. A successful probe does not establish gameplay compatibility.

Graphics cache management lists only recognized shader and raster caches, and requires the game to be closed before deletion. It never selects saves, imported game files or the Rally adapter. First visits after clearing can pause while graphics are prepared.

## Game files

Use Game files with your own supported USA base-game dump. **Choose game ISO** keeps a seekable disc image in place with persistent document permission. The game reads the selected document descriptor directly, which supports local providers that allow seeking and reading but reject memory mapping. Only executables and host UI/audio support files (about 100 MB, allow 150 MB free) are copied locally. Keep the ISO accessible while playing. The bounded Java inspector and native reader reject truncated directories and cycles.

Choose **Use extracted folder in place** for a local folder Android lets the runtime read directly. The app checks the supported files and keeps permission across restarts. If the provider only grants access through its file picker, choose **Copy extracted folder**. The app copies the catalogued files and resumes completed copies after an interruption.

The installation card shows the active source and reports byte progress while copying or checking files. **Change source**, **Check installation** and **Storage** are next to the card. Storage shows the source and available volume space above the installed-data breakdown. Copied game files, ISO support files, DLC, mods, graphics caches, saves and backups have their own sizes. Graphics cache and Android storage open from that page. Open **More → Problem reports → Check collision sound files** to verify the collision and glass banks.

The previously selected source stays active while the new one is verified. An interrupted folder copy can reuse files it has already verified. A new ISO stages its own local support files before it replaces the source selection. Automatic ISO launch verification can reuse a prior pass when the local ISO and support files have the same filesystem identity. Changed files or providers without a usable identity go through verification again. **Check installation** always checks the full catalog. Clustered small native disc reads share a bounded 64 KiB read window while the descriptor's file identity is unchanged. Keep the image accessible and unchanged while playing.

The Android app imports local files. PC discovery, pairing, Wi-Fi copying and network APK updates have been removed. Copy your own extracted folder to the device over USB before selecting it; install app updates through Android’s installer. File-replacing overlays work in ISO mode, but mods which add files to directory enumerations should use the complete extracted folder.

## Source polish follow-up

The user confirmed that upstream test 2 starts and drives. The 12 November source follow-up fixes stale native configuration after a failed reload, commits hardware defaults with their completion marker, preserves interrupted-session reporting on forced closes, and improves damaged-mod recovery. Ordinary mod imports strip the internal built-in marker; installed built-ins open their details, and the content manager uses the shared controller navigation. These changes are source-only until the next APK is built. See the [full source and Android audit](https://github.com/XHYN-PH/fh4droid/blob/main/docs/releases/fh4droid-polish-audit-2026-11-12.md) for the original findings. The [performance and recovery follow-up](https://github.com/XHYN-PH/fh4droid/blob/main/docs/releases/fh4droid-performance-polish-2026-11-12.md) retains all 36 performance-first choices, implemented changes and remaining device qualification.

## Startup checks

The first 0.8.2 upstream test APK aborted while loading XMedia, before the title screen. Its refreshed main-only generated tree omitted the callback that registers SpeechFacade and XMediaFacade, even though both libraries were packaged. Test 2 restores that metadata. Configuration now rejects a missing or disconnected module registry, and packaging requires the linked registry and both libraries' registration exports. A host smoke test exercises the generated entrypoint callback and all 50,935 facade mappings with the matching SDK. This covers the reported startup failure; Android gameplay still requires device testing.

## Mods, DLC and prepared shaders

Open More → Mods, DLC and shaders while the game is closed. Import one mod ZIP or folder, enable it, and adjust its priority or manifest options. Earlier enabled mods win file conflicts. Festival pacing and Hidden cars are included as optional installs. Database edits rebuild a separate overlay and use the modded profile. Disable and remove an installed mod before replacing it. Archive-member patches need PC preparation; native plugins need a compatible Android ARM64 build.

The DLC tab accepts original Marketplace packages, folders and ZIPs nested at most three levels. It checks the supported catalog, raw package hash, extracted payload and licence header. Imports start disabled. Unsupported inputs are reported individually; completed imports survive cancellation. Enable dependencies first. UDLC links explain the compatible inputs; no content is downloaded automatically. Rally requires a verified prepared adapter folder from the matching upstream PC tools before it can be enabled. This APK targets the supported USA base game, without title update v4.

Content discovery runs on a worker and preserves the open detail page, scroll and focus after changes. Invalid options do not hide mod recovery. Durable publication journals recover interrupted mod/DLC/Rally changes before import or Play. Space checks cover expansion/staging, and unknown-size copies check free space while reading. Picker names remain readable in import results.

The Shaders tab imports a compatible `.xpso` pipeline list prepared from the player's files. The startup panel shows when it reads caches, translates shaders, builds pipelines and saves the driver cache before gameplay. **Advanced → Shader workers** controls preparation concurrency. A cache write failure points to storage and cache recovery. A pipeline build failure points to driver selection and problem reports. New scenes can still introduce pipelines outside the installed lists and cache. The Graphics cache page can rebuild a single driver blob while retaining the imported pipeline list and other driver caches.

**Game menu → More → Achievements** shows the current game's achievement data in the same paged view the launcher uses. The local profile header shows the gamertag, gamer picture and achievement progress. Search sits beside the filters when space permits. Selecting an achievement opens a compact dialog with its score, unlock status and description. Live chase-camera controls and the 80–200% global FOV setting are in Settings. Quit to launcher requests the upstream shutdown path.

## Rendering resolution

Graphics offers raster resolution from 25% to 100% in one-percent steps, custom frame limits, FSR output profiles, screen modes and graphics presets. Presets coordinate related values and clear incompatible settings from the previous preset. The default raster scale is 100%.

Use the arrows on the resolution row, D-pad left and right, or the left stick to adjust a focused value. Selecting the resolution opens a slider that accepts touch drag and controller input. The **Display**, **Quality** and **Renderer** groups keep related options together. The right-hand panel explains the focused setting and shows pending changes. The launcher's **Ready to drive** line shows the configured resolution, frame target, filter and driver.

Under **Settings → Graphics → Upscaling filter**, **Automatic (sharp)** uses CAS sharpening at reduced render resolutions and Bilinear at 100%. Upgrading moves the previous Bilinear setting to Automatic once. Choose **Bilinear** to keep smoothing or **Off (nearest)** for nearest-neighbour scaling. FSR 1 is also available. **Post-process anti-aliasing** stays independent. The filter can change live. Render resolution applies on the next launch.

The FSR preset in 0.9.5 could save softness as `.5`, which caused the game to ignore the settings file and run with defaults. Version 0.9.6 writes `0.5` and repairs invalid values from the preset and camera controls at startup. On 0.9.5, choose **FSR softness → 0.2** after setting the preset, then quit and relaunch the game.

Version 0.9.7 fixes the sharp-edged patch of asphalt around the car with **Motion blur** enabled. The shader uses the same camera motion for that patch and the surrounding road. [Rendering details](game-rendering.md#post-effects)

Native attachment dimensions, viewport/scissor, depth/colour transfers and source reads scale together. Guest resolve destinations retain their original layout. Fractional resolutions use separate raster-v3 shader/pipeline caches; the precompiled SPIR-V pack is used only at 100%. Shader preparation is scheduled with bounded concurrency, and detailed GPU timestamp profiling is opt-in.

A software Vulkan readback suite checked 953,854 colour/depth values across every supported scale, including generic and specialized paths. Vulkan 1.2 extension and 1.3 core probe paths passed on that software driver. These checks establish neither phone-driver compatibility nor sustained FPS. No performance gain is claimed. Compare the same route, captures, duration and thermal conditions before recommending a preset.

Settings keep explanations in a right-hand panel at both tested landscape sizes. Focus updates the description, and Left/Right adjusts choice rows. Render resolution changes by 1% and is saved for the next launch. Search/favourites and reset/undo remain available. The game menu uses a small icon at the upper right, text-only actions and single-tap activation. Profile actions share a compact grid; achievements show six entries per page with filters and search.

Automatic driver selection keeps the system driver on Adreno 730 (Snapdragon 8 Gen 1 and 8+ Gen 1) following reports of choppy rendering with newer custom drivers. This is a conservative selection policy, not a measured performance improvement. Other imported drivers remain selectable. Camera-dependent engine audio and random stutter/crash reports still require matching diagnostics and phone testing; full dynamic range changes output compression, not the camera's audio perspective.

## Controls

Touch steering defaults to separate left and right arrow buttons. Holding both gives neutral steering; sliding between them changes direction, and moving outside them releases steering. Every control has a text action name or controller button code: gas, brake, handbrake, shifting, rewind, clutch, camera, map and pause. Small button/action captions keep menu prompts recognizable; action names assume the game's default bindings and do not remap buttons. The right look stick, four D-pad directions and L3/R3 stick clicks are also editable. D-pad presses stay separate from steering, and the look stick works while holding steering and a pedal. Touch drawing and its button hints do not require the game's font archive.

Controls has distinct icons for touch layout, touch buttons, phone motion, physical controllers and saved layouts. In the full-window editor, **Properties** opens a side inspector that can move to either edge. Drag or pinch controls or set their size and position with sliders. The **Preview** menu switches between Driving, Game menus and Photo mode, including both photo sticks and the shoulder buttons. Test highlights touches locally. Edit offers undo, redo, overlap and small-target checks, and grid settings. Back or Cancel asks before discarding a changed draft.

The editor and native overlay use the same antialiased PNG atlas for text action names and optional controller-code badges. Pedal targets have translucent backing and pink pressed feedback. The moving stick knob stays unlabelled. Existing positions, mappings and hit geometry are preserved. Text names assume the default in-game controller bindings; remapping updates the displayed action identity. Detected menu/photo contexts keep Confirm/Back and both analog sticks. The pressed-button popup is removed.

The floating toolbar can dock at the top or bottom. **Add button** restores a hidden control. Set per-button opacity to Global to inherit the layout setting. Grid spacing is 16, 24 or 32 dp, and guides mark alignment while snapping is active. **Hide tools** frees the canvas, including the right half of the screen for testing the invisible look stick.

New layouts start with the compact Modern racing preset. Existing layouts keep their saved positions and visibility. Apply Modern racing, Full controller or a handed preset explicitly to replace positions. Steering has Arrows and Stick choices; floating, fixed and invisible modes remain available for each stick. The optional manual group shows clutch and both gear controls together.

**Steering: Arrows / Stick** is available both in Touch settings and the editor. Switching preserves positions for both. The left and right sticks each offer floating, fixed and invisible modes. The invisible look stick uses the right half of the safe screen area, with visible buttons taking priority. The independent D-pad and look stick remain available. Older layouts retain their positions, including the hotfix's separate D-pad and steering controls.

The base game's photo camera shows LB, RB and both sticks at their saved positions, even if they are hidden, set to invisible or faded out while driving. Photo controls use their original button bindings. Returning to driving restores the saved layout.

Local named layouts, left/right-handed templates, compact driving and full-controller templates help configure the pad. Full controller selects stick steering and Xbox labels; driving templates use arrows and racing artwork. Switching between driving and controller styles remembers each style separately. Layout document import/export is not provided.

Held controls remain visible. Global and per-control idle timers control fading; controller auto-hide is optional. Cancellation, menus, controller changes and lifecycle suspension release touch input. Driving options include immediate/120 ms/240 ms arrow response, optional sliding pedals, high contrast, button/action captions and touch haptics. Action names assume default game bindings and do not remap buttons.

The controller tester shows raw and calibrated stick and trigger positions together. Calibration applies deadzones, curves and trigger ranges to physical SDL controllers. Phone tilt has its own invert option, sensitivity, deadzone and centre calibration. Deliberate stick or controller steering takes priority. Phone rumble has its own strength and stops when paused or while menus capture input.

Phone tilt is independent of the Arrows / Stick layout choice. If tilt is enabled, holding only the accelerator still permits phone steering. Turn it off under Phone for arrows-only steering. Holding a steering arrow or touching the steering stick takes priority over tilt; a physical controller suppresses phone steering. Host regressions cover accelerator-only input, dragging a pedal over an arrow, and simultaneous accelerator/arrow/tilt input.

Achievements shows current progress with cached artwork. The trainer operates on a separate modded profile; enabling it requires a restart. Opt-in sustained mode requests supported Android performance mode and temporarily caps a severe thermal state at 30 FPS, or keeps a lower selected limit. It restores the saved target after a minute at light-or-lower severity without rewriting the preset. Adreno experiments are gated to matching hardware and provide a reset; they can reduce stability.

The top-right icon or Android Back opens the pause drawer. The drawer takes game input once its Android window opens. Resume is the primary action. Settings, Touch controls and Controller are quick actions. More groups the profile, achievements, diagnostics and trainer. Quit to launcher is at the bottom. Settings use six categories, with `LB` and `RB` switching related pages and `B` returning to the previous focus. Native controller remapping is reachable from the in-game Controller page.

## Android resource policy

Automatic texture reserve is 64 MiB on low-RAM/up-to-4-GiB devices, 128 MiB up to 6 GiB, and 256 MiB otherwise. Explicit choices are retained. It is an initial reserve, not a hard memory limit or a smaller guest address space. The optional Budget preview selects 512-pixel textures; 256 remains an explicit choice. Balanced preserves original textures. These alternatives need phone quality/performance comparisons.

**Advanced → Extended comparison capture** raises the diagnostic CSV bound from 16 to 64 MiB for long tests. Report exports keep a bounded tail and identify truncation. Detailed GPU timestamps are opt-in.

Surface refresh hints follow the lower active game or presentation cap. When possible, **Match display refresh** favours seamless refresh rates that are an integer multiple of the game target, such as 120 Hz at 40 FPS. Memory and frame-timing samples run on a worker. **Advanced → CPU performance hints** is an off-by-default Android scheduling experiment. It reports budget and CPU work for the game frame thread. The older Android command/recording-thread experiment showed no clear benefit, so this experiment needs a fresh same-device comparison before any performance claim.

An unchanged, successful driver probe can be reused across Play launches. An Android or app update, changed driver files, changed probe settings, or an interrupted game triggers a fresh check. **Graphics drivers** shows the current selection alongside the driver and Vulkan version used in the last startup test. Each result identifies the check that passed or failed. The probe also reports BC and fragment-shading-rate availability. Coarse shading and prepared immersive-camera controls explain unavailable dependencies. Quit shows backup and closing progress until native shutdown completes.

## Saves

Saves exports Normal, Modded or Both base-game save trees. **Clear saves** confirms the selected save type, moves that progress to a backup, and keeps installed DLC in place. Use **Restore previous saves** to undo the clear. **Manage backups** lets you export, restore or delete an individual backup while the game is closed. A recovery journal protects the original saves and DLC if an import, clear or restore is interrupted.

**Import ForzaProfile** accepts Xbox 360 Forza Horizon save packages, including files with no filename extension. Choose Normal or Modded as the destination. The app checks the profile, companion database and version flags in staging, then keeps the old saves as a rollback copy and selects the package's player ID when available. A modded import selects that player only for modded sessions. Use **Restore saves** for an FH4Droid ZIP and **Import a Xenia profile** for a Xenia ZIP. Title-update saves require a matching game build.

Use **Manage backups → Create named checkpoint** to copy the selected save type before changing cars, mods or settings. Backups show the saved profile type and date. Their detail panel shows the size and offers export, restore and delete.

FH4Droid and Xenia ZIP imports support up to 4 GiB of expanded save data. Imports check free space while staging and show progress as each file is read. Older archives can contain installed DLC, disabled packages or licence headers. The importer reads past those entries without staging them or counting them toward the save-data limit. It can scan up to 8 GiB of skipped content, and any DLC already installed is preserved. FH4Droid save exports also support up to 4 GiB of save data and exclude DLC.

To transfer a base-game backup to another phone, choose **More → Save backups → Restore saves** on FH4Droid 0.9.5 or newer and select the ZIP. The importer selects the archived player ID, or identifies a single owner from the save folders in an older export. The previous profile selection is saved with the rollback backup. FH4Droid and Xenia imports check version flags before replacing saves. Title-update saves require a matching game build.

## Reports and recovery

**Settings → Audio → 0.5.1-style audio** uses the guest XAudio2 output, original FMOD mixer and EQ, inline XMA decoding, and per-frame audio updates on the next launch. The current decoder and Android output driver still provide their fixes. The switch lets the same phone compare current audio with the major game-side audio settings from Pinyon Shift 0.5.1.

**In-game menu → More → Diagnostics → Capture a problem** saves recent frame times, audio counters, page recoveries and active settings. Capturing a white car or blank tile also tries to save the visible game frame. Export a problem report to include these captures. Regular Play records opens of the collision, glass and festival audio banks.

Regular **Play** records runtime logs, settings snapshots and bounded frame timing data. Reproduce the issue using Play and open **More → Problem reports → Share problem report**. Reports distinguish a session recorded without logs from earlier captures.

**More → Problem reports → Play without logs** selects quiet mode for one launch. It suppresses app session logs, frame history and diagnostics during that session. Saves, achievement data and graphics caches are still written. Android manages its own system logs.

Exports retain CSV headers and whole rows. The report index identifies file size/truncation, originating session and source commit, and separates the latest session from older history and shared logs. A session from an older APK is not labelled current after an update. Thermal values are named severity levels, not temperatures, and normal self-exits are distinguished from crashes and low-memory kills. Saved, launch and live settings have separate snapshots; phone FPS/thermal samples carry session/build provenance.

The report analyzer, `tools/analyze-android-report.py REPORT.zip --target-fps 30 --skip-seconds 10 --duration 60`, summarizes an explicit capture window. Menus/loading remain included unless the selected window excludes them. Audio-counter overlap is correlation, not a diagnosis. Truncated captures describe their exported tail, not the whole run.

Repeated export taps are blocked during preparation. Each share uses its own completed ZIP. Reports include device/settings/driver data and bounded logs, not saves or game media; review them before sharing.

The launcher and game run in different processes. Recovery uses Android's exit reason when reported, with advice for low memory, an unresponsive game or a crash. **Try system driver once** uses the system driver for that session while keeping the saved selection.

### Android comparisons

Use a pinned render seed and a scripted route on a connected Android device. The capture runner starts two baselines and one trial in separately copied saves, settings and caches. It pulls the frame CSV, route events and requested scene images from each. The optional warm-cache run takes its initial shader cache from the seed.

```sh
python tools/android-performance-suite.py capture-pair --seed PATH/TO/SEED --route PATH/TO/ROUTE.fh1test --trial cpu-hints --output .local/android-comparison
```

Record an equal gameplay window and matching start temperatures for comparisons. Track startup, a race, Autoshow paint, car thumbnails, engine and impact audio, low resolution, photo and widescreen transitions, resume and sustained driving separately on each device and driver. A timing result or a startup probe gives partial evidence for that combination. Adreno 630, Mali-G52 and Xclipse still need those gameplay results.

An intentional native window close records a clean exit before the runtime's hard termination. Finishing the game activity also records that intent before SDL teardown, so a normal quit does not depend on Java cleanup running after native shutdown.

## Build and implementation notes

The public maintainer guide covers the released source, toolchain, fork signing and save transfers. [FH4Droid fork and maintainer guide](https://github.com/XHYN-PH/fh4droid/blob/main/docs/releases/fork-handoff.md)

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
