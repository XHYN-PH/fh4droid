# FH4Droid pre-UI audit — 2026-10-27

This unreleased follow-up reviews report `eff686ae-60a9-4f7f-94b4-c44e8134cf2e`
against r7 source `6642d6fd969bcb000000049d5164073271d71316`. UI redesign,
artwork, layout changes, blur, APK packaging and publication are deferred.
This is a host-code audit and software-renderer check, not phone qualification.

## Evidence from the current session

The report identifies 0.6.8-r7 on vivo V2425A, Android 16, Turnip Adreno 750
(Mesa 27.0.0-devel). Its source manifest matches the clean r7 source. The current
session is `20261027T133326Z-p25861`, approximately 21:33–21:43 Asia/Manila.
Only the current session's segment of the shared runtime log was counted.

- The session ends normally. Its summary contains 371 warning **lines**, zero
  errors; stack dumps account for many lines, not independent failures.
- Saved controls select arrows, while phone preferences enable tilt. The user
  subsequently confirmed forgetting to turn tilt off. Accelerator-only touch
  does not count as manual steering, so this combination intentionally allows
  tilt. No forced mode migration or input-preference change was made.
- At 21:39:10.370 the app enters the background and pauses GPU, audio and guest
  time. A 40.7-second stall dump at 21:39:51.105 precedes the foreground message
  at 21:39:51.247. This is evidence of a background-watchdog false positive,
  not a confirmed 41-second driving freeze.
- Thirteen view-mode events include rapid wide/narrow transitions. These can
  include legitimate menus; the log alone does not identify every bad frame.
- There are 119 XMA warning lines, including 118 packet-resolution warnings;
  two stale physical-page protection recoveries; and four incomplete GPU timing
  warnings. None establishes a crash or an audible defect by itself.
- The current performance CSV is marked tail-truncated. Median frame time is
  33.323 ms and p95 is 33.986 ms; its 40.912-second interval includes the
  background pause. This mixed startup/menu/driving trace is not a sustained
  gameplay benchmark. Export reports no memory pressure and normal thermal
  status. Sampled process resident memory peaks around 1,304 MB.

## Implemented fixes

### Stable view transition timing

Previously, a late map sample could reduce an already-running ordinary
800 ms camera-gap delay to 180 ms immediately. A lone map signal 300 ms into
that gap could therefore shrink the view. The map delay now measures its own
continuous interval. Interrupting map evidence resets only that interval;
ordinary frontend detection still completes after 800 ms. Gameplay widens
after its existing 150 ms interval.

A gap over one second between observations preserves the last committed view
and restarts pending evidence. Background time cannot complete a pending view
transition without fresh samples. Seven newly added assertions failed against
the old policy; all new and existing cases pass against the correction.

### Background-aware stall timing

The watchdog receives application background/foreground lifecycle events.
Automatic dumps are suppressed in the background. Resume starts a complete
new grace interval even when the watchdog received no background polls and no
new title frame has arrived. A genuine post-resume stall still produces one
dump. Automatic messages report active elapsed time; an explicitly requested
thread dump remains available and is identified as requested.

The timing state is synchronized separately from the atomic frame timestamp;
the frame hook acquires no additional per-frame mutex. Host menus do not
masquerade as application background events.

### Optional GPU profiling on unsupported queues

Before allocating timestamp queries, the native executor checks the selected
graphics/compute queue's `timestampValidBits`. A zero count disables optional
profiling with one explanatory startup warning, while renderer initialization
continues. The change is recorded in the reproducible pinned-SDK runtime patch.

This does not claim to fix Turnip's incomplete query results in the report.
That existing path already retries without blocking, discards unavailable
spans and bounds warnings to four. Partial timings must not be treated as
complete GPU frame measurements. Profiling remains opt-in.

## Investigations and remaining limits

