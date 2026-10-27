package studio.deimos.pinyonshift;
import java.util.Locale;
/** Identification for explanations only. Acceptance always uses Vulkan features. */
final class GpuCompatibility {
    static String family(int vendor,String name){
        String n=name==null?"":name.toLowerCase(Locale.ROOT);
        if(n.contains("xclipse")||vendor==0x144d)return "Samsung Xclipse";
        if(vendor==0x13b5||n.contains("mali")||n.contains("immortalis"))return "ARM Mali / Immortalis";
        if(vendor==0x5143||n.contains("adreno"))return "Qualcomm Adreno";
        return "Other Vulkan GPU";
    }
    static String guidance(int vendor,String name){
        String f=family(vendor,name);
        if(f.startsWith("ARM"))return f+": start with the system driver. Turnip drivers are for Adreno. PanVK is only suitable when its exact GPU, Android and kernel requirements match your phone. A driver test pass does not qualify races or sustained performance.";
        if(f.startsWith("Samsung"))return f+": use the system Vulkan driver. Turnip and PanVK target other GPU families. Vulkan version alone does not establish game compatibility; test Autoshow, races, low resolutions and resume.";
        return f+": use a driver made for your exact GPU and Android version. A basic Vulkan test is separate from gameplay qualification.";
    }
    private GpuCompatibility(){}
}
