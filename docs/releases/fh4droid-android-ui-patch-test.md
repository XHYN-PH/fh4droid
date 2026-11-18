# Android UI patch test

This test integrates Android UI refresh PR #2, branch `android-ui-refresh`,
commit `69731d0518e9dd239f09450efb3096da44acf123`, on top of the local
UI preview 2 and previously checked runtime fixes. It is packaged as
`0.6.11-android-ui-patch-test1`; this does not merge or publish the PR.

## Review and integration

- Shared icon/title/description cards, selectable radio cards, responsive
  action tiles, save-scope chips and branded full-page headers come from the patch.
- Game files show installation status with an explicit content-verification
  action. Saves retain scoped exports and backed-up imports/restores. Driver
  activation still requires the isolated probe to succeed.
- File, save and driver pages rebuild after operations and activity results.
  Existing runtime, settings details, touch layout and input fixes are retained.
- The patch's launcher originally used the large symbol-only image again.
  Integration uses its shared compact logo/wordmark and prevents initial
  button focus from scrolling the header off-screen.
- Launcher background blur, dialog blur and menu button symbols are retained
  from UI preview 2. The header Back target is raised from 42 to 48 dp.
- The original background image is unchanged. The patch applies its dusk colour
  overlay and magenta card/button theme. Shader and input semantics are unchanged.

## Validation scope

Android Java compilation and 2,973 host checks pass. The Python suite completes
563 tests with 88 existing platform/fixture skips. The native ARM64/API 31 target
links successfully. Artwork consistency, all 112 patched SDK files, documentation
site generation and tracked Markdown links pass. APK delivery checks verify its
upgrade signing key, version, 16 KiB alignment and packaged artwork.
No connected Android phone or installed emulator is available here. These
checks do not establish visual fidelity, tap comfort or gameplay stability on
the user's phone. Test the launcher, Settings, Game files, Drivers, Saves,
More, Back/controller focus and the in-game menu on-device.
