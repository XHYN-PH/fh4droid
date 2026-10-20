# FH4Droid 0.6.1-r2

An Android testing build for Forza Horizon (2012), Xbox 360.

## What changed

- New driving-background launcher, transparent logo at the upper right, red-to-orange buttons and optional menu animations.
- Settings summary beneath Ready to drive, with an in-game menu reached through ☰ or Android Back.
- Graphics, display, audio, controls and gameplay pages with explanations and restart labels.
- Rendering resolution choices of 50% (640 × 360), 75% (960 × 540) and 100% (1280 × 720).
- Touch-layout editor with move, resize, hide, opacity, reset and controller auto-hide.
- Save export/import and reversible restoration of the previous standard profile.
- Separate-process graphics-driver tests, system-driver recovery and shareable problem reports.
- Direct ISO access and complete extracted-folder validation against the supported file catalog. Resuming an import checks content and the remaining space needed.

## Install and use

Install over the existing FH4Droid app to retain its files and saves. Back up important saves before testing. Android 13+, ARMv8.2-capable ARM64 CPU and Vulkan 1.3 remain required.

Open Game files and choose your own supported USA base-game ISO or complete extracted folder. ISO mode needs about 150 MB extra for menu and audio configuration files. Keep the ISO accessible during play. Folder mode is recommended for mods that add new files.

Choose Settings before playing. During a game, ☰ or Android Back opens the menu. Driver changes and restart-marked options apply on the next launch.

## Known limits

This build has not been gameplay-tested on a physical Android device. Automated storage, parser, compilation and shader validation checks do not establish image correctness or FPS. The fractional-resolution and ISO paths need phone testing.

Lower rendering resolution reduces sharpness. Each resolution uses its own graphics cache, and first visits to scenes can stutter while preparing graphics. The existing precompiled shader pack applies at 100% resolution.

Save ZIPs cover the standard base-game profile. Advanced game settings manage profiles used by mods or cheats. Title Update 4 is not needed; TU4 saves require a separate TU4 build.

Use Reports → Play with logs to reproduce a problem, then Share latest problem report. Review the report before sharing. Do not attach game files or saves to public bug reports.
