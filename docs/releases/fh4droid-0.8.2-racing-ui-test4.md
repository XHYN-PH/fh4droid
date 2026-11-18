# FH4Droid 0.8.2 Racing UI test 4

This test update brings the approved B launcher into the APK. It keeps the road
background and transparent logo, with a horizontal row for Play, Settings,
Controls, Game files and More.

- More groups mods, drivers, saves, achievements, reports and About into six cards.
- Settings, profile and file tools share the racing colours and clearer cards.
- Controller focus, button presses and page changes animate. Compact screens
  scroll to the selected card; Back remembers the selected item and scroll position.
- Larger text uses a two-column management grid and a separate footer line.
- Animation settings and Android's reduced-motion setting are respected.
- The footer shows the installed version/build. Cheats is under the Mods tab.

Android 13 or newer and ARM64 are required. The package retains the existing
application ID and signing certificate and uses version code 804, allowing an
update over polish test 3. It includes the existing gameplay/runtime fixes;
this update focuses on the launcher.

The production UI passed native Android rendering and navigation checks. Home
and More match the approved previews at the reference viewport, font scale and
fixture state. Final package checks cover identity, upgrade signing, resources,
artwork, native modules/JNI and alignment. Cloud checks do not substitute for an
installed-APK visual check or gameplay on a phone.

For testing, check Play/resume, navigating all five home cards with touch and a
controller, More and its tools, returning with Back, and changing menu scale.
