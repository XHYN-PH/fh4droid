# FH4Droid 0.8.2 upstream test 1

This test integrates stable Pinyon Shift 0.8.2 (620e7c4d) and ShiftGlue
101ad468. Android 13/API 33 is the minimum; ARMv8-A and 16 KiB native/ZIP
alignment are retained. The original background, local file import, mobile
controls, profiles, reports and driver tooling remain. No PC-over-Wi-Fi setup
is restored.

## Included changes

1. Upstream XMA2 audio changes.
2. Upstream native renderer and shader corrections, combined with the existing
   fractional raster scale and view-transition fixes.
3. Optional vertex shader stores requirement for Mali compatibility.
4. Mali coarse scene-shading options and first-run hardware defaults.
5. Pipeline preparation before play or during the title; compatible prepared
   pipeline lists can be imported in More → Mods, DLC and shaders.
6. Upstream lighter automatic backup handling.
7. In-game achievement categories, counted progress, nearest unlocks, tracking
   and notifications. The launcher retains its cached offline summary.
8. Live chase-camera height, distance, FOV and stiffness controls.
9. Global FOV scaling up to 200%.
10. Catalog-verified on-device DLC package, ZIP and folder imports. Nested ZIPs
    are bounded to three levels; skipped inputs are reported individually.
    Packages start disabled. Payloads, headers and licence variants are verified.
11. UDLC guidance and dependency checks, with no automatic content download.
12. Mod import, enable/disable, priority and manifest options. Database changes
    are built into a separate overlay transactionally, using the modded profile.
13. Optional Festival pacing and Hidden cars installs with upstream SQL/options.
14. Verified prepared Rally adapter import and database rebuild on Rally changes.
15. Upstream partial-config repair, retaining saved values and backup behavior.
16. Graceful native shutdown from Quit to launcher.
17. Updated build tools, pinned SDK patch replay and source-built Android shell.

## Setup and limits

Use your own supported USA MS-2505 base-game dump. This APK is the base build,
without title update v4. The original supported executable was verified and
main game translations regenerated with the matching upstream toolchain and
analysis. Unchanged speech/xmedia translations are reused through current SDK
headers; they were not regenerated from new executables.

Content edits require the game to be closed. Earlier enabled mods win file
conflicts. Disable/remove a mod before replacing it. Importing source alone
does not build native plugins: they need compatible Android ARM64 libraries.
Archive-member/merge scripts and the Rally adapter require preparation using
the upstream PC tools and the player's matching files. SQL triggers also need
PC preparation. No game files, DLC, shader payloads or signing keys are added
to the source repository.

This is not a physical-device qualification. Vulkan 1.1/1.2 capability paths,
Mali-G52, Xclipse and older Adreno phones still need testing. The followers'
Turnip device-loss reports and the white-car/paint screenshot are not proven
fixed by this update. Xbox LIVE authentication/services are not implemented.

## Validation

The matching ARM64 game library and native helper compile and link. The Python
suite runs 719 tests with 99 skipped; Java compilation and 3,010 host checks
pass. Native touch/view/stall, configuration/save, disc-image bounds and software
Vulkan regression checks pass. The GPU checks cover Vulkan 1.1/1.2/1.3 dynamic
and legacy render/readback and 6,304,254 colour/depth pixel comparisons across
25–100% raster scale. The 107 SDK source changes reproduce exactly from the
pin plus seven tracked patches. The documentation build and 4,235 local
links/anchors pass. Detailed results are recorded with the test APK. These checks do not measure phone FPS,
thermal stability, Android SQLite execution, or actual DLC/Rally gameplay.

## Phone checks

- Install as an update and confirm profiles, saves and settings are retained.
- Launch, drive, pause/resume, background/resume, and quit to launcher.
- Compare arrows with phone tilt disabled, and test simultaneous gas/brake,
  steering, camera and menu inputs.
- Change low raster resolutions and enter/leave map, garage and photo views;
  watch for horizontal lines, shrinking or sidebars.
- Try the system driver first on Mali/Xclipse; report selected GPU/API/driver.
- Check achievements, camera changes, audio and a longer driving session.
- With backed-up saves, test optional mods and owned DLC one at a time. Report
  the exact import action, package/mod name and exported report if it fails.
