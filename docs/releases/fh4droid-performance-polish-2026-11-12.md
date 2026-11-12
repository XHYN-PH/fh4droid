# FH4Droid performance and recovery follow-up — 12 November 2026

The working comparison APK is **0.8.2-upstream-test2**, which the player
confirmed reaches the title and drives. The next package is
**0.8.2-polish-test3**, built with Release optimization and the original
application ID and signing certificate. Android 13 is still the minimum.
No phone was available for this follow-up; the player requested completion
of the cloud checks. A software-driver pass does not qualify phone gameplay.

The following numbers retain the complete, performance-first 36-item list.
They replace the older audit's 24 choice numbers. Implementation and
measurement are distinguished so an unmeasured optimization is not called
a performance improvement.

| # | Result / remaining qualification |
| --- | --- |
| 1 | Performance captures now include optional CPU-thread attribution; summaries include memory gauges, GPU upload/submission work and audio counters when present. The suite requires device/driver/build/route/seed/cache/thermal provenance. A phone baseline still needs capture. |
| 2 | Automatic Android texture reserve is 64 MiB on low-RAM/up-to-4-GiB devices, 128 MiB up to 6 GiB, and the existing 256 MiB otherwise. Explicit saved choices are honored. This is initial reserve, not a hard texture cap or a smaller guest address space. |
| 3 | The optional Budget preview uses 512-pixel textures; 256 remains an explicit texture-size choice and a separate benchmark experiment. Balanced and ordinary profiles preserve original textures. Compare detail and peak RAM before recommending reduced textures. |
| 4 | UI-hidden/moderate notifications no longer cause SDL's full native texture purge. Actual running-low/critical and background memory pressure are forwarded. Phone resume hitches/memory returns need measurement. |
| 5 | Android's surface rate follows the lower active game/presentation cap, including the sustained-mode target. Experiment overlays cover 30/40/60 FPS and sleep/spin pacing. Existing source-frame/presentation coordination is retained. |
| 6 | Automatic regular and AOT shader preparation use one worker on low-memory/four-core devices and up to two otherwise. Explicit worker choices remain. Existing below-normal background priority and OS CPU placement are retained; no unmeasured CPU affinity is forced. |
| 7 | The overlay reports real cached-pipeline attempts and totals during startup/background preparation. Failures and cancellation counts are available in report snapshots. Existing bounded imports and renderer cache validation remain. Interrupted compilation must still be retested on phones. |
| 8 | Existing upstream batching/submission optimizations are retained. Summaries expose renderings, split uploads, upload bytes, submission/decoder/recorder costs and waits. Further renderer changes require a route capture identifying the bottleneck. |
| 9 | Diagnostic runs attribute Guest/GPU/Vulkan/XMA/Audio thread CPU work. Existing guest profiling tools remain; guest function rewrites need representative gameplay profiles and correctness comparisons. |
| 10 | Existing direct-ISO and asset-streaming paths are retained. The experiment protocol requires identical input storage/route and cold/warm state. No speculative buffering/decompression rewrite or streaming gain is claimed without game assets and measurements. |
| 11 | New tests execute the shipped BC1/BC3/BC5 and RGBA8 fallback SPIR-V with synthetic data, four byte orders and linear/tiled layouts. Forced BC decoding has its own device experiment. Software results do not cover every real material, mip tail or driver upload path. |
| 12 | Mali/Xclipse system-driver and compatibility paths remain. Driver reports now expose BC sampling and fragment-shading-rate availability. Device-specific tuning requires measurements on those GPUs; unsupported features are not advertised as qualified. |
| 13 | Normal Android sampling runs every five seconds; diagnostic sampling remains one second. Native session lookup is cached. Per-frame renderer allocation changes await measured allocation hotspots. |
| 14 | Balanced settings remain conservative. The suite varies shadows, reflection rate, bloom, depth of field and motion blur separately rather than attributing a combined preset change to one effect. |
| 15 | The suite covers 50/67/75/100% scale, bilinear, CAS and FSR1. Existing scale/resolve readback tests remain mandatory. FSR2/3 are not supplied by this build. |
| 16 | Summaries retain XMA stall counters and add available decoder/queue/latency work. Diagnostic thread attribution separates decoder CPU from rendering. Audible quality and long sessions require a phone; earlier warnings alone were not audible failures. |
| 17 | Routine telemetry/file scans are reduced; detailed CSV/GPU profiling remains opt-in. Achievement behavior is preserved. Further logging/achievement changes require measured overhead. |
| 18 | Existing optional sustained-performance mode, severe-thermal cap and cooling hysteresis remain. The suite requires a 15-minute run and flags thermally confounded comparisons. No sustained FPS result is invented. |
| 19 | The matrix explicitly includes Snapdragon 845/Adreno 630, Mali-G52, Xclipse, Adreno 750 and the follower 825/840 regressions. None is newly qualified by software Vulkan tests. |
| 20 | A repeatable experiment planner/comparator now distinguishes missing, short, mismatched and thermally confounded runs from timing regressions. A/A repeats and cold/warm routes are required in the protocol. |
| 21 | Paint/exposure remains a reported reproduction task. Clear/resolve/BC decode tests do not establish a paint-shader fix; use a fixed car/scene and SDR/HDR comparison. |
| 22 | Existing view hysteresis/obsolete-request rejection is retained and host-tested. Reports retain view/frame markers. Map/garage/photo/driving transitions still need phone captures of sudden shrinking/sidebars. |
| 23 | Historical Adreno 825/840 crashes are not asserted to persist in this version. Capture current build, exact driver, settings, route and duration before selecting a workaround. |
| 24 | Durable undo journals cover mod publication, generated database/config/options, DLC metadata/payload/enable moves and Rally replacement/rebuild. Recovery runs before import and launch. Completed earlier DLC publications remain installed. Separate-process kill tests exercise real publication boundaries. |
| 25 | Coarse shading is unavailable in the UI until the selected driver's probe exposes support. Immersive camera group controls explain their prepared-mod dependency; live chase/global FOV remain separate. |
| 26 | Java/native writers use the same sibling byte-range lock. Native saves merge only pending edits into the latest file and use unique flushed temporary files. Mod rollback restores only its settings key, preserving unrelated concurrent edits. Cross-process tests exercise both languages together. |
| 27 | ZIP expansion, DLC extraction, tree copies and database staging check peak additional space with a 64-MiB reserve. Unknown-size streamed imports check remaining space during copying. Storage can still change concurrently; failed writes recover through the journal. |
| 28 | Reports include the last host-menu/content action and content-journal phase, without selected URIs or absolute input paths. Existing session/build/driver/settings provenance is retained. |
| 29 | Mod discovery, native manifest parsing, DLC metadata and options load on a worker. Generation/lifecycle guards discard obsolete results; loading failures have Retry. Damaged options do not hide Disable all recovery. |
| 30 | Refresh retains the selected mod/DLC detail, scroll state and stable focus keys across enable/order/option changes. Removing an entry returns to the list. Physical accessibility/controller navigation needs on-device confirmation. |
| 31 | Per-file DLC results use picker display names as metadata; UUID staging paths stay internal. Folder/archive display names remain readable. |
| 32 | Existing responsive landscape/cutout/large-font layouts remain. New content pages use shared layout/focus components. Full screenshot/accessibility qualification across 16:9, 20:9, tablets and large fonts requires Android UI execution. |
| 33 | Public source CI compiles Java and runs recovery/settings tests. SDK patches replay from pinned objects. The final-APK verifier checks identity/version/signature, source/pin/patch/art/catalog hashes, module registry, JNI, strong imports against minimum-API stubs, ELF and ZIP alignment. Private game/signing inputs stay out of CI artifacts. |
| 34 | Prepared `immersive_camera` dependency is visible before changing its motion-group sliders; it is not silently advertised as packaged. |
| 35 | Quit shows backup/cleanup progress and rejects repeated requests in Java and native code. Clean-session marking remains after native backup/cleanup. Cancellation keeps progress visible until content rollback finishes. |
| 36 | Deterministic malformed-input corpora cover pipeline lists, SQL, paths and native disc descriptors. Native parser/config checks run with sanitizers. Existing platform deprecation warnings are distinguished from build failures; warnings are not suppressed to manufacture a clean report. |

