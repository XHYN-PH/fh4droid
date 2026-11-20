# FH4Droid 0.9.6

FH4Droid 0.9.6 (build 916) uses Pinyon Shift 0.9.1. It requires Android 13 or later, an ARMv8.2-A CPU, a compatible Vulkan driver, and your own supported USA Xbox 360 base-game files.

## Graphics settings apply again

- FSR presets and camera controls save fractional values in the format the game can read. An invalid fractional value could make the whole settings file fall back to defaults, affecting resolution, shadows and other options.
- On the first launch with affected settings, the app repairs the saved values and keeps a copy of the original configuration. Your selected render resolution, filter and lighting settings can take effect again.

## Console saves and clear saves

- Open **More → Save backups → Import ForzaProfile** to select a compatible Xbox 360 Forza Horizon save package. The file picker also shows packages with no extension. Choose **Normal saves** or **Modded saves**.
- An import checks the profile files and version flags before it moves existing progress to a recovery backup. Importing into Modded uses the package's player ID in modded sessions and preserves the selected normal player.
- **Clear saves** asks for confirmation and clears the selected Normal, Modded or Both scope after making a backup. Installed DLC stays in place. Use **Restore previous saves** to undo the clear.
- This APK uses the base game. Saves written with a title update require a matching build.

## Verification

Cloud checks cover settings migration, the launcher controls, scoped save import and rollback, and safe rejection of an incompatible title-update package. The APK is checked against the signing certificate and app ID from 0.9.5 and for 16 KiB alignment. Road appearance and frame rate still need a same-route check on the affected phone.

[Source and build guide](https://github.com/XHYN-PH/fh4droid/blob/v0.9.6/docs/releases/fork-handoff.md)
