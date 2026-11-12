package studio.deimos.pinyonshift;

import android.content.Context;
import java.util.*;

final class SettingAvailability {
    static String reason(Context context,String key){
        if(key.equals("fh1_coarse_shading")){
            if(context instanceof PinyonShiftActivity){int active=((PinyonShiftActivity)context).coarseShadingSupport();if(active>=0)return active==1?"":"The active graphics device does not expose pipeline fragment shading rate. Scene shading stays normal.";}
            org.json.JSONObject report=AdrenoSettings.report(context);
            if(!report.has("coarse_shading_supported"))return "Run the selected driver's Vulkan test to check scene-shading support.";
            if(!report.optBoolean("coarse_shading_supported"))return "This driver does not expose pipeline fragment shading rate. Scene shading stays normal.";
        }
        if(Arrays.asList("pinyon_shift_camera_gforce","pinyon_shift_camera_lean","pinyon_shift_camera_shake","pinyon_shift_camera_head").contains(key)){
            // Prepared mod names come from the upstream manifest, not from
            // the existence of the native hook or a slider's saved value.
            if(!ModStore.enabled(context).contains("immersive_camera"))return "Enable the prepared immersive_camera mod in Mods and DLC first. Live chase offsets and global FOV work separately.";
        }
        return "";
    }
    private SettingAvailability(){}
}
