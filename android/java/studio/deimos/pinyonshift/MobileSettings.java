package studio.deimos.pinyonshift;

import android.app.Activity;

final class MobileSettings {
  static final Option[] OPTIONS = {
      o("Graphics", "vulkan_texture_max_size", "Texture size", "0", true,
          "Smaller textures use less graphics memory. Original preserves the "
              + "game's detail.",
          "256", "512", "1024", "0"),
      o("Graphics", "anisotropic_override", "Texture filtering", "-1", false,
          "Higher filtering keeps roads clearer at an angle and adds GPU work.", "-1", "0", "3",
          "4", "5"),
      o("Graphics", "texture_mip_lod_bias", "Texture sharpness", "0", false,
          "Negative values sharpen textures and can shimmer.", "0.5", "0", "-0.5", "-1"),
      s("Graphics", "swap_post_effect", "Post-process anti-aliasing", "none", false,
          "Smooths jagged edges after rendering. Independent of MSAA.", "none", "fxaa",
          "fxaa_extreme"),
      o("Graphics", "swap_post_effect_scene_only", "Keep HUD sharp", "true", false,
          "Limits post-process anti-aliasing to the scene.", "false", "true"),
      o("Graphics", "pinyon_shift_fh1_shadows", "Shadows", "true", false,
          "Turning shadows off reduces graphics work and changes scene lighting.", "false", "true"),
      o("Graphics", "pinyon_shift_fh1_env_map_rate", "Reflection updates", "0.25", false,
          "Controls how often the environment reflected by cars is redrawn.", "0.25", "0.5", "1"),
      o("Graphics", "fh1_untile_predicated_tiling", "Single-pass scene", "true", false,
          "Draws the scene with fewer repeated passes. Leave on for normal play.", "false", "true"),
      o("Graphics", "disable_bloom", "Bloom", "true", false, "Bright light glow.", "true", "false"),
      o("Graphics", "disable_motion_blur", "Motion blur", "true", false, "Blur while moving.",
          "true", "false"),
      o("Graphics", "disable_depth_of_field", "Depth of field", "true", false,
          "Focus blur in supported game views.", "true", "false"),
      o("Display", "pinyon_shift_fh1_render_fps_limit", "Game frame limit", "30", false,
          "30 limits CPU/GPU work. 40 pairs well with 120 Hz displays. Higher "
              + "targets need a faster device.",
          "30", "40", "60", "120"),
      o("Performance", "vulkan_pipeline_creation_threads", "Shader workers", "-1", true,
          "Automatic uses up to two workers on Android to leave CPU time for "
              + "the game. More workers can finish preparation sooner but compete "
              + "with gameplay.",
          "-1", "1", "2", "4"),
      o("Performance", "vulkan_pipeline_prewarm_background", "Prepare shaders while playing",
          "false", true,
          "Off completes cached pipeline preparation before play. On reaches "
              + "the title sooner but may cause early stutters.",
          "false", "true"),
      o("Performance", "host_present_fps_limit", "Additional display limit", "0", false,
          "Automatic follows game frames. An additional cap can reduce display "
              + "work; keep it at or above the game frame limit.",
          "0", "30", "40", "60", "90", "120"),
      o("Performance", "host_present_sleep_spin", "Precise frame pacing", "true", false,
          "With an additional display limit, uses a short CPU spin after "
              + "sleeping for steadier pacing. Turn off to reduce power use.",
          "false", "true"),
      o("Performance", "vulkan_present_wait", "Wait for display completion", "false", true,
          "Can reduce queued frames on drivers that support it. Leave off if "
              + "your device stutters.",
          "false", "true"),
      o("Display", "vsync", "VSync", "true", false,
          "Synchronizes presentation to the display; changing it recreates the "
              + "presentation surface.",
          "false", "true"),
      s("Display", "present_effect", "Upscaling filter", "bilinear", false,
          "Bilinear is the cheapest starting point. CAS and FSR 1 add "
              + "sharpening and GPU work.",
          "bilinear", "cas", "fsr"),
      o("Display", "present_cas_additional_sharpness", "CAS sharpness", "0", false,
          "Additional sharpening when CAS is selected.", "0", "0.25", "0.5", "0.75", "1"),
      o("Display", "present_fsr_sharpness_reduction", "FSR softness", "0", false,
          "Higher values reduce sharpening halos when FSR is selected.", "0", "0.5", "1", "1.5",
          "2"),
      o("Display", "present_dither", "Smooth colour gradients", "false", false,
          "Dithering can reduce visible colour bands in the final image.", "false", "true"),
      o("Display", "hud_hidden", "Game HUD", "false", false,
          "Show the minimap, speedometer and event prompts. Separate from "
              + "graphics presets.",
          "true", "false"),
      o("Display", "fh1_hud_scale", "HUD size", "1", false,
          "Changes HUD size around the screen centre.", "0.8", "0.9", "1", "1.1", "1.2"),
      o("Display", "fh1_hud_safe_zone", "HUD margin", "0", false,
          "Keeps the HUD away from screen edges.", "0", "0.025", "0.05", "0.075", "0.1"),
      o("Audio", "pinyon_shift_master_volume", "Master volume", "100", false,
          "Overall game output volume.", "0", "10", "20", "30", "40", "50", "60", "70", "80", "90",
          "100"),
      o("Audio", "audio_mute", "Mute audio", "false", false, "Silences the game output.", "false",
          "true"),
      o("Audio", "pinyon_shift_mix_engine", "Your car engine", "100", true,
          "Percent of the original game mix.", "0", "50", "75", "100", "125", "150", "200"),
      o("Audio", "pinyon_shift_mix_opponents", "Other cars", "100", true,
          "Percent of the original game mix.", "0", "50", "75", "100", "125", "150", "200"),
      o("Audio", "pinyon_shift_mix_tyres", "Tyres", "100", true,
          "Percent of the original game mix.", "0", "50", "75", "100", "125", "150", "200"),
      o("Audio", "pinyon_shift_mix_wind", "Wind", "100", true, "Percent of the original game mix.",
          "0", "50", "75", "100", "125", "150", "200"),
      o("Audio", "pinyon_shift_mix_ambience", "World ambience", "100", true,
          "Percent of the original game mix.", "0", "50", "75", "100", "125", "150", "200"),
      o("Audio", "pinyon_shift_mix_music", "Game radio and music", "100", true,
          "Percent of the original game mix.", "0", "50", "75", "100", "125", "150", "200"),
      o("Audio", "pinyon_shift_mix_full_dynamic_range", "Full dynamic range", "false", true,
          "Retains louder peaks. Leave off for a more even listening level.", "false", "true"),
      o("Controls", "pinyon_shift_touch_controls", "Touch controls", "true", true,
          "Enable the on-screen driving controller.", "false", "true"),
      o("Controls", "pad_rumble_strength", "Game rumble strength", "100", false,
          "Scales vibration sent to the phone and connected controllers. Phone "
              + "strength can also be adjusted below.",
          "0", "25", "50", "75", "100"),
      o("Controls", "pad_gyro_steering", "Controller tilt steering", "false", false,
          "Uses a supported controller's gyro, not the phone's sensors.", "false", "true"),
      o("Controls", "pad_gyro_sensitivity", "Controller tilt sensitivity", "100", false,
          "Sensitivity for compatible controller tilt steering.", "25", "50", "75", "100", "125",
          "150", "200"),
      o("Gameplay", "pinyon_shift_skip_opening_movies", "Skip opening movies", "false", true,
          "Skip the opening splash videos on the next launch.", "false", "true"),
      o("Gameplay", "pinyon_shift_auto_start_single_player", "Start in single player", "false",
          true, "Start from the installed single-player profile when supported.", "false", "true"),
      s("Gameplay", "pinyon_shift_ai_opponents", "AI opponents", "stock", false,
          "Opponent changes apply from the next event.", "stock", "fair", "hard")};

