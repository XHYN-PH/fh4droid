# FH4Droid 0.8.2 UI polish test 5

This test build brings the revised dashboard and settings into the app, using the existing road background and logo.

- Play sits beside a two-by-two action grid. Narrow displays and larger text use a stacked layout.
- Settings show explanations on the right. Move focus to read about an option; use Left/Right to adjust choices. Render resolution changes by 1% and saves for the next launch. Search, favourites, reset and undo remain available.
- The in-game menu opens from a small icon at the upper right or Android Back. Its actions use text only. The pressed-button popup is removed.
- Menu buttons activate on the first tap. Profile actions use a compact grid; achievements have search, filters and six entries per page.
- Phone tilt has corrected steering direction and an optional inversion setting. Both analog sticks stay available in detected menu/photo contexts, even when driving uses arrows. Arrow processing no longer overwrites the menu's left-stick input.
- Save exports exclude installed DLC. Importing and restoring saves preserves installed DLC and its headers. Older exports that include packages are rejected before replacing saves; make a new save export with this version.
- The UI language and phone-tilt inversion preference survive the launcher/game process handoff.
- Automatic driver selection uses the system driver on Adreno 730, including Snapdragon 8+ Gen 1. Imported drivers are still selectable. This is a conservative policy following user reports, not a measured FPS gain.

Android 13 or later and ARM64 are required. This APK keeps the application ID and release signing certificate, with version code 805 for an in-place update.

Cloud checks cover Java/storage regressions, native touch input, actual Android-view rendering and interaction, software Vulkan readback, SDK patch reproduction and APK integrity. They do not replace installing and driving on a phone. Random stutter/crashes, camera-dependent engine sound and custom-driver performance remain unconfirmed; they need a report from this build and a repeatable route. Full dynamic range controls the output compressor and is not a fix for camera audio perspective. Existing upstream audio behavior is retained.
