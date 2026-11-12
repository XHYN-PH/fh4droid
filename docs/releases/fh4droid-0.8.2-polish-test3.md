# FH4Droid 0.8.2-polish-test3

Release-optimized Android preview, version code 803. Updates the working
0.8.2-upstream-test2 installation using the original package and certificate.
Android 13+ and ARM64 remain required. Existing saves/layouts are preserved.

- Adaptive initial texture reserve and shader concurrency; pressure-aware
  trimming, effective refresh hints and reduced routine telemetry.
- Real cached-pipeline preparation progress, richer performance summaries,
  controlled experiment plans and optional extended diagnostic capture.
- Durable mod/DLC/Rally publication recovery, staging-space checks,
  asynchronous content discovery and retained detail/scroll/focus.
- Coordinated Java/native settings writes and selective mod rollback that
  preserves unrelated edits, including PC-imported BOM settings.
- Capability/dependency feedback, readable import names, action/transaction
  breadcrumbs and guarded backup/closing feedback.
- Previous source audit fixes for stale settings loads, first-run markers,
  damaged mods, internal import markers and interrupted-session reporting.

The pre-build rerun ran 733 Python tests (638 passed, 95 platform-dependent skips),
14,185 Java checks/corpus cases and 201 mixed native/Java writer checks.
Native configuration and 250 malformed-disc cases passed ASan/UBSan; the
mixed host SDK harness uses a consistent allocator without disabling those
sanitizers. The facade smoke checks all 50,935 function mappings.

Software Vulkan passed 6,304,254 colour/depth comparisons, API 1.1/1.2/1.3
readback paths, and 50,331,648 compressed-texture/output-guard byte checks.
All seven SDK patches replay exactly across 107 files. Shared artwork,
tracked Markdown links and the built documentation site also pass.

The signed-APK verification JSON beside the download records the final
package hash, certificate, native dependencies, module/JNI checks and
16-KiB alignment after packaging. No phone was available; the player chose
cloud checks. This is a preview for device testing, with no measured FPS
increase or new GPU-family qualification claimed. Paint overexposure,
widescreen transitions, follower GPU crashes, real material/mip paths,
long-session audio/thermals and responsive UI still need phone evidence.

See [the complete 36-item disposition](fh4droid-performance-polish-2026-11-12.md)
for the retained upstream optimizations, new implementations and measurement
work that remains. Full game assets and signing inputs are not distributed
separately or included in public test artifacts.
