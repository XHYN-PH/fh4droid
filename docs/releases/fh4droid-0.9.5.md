# FH4Droid 0.9.5

FH4Droid 0.9.5 (build 915) uses Pinyon Shift 0.9.1. It requires Android 13 or later, an ARMv8.2-A CPU, a compatible Vulkan driver and your own supported USA Xbox 360 base-game files.

## In-game menu and reports

- The pause drawer sets up its content before applying the slide-and-fade animation. It captures game input only after opening. If Android rejects the window, the game can keep running and you can retry. Resume, Back and outside taps release menu input.
- Main-menu **Play** now records detailed session logs. **More → Problem reports** offers report sharing, the collision audio file check and **Play without logs** for a single session.
- Reports include the current session marker. Menu logs identify whether the window opened or failed to attach.

## Achievements, backups, drivers and storage

- Achievements has a gap below the profile header, a compact search field and smaller result cards. Detail dialogs fit the achievement name, score, status and description. Closing the dialog returns focus to its achievement.
- Save backup details show the scope, date and size with compact Export, Restore and Delete actions. Restore and Delete still ask for confirmation.
- Graphics drivers shows the current selection and the driver used for the last test. The result card gives each check its own pass or fail row, and the test screen shows the driver being checked.
- Storage shows the active source, available space and separate installed-data sizes. Its breakdown fits into two columns on wide screens, with Graphics cache and Android storage actions below.

## Transferring saves

- Base-game save exports carry the original player ID when the receiving phone needs it. Restoring from ZIP selects that profile and keeps the receiving phone's other settings. Older exports with a single recognised owner can also transfer.
- The recovery backup records the previous player selection. Restore it to bring back the prior saves and profile selection together.
- FH4Droid and Xenia imports check for incompatible title-update saves before replacing live data. These saves require a matching game build.

## Sharper reduced resolution

- **Settings → Graphics → Upscaling filter → Automatic (sharp)** uses CAS at reduced render resolutions and Bilinear at 100%. CAS is intended to reduce the softness reported at 90% with the previous default.
- The upgrade moves the previous Bilinear setting to Automatic once. Bilinear, CAS, FSR 1 and Off (nearest) are still selectable. The filter setting applies live, and render resolution applies on the next launch.

## Verification

Cloud checks cover opening, closing and recovering the pause drawer on Android 13 and 15, controller navigation, detailed and quiet launches, cross-phone save transfer, incompatible save rejection, and the upscaling setting upgrade. Native disc and software Vulkan checks cover the runtime. The signed APK is checked for upgrade compatibility with 0.9.4 and for 16 KiB alignment. The reported crash, the save transfer and the new filter's appearance still need confirmation on the affected phones.