  interface Runtime {
    void changed(String str, String str2);
    default String value(String key) {
      return null;
    }
  }

  static final class Option {
    final String help;
    final String initial;
    final String key;
    final String page;
    final boolean restart;
    final boolean string;
    final String title;
    final String[] values;

    Option(String str, String str2, String str3, String str4, boolean z, boolean z2, String str5,
        String... strArr) {
      this.page = str;
      this.key = str2;
      this.title = str3;
      this.initial = str4;
      this.restart = z;
      this.string = z2;
      this.help = str5;
      this.values = strArr;
    }
  }

  private static Option o(
      String str, String str2, String str3, String str4, boolean z, String str5, String... strArr) {
    return new Option(str, str2, str3, str4, z, false, str5, strArr);
  }

  private static Option s(
      String str, String str2, String str3, String str4, boolean z, String str5, String... strArr) {
    return new Option(str, str2, str3, str4, z, true, str5, strArr);
  }

  static void show(Activity activity, Runtime runtime, Runnable editor, Runnable drivers) {
    SettingsHub.show(activity, runtime, editor, drivers);
  }

  static void page(Activity activity, String page, Runtime runtime, Runnable editor) {
    SettingsHub.page(activity, page.equals("Controls") ? "Touch" : page, runtime, editor);
  }

