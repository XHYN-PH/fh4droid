package studio.deimos.pinyonshift;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Local graphics transactions; never capture profile, save, trainer or file settings. */
final class GraphicsProfileStore {
    static Map<String,String> defaults(){
        Map<String,String> values=new LinkedHashMap<>(GraphicsProfiles.preset(1));
        for(MobileSettings.Option o:MobileSettings.OPTIONS)if((o.page.equals("Graphics")||o.page.equals("Display")||o.page.equals("Performance"))&&!o.key.startsWith("fh1_hud")&&!o.key.equals("hud_hidden"))values.put(o.key,o.string?MobileConfig.quote(o.initial):o.initial);
        values.putAll(GraphicsProfiles.screenMode(0));values.put("pinyon_shift_fov_scale","1");
        values.put("vulkan_dynamic_rendering","true");values.put("vulkan_fh1_native_executor","true");
        return values;
    }
    static Map<String,String> snapshot(Map<String,String> plain)throws IOException{
        Map<String,String> result=defaults();
        for(Map.Entry<String,String> e:result.entrySet()){
            String value=plain.get(e.getKey());if(value==null)continue;
            boolean quoted=e.getValue().startsWith("\"");
            if(quoted){
                boolean known=false;
                for(MobileSettings.Option o:MobileSettings.OPTIONS)if(o.key.equals(e.getKey()))known=Arrays.asList(o.values).contains(value);
                if(!known)throw new IOException("Unsupported graphics value for "+e.getKey()+".");
                e.setValue(MobileConfig.quote(value));
            }else if(e.getValue().equals("true")||e.getValue().equals("false")){
                if(!value.equals("true")&&!value.equals("false"))throw new IOException("Invalid graphics switch.");e.setValue(value);
            }else{
                try{double n=Double.parseDouble(value);if(!Double.isFinite(n)||Math.abs(n)>1000000||value.length()>256)throw new NumberFormatException();value=new java.math.BigDecimal(value).stripTrailingZeros().toPlainString();}
                catch(NumberFormatException error){throw new IOException("Invalid numeric graphics value for "+e.getKey()+".");}
                e.setValue(value);
            }
        }
        return result;
    }
    static String name(String raw)throws IOException{
        String name=raw.trim();if(!name.matches("[A-Za-z0-9][A-Za-z0-9 _-]{0,39}"))throw new IOException("Use 1–40 letters, numbers, spaces or dashes.");return name;
    }
    static void save(File file,Map<String,String> values)throws IOException{
        if(Files.isSymbolicLink(file.toPath())||Files.isSymbolicLink(file.toPath().getParent()))throw new IOException("Linked profiles are not supported.");
        Set<String> allowed=defaults().keySet();if(!allowed.equals(values.keySet()))throw new IOException("Incomplete graphics profile.");
        Map<String,String> plain=new LinkedHashMap<>();for(Map.Entry<String,String> e:values.entrySet())plain.put(e.getKey(),SettingValues.plain(e.getValue()));
        Map<String,String> data=snapshot(plain);data.put("fh4droid_graphics_profile_schema","1");MobileConfig.putFile(file,data);
    }
    static Map<String,String> load(File file)throws IOException{
        if(!file.isFile()||file.length()>65536||Files.isSymbolicLink(file.toPath()))throw new IOException("Graphics profile is unavailable.");
        Map<String,String> plain=MobileConfig.readFile(file);
        if(!"1".equals(plain.get("fh4droid_graphics_profile_schema"))||!plain.keySet().containsAll(defaults().keySet()))throw new IOException("Invalid or incomplete graphics profile.");
        return snapshot(plain);
    }
    static String describe(Map<String,String> values){
        Map<String,String> extra=new LinkedHashMap<>();extra.put("fh1_render_scale_percent","Render resolution (%)");extra.put("fh1_msaa_single_sample","Single-sample rendering");extra.put("fh1_msaa_2x","2× MSAA");extra.put("pinyon_shift_fov_scale","Camera field of view");extra.put("pinyon_shift_hor_plus","Wider gameplay view");extra.put("present_letterbox","Original image proportions");extra.put("present_allow_overscan_cutoff","Crop image edges");extra.put("vulkan_dynamic_rendering","Dynamic rendering");extra.put("vulkan_fh1_native_executor","Native renderer");extra.put("vulkan_allow_present_mode_mailbox","Mailbox presentation");extra.put("vulkan_allow_present_mode_immediate","Immediate presentation");extra.put("vulkan_allow_present_mode_fifo_relaxed","Relaxed presentation");
        StringBuilder text=new StringBuilder();
        for(Map.Entry<String,String> e:values.entrySet()){
            String title=extra.get(e.getKey()),value=SettingValues.plain(e.getValue());
            for(MobileSettings.Option o:MobileSettings.OPTIONS)if(o.key.equals(e.getKey())){title=o.title;value=MobileSettings.label(o,value);break;}
            if(title==null)continue;
            if(value.equals("true")||value.equals("false"))value=value.equals("true")?"On":"Off";
            text.append(title).append(": ").append(value).append('\n');
        }return text.toString();
    }
    private GraphicsProfileStore(){}
}
