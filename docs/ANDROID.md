# Pinyon Shift on Android

## FH4Droid Android-only fork

FH4Droid focuses on Android only. The `0.6.8-r7` testing build uses application ID `ph.xhyn.fh4droid`. Install the APK and choose your own supported USA retail ISO or complete extracted game folder; the phone does not compile the game. See the [fork release notes](releases/fh4droid-0.6.8-r7.md) for requirements and limitations. The r7 integration has not been tested on a physical Android device. The [current Android guide](../site/pages/android.md) describes the launcher, drawer, settings, profiles, controls and reports; the upstream sections below retain their named historical measurements.

The current minimum is Android 12/API 31 with ARMv8-A. Vulkan 1.1/1.2, Mali and Xclipse are experimental capability paths, not qualified gameplay claims. The [70-item review](releases/fh4droid-0.6.8-r7-review.md) lists unresolved bugs and device tests.

The developer build instructions and historical measurements below are inherited from upstream. Current FH4Droid source uses local ISO/folder import; its PC discovery, pairing, Wi-Fi transfer and network app-update client have been removed. Its private-build distribution policy describes upstream Pinyon Shift; this fork's prebuilt APK release is separate and contains compiled game translations. Raw game files, generated source, native build products and signing keys remain excluded from the source repository.

## Upstream Android status

**Status: alpha, for developers.** The game builds for Android, installs and
plays through the Vulkan renderer. On the reference tablet (nubia NP05J,
Snapdragon 8 Elite) the race runs at 60 fps at 1x until the device heats up,
and settles near 30 once its skin passes about 44 C
([60 fps backlog](ANDROID_60FPS_BACKLOG.md)). The target is a high-end Android handheld or phone with
a Snapdragon 8 Gen 2 or newer (Adreno 740+), 12 GB of memory and Android 13
or later. The plan and its progress are in the
[Android port backlog](ANDROID_PORT_BACKLOG.md); the AYN Odin 2 Portal
(Adreno 740) has its own [Odin backlog](ANDROID_ODIN_BACKLOG.md). On Adreno
7xx, Mesa Turnip runs the game much faster than the stock driver, and
newer Mesa builds faster still: on the Odin 2 Portal the busy drive takes
32 ms at 4x MSAA and 28 ms at 1x with Turnip Gen8 V37 (Mesa, Vulkan
1.4.359), against 39 and 34 ms with an older Turnip and 49 and 41 ms with
the stock driver.

The package bundles Turnip Gen8 V37 (MIT, built by StevenMXZ from Mesa; its
notice is in `android/drivers/turnip-gen8-v37/NOTICE.txt`) and installs it
into `files/state/drivers` at start. SETTINGS > GRAPHICS > GPU DRIVER picks
the Vulkan driver loaded at the next start (`android_gpu_driver`): AUTO, the
default, loads the one recommended for the GPU (Turnip Gen8 V37 on Adreno
7xx and 8xx, the system's elsewhere), SYSTEM the device's own, and every
folder under `files/state/drivers` by name. IMPORT DRIVER (.ZIP) opens the
system's file picker for an adrenotools package (a `.zip` with `meta.json`
and the driver `.so`), which is unpacked into a folder named after the zip.
The page's note names the driver in use.

On the Adreno 830 (Snapdragon 8 Elite, Android 15) Turnip Gen8 V37 takes
20.2 ms of GPU per frame in the race at 1x with MSAA off, against 24.8 ms
for the system driver (Qualcomm v800.40) and the same for Qualcomm's
v800.51 and v842.8 packages; the route matrix renders the same on Turnip
and the system driver. Qualcomm packages (the "Adreno driver" zips shared
for emulators) name their libraries like the system driver's
(`vulkan.adreno.so`, `libgsl.so` and others). The system driver is already
loaded when the game opens Vulkan, so the loader once returned it for every
such package; the game now gives the copied package's libraries unique
names first, and the log's `Vulkan device` line shows the driver version
that runs. Packages built for Android 16, such as v891.7, need a binder
function Android 15 lacks: with that stubbed out v891.7 loads, then crashes
within a minute. Packaging
reads the driver binary from `.local/android/drivers/turnip-gen8-v37`
(`meta.json` and `libvulkan_freedreno.so`) and skips it with a warning when
absent.