  static String label(Option option, String str) {
    str = SettingValues.plain(str);
    if (str.equals("true") || str.equals("false")) {
      boolean zEquals = str.equals("true");
      if (option.key.startsWith("disable_") || option.key.equals("hud_hidden")) {
        zEquals = !zEquals;
      }
      return zEquals ? "On" : "Off";
    }
    if (option.key.equals("host_present_fps_limit") && str.equals("0")) {
      return "Automatic";
    }
    if (option.key.equals("vulkan_pipeline_creation_threads") && str.equals("-1")) {
      return "Automatic";
    }
    if (option.key.equals("vulkan_texture_max_size") && str.equals("0")) {
      return "Original";
    }
    if (option.key.equals("anisotropic_override")) {
      return str.equals("-1") ? "Game default"
          : str.equals("0")   ? "Off"
          : str.equals("3")   ? "4×"
          : str.equals("4")   ? "8×"
                              : "16×";
    }
    if (option.key.equals("pinyon_shift_fov_scale")
        || option.key.equals("pinyon_shift_fh1_env_map_rate")) {
      try {
        return Math.round(Float.parseFloat(str) * 100) + "%";
      } catch (NumberFormatException ignored) {
      }
    }
    if (option.key.equals("present_effect")) {
      if (str.equals("fsr"))
        return "FSR 1";
      if (str.equals("cas"))
        return "CAS";
      return str.equals("bilinear") ? "Bilinear" : str;
    }
    if (option.key.equals("pinyon_shift_ai_opponents"))
      return str.equals("fair") ? "No rubber-banding"
          : str.equals("hard")  ? "Hard"
          : str.equals("stock") ? "Original"
                                : str;
    if (!option.key.equals("fh1_render_scale_percent"))
      return str;
    return str + "%";
  }

  static String summary(Activity activity) throws NumberFormatException {
    int i;
    try {
      i = Integer.parseInt(MobileConfig.get(activity, "fh1_render_scale_percent", "100"));
    } catch (Exception unused) {
      i = 100;
    }
    i = Math.max(25, Math.min(100, i));
    return "Vulkan  ·  " + i + "% (" + ((i * 1280 + 99) / 100) + " × " + ((i * 720 + 99) / 100)
        + ")  ·  target " + MobileConfig.get(activity, "pinyon_shift_fh1_render_fps_limit", "30")
        + " FPS  ·  "
        + DriverStore.displayName(
            activity, MobileConfig.get(activity, "android_gpu_driver", "auto"));
  }

  private MobileSettings() {}
}
