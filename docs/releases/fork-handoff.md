# FH4Droid fork and maintainer guide

FH4Droid 0.9.10, build 920, is the final release from XHYN-PH. It supports compatible ARM64 devices running Android 13 or newer. The signed APK, SHA-256 checksum and verification report are on GitHub. Tag `v0.9.10` points to the exact APK source, commit `f9f6a98217b96ab3d6e390451e1ce0065850f35a`. Start new development from `main`. [Release downloads](https://github.com/XHYN-PH/fh4droid/releases/tag/v0.9.10) · [Tagged source](https://github.com/XHYN-PH/fh4droid/tree/v0.9.10) · [Changelog](../../CHANGELOG.md)

The source includes the Android launcher, runtime changes, SDK patches, build tooling and tests. FH4Droid 0.9.10 uses Pinyon Shift 0.9.1 and pins ShiftGlue at `537b2b6465e612880597aa54e026b56f9aeacfc5`. Preserve the license and third-party notices in a fork. [License](../../LICENSE) · [Third-party notices](../../THIRD_PARTY_NOTICES.md)

## Build from source

Fork on GitHub and clone with recursive submodules. Use `v0.9.10` when reproducing the published version. [Fork FH4Droid](https://github.com/XHYN-PH/fh4droid/fork)

```sh
git clone --branch v0.9.10 --recurse-submodules https://github.com/XHYN-PH/fh4droid.git
cd fh4droid
git submodule status --recursive
```

Use your own supported Forza Horizon (2012) USA retail base-game disc image or complete extracted folder. Setup verifies the files and generates the translated game modules locally. Keep the game files and generated modules on your build machine. [Supported dump catalog](../../config/supported-dumps.json) · [Distribution policy](../LEGAL.md)

On a Linux x86-64 build host with Git and Python 3, install JDK 17 and the Android SDK command-line tools. Set `JAVA_HOME` and `ANDROID_HOME` to those installations. Setup provisions the pinned host tools, including CMake and Ninja. The Android build uses NDK r29, SDK Platform 36 and Build Tools 36.0.0. [Linux prerequisites](../../site/pages/linux.md#linux-pcs) · [Android toolchain pin](../../config/android-toolchain.json)

```sh
export PINYON_SHIFT_SOURCE_SDK=1
python3 tools/pinyon.py setup --iso /path/to/your-disc.iso
python3 tools/pinyon.py android doctor --install
python3 tools/pinyon.py android build --configuration Release
```

Use `setup --extracted /path/to/your-game-folder` in place of the ISO command if you already extracted the disc. On Windows, run `.\tools\setup-preview.ps1 -IsoPath C:\path\to\your-disc.iso` and then `.\tools\build-android.ps1` to build a local test APK. [Windows setup](../../site/pages/building.md)

The Android configure step applies the source SDK patches. The resulting APK is `.local/android/pinyon-shift.apk`. The build command above uses a local test key. Use a dedicated release key for public releases.

## Give a fork its own app identity

Use a unique application ID for a publicly distributed fork so users can install it alongside FH4Droid. Update `package` in the Android toolchain configuration, the report provider authority and setup intent action in the manifest, and the intent action and target package in the shortcut resources. Update the save sync path displayed in Settings to match. Keep the `studio.deimos.pinyonshift` Java namespace and JNI names. Keep `PINYON_SHIFT_SOURCE_SDK=1` for builds with a renamed package. [Configuration](../../config/android-toolchain.json) · [Manifest](../../android/AndroidManifest.xml) · [Shortcuts](../../android/res/xml/shortcuts.xml)

Set the fork’s release `version` and `android_version_code` in its release configuration. Increase the version code on updates. For game progress, export from **More → Save backups** in FH4Droid, then restore the ZIP in the fork using the matching game build. [Version configuration](../../config/release.json) · [Save transfer guide](../../site/pages/android.md#saves)

## Sign a fork release

Create a dedicated signing key with JDK 17 `keytool`. This example creates a password-protected PKCS#12 keystore outside the checkout and prompts for the password.

```sh
signing_dir="$HOME/fh4droid-fork-signing"
install -d -m 700 "$signing_dir"
"$JAVA_HOME/bin/keytool" -genkeypair -storetype PKCS12 -keystore "$signing_dir/release.p12" \
  -alias fork-release -keyalg RSA -keysize 3072 -validity 10000
```

Keep a protected backup of the key. Put the keystore password in a file readable only by the build account. For this PKCS#12 example the key password and keystore password are the same, so both options can use that file.

```sh
python3 tools/pinyon.py android build --configuration Release \
  --keystore "$signing_dir/release.p12" \
  --key-alias fork-release \
  --storepass-file "$signing_dir/password.txt" \
  --keypass-file "$signing_dir/password.txt"
```

Use `android package` with the same signing arguments to package an already compiled build. Verify the signature, 16 KiB alignment, package name and version before publishing. Keep the APK checksum with the release.

```sh
apk=.local/android/pinyon-shift.apk
build_tools="$ANDROID_HOME/build-tools/36.0.0"
"$build_tools/apksigner" verify --verbose --print-certs "$apk"
"$build_tools/zipalign" -c -P 16 4 "$apk"
"$build_tools/aapt2" dump badging "$apk"
sha256sum "$apk"
```

Before publishing a tag from an Android-only fork, disable or adapt the inherited **Launcher release** workflow. It builds the upstream desktop launcher when a `v*` tag is pushed from a public fork. [Launcher release workflow](../../.github/workflows/release.yml)

## Continue the original release line

An in-place update to the published app must keep `ph.xhyn.fh4droid`, use an accepted signing identity and raise `android_version_code`. The current release verifier checks for the same signing certificate as the baseline APK. Run it on a committed build with the previous published APK as `--baseline`. [Release verifier](../../tools/verify-android-release.py)

```sh
python3 tools/verify-android-release.py .local/android/pinyon-shift.apk \
  --baseline /path/to/previous.apk --output /path/to/verification.json
```

The v0.9.5 public signing certificate is available for verification. Its SHA-256 fingerprint is `113ab182105837ea5776d8efbf8cc84281a5708a630f9a241945d3c7feb5e075`. The active signing key and its passwords require a private transfer to a selected maintainer. Possession of the key lets someone sign APKs Android can accept as updates to existing installations. [Public certificate](fh4droid-v0.9.5-cert.crt)

## Check a build before release

Run `python3 tools/check-android-sdk-patches.py` on the patched submodule and `python3 tools/test-android-mobile.py` against the pinned SDK. Run the launcher UI tests with `mvn -B -f tests/android/ui/pom.xml test`. The source workflow exercises these checks without game files and provides a starting point for fork CI. [Android source checks](../../.github/workflows/android-source.yml)

Test on an Android 13 or later ARMv8.2-A device. Import game files, start a drive, open and resume from the in-game menu, and try touch and controller navigation. Check audio, low-resolution rendering, and a save export and restore. For an update, install over the previous release and verify the existing profile and settings.

The final APK passed the signature, package, native library and upgrade checks against 0.9.9. FSR 3 initialized on software Vulkan. Phone gameplay checks for FSR 3 and fast travel are still pending. [Verification report](https://github.com/XHYN-PH/fh4droid/releases/download/v0.9.10/FH4Droid-0.9.10-arm64-verification.json)

## Upstream submissions

- Keep the source profile selected after importing a multi-profile save. [Pinyon Shift PR #1124](https://github.com/arcanite24/pinyon-shift/pull/1124)
- Recover malformed decimals in settings from older Android builds. [Pinyon Shift PR #1125](https://github.com/arcanite24/pinyon-shift/pull/1125)
- Correct the road motion-blur boundary around the car on Vulkan. The upstream issue includes the ShiftGlue patch and test results. [Pinyon Shift issue #1126](https://github.com/arcanite24/pinyon-shift/issues/1126)
