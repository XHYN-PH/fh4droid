# FH4Droid 0.6.8-r7 testing build

This preview targets Android 12 (API 31) or newer, ARM64 with the ARMv8-A CPU baseline. Vulkan 1.1 and 1.2 paths are experimental and still require independent blending, vertex/fragment atomics, the required colour/depth formats and suitable graphics/compute queues. A basic driver pass does not establish gameplay support. Snapdragon 845/Adreno 630, Mali-G52 and Samsung Xclipse have not been physically qualified here. A stock Android 10 Snapdragon 845 phone does not meet the selected Android 12 minimum.

The [complete 70-item review](fh4droid-0.6.8-r7-review.md) records implemented work, unresolved bugs, research decisions and the phone test checklist. The reported white/overexposed car, audio packet warnings, ambience lookup and stale-page recoveries are not claimed fixed. No release publication is included in this task.

## Changes to try

- Widescreen waits for sustained view changes, keeps brief camera gaps from immediately shrinking gameplay, and drops obsolete queued view updates. Original menus retain 16:9 proportions. Phone view-transition testing is still needed.
- Touch controls support digital action remapping with matching artwork/captions, separate arrow turn-in/return rates, maximum steering lock and pedal/arrow amount feedback. Always visible works before the first tap. Detected menus use controller navigation labels and preserve Confirm/Back even with a custom driving mapping.
- Fullscreen editor adds bounded undo/redo, unsaved-draft protection and optional overlap/small-target warnings. Safe areas refresh before native drawing; geometry follows remapped artwork. The host Menu handle is smaller and centred at the top.
- Normal and Modded saves can be exported separately or together, imported through bounded staged validation and restored reversibly. Existing v1 normal-save exports remain readable. Save management shows profile/backup status.
- Offline Profile adds local gamertag and gamer-picture editing. Guided Xenia ZIP import preserves the original XUID, imports beside existing profiles, backs up settings and refuses detected TU4 saves for this base-game APK. These features do not sign in to Xbox LIVE.
- Named graphics profiles and an explicit last-working snapshot preserve custom settings. Recovery can restore graphics independently of selecting the system driver. Graphics shows running versus next-launch resolution, MSAA and screen mode, with a preset change preview.
- Budget preview starts at 50%, single sample, target 30 FPS and one shader worker. The target is not a measured performance guarantee. Experimental native/classic renderer controls and existing camera comfort controls are exposed with their limits.
- App and game languages are separate. English and Filipino core app menus are available; detailed help falls back to English. Game choices use known native language/country pairs and appear only for archives found in the owned folder/disc.
- Reports refresh live state periodically and at lifecycle/view changes, prioritize the current trace, identify truncated data and refuse to count old unscoped warnings as current. A visual marker records timestamp/frame/view/settings for an external screenshot. Optional FPS details show latest frame time, RAM and thermal severity.
- Mali/Immortalis and Xclipse get GPU-specific driver guidance; Turnip controls stay Adreno-only. Start Mali/Xclipse with the system driver. PT4Droid's Mali shaders are specific to its renderer and were not copied. Its PanVK RC3 reference requires Android API 35 and exact GPU/kernel compatibility, so it is not bundled as a universal Android 12 driver.

The probe exercises 8-bit, 10-bit and float16 clear/readback with dynamic or classic render passes. SDK device creation now supplies the complete pre-1.2 dynamic-rendering dependency chain or retains classic fallback. Software-driver tests cover these paths; they do not emulate an old phone's actual feature limits or test the car's material shaders.

## Testing boundaries

Host checks cover archive rollback/identity isolation, settings transactions, remapping/steering/fade, editor history, presentation hysteresis, production artwork, fractional-resolution resolves and SDK patch reproduction. Native libraries are rebuilt for API 31 and ARMv8-A. Signing, JNI exports, packaged native hashes and 16 KiB alignment are checked on the APK after packaging.

There is no connected Android phone or raw playable game in this workspace. Phone installation, image-picker decoding, guest scene correctness, driver-layer loading, real 1.1/1.2 gameplay, save compatibility with arbitrary exports and sustained budget-device performance remain device tests. Keep an independent copy of your saves before testing imports/restores.

## Optional Vulkan validation APK

Normal APKs do not include or enable validation layers. Developers can package a separate debuggable diagnostic APK with the ARM64 Khronos library:

```sh
python tools/pinyon.py android package --configuration Release --debuggable \
  --validation-layer /path/to/arm64-v8a/libVkLayer_khronos_validation.so \
  --keystore /path/to/release.keystore --key-alias ALIAS \
  --storepass-file /private/storepass.txt --keypass-file /private/keypass.txt
```

Use the pinned [Khronos Vulkan SDK 1.4.363.0 Android binaries](https://github.com/KhronosGroup/Vulkan-ValidationLayers/releases/tag/vulkan-sdk-1.4.363.0). The tarball SHA-256 is `b2727789f4c4d83b7149e904768bbaf54e8236ec912691730a70e22aebcba419`; the ARM64 library SHA-256 is `db7447027e28db95584e9fde8a57cc1f28c2dd09defcbe2f2b76ee2bbc65bf09`. Packaging rejects another architecture, requires `--debuggable` and checks 16 KiB load alignment. Build metadata enables Vulkan validation and synchronization validation for this APK only.

Android's loader must discover the packaged layer. On a test phone with adb enabled, use:

```sh
adb shell settings put global enable_gpu_debug_layers 1
adb shell settings put global gpu_debug_app ph.xhyn.fh4droid
adb shell settings put global gpu_debug_layers VK_LAYER_KHRONOS_validation
adb shell settings put global gpu_debug_layer_app ph.xhyn.fh4droid
```

Verify that the runtime log reports the layer and synchronization validation as enabled; a packaged library alone is not proof that validation ran. Export the report and logcat, then delete those four global settings and reinstall the normal APK for performance tests. Validation adds overhead and has not been load-tested on a phone here. Both APKs use the same application ID; keep the diagnostic filename distinct.

No minimap positioning, game photo export, layout-document exchange, PC Wi-Fi file copy or network APK updater was added.
