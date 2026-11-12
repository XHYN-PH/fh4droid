# FH4Droid source and Android polish audit — 12 November 2026

The user confirmed that **0.8.2-upstream-test2 reaches the title and drives**.
That is the working APK baseline. Stable upstream `main` was checked on
12 November and still points to `620e7c4d`, the 0.8.2 source already integrated.
The following fixes are in the source follow-up; the distributed test 2 APK
has not been replaced by this audit.

## Confirmed issues fixed in source

| ID | Finding and resulting behavior | Source |
| --- | --- | --- |
| F1 | A failed native settings reload retained the previous successful snapshot and permission to save it. Failed reloads now invalidate that snapshot; subsequent edits cannot recreate stale settings. | [HostConfig](../../src/config/host_config.cpp), [regression](../../tests/config/host_config_test.cpp) |
| F2 | First-run hardware defaults and their completion marker were saved separately; a failed defaults write could still clear the marker. They now share one save, and live flags change only after it succeeds. | [App setup](../../src/pinyon_shift_app.cpp) |
| F3 | Activity destruction could label a forced close as clean before native shutdown finished. The clean marker now comes from the native close path after its backup/cleanup boundary. | [Game activity](../../android/java/studio/deimos/pinyonshift/PinyonShiftActivity.java) |
| F4 | A missing or invalid mod manifest prevented disabling/removing the mod. Disabled user mods can now be removed without parsing damaged metadata, and a confirmed Disable all optional mods action clears the generated database overlay without reading those manifests. | [Mod store](../../android/java/studio/deimos/pinyonshift/ModStore.java), [content UI](../../android/java/studio/deimos/pinyonshift/ContentActivity.java) |
| F5 | Ordinary imports could retain `.pinyon-builtin`, allowing supplied metadata to hide the mod from the manager and block removal. Ordinary copies now strip that marker; only the APK built-in installation path retains it. | [Mod import](../../android/java/studio/deimos/pinyonshift/ModStore.java), [file regressions](../../tests/android/UpstreamContentTest.java) |
| F6 | The built-in mod buttons still offered Install after installation, producing an already-installed error. Installed entries now open their details. | [Content UI](../../android/java/studio/deimos/pinyonshift/ContentActivity.java) |
| F7 | The new content screen omitted the shared controller-key handler used by the launcher. It now routes controller navigation and Back through that handler. | [Content UI](../../android/java/studio/deimos/pinyonshift/ContentActivity.java) |

The mod file transaction now has direct host coverage. The test forces an
options-write failure after the replacement database and settings have been
written, then checks restoration of the old database/settings. It also checks
that disabling all preserves installed mods and options, damaged disabled mods
can be removed, traversal names are rejected, and ordinary imports cannot
retain the internal built-in marker.

## Main-source and Android choices

These are stable choice numbers for the next work. Reported symptoms are not
asserted to persist in test 2 without a current reproduction. Optimization
candidates have no measured performance gain yet.

