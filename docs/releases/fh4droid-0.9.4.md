# FH4Droid 0.9.4

FH4Droid 0.9.4 (build 914) is based on Pinyon Shift 0.9.1. It runs on Android 13 or newer with an ARMv8.2-A CPU and a compatible Vulkan driver. Use your own supported USA Xbox 360 base-game ISO or extracted folder.

## Home and launcher menus

- Home fits the tall **Play** card and the four-card grid inside the display cutout and system bars. Text, card sizes and gaps adapt to the available space. On short screens, the cards scroll between the header and footer.
- The controller hints, community links and installed version sit in a dedicated Home footer below the cards. Other menus use the space below their header.
- **Ready to drive** shows render scale and resolution, the FPS target, the upscaling filter and the selected or verified driver.
- The Play card rises into place and the two rows enter in sequence. Menu transitions respect **Settings → Interface → Menu animations** and Android's animation settings.
- Local profile groups the gamer picture, gamertag and achievement progress with direct links to achievements and save backups. Achievements uses compact search, spaced filters, six entries per page and LB/RB filter switching.
- **More → About** shows the version and build, device and game-file requirements, and project credits. It links to Pinyon Shift, ShiftGlue, third-party notices, artwork licences and the FH4Droid community.

## Settings, controller and touch

- The six settings cards open their own pages. The right-hand details panel follows the focused option, explains its effect and shows saved, running or next-launch values where applicable. LB/RB switches related pages, and Back restores focus.
- Graphics groups related options under **Display**, **Quality**, **Renderer** and **Presets**. Inline arrows cycle choices such as MSAA, screen layout and FSR 1 presets. Preset confirmations show the values they will change.
- Adjust **Render resolution** from 25% to 100% in 1% steps using the on-screen arrows, D-pad or left stick. The expanded slider also accepts touch or controller input. Resolution changes apply on the next launch.
- **Upscaling filter → Off (nearest)** is available for display scaling without smoothing. It applies live and can be adjusted independently of render resolution and FXAA.
- Controls gives touch layout, steering, phone tilt and vibration, physical controller and saved layouts their own icons and entries. The launcher and settings cards open the same Controls hub.
- The controller test shows raw and calibrated stick and trigger input together. Adjust dead zones, range and stick response from the calibration screen. Held controller input repeats while adjusting and stops when released or focus moves away.
- The fullscreen touch editor previews **Driving**, **Game menus** and **Photo mode**. Each preview shows the relevant buttons and stick positions, including the photo controls.
- The editor includes movable tools, grid and snapping, per-button size and opacity, and **Add button** for hidden controls. The property panel stays alongside the canvas. Saved layouts and driving/controller templates use distinct icons.
- Driving supports arrow or stick steering and an optional invisible look area on the right half of the screen. The D-pad has its own directional inputs. Photo mode makes LB, RB and both sticks available for camera control.

## In-game menu

- Open the right-side drawer with the small top-right menu icon or Android Back. The drawer puts **Resume** first, with text-only actions for Settings, Touch controls, Controller and More. More groups the local profile, achievements, diagnostics and trainer. Quit to launcher has a save reminder and confirmation.
- The drawer slides in from the right with a fade and slides back out to the right with a quicker fade. Inner pages slide as you move forward or back.
- Resume, Back and outside taps use the same dismissal handling to release menu input when the game is active. Returning from an inner page restores the drawer focus.

## Game files, saves and content

- The installation page shows the active game source and reports verification or copy progress in one status area. **Change source**, **Check installation** and **Storage** sit below it.
- Choose an ISO, link a readable extracted folder in place, or copy an extracted folder into app storage. The previous source stays selected until the change succeeds. Interrupted folder copies can reuse verified files, and new ISOs stage their own local support files.
- **Check installation** verifies the full supported catalog. Storage shows separate totals for copied game files, ISO support files, DLC, mods, graphics caches, saves and backups.
- **More → Save backups → Manage backups** can create a named checkpoint of your selected Normal, Modded or Both save types. Each backup shows its scope, date and size, with individual export, restore and delete actions.
- Save archives support up to 4 GiB of expanded save data. Save exports exclude installed DLC, and resetting a selected save type creates a backup that can be restored.
- Mods, DLC and Shaders have spaced tabs and compact import cards. Installed mods and DLC appear first with their status. Built-in **Festival pacing** and **Hidden cars** use the same card layout as the import actions. The Shaders tab shows the installed pipeline list and offers `.xpso` import.

## Startup, graphics drivers and performance

- Repeat launches can reuse verification of a local ISO and its support files when their filesystem identities match. Changed files are checked again. The native disc reader buffers nearby small reads and invalidates the buffer if the image changes.
- A successful driver test can be reused when the driver, relevant settings, Android version and app build match. An interrupted game triggers a fresh check. The app also reuses verified copies of bundled driver files.
- Automatic driver selection uses the system driver on Adreno 730. The APK includes Turnip Gen8 V37 for compatible Adreno devices. The Graphics drivers page supports testing, importing and removing custom drivers.
- Before gameplay, the startup panel shows when it reads caches, prepares shaders, builds known pipelines and saves the Vulkan cache. A progress bar shows the pipeline count when available. Failures point to storage, cache recovery or driver checks.
- **Match display refresh** can request a seamless refresh rate that matches the game target, such as 120 Hz at 40 FPS. Memory and frame-timing samples run off the UI thread.
- **Settings → Advanced → CPU performance hints** lets you test optional Android scheduling hints for the game thread. It is off by default. On a connected phone, the source comparison tool can run two baselines and a trial from copies of the same starting save and configuration, with matching cold or warm cache conditions.

## Logging and recovery

- Regular **Play** starts with app session logging off by default. **More → Problem reports → Play without logs** selects that mode for one launch. It suppresses the app's runtime logs, crash reports, frame history and diagnostic captures. Android manages its own system logs.
- **Settings → Advanced → Record standard session logs** enables regular startup and runtime logs. **Play with diagnostics** captures detailed data for one session. Report exports identify a recent play session run without logs and label earlier captures as history.
- The launcher uses Android's reported exit reason when available and gives different recovery advice for low memory, an unresponsive game or a crash. **Try system driver once** tests recovery for one launch and retains the saved driver selection.
- **Problem reports** includes a collision and glass asset check. **Settings → Audio → 0.5.1-style audio** lets you compare the older mixer and decoder scheduling behavior.

## Verification

- All 31 Android UI tests passed. They covered short-screen layout, controller adjustment and reduced motion, and exercised 232 choices across 55 settings. Host checks covered storage, ISO reads, settings recovery, SDK patch replay and software Vulkan rendering. GitHub CI and Android source checks passed.
- The signed APK passed the v0.9.3 upgrade identity check, Android 13 native library checks and 16 KiB alignment. The published APK's checksum matches the verified build. [APK verification report](https://github.com/XHYN-PH/fh4droid/releases/download/v0.9.4/FH4Droid-0.9.4-arm64-verification.json)
- Pause and resume, engine and collision audio, car rendering, phone GPU compatibility and sustained frame pacing still need on-device checks.

See all source changes in this release. [Compare v0.9.3 and v0.9.4](https://github.com/XHYN-PH/fh4droid/compare/v0.9.3...v0.9.4)