Android builds trade some image quality for frame rate by default. Each is
a row on the in-game SETTINGS > GRAPHICS page that applies while the game
runs (SHADOWS, REFLECTION UPDATES, SINGLE-PASS SCENE; the last rebuilds the
renderer between frames, about 80 ms), and the GRAPHICS PRESET row sets
them together: QUALITY 30 (4x MSAA, shadows, 30 fps) or SMOOTH 60 (2x MSAA,
no shadows, 60 fps), both single-pass with quarter-rate reflections. As
settings in `config/pinyon_shift.toml`: FH1's three predicated tiles are
drawn once (`fh1_untile_predicated_tiling`, same image), the reflection
cubemap is redrawn at a quarter of the game's rate
(`pinyon_shift_fh1_env_map_rate = 0.25`; 1 restores it) and the sun's
shadows are off (`pinyon_shift_fh1_shadows = false`; true restores them,
about 6 ms a frame on the Odin). With these, MSAA off and the frame limits
at 60 (`host_present_fps_limit` and `pinyon_shift_fh1_render_fps_limit`),
the Odin 2 Portal holds 60 fps in free roam and races on Turnip Gen8 V37
(15.5 ms of GPU a frame); with 4x MSAA it runs near 52 fps.

`android_thread_cores` (default `auto`, changes apply while the game
runs) pins threads to cores by thread-name prefix. `auto` puts the GPU
command decoder on the fastest cores when two or more of them form
their own cluster and do not cover every core (Snapdragon 8 Elite:
cores 6-7); other devices are left to the scheduler. On the Astra, in an
in-run A/B at the 60 fps limit, frames over 17.5 ms went from about
25 % to 2 %, p95 from 18.7 to 17.2 ms and decoder CPU from 16.2 to
10.2 ms a frame, with GPU time unchanged. The 22-minute soak with it
has not been run. Explicit values look like
`GPU_Commands=6-7;*=0-5` (`_` matches a space, `*` every other
thread); `off` restores the threads' earlier masks.

## What stays private

The package holds the game translated from your own disc, exactly as
`pinyon_shift.exe` does on Windows, so it is built on your PC and installed
on your own device, and it is never published or shared. The game files go
from your PC to your device over your local network or USB. Nothing is
uploaded. The repository refuses Android packages, libraries and signing keys
(see [legal](LEGAL.md)).

## Requirements

- A Windows PC where the game is already built (the launcher, or
  `tools/build-preview.ps1`): the Android build reuses the code translated
  there.
- Nothing else on the PC: the launcher downloads the pinned Android SDK
  command-line tools and JDK 17 when the PC has none, then the NDK, build
  tools and platform from `config/android-toolchain.json` once you accept the
  Android SDK license, and builds with the pinned CMake and Ninja. About 15 GB
  free on the install drive. From a terminal, `pinyon.py android doctor
  --install` does the same (`--accept-licenses` answers yes).
- The device: arm64, Android 13 (API 33) or later, a Vulkan 1.3 driver,
  about 8 GB free for an extracted folder. USB debugging is needed only
  for the adb commands.

## Local installation and game import

Install the signed FH4Droid APK through Android's installer. Open **Game files**
and choose your supported USA base-game ISO or complete extracted folder.
To use an extracted folder from a PC, copy it to the device over USB first,
then choose the folder containing `default.xex`. The app checks it against the
supported catalog before replacing an installation. Completed files are kept
when an import is cancelled; select the same source to resume it.

The long-press **Game files** shortcut opens the local importer. The launcher,
importer, touch editor, driver probe and game stay in fixed landscape orientation.
The left footer contains text links; the right shows the installed version and
build number. App updates are installed manually through Android.

The following commands are for developer builds and USB deployment.
With USB debugging on, the tools can install the package and copy the game
with adb. From a terminal:

```bash
python tools/pinyon.py android doctor --install
```

```bash
python tools/pinyon.py android build
```

```bash
python tools/pinyon.py android install
```

```bash
python tools/pinyon.py android push-data
```

`build` cross-compiles the game and packages `.local/android/pinyon-shift.apk`,
signed with a key made on your PC. `push-data` copies the extracted game
(7.2 GB, 2,400 files) into the app's folder on the device,
`Android/data/ph.xhyn.fh4droid/files/game/base`; it resumes where it
stopped if interrupted. Saves, settings, logs and mods live beside it in
`files/state`, with the same layout as on the PC, so a save copies between
the two as a folder.

FH4Droid installs separately as `ph.xhyn.fh4droid`. Upstream Pinyon Shift
uses `studio.deimos.pinyonshift` (or `com.pinyonshift.fh1` for older builds).
This fork does not migrate those installations automatically. Import the
extracted game folder with **Choose folder**, and back up existing saves
before copying progress between apps.

DLC and the v4 title update work as on the PC, with the content copied into
the device's state. Import and enable packages into a state folder on the PC
(`tools/manage-fh1-dlc.py`), then push its `user` folder (and `dlc`) to
`files/state`. On the base build, Rally also needs the overlay
`tools/prepare-fh1-rally.py` writes to `cache/rally_adapter`; the activity
turns Rally on when that overlay is present. The v4 build runs Rally and 1000
Club natively. It is built from the player's verified update after
`tools/build-v4.ps1` has generated its code:

```bash
python tools/verify-fh1-title-update.py --install STATE tu00000001_00000000
python tools/pinyon.py android build --title-update-v4
python tools/pinyon.py android push-title-update --state-root STATE
```

A save written by v4 cannot be loaded by the base build afterwards, so keep a
copy before its first v4 start. Offline 1000 Club needs
`pinyon_shift_car_challenge_gate_probe` and `xam_report_live_signin` on. For a developer v4 build, put both flags in
`files/state/config/android-arguments.txt`; the game adds that file’s lines
to its arguments at every start.

Start the game from the launcher icon, or:

```bash
python tools/pinyon.py android run
```

`run --null-gpu` starts it without a renderer, and `run --route FILE` runs a
render-test route (`config/render-tests/`). `pull-logs` copies the logs,
crash reports and route output to `.local/android/device-logs/`.

## Controls

A controller (built in, Bluetooth or USB) works as on the PC. On a touch
screen, the default controls have separate left/right steering arrows,
accelerator (RT) and brake (LT) pedal pictograms, A, B, X, Y, the bumpers,
Back and Start, a right look stick, four separate D-pad directions and both
stick clicks. Both arrows held together give neutral steering. Controls →
Steering in the touch editor switches between Arrows and the original Stick,
keeping saved positions for both. Icons use a shared PNG atlas independently of
game fonts. Driving symbols show handbrake (A), shift up (B), shift down (X),
rewind (Y), clutch (LB), camera (RB), map (Back) and pause (Start), with small
button/action captions for menu prompts. These action names assume default game
bindings. The D-pad remains independent of steering; L3/R3 press the stick
buttons and the right stick controls the camera. All 20 editable controls,
including the optional left steering stick, have shared antialiased PNG artwork.
Held controls stay visible; global and per-control idle timers govern fading after
release. Controller auto-hide can be changed in the editor
(`pinyon_shift_touch_controls` turns the overlay off).

The editor also provides immediate/120 ms/240 ms arrow response, optional sliding
pedals (half input initially; slide up for more or down for less), high contrast,
and optional touch vibration. The full-window Properties view groups tools under Control, Layout, Driving and
Appearance; the page scrolls when needed. Position, size, opacity and response
values are visible, including effective high-contrast opacity. Position updates
while dragging. The control selector and position/size sliders support editing
without drag gestures, with accessible value feedback. These changes have host tests and Android compilation
checks; on-device gameplay and accessibility validation remain pending.

Problem-report exports use separate completed ZIP files. Repeat export taps are
blocked while a report is being prepared; a later export does not replace an
already shared report. Advanced driver environment settings decode quoted TOML
escapes before preflight, matching the saved string values.

For maintainer releases, the Python Android build/package commands accept
`--keystore`, `--key-alias`, `--storepass-file` and `--keypass-file` together to
reuse an existing signing key. Passwords are read by the signer from private
files. Incomplete release-signing arguments fail before compilation; they do not
fall back to a new local key. APK packaging verifies signatures and 16 KiB ZIP
alignment after signing. Before publishing an update, compare its certificate,
application ID and version code with the previous APK. Keep keys, password files
and signing backups outside the public source and release assets.

When configuring an existing, reviewed generated-code snapshot, set
`PINYON_SHIFT_FROZEN_CODEGEN=ON`. This uses the complete saved modules and stamp
without requiring the standalone generator. It does not regenerate changed game
inputs or generator settings; use matching source and SDK revisions, and retain
the normal generator requirement for builds which regenerate code.

Android's Back button or gesture opens the pause drawer, and inside the menus it steps
back; tap a row to open or change it. The FH4Droid drawer also holds achievements and the separate-profile trainer.
Android photo capture and minimap repositioning are omitted.
Clicking both sticks (LS + RS) shows or hides the performance panel, F3 on a
keyboard; SETTINGS > CONTROLLER > PERFORMANCE PANEL changes the buttons
(`pad_chord_debug_overlay`).

## Troubleshooting