| # | Priority / status | Work and evidence |
| --- | --- | --- |
| 1 | High — reported rendering issue | Retest white-car/paint overexposure, then isolate material/shader/colour-space handling if reproduced. Clear/readback probe tests do not exercise the car-paint shaders. Compare a fixed car and scene, native/compatibility paths and SDR/HDR separately. |
| 2 | High — reported transition issue | Recheck widescreen across map, garage, photo, cutscene and return to driving. The existing hysteresis and queue tests pass; correct restoration on the phone still needs coverage. A 16:9 frontend and wide gameplay have different intended framing. [View policy](../../src/ui/view_policy.h). |
| 3 | High — historical follower reports | Reproduce Adreno 825/840 device loss on the current APK. Keep driver identity, settings, route and duration fixed. The older reports do not establish that this version still fails. Replace broad automatic driver recommendations with qualified per-device results when evidence supports it. [Driver selection](../../android/java/studio/deimos/pinyonshift/PinyonShiftActivity.java). |
| 4 | High — recovery gap | Add durable recovery for mod/DLC updates interrupted by an Android process kill. Current exception rollback is tested; an OS kill can bypass it between file moves. Record the transaction phase and recover it before Play. [Mod transaction](../../android/java/studio/deimos/pinyonshift/ModStore.java), [DLC store](../../android/java/studio/deimos/pinyonshift/DlcStore.java). |
| 5 | Medium — optimization candidate | Measure adaptive GPU texture-pool budgets on 4/6/8 GB devices. Android currently preallocates 256 MB for the texture pool; non-sparse shared memory also requires its separate backing buffer. Do not arbitrarily shrink the guest address space or remove driver workarounds. SDK `src/graphics/vulkan/texture_cache.cpp` and `shared_memory.cpp`. |
| 6 | Medium — optimization candidate | Distinguish trim severity before purging reloadable textures. SDL forwards every `onTrimMemory` level, including ordinary UI-hidden events, to the native low-memory path. Measure memory returned and resume hitches before changing that policy. SDK `SDLActivity.java` and `src/ui/rex_app.cpp`. |
| 7 | Medium — performance qualification | Coordinate game cap, host presentation cap, Android surface rate and refresh modes for steady 30/40/60 FPS. Compare frame-time percentiles on the same route rather than menu FPS. [Activity pacing](../../android/java/studio/deimos/pinyonshift/PinyonShiftActivity.java). |
| 8 | Medium — optimization candidate | Benchmark one versus two automatic shader workers and CPU placement on budget big/little CPUs. Both current automatic creation paths already cap at two on Android; do not raise this without sustained measurements. SDK `src/graphics/vulkan/pipeline_cache.cpp`. |
| 9 | Medium — polish / qualification | Improve shader-preparation status and test blocking/background warmup for startup time, early stutters and interruption. The preparation switch already exists; the goal is useful progress and predictable behavior. [Shader management](../../android/java/studio/deimos/pinyonshift/ShaderPreparation.java). |
| 10 | Medium — Android graphics UI | Make experimental options reflect effective device capabilities. For example, explain when coarse shading is unavailable rather than implying that saving the option proves it is active. A successful probe remains separate from gameplay qualification. [Settings](../../android/java/studio/deimos/pinyonshift/MobileSettings.java). |
| 11 | Medium — configuration hardening | Serialize Java/native configuration updates across processes, or detect conflicting snapshots, and use collision-resistant native temporary files. Atomic replacement alone does not merge concurrent writers. [Mobile config](../../android/java/studio/deimos/pinyonshift/MobileConfig.java), [native config](../../src/config/host_config.cpp). |
| 12 | Medium — Android storage | Add free-space estimates for mod/DLC/Rally staging and database rebuilds. The newer content tools bound input sizes but do not yet calculate their peak temporary-space requirement as the game-folder importer does. [Content files](../../android/java/studio/deimos/pinyonshift/ContentFiles.java). |
| 13 | Medium — diagnostics | Record the last menu/import/mod operation and transaction phase in reports, alongside existing settings, driver and performance data. This would make follower reports more actionable without requiring them to remember every step. [Problem reports](../../android/java/studio/deimos/pinyonshift/ProblemReports.java). |
| 14 | Qualification — audio | Test XMA audio during long driving, loading, pause/resume and shader pressure. Investigate decoder warnings with audible symptoms or a reproducible decode failure; earlier warnings alone were not proof of bad audio. SDK `src/audio/xma_decoder.cpp`. |
| 15 | Polish — responsiveness | Move mod discovery and native TOML parsing off the Android UI thread, with loading/error state. Listing is currently synchronous; large mod collections should not block menu input. [Content activity](../../android/java/studio/deimos/pinyonshift/ContentActivity.java). |
| 16 | Polish — navigation | Preserve the selected mod/detail page, scroll and focus after toggling, reordering or changing options. Current completion refresh returns to the root content list. [Content activity](../../android/java/studio/deimos/pinyonshift/ContentActivity.java). |
| 17 | Polish — import feedback | Preserve readable source names in per-file results. Raw picker inputs currently get UUID staging filenames, which can make a skipped-file report difficult to match to the selection. Keep those names as display metadata, not trusted filesystem paths. [Content activity](../../android/java/studio/deimos/pinyonshift/ContentActivity.java). |
| 18 | Qualification — interface | Check every menu at 16:9, 20:9, wider phones, tablets, cutouts and large Android font sizes. Keep the approved background/icons and refine clipping, spacing, focus and contrast using actual screenshots. [Festival UI](../../android/java/studio/deimos/pinyonshift/FestivalUi.java). |
| 19 | Qualification — supported phones | Test Android 13+ Snapdragon 845/Adreno 630, Mali-G52 and Xclipse separately. Include multi-touch, controller disconnect, background/resume, thermal behavior and sustained gameplay. One successful Adreno 750 session is not that device matrix. |
| 20 | Build / release process | Automate the existing module completeness, SDK patch replay, source payload, native smoke, signature, alignment and APK identity gates in a repeatable release job with private game/signing inputs. Source checks can run publicly; raw game files and keys must stay out of the repo. [Module gate](../../tools/android_module_check.py). |
| 21 | Qualification — camera controls | Verify which camera-comfort settings depend on the prepared immersive-camera mod, then show that dependency/availability clearly. Live chase offsets and global FOV have separate native hooks. [Camera options](../../android/java/studio/deimos/pinyonshift/MobileSettings.java). |
| 22 | Native renderer coverage | Add reference tests for BC1/BC3/BC5 and uncompressed texture paths, including forced BC decoding. Android GPUs without direct BC sampling can expand texture memory substantially. The current resolve/readback suite does not cover every material texture decode. SDK `src/graphics/vulkan/texture_cache.cpp`. |
| 23 | Polish — shutdown | Show a clear closing/backup state and prevent repeated Quit requests while the final save backup and native cleanup finish. Preserve the native clean-exit boundary fixed in this audit. [Shutdown](../../src/pinyon_shift_app.cpp). |
| 24 | Source quality | Clear inherited compiler warnings where behavior can be proved, and expand sanitizer/fuzz coverage for ISO/archive/texture/pipeline parsers. This is separate from claiming a measured frame-rate improvement. |

