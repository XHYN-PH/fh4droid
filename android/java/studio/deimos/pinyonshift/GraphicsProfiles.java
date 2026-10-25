package studio.deimos.pinyonshift;

import java.util.LinkedHashMap;
import java.util.Map;

/** Graphics transactions shared by the launcher and in-game settings. */
final class GraphicsProfiles {
    static final String[] NAMES = {
        "Battery saver · 50% · target 30 FPS",
        "Balanced · 75% · target 30 FPS",
        "Performance priority · 67% · target 60 FPS",
        "Graphics priority · 100% · target 30 FPS",
        "Smooth 40 · 75% · target 40 FPS"
    };
    static final String[] SCREEN_MODES = {
        "Original proportions · side bars",
        "Widescreen · wider gameplay view",
        "Stretch · fills screen, changes proportions"
    };

    static Map<String, String> preset(int index) {
        if (index < 0 || index >= NAMES.length)
            throw new IllegalArgumentException("Unknown graphics preset");
        boolean quality = index == 3;
        Map<String, String> values = new LinkedHashMap<>();
        values.put("fh1_render_scale_percent", new String[]{"50", "75", "67", "100", "75"}[index]);
        values.put("pinyon_shift_fh1_render_fps_limit", new String[]{"30", "30", "60", "30", "40"}[index]);
        values.put("fh1_msaa_single_sample", quality ? "false" : "true");
        values.put("fh1_msaa_2x", quality ? "true" : "false");
        values.put("present_effect", "\"bilinear\"");
        values.put("swap_post_effect", "\"none\"");
        values.put("swap_post_effect_scene_only", "true");
        values.put("anisotropic_override", quality ? "3" : "0");
        values.put("texture_mip_lod_bias", "0");
        values.put("present_dither", "false");
        values.put("present_cas_additional_sharpness", "0");
        values.put("present_fsr_sharpness_reduction", "0");
        // Keep these paths at their original settings until lower-detail
        // variants have been qualified on phones. Reduced raster resolution
        // and AA are sufficient to distinguish the performance profiles.
        values.put("vulkan_texture_max_size", "0");
        values.put("pinyon_shift_fh1_shadows", "true");
        values.put("disable_bloom", "false");
        values.put("disable_motion_blur", "true");
        values.put("disable_depth_of_field", quality ? "false" : "true");
        values.put("pinyon_shift_fh1_env_map_rate", quality ? "0.5" : "0.25");
        values.put("fh1_untile_predicated_tiling", "true");
        values.put("host_present_fps_limit", "0");
        values.put("vsync", "true");
        values.put("vulkan_allow_present_mode_mailbox", "false");
        values.put("vulkan_allow_present_mode_immediate", "false");
        values.put("vulkan_allow_present_mode_fifo_relaxed", "false");
        values.put("vulkan_present_wait", "false");
        values.put("vulkan_pipeline_creation_threads", "-1");
        values.put("vulkan_pipeline_prewarm_background", "false");
        return values;
    }

    static Map<String, String> screenMode(int index) {
        if (index < 0 || index >= SCREEN_MODES.length)
            throw new IllegalArgumentException("Unknown screen mode");
        Map<String, String> values = new LinkedHashMap<>();
        values.put("present_allow_overscan_cutoff", "false");
        values.put("present_letterbox", index == 0 ? "true" : "false");
        values.put("pinyon_shift_hor_plus", index == 1 ? "true" : "false");
        return values;
    }

    static int screenModeIndex(Map<String, String> settings) {
        if (Boolean.parseBoolean(settings.get("pinyon_shift_hor_plus"))) return 1;
        return "false".equals(settings.get("present_letterbox")) ? 2 : 0;
    }

    private GraphicsProfiles() {}
}
