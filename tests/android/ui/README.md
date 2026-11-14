# Native launcher checks

Run from the repository root with JDK 17+, Maven 3.9+ and the SDL submodule checked out:

```sh
mvn -B -f tests/android/ui/pom.xml test
```

This compiles the production Android Java sources with Java 8 compatibility and
runs Robolectric 4.14.1 with Android 13 resources and native Skia rendering. No
generated game code, signing key, phone or APK packaging is involved. Maven
retrieves the pinned test dependencies on the first run. If the SDL sources are
in a prepared SDK elsewhere, pass `-Dsdl.source=/absolute/path/to/sdl/java`.
Robolectric also supports its standard `robolectric.offline` and
`robolectric.dependency.dir` properties for a prepopulated SDK cache.

Outputs stay under `.local/android/launcher-ui-tests/`. `renders-integrated/`
contains the home, focused card, management grid, settings categories, actual
launcher settings form, compact grid and large-text examples. A small asset ZIP
created by the test supplies the existing menu images to the test activity.

The home and management checks pin the decoded ARGB pixel hashes of the approved
B home and management previews at 2400×1080, 400 dpi, font scale 1, default menu
scale and the ready/local-profile fixture state:

| Screen | SHA-256 of ARGB pixels |
| --- | --- |
| B home | `4286376af7d26f01699391bd112bbf7de3ab6e76ed754374257009cce9ccfbc7` |
| Management | `0873a07ec285d44bd4158f00ec6ec1e23e240c4ef10c398214e0009f8e58e928` |

These checks always run in CI. Optional `-Dlauncher.preview.baseline=/path/B.png`
and `-Dlauncher.preview.manage=/path/manage.png` also compare directly with saved
reference PNGs. Do not update the hashes to accept an unreviewed visual change.
The fixture preset/version are screenshot data; the actual launcher reads the
saved graphics and installed package version.

Interaction cases exercise D-pad/A/B navigation, all category callbacks,
restored focus/scroll and configuration changes, native slider keys, compact and
large-text clipping, motion settling and reduced motion, and held-stick repeat
with neutral/lost-focus cancellation. These are simulated input events. They do
not establish physical controller compatibility or installed-APK rendering.

Test 5 uses the approved dashboard revision with the two-by-two action grid.
UiPolishTest renders the actual SettingsHub and GameMenuView, verifies the details
panel stays on the right, persists a controller resolution change, checks a first
touch activates exactly once and verifies the UI preference handoff.