| Original list | Outcome |
| --- | --- |
| 1 — Widescreen stability | Corrected timer inheritance and long-gap handling; phone transitions remain to verify. |
| 2 — Background diagnostics | Corrected lifecycle timing; genuine active stalls still dump. |
| 3 — Steering-mode clarity | UI deferred. Input semantics documented; tilt settings are preserved. |
| 4 — Audio warnings | Status 4 is an out-of-range packet in the existing bounded resolver. Cross-buffer bounds and strict padding were retained; no captured failing packet/audio fixture establishes a safe decoder fix. The user's earlier listening report described normal audio. |
| 5 — GPU profiling | Added unsupported-queue guard. Existing incomplete-query recovery retained; no rendering compatibility or performance claim follows from a profiling warning. |
| 6 — Memory-protection recoveries | Reviewed the write-watch and alias-aware recovery path. Two reported writes recovered; no unhandled fault follows. A reproduced fault/invalidation trace is still needed before changing protection rules. No memory permissions were relaxed. |
| 7 — Horizontal lines | Production SPIR-V readbacks pass across 25–100% resolution, including the prior band-clear failure. No new shader correction is justified by this report alone. The reported phone artifact remains unverified. |
| 8 — White/overexposed cars | Existing cube-LOD and luminance-resolve findings remain relevant, but this report has no same-frame material/resolve capture. Clear/readback tests do not exercise car-paint shaders. No global colour clamp, reflection removal or exposure workaround was introduced. Unresolved pending reproduction. |
| 9 — Touch artwork | UI deferred. |
| 10 — Touch layout/input | Added pedal ownership, accelerator-only and simultaneous steering/tilt/controller regressions. Spacing and visual layout work are deferred. |
| 11–15 — Editor, launcher, settings, in-game menu, blur | UI deferred. |
| 16 — Regression testing | Focused native policies, touch behavior, Android Java, Python tools, actual Vulkan shaders, driver probe paths, SDK patch reproduction and changed Android native compilation checked. Physical gameplay and budget-device qualification remain outstanding. |

## Validation

- Native view policy and stall timer tests pass, including map flashes,
  interrupted evidence, ordinary frontend detection, long gaps, clock reversal,
  disabled watchdog, repeated lifecycle events and genuine post-resume stalls.
  Both also pass AddressSanitizer and UndefinedBehaviorSanitizer checks.
- Native touch-pad regressions pass: tilt-off acceleration has zero steering;
  a pedal pointer dragged over an arrow remains a pedal; an independent arrow
  overrides tilt while gas stays held; controller handoff clears synthetic input.
- Seven existing lightweight native host suites pass.
- Android Java source compilation and 2,973 host checks pass.
- Python tools: 563 tests complete, 88 skipped by existing platform/fixture gates.
- Shipped SPIR-V on llvmpipe checks 972,800 MSAA colour pixels, 4,377,600
  tiled-resolve pixels and 953,854 colour/depth values. The old band-clear path
  reproduces 13,600 corrupt pixels; the existing corrected path has zero.
- Driver probe: Vulkan 1.1, 1.2 and 1.3, dynamic and classic paths, pass RGBA8,
  10-bit and float16 clear/readbacks (18 image cases). This is software-driver
  coverage, not Mali, Xclipse or Adreno gameplay qualification.
- All 112 patched SDK files reproduce from the pinned sources and seven
  tracked patches. Changed Android ARMv8-A/API 31 native translation units
  compile; both the GPU plugin and full `libmain.so` target link. Existing SDK
  compiler warnings remain (including missing `override` and register-width
  diagnostics); the build has no compilation or link errors. No APK was packaged.
- The documentation site builds all 37 pages, Markdown links pass, and the
  final source diff has no whitespace errors.

## Remaining phone checks

Use tilt off for the arrows-only control test, then enable tilt deliberately
for a separate comparison. Check gas plus left/right, both arrows, brake,
finger release, host menus, controller handoff, and background/resume.

For display testing, repeatedly enter/leave map, Autoshow and pause, including
a background/resume during a transition. Record the same car/colour/camera at
100%, 90%, 67%, 59% and 50% after fresh launches. Use the existing visual-issue
marker with external screenshots; compare bloom, reflection settings and
system/imported drivers one change at a time. Keep the save and driver details
with the capture. A log without a matching bad frame cannot establish that
either paint or horizontal lines are fixed.
