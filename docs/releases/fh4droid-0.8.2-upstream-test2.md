# FH4Droid 0.8.2 upstream test 2

Test 1 could not reach the title screen. All four launches in the uploaded
report reached runtime and graphics setup, then failed while loading XMedia:
its entrypoint and first dispatched function had no registered translations.
The fresh main-only codegen manifest had omitted `module_registry.cpp` and the
`PPCImageConfig.register_modules` connection. Packaging the facade libraries
alone did not register their functions. This was a build integration error.

Test 2 restores both facade registry entries and the callback, keeping the
fresh main translations and the retained, compatible facade translations.
It retains Android 13/API 33, the original background and all changes in
[test 1](fh4droid-0.8.2-upstream-test1.md).

## Checks added

- Configure rejects a missing registry, either missing facade, a disconnected
  callback, or a registry omitted from the generated build sources.
- Packaging inspects the actual main-library registry and both facade
  registration exports before signing. The original test 1 APK is rejected.
- A host smoke test uses the actual generated entrypoint metadata, matching SDK
  runtime and both generated libraries. It checks the entrypoint callback,
  normalized guest paths, module layouts, exports and all 50,935 mappings,
  including both addresses that failed in the report.
- The real CMake codegen/build-graph fixture now includes module registration
  and the current SDK helper inputs, so it runs with CMake/Ninja available.

The recheck runs 727 Python tests (95 skipped) and passes all 3,010 Java
checks, native touch/view/stall/save/disc checks, 6,304,254 software Vulkan
pixel comparisons and the 50,935-function facade startup test. The 107 SDK
file changes reproduce exactly from seven patches. The docs site and 4,239
local links/anchors pass.
The APK is signed with the existing certificate and passes alignment, ABI,
packaged asset, native dependency and JNI checks. Detailed counts accompany
the artifact verification record.

## Device retest

Install this APK over the existing app, keeping game files and saves. Check
that it reaches the title screen, then try Continue, driving, pause/resume,
background/resume and Quit to launcher. Share a new report if any step fails.

Host tests establish that the reported missing-registration path is corrected;
they do not prove phone gameplay is stable. No fix is claimed for the separate
white-car rendering or followers' Turnip device-loss reports. Missing optional
FSR2/3 runtime messages in test 1 were not the cause of this startup abort;
the Android menu continues to offer bilinear, CAS and FSR1.
