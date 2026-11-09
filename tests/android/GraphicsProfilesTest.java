package studio.deimos.pinyonshift;

import java.util.LinkedHashMap;
import java.util.Map;

/** Reproduce conflicting saved settings without loading Android framework code. */
public final class GraphicsProfilesTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    private static Map<String, String> reportedSettings() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("swap_post_effect", "\"fxaa\"");
        values.put("anisotropic_override", "5");
        values.put("texture_mip_lod_bias", "-1");
        values.put("vulkan_texture_max_size", "1024");
        values.put("pinyon_shift_fh1_shadows", "false");
        values.put("vulkan_pipeline_prewarm_background", "true");
        values.put("vulkan_allow_present_mode_immediate", "true");
        values.put("vulkan_allow_present_mode_fifo_relaxed", "true");
        values.put("hud_hidden", "true");
        values.put("android_gpu_driver", "imported-driver");
        values.putAll(GraphicsProfiles.screenMode(1));
        return values;
    }

    public static void main(String[] args) {
        Map<String, String> current = reportedSettings();
        current.putAll(GraphicsProfiles.preset(2));
        check("\"none\"".equals(current.get("swap_post_effect")), "performance clears inherited FXAA");
        check("0".equals(current.get("anisotropic_override")), "performance clears inherited high filtering");
        check("0".equals(current.get("texture_mip_lod_bias")), "performance clears inherited sharpening");
        check("0".equals(current.get("vulkan_texture_max_size")), "preserve original texture path");
        check("true".equals(current.get("pinyon_shift_fh1_shadows")), "preserve original shadow path");
        check("false".equals(current.get("vulkan_pipeline_prewarm_background")), "prepare cached shaders before play");
        check("false".equals(current.get("vulkan_allow_present_mode_immediate")), "clear inherited immediate mode");
        check("false".equals(current.get("vulkan_allow_present_mode_fifo_relaxed")), "clear inherited relaxed FIFO");
        check("67".equals(current.get("fh1_render_scale_percent")), "performance render scale");
        check("60".equals(current.get("pinyon_shift_fh1_render_fps_limit")), "performance frame target");
        check("true".equals(current.get("hud_hidden")), "graphics preset preserves explicit HUD choice");
        check("imported-driver".equals(current.get("android_gpu_driver")), "graphics preset preserves driver");
        check(GraphicsProfiles.screenModeIndex(current) == 1, "graphics preset preserves widescreen choice");

        // Every preset must produce the same owned settings regardless of
        // which preset ran before it, while unrelated choices survive.
        for (int before = 0; before < GraphicsProfiles.NAMES.length; before++) {
            for (int after = 0; after < GraphicsProfiles.NAMES.length; after++) {
                Map<String, String> transitioned = reportedSettings();
                transitioned.putAll(GraphicsProfiles.preset(before));
                transitioned.putAll(GraphicsProfiles.preset(after));
                Map<String, String> direct = reportedSettings();
                direct.putAll(GraphicsProfiles.preset(after));
                check(transitioned.equals(direct), "preset transition " + before + " -> " + after);
            }
        }
        check("true".equals(GraphicsProfiles.preset(3).get("fh1_msaa_2x")), "quality selects 2x MSAA");
        check("true".equals(GraphicsProfiles.preset(2).get("fh1_msaa_single_sample")), "performance disables MSAA");

        for (int before = 0; before < GraphicsProfiles.SCREEN_MODES.length; before++) {
            for (int after = 0; after < GraphicsProfiles.SCREEN_MODES.length; after++) {
                Map<String, String> settings = GraphicsProfiles.screenMode(before);
                settings.put("present_allow_overscan_cutoff", "true");
                settings.putAll(GraphicsProfiles.screenMode(after));
                check(GraphicsProfiles.screenModeIndex(settings) == after, "screen-mode transition");
                check("false".equals(settings.get("present_allow_overscan_cutoff")), "screen modes disable overscan cropping");
            }
        }
        check("false".equals(GraphicsProfiles.screenMode(1).get("present_letterbox")), "widescreen fills gameplay output");
        check("true".equals(GraphicsProfiles.screenMode(1).get("pinyon_shift_hor_plus")), "widescreen uses the wider camera view");
        check("false".equals(GraphicsProfiles.screenMode(2).get("pinyon_shift_hor_plus")), "stretch does not widen the camera");
        check("true".equals(GraphicsProfiles.screenMode(0).get("present_letterbox")), "original keeps proportions");
        System.out.println("PASS: " + checks + " graphics preset and screen-mode checks");
    }
}