| Symptom | Cause |
| --- | --- |
| Folder import rejects the source | Choose the complete supported base-game folder containing `default.xex`. Copy it to the device over USB before importing. |
| The app closes at once | Started from adb without the game files: open it from the app icon, or run `push-data`. If logcat says `Cannot create the state folder`, files were copied into the app's folder by hand: run `push-data` again, which lets the app use them. |
| `VULKAN_CAPABILITY_REPORT` in the log | The device's features, formats and memory, logged at every start; attach it to reports. |
| Resolution or preset causes artifacts | Restore 100% raster scale and the default preset, restart and export a report. The Android UI offers 25–100%; phone-driver qualification is pending. |
| `skipped a resolve` in the log | A guest copy the renderer cannot pack yet (one is known, in the title screen's attract sequence); the frame continues without it. |
| No sound | No output device could be opened; the game runs silently instead of stopping. |


## Settings restart verification (2026-10-06)

The settings menu now derives renderer restart requirements from SDK flag
metadata, while retaining project-specific title/profile requirements. On the
Odin 2 Portal, changing MSAA from 4X to OFF shows a restart badge and pending
restart note. A fresh process retains OFF and clears that pending note. This
qualifies settings persistence and restart presentation; it is not a new GPU
performance or image-quality measurement.

The [Android MSAA scenario](../config/render-tests/fh1-android-msaa-restart.fh1test)
uses a private state with MSAA initially 4X. Its Windows-line-ending variant
also runs on Android, covering CRLF handling in both the schema and clock
metadata. Run the scenario using a private state-root intent override and
inspect the host menu with an ADB screenshot; ordinary render-test captures
contain the guest output and omit the host UI. Existing device progress is
outside the qualification state and must not be replaced for this check.

The subsequent build includes the shared diagnostic Rally AI control-release
fix. Its signed/aligned APK was installed on the Odin without clearing app
data, and private session `20261006T065043Z-p2335` completed the CRLF reload
route at frame 1200 and shut down normally. This is a startup check; Rally
driving and GPU performance on Android remain unqualified. Current artifact
verification is recorded in ignored `.local/android/latest-apk-verification.json`.

In the historical upstream qualification below, bloom defaulted to OFF with a live toggle in Graphics settings. It zeros
the title's frame-local bloom scale while preserving exposure, tone mapping
and weather data. A signed/aligned ARM64 build was installed on the Odin 2
Portal and passed a private-state on/off/restore gameplay run through frame
5400 (`20261006T103905Z-p8506`), with three native free-roam captures and normal
shutdown. All 17 files in the normal device user tree remain byte-identical.
The PC pinned seed profile is unchanged. Receipt:
`D:/horizon1-recomp-tests/android-bloom-20261006/qualification.json`.
This checks the switch and gameplay stability. Captures have different vehicle
poses; they are not a pixel-matched A/B. Stepped paint shading and very bright
vegetation are still visible on Android. Cutscenes, showroom/photo mode and
those graphics defects remain unqualified. Bloom filter passes still execute;
no performance improvement is claimed.

MSAA now defaults to OFF for unset configurations, including desktop. The
subsequent signed/aligned Odin build showed OFF with no saved MSAA flag and
retained an explicit saved 4X choice in separate private states. All 16 normal
device profile files retained their pre-install hashes. These are configuration
and startup checks; sustained driving performance remains unqualified.

Fatal scripted-test rejection now exits immediately after flushing its failure
receipt. Reusing an existing output directory previously triggered SDL thread
crashes during static destruction on the Odin. A private rejection check and
desktop subprocess check preserve that directory and emit the expected failure;
the updated Odin run produces no corresponding native crash record.

## Current mobile presentation

The launcher shows the existing transparent FH4Droid logo without a backing box.
Settings use Graphics, Controls, Audio, Interface, Gameplay and Advanced sections;
Android Back opens Resume, Settings, Controls, More and Quit. The touch editor
uses the whole window for placement, with a separate full-window Properties
view and Save, Cancel, Test and Hide tools actions. The active custom layout is
kept until the player explicitly applies a preset or saves an edited draft.

`config/touch-artwork.json` generates the PNG loaded by Android and embedded
byte-for-byte in the native overlay. `tools/generate-touch-artwork.py --check`
validates both consumers without Pillow. Regeneration uses Pillow 11.3.0 and CairoSVG 2.8.2 from `tools/requirements-touch-artwork.txt`. The source SVGs use square 24-unit viewports, with proportions preserved. Lucide/Feather license notices ship in the Android resources and are available through More → About → Control artwork credits. The
production mesh readback test covers all 20 symbols, captions, classic letters,
pressed state and stick knobs; its optional PPM output is a software artwork
proof, not an Android gameplay screenshot. Phone layout and legibility still
need device testing.

The fractional-resolution band-clear regression and its limits are documented
in the [Android player guide](../site/pages/android.md#fractional-resolution-lines).
The fix changes intermediate untiled-band clears only; guest texture layouts
and 100% resolution remain unchanged.

The current driving artwork uses thin utility symbols and translucent pedal
faces with bevel highlights and grip grooves. Accelerator, brake and clutch
use their pedal silhouette directly; the touch targets retain their saved size.
Pressed modern pedals receive the same warm tint in native rendering and the
editor. SVG gradients reference only definitions inside the same source file.
