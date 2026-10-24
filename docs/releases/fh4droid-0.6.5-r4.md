# FH4Droid 0.6.5-r4 — Android test release

Fresh-source testing build for the Xbox 360 USA base release of Forza Horizon
(2012). Android 13+, ARM64 and Vulkan 1.3 are required. Title Update 4 is not
required; keep TU4 saves separate.

## Changes

- Drawn touch icons independent of game fonts, with left/right arrow steering
  by default. Holding opposite arrows is neutral; sliding off releases steering.
- Saved steering positions from the reference hotfix are converted separately
  from its D-pad positions when upgrading.
- Held controls remain visible. Cancellation, menus and controller handoff
  release touch inputs.
- Optional steering response, sliding pedals, high contrast and touch vibration.
- Grouped touch-editor tools, visible position/size/opacity values and accessible
  selection and movement controls. Launcher navigation adapts to window changes.
- Atomic settings presets, correct default values, clear failed-save feedback,
  and decoded quotes/backslashes in advanced driver settings.
- Clean quits stop triggering interrupted-session recovery. Folder/Wi-Fi imports
  clear an older ISO selection after completion and support progress/cancellation.
- Driver preflight and gameplay share the custom loader; bundled driver updates
  compare file content. Failed custom loads do not silently select the system.
- Report exports use separate completed ZIPs and block repeated export taps.
- Build reports identify the exact stripped library shipped in the APK.
- Damaged desktop ISO input closes cleanly and reports truncated headers.

This build uses the fresh source developed from main, with its own reviewed fixes.
The uploaded r3 source was inspected as a reference and was not integrated.

## Install and use

Back up important saves before testing. Install the signed update over FH4Droid
to retain its app data. Android versionCode 605 is above the reference hotfix's
604. APK certificate, alignment and upgrade compatibility must pass validation
before publication.

Use Game files to select your own supported USA base-game ISO or complete
extracted folder. Keep an ISO accessible during play. Use Controls to edit the
arrows and pedals, and Reports to export logs after reproducing a problem.

## Validation and limits

All 343 host Java checks and the native touch tests pass. The Python suite
passes 550 tests with 88 platform/environment skips, and documentation checks
pass. The full ARM64 game, driver helper, runtime, GPU plugin, driver hooks and
FidelityFX library compile and link successfully. Final APK verification is
recorded with the release artifact.
These checks do not establish phone rendering correctness, touch feel, haptics,
TalkBack behavior or sustained performance. Physical-device qualification is
pending; this remains a testing build.

Installation/cache verification retains its existing behavior. In ISO mode,
the content check does not additionally validate cached support files.
Generated game code, game media and private signing backups are excluded from
the public source and release assets.