## Reproduce the checks

Source checks need the pinned SDK/JDK/Android platform and a C++23 compiler.
The Android source workflow needs no game-generated code or signing key.

```text
python -m unittest discover -s tools/tests
python tools/test-android-mobile.py
python tools/check-android-sdk-patches.py
python tools/generate-touch-icons.py --check
python tools/generate-touch-artwork.py --check
python site/build.py --out .local/site-polish
```

Run the native touch/view/config/disc and software Vulkan targets too. The
private generated-entrypoint smoke test must still load both facade modules
and verify all 50,935 mappings; a signed APK alone would not catch the prior
startup failure. Preserve that module registry when regenerating code.

Prepare experiments with `python tools/android-performance-suite.py plan
.local/performance-plan`. Apply overlays only to copied test state. The
planner does not delete live caches or edit saves. Compare measured records
with `python tools/android-performance-suite.py compare results.json
--output comparison.json`. A matching timing result is separate from visual,
audio and stability qualification.

For long phone comparisons, enable Advanced → Extended comparison capture
before launching the diagnostic run. It raises the CSV bound from 16 to
64 MiB; report export still retains a bounded tail with its CSV header and
labels truncation. Ordinary launches keep detailed CSV/GPU profiling off.

After all pre-build checks pass, compile the Release libraries, package with
the private signing key, then run `python tools/verify-android-release.py
path/to/new.apk --baseline path/to/test2.apk --output verification.json`.
Do not publish a stable/device-qualified release merely because these cloud
checks pass.

Final run results are recorded separately beside the APK; this document
describes the checks and their scope, not fabricated gameplay measurements.
