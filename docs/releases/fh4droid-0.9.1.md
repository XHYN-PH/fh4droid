# FH4Droid 0.9.1

FH4Droid 0.9.1 is based on Pinyon Shift 0.9.1. The APK targets Android 13 or newer on ARM64 devices with ARMv8.2-A and a compatible Vulkan driver. Use your own supported Xbox 360 USA base-game files.

## What changed

- **Graphics startup.** Play waits for the app to prepare known compatible shaders and pipelines and save the Vulkan driver cache. The launcher shows preparation progress, and Graphics cache can repair an individual driver cache. A new scene may still introduce a pipeline the app has never seen.
- **Touch and controller.** Photo mode shows LB, RB and both sticks at their saved positions. It restores your driving layout when you leave. The optional invisible look stick covers the right half of the screen while driving. LB and RB cycle tabs in Settings, the content manager and Achievements.
- **Game files.** Installation checks show progress. Choose **Use extracted folder in place** when Android grants direct access, or use **Copy extracted folder** with resumable copying. Storage shows separate totals for the app's game files, DLC, mods, shaders, saves and backups.
- **Saves.** Export, restore or delete individual backups. Save exports exclude installed DLC. Resetting or restoring a save keeps installed DLC.
- **Audio and reports.** Try **Settings → Audio → 0.5.1-style audio** for the older mixer and decoder scheduling behavior. The game-files check can verify collision and glass audio files. Problem captures include recent frame timings and can include an image for showroom and car-tile reports.

## Verification

Cloud checks covered launcher navigation, touch controls, save and DLC handling, SDK patch replay, and software Vulkan rendering. Phone testing is still pending for photo-mode input, engine and impact audio, the white showroom car, and blank car thumbnails. Device performance depends on the GPU and driver.
