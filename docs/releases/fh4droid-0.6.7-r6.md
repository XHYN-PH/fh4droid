# FH4Droid 0.6.7-r6 — Mobile UI and controls test release

Android 13+, ARM64 and Vulkan 1.3 are required. Supply your own supported
USA base-game ISO or complete extracted folder. Title Update 4 is not required;
keep TU4 saves separate. This is a test release for on-device feedback.

## Controls

- Reworked driving artwork for all 20 controls, including Y rewind, A handbrake,
  camera, map, pause, gear controls, both sticks and the independent D-pad.
- Gas, brake and clutch use translucent pedal faces with slim bevels, recessed
  grip strips and mounting details. Modern pedals replace the extra rectangular
  keycaps and warm to orange when pressed. Steering arrows use circular pads.
- Separate button-code badges and action labels, optional pressed-button names,
  and independent artwork/background opacity. Action labels assume default
  game bindings and do not remap the buttons.
- Modern racing preset with grouped utility controls and spacing that adapts
  to tablet and wide-phone aspect ratios. Arrows and stick steering, manual
  controls, handed templates and Full controller remain available.
- Existing custom positions are retained. Choose **Main menu → Controls → Properties → Layout → Modern racing preset**, then Save, to use
  the new arrangement.
- Fully fullscreen editor with a separate fullscreen Properties view,
  Save/Cancel, local touch Test mode and Hide tools. The editor and native
  overlay use the same production artwork. Stick bases and moving knobs
  retain their labels and release correctly during input handoff.

## Menus and local setup

- Transparent FH4Droid logo on the main menu, one primary Play action, quieter
  shortcuts, and text-only YouTube, Telegram and GitHub footer links.
- The right footer shows the installed version/build and game-file status.
- Fixed landscape orientation across launcher, import, editor, driver probe
  and gameplay; SDL orientation requests retain the lock.
- Settings are grouped into Graphics, Controls, Audio, Interface, Gameplay and
  Advanced. The pause drawer has Resume, Settings, Controls, More and Quit.
- PC discovery, pairing, Wi-Fi copying and network APK updates have been
  removed. Use local ISO/folder import and Android's app installer.
  Cancellation, resumable folder copying and atomic settings remain available.

## Low-resolution rendering fixes

- Intermediate band clears retain a fractional-resolution texel still needed
  by the next band, preventing the reproduced horizontal-seam corruption.
- The final band copy uses rounded endpoints for its extent, avoiding a
  one-row attachment overrun at scales such as 59%.
- The tiled-resolve fixture reproduces 13,600 corrupt pixels with the previous
  clear bounds and zero with the fix across 4,377,600 pixel checks at 25–100%.
  Another 953,854 colour/depth resolve/transfer values and 972,800 colour MSAA
  values passed on software Vulkan. These establish correctness of those
  paths, not phone performance or confirmation of the original phone symptom.

## Installation and testing

Install `FH4Droid-0.6.7-r6-arm64.apk` over the existing FH4Droid app to retain
app data. It uses the existing release certificate; versionCode 607 supersedes
606. Back up important saves before testing.

Check the main menu, setting categories and fullscreen editor at your preferred
text size. Try the Modern racing preset, both steering choices, simultaneous
pedal/steering presses, controller handoff, save/cancel and background/resume.
Retest the reported lines at the affected resolution and compare 50%, 59%,
67%, 75% and 100%, restarting after resolution changes. Share a report through
More → Report a problem if an issue remains.

## Validation and limits

The release process builds the ARM64 game, runtime, GPU plugin, driver helper
and native UI bridge, and checks Java, native touch/artwork, Python tooling,
resources and documentation. Before publication the signed APK is checked for
upgrade identity, source provenance, matching artwork, native JNI exports,
packaged hashes and 16 KiB alignment. A SHA-256 file and validation report
accompany the APK.

This release has not been gameplay-tested on a physical Android device.
Phone-driver compatibility, control legibility, HUD clearance, accessibility,
haptics, sensors and sustained performance still need on-device feedback.
The installation/content checker keeps its previous behavior, including the
ISO support-cache limitation. Android photos, minimap repositioning and layout
document import/export remain excluded. Generated game source, raw game media
and signing material are excluded from public source and release attachments.
