# FH4Droid 0.6.6-r5 — Android test release

Android 13+, ARM64 and Vulkan 1.3 are required. Supply your own supported USA
base-game ISO or complete extracted folder. Title Update 4 is not required;
keep TU4 saves separate. This release integrates selected UI and runtime work
from the reviewed hotfix reference into the fresh source.

## Controls and menus

- Racing artwork for all 20 touch controls, including rewind on Y, with optional
  vector Xbox hints for game prompts. Action names assume default bindings.
- Explicit **Steering: Arrows / Stick** in Touch settings and the editor. Both
  positions are remembered. Each stick supports floating, fixed and invisible.
- Full-screen editor, movable/collapsible toolbar, precise sliders, accessible
  nudges, local named layouts, handed templates and separate driving/controller
  styles. Global/per-control fade timers keep held buttons visible.
- Compact responsive launcher, brighter branding, right-side pages and an
  in-game drawer with Resume. Adjustable menu scale and optional animations.
- Categorized settings, search, favourites, remembered scroll/focus, active/saved
  labels, section resets and undo.

## Graphics, input and game features

- Raster scale 25–100% in one-percent steps, custom values, FSR profiles, screen
  modes and coordinated graphics presets. Fractional caches use raster-v3.
- Fractional transfer/sampling fixes, bounded shader-preparation concurrency and
  optional detailed GPU profiling. No phone speedup is claimed.
- Driver names, confirmed imported-driver removal, extended feature/format and
  render/readback diagnostics; cache-only graphics management while closed.
- Physical-controller calibration, optional phone rumble and tilt steering.
- Achievements with cached artwork and a trainer on a separate modded profile.
  Normal save exports cover the normal profile, not the trainer profile.
- Opt-in sustained mode: temporary severe-thermal cap and delayed recovery
  without changing saved presets. Adreno-specific experiments with a reset.

## Reports and fixes

- CSV exports retain headers and complete records. Reports label current,
  historical and shared files with session/build provenance and truncation.
- Saved, launch and active settings are recorded separately. Thermal severities
  and normal exits have readable labels; bounded phone telemetry carries its
  session/source commit. Detailed native profiling remains opt-in.
- A comparison analyzer accepts explicit time windows and reports frame-time
  percentiles and audio-counter overlap without claiming causation.
- UI preference handoff uses unique atomic temporary files. Pausing stops
  steering, vibration and thermal callbacks; huge tilt values cannot loop.
- The docs workflow builds main or the published source tag instead of requiring
  a missing dev branch. Android release tags skip inherited desktop packaging.

Layout document import/export, Android photos and minimap positioning are
excluded. Installation/content verification keeps its previous behavior,
including the existing ISO support-cache limitation. Wi-Fi import, cancellable
file operations, atomic settings and shared custom-driver loading are retained.

## Install and test

Back up important saves. Install this signed update over FH4Droid to retain
app data; versionCode 606 supersedes 605 and the reference hotfix's 604.

Test arrows and stick steering, simultaneous pedals/look controls, each stick
mode, controller handoff and layout save/cancel. Check the drawer and editor
with larger text, try presets with a restart, and drive the same short route
before exporting a report through More → Report a problem. Disable tilt,
rumble, sustained mode and driver experiments individually when isolating an
issue. Keep the same scene and thermal conditions for performance comparisons.

## Validation and limits

The full ARM64 game/runtime/driver/UI build and host Java/native/Python suites
pass. Software Vulkan checked 953,854 colour/depth results across every
supported raster scale, and the extended probe passed its Vulkan 1.2-extension
and 1.3-core paths. APK signature, upgrade identity, packaged hashes and 16 KiB
alignment are verified before publication.

These checks do not qualify phone rendering, haptics, sensors, controller
calibration, accessibility or sustained performance. This remains a test release
for your device feedback. Generated source, game media and private signing
material are excluded from public source and release assets.