## Validation and limits

- 727 Python tests run with 95 platform-dependent skips when the available
  CMake/Ninja tools are included. An initial environment-limited run skipped
  additional tests; it is not the final result.
- Android Java compiles; 3,024 host checks pass, including 51 content-integrity,
  rollback, mod-recovery and internal-marker checks.
- The stale-load regression fails on the previous native source and passes on
  the fix. A blocked atomic write also preserves the hardware-default marker
  and old settings until the complete update succeeds.
- The changed ARM64 main library compiles and links.
- The generated-entrypoint/facade smoke test checks all 50,935 mappings.
- Native touch/view/stall/save/disc and software Vulkan checks pass; the latter
  cover 6,304,254 colour/depth comparisons and API 1.1/1.2/1.3 dynamic/legacy
  readback paths. These are software-driver checks, not phone performance.
- SDK patch reproduction, shared artwork and documentation checks accompany
  the audit record.

No replacement APK is packaged or published by this audit. The user confirmed
startup/driving on test 2; these source changes still need their own device
retest. Automatic Android instrumentation was not run: this workspace has no
usable attached-device session, and its ADB startup attempts were blocked by
the read-only default Android configuration directory. Existing saves and game
installations were not used for tests; all file regressions use temporary data.

Work on a Lite asset converter remains a separate next feature. Prioritize
current rendering/stability reproduction and recovery before changing assets
or performance defaults.
