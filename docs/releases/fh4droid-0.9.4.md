# FH4Droid 0.9.4

Built on Pinyon Shift 0.9.1 for Android 13 or newer. The APK requires an ARMv8.2-A device and a compatible Vulkan driver. Use your own supported USA Xbox 360 base-game ISO or extracted folder.

## Launcher and controls

- Home keeps Play on the left and the four menu cards on the right. Text and spacing adapt to shorter screens. The footer stays below the cards on Home, and option screens use the full menu area.
- The Home cards enter in sequence. The in-game drawer slides in from the right and fades, then slides back to the right when you close it. The **Animations** setting controls these effects.
- **More → About** shows the installed build, requirements, project credits, and links to the upstream projects and artwork licences.
- Controls has distinct icons and a controller test view for raw and calibrated input. Adjust render resolution with touch arrows, the D-pad or the left stick. The touch editor previews Driving, Game menus and Photo mode.
- Save backups supports named checkpoints. Mods, DLC and Shaders show installed content first. Local profile and achievements share the launcher styling.

## Launch and troubleshooting

- Repeat launches can reuse a verified local ISO and a successful driver test when their inputs match. A failed or cancelled source change keeps the previous game source. The disc reader refreshes its read cache if the ISO changes.
- Shader preparation shows its progress. Android can request a display refresh rate that matches the frame target when the phone supports a seamless switch. CPU performance hints are optional.
- **Play** starts with app session logging off by default. Use **More → Problem reports → Play with diagnostics** to record a session, or enable standard logging in Advanced. Recovery can try the system driver for one launch.

## Verification

Cloud checks cover Android UI and controller navigation, storage and settings, the pinned SDK patches, native disc reads and software Vulkan. The release verifier checks APK signing and upgrade identity against v0.9.3, Android 13 library imports, and 16 KiB alignment. Physical gameplay, phone GPU compatibility and performance gains need on-device measurements.
