package studio.deimos.pinyonshift;

import android.app.*;
import android.widget.*;
import java.io.IOException;
import java.util.*;

/** Player-facing pages backed by the host's supported settings. */
final class MobileSettings {
    interface Runtime { void changed(String key, String value); }
    private static final class Option {
        final String page,key,title,initial,help; final boolean restart,string;
        final String[] values;
        Option(String page,String key,String title,String initial,boolean restart,boolean string,String help,String... values) {
            this.page=page;this.key=key;this.title=title;this.initial=initial;this.restart=restart;
            this.string=string;this.help=help;this.values=values;
        }
    }
    private static Option o(String page,String key,String title,String initial,boolean restart,String help,String... values) {
        return new Option(page,key,title,initial,restart,false,help,values);
    }
    private static Option s(String page,String key,String title,String initial,boolean restart,String help,String... values) {
        return new Option(page,key,title,initial,restart,true,help,values);
    }
    private static final Option[] OPTIONS={
        o("Graphics","fh1_render_scale_percent","Rendering resolution","100",true,"50% = 640 × 360; 75% = 960 × 540. Lower values can improve performance at the cost of sharpness.","50","75","100"),
        o("Graphics","vulkan_texture_max_size","Texture size","0",true,"Smaller textures use less graphics memory. Original preserves the game's detail.","256","512","1024","0"),
        o("Graphics","anisotropic_override","Texture filtering","-1",false,"Higher filtering keeps roads clearer at an angle and adds GPU work.","-1","0","3","4","5"),
        o("Graphics","texture_mip_lod_bias","Texture sharpness","0",false,"Negative values sharpen textures and can shimmer.","0.5","0","-0.5","-1"),
        s("Graphics","swap_post_effect","Post-process anti-aliasing","none",false,"Smooths jagged edges after rendering. Independent of MSAA.","none","fxaa","fxaa_extreme"),
        o("Graphics","swap_post_effect_scene_only","Keep HUD sharp","true",false,"Limits post-process anti-aliasing to the scene.","false","true"),
        o("Graphics","pinyon_shift_fh1_shadows","Shadows","true",false,"Turning shadows off reduces graphics work and changes scene lighting.","false","true"),
        o("Graphics","pinyon_shift_fh1_env_map_rate","Reflection updates","0.25",false,"Controls how often the environment reflected by cars is redrawn.","0.25","0.5","1"),
        o("Graphics","fh1_untile_predicated_tiling","Single-pass scene","true",false,"Draws the scene with fewer repeated passes. Leave on for normal play.","false","true"),
        o("Graphics","disable_bloom","Bloom","true",false,"Bright light glow.","true","false"),
        o("Graphics","disable_motion_blur","Motion blur","true",false,"Blur while moving.","true","false"),
        o("Graphics","disable_depth_of_field","Depth of field","true",false,"Focus blur in supported game views.","true","false"),
        o("Display","pinyon_shift_fh1_render_fps_limit","Game frame limit","30",false,"30 limits CPU/GPU work. 40 pairs well with 120 Hz displays. Higher targets need a faster device.","30","40","60","120"),
        o("Display","vsync","VSync","true",false,"Synchronizes presentation to the display; changing it recreates the presentation surface.","false","true"),
        s("Display","present_effect","Upscaling filter","bilinear",false,"Bilinear is the cheapest starting point. CAS and FSR 1 add sharpening and GPU work.","bilinear","cas","fsr"),
        o("Display","present_letterbox","Keep original aspect ratio","true",false,"Keeps the original proportions rather than stretching the picture.","false","true"),
        o("Display","hud_hidden","Game HUD","false",false,"Hide or show the game's heads-up display.","true","false"),
        o("Display","fh1_hud_scale","HUD size","1",false,"Changes HUD size around the screen centre.","0.8","0.9","1","1.1","1.2"),
        o("Display","fh1_hud_safe_zone","HUD margin","0",false,"Keeps the HUD away from screen edges.","0","0.025","0.05","0.075","0.1"),
        o("Audio","pinyon_shift_master_volume","Master volume","100",false,"Overall game output volume.","0","10","20","30","40","50","60","70","80","90","100"),
        o("Audio","audio_mute","Mute audio","false",false,"Silences the game output.","false","true"),
        o("Audio","pinyon_shift_mix_engine","Your car engine","100",true,"Percent of the original game mix.","0","50","75","100","125","150","200"),
        o("Audio","pinyon_shift_mix_opponents","Other cars","100",true,"Percent of the original game mix.","0","50","75","100","125","150","200"),
        o("Audio","pinyon_shift_mix_tyres","Tyres","100",true,"Percent of the original game mix.","0","50","75","100","125","150","200"),
        o("Audio","pinyon_shift_mix_wind","Wind","100",true,"Percent of the original game mix.","0","50","75","100","125","150","200"),
        o("Audio","pinyon_shift_mix_ambience","World ambience","100",true,"Percent of the original game mix.","0","50","75","100","125","150","200"),
        o("Audio","pinyon_shift_mix_music","Game radio and music","100",true,"Percent of the original game mix.","0","50","75","100","125","150","200"),
        o("Audio","pinyon_shift_mix_full_dynamic_range","Full dynamic range","false",true,"Retains louder peaks. Leave off for a more even listening level.","false","true"),
        o("Controls","pinyon_shift_touch_controls","Touch controls","true",true,"Enable the on-screen driving controller.","false","true"),
        o("Controls","pad_rumble_strength","Controller vibration","100",false,"Requires a controller with working vibration support.","0","25","50","75","100"),
        o("Controls","pad_gyro_steering","Controller tilt steering","false",false,"Uses a supported controller's gyro, not the phone's sensors.","false","true"),
        o("Controls","pad_gyro_sensitivity","Controller tilt sensitivity","100",false,"Sensitivity for compatible controller tilt steering.","25","50","75","100","125","150","200"),
        o("Gameplay","pinyon_shift_skip_opening_movies","Skip opening movies","false",true,"Skip the opening splash videos on the next launch.","false","true"),
        o("Gameplay","pinyon_shift_auto_start_single_player","Start in single player","false",true,"Start from the installed single-player profile when supported.","false","true"),
        s("Gameplay","pinyon_shift_ai_opponents","AI opponents","stock",false,"Opponent changes apply from the next event.","stock","fair","hard")
    };
    static void show(Activity a,Runtime runtime,Runnable controls,Runnable drivers) {
        LinearLayout body=FestivalUi.column(a);
        body.addView(FestivalUi.text(a,"Changes are saved automatically. Options marked ‘next launch’ apply after quitting and starting the game again.",14,FestivalUi.MUTED));
        FestivalUi.action(a,body,"Performance preset",()->presets(a,runtime));
        for(String page:new String[]{"Graphics","Display","Audio","Controls","Gameplay"})
            FestivalUi.action(a,body,page,()->page(a,page,runtime,controls));
        FestivalUi.action(a,body,"Graphics driver · next launch",drivers);
        FestivalUi.dialog(a,"Settings",body).show();
    }
    private static void page(Activity a,String page,Runtime runtime,Runnable controls) {
        LinearLayout body=FestivalUi.column(a);
        if(page.equals("Graphics")) {
            Button msaa=FestivalUi.action(a,body,"MSAA · next launch: "+msaa(a),()->{});
            msaa.setOnClickListener(v->FestivalUi.choose(a,"MSAA",new String[]{"Off","2×","4×"},Arrays.asList("Off","2×","4×").indexOf(msaa(a)),n->{
                put(a,null,"fh1_msaa_single_sample",n==0?"true":"false");
                put(a,null,"fh1_msaa_2x",n==1?"true":"false");msaa.setText("MSAA · next launch: "+msaa(a));}));
        }
        for(Option option:OPTIONS)if(option.page.equals(page)) {
            String suffix=option.restart?" · next launch":"";
            Button b=FestivalUi.action(a,body,option.title+suffix+": "+label(option,MobileConfig.get(a,option.key,option.initial)),()->{});
            TextView help=FestivalUi.text(a,option.help,12,FestivalUi.MUTED);help.setPadding(4,3,4,5);body.addView(help);
            b.setOnClickListener(v->{String[] labels=new String[option.values.length];for(int i=0;i<labels.length;i++)labels[i]=label(option,option.values[i]);
                int selected=Arrays.asList(option.values).indexOf(MobileConfig.get(a,option.key,option.initial));
                FestivalUi.choose(a,option.title,labels,selected,n->{String value=option.values[n];
                    put(a,option.restart?null:runtime,option.key,option.string?MobileConfig.quote(value):value);
                    b.setText(option.title+suffix+": "+label(option,value));});});
        }
        if(page.equals("Controls"))FestivalUi.action(a,body,"Edit layout, opacity and controller auto-hide",controls);
        if(page.equals("Display")) {
            android.widget.Switch animations=new android.widget.Switch(a);animations.setText("Menu animations");animations.setTextColor(FestivalUi.TEXT);
            animations.setMinHeight(FestivalUi.dp(a,48));animations.setChecked(a.getSharedPreferences("mobile",0).getBoolean("animations",true));
            animations.setOnCheckedChangeListener((b,on)->a.getSharedPreferences("mobile",0).edit().putBoolean("animations",on).apply());body.addView(animations);
        }
        FestivalUi.dialog(a,page,body).show();
    }
    private static String msaa(Activity a) {
        return MobileConfig.get(a,"fh1_msaa_single_sample","true").equals("true")?"Off":MobileConfig.get(a,"fh1_msaa_2x","false").equals("true")?"2×":"4×";
    }
    private static void presets(Activity a,Runtime runtime) {
        FestivalUi.choose(a,"Performance preset",new String[]{"Lower power · 30 FPS target","Balanced · 30 FPS target","Higher frame limit · 60 FPS target"},-1,n->{
            put(a,runtime,"pinyon_shift_fh1_render_fps_limit",n==2?"60":"30");
            put(a,null,"fh1_render_scale_percent",n==0?"50":n==1?"75":"100");
            put(a,null,"fh1_msaa_single_sample","true");put(a,null,"fh1_msaa_2x","false");
            put(a,runtime,"present_effect","\"bilinear\"");put(a,runtime,"pinyon_shift_fh1_shadows",n==0?"false":"true");
            put(a,null,"vulkan_texture_max_size",n==0?"256":n==1?"512":"0");
            Toast.makeText(a,"Preset saved. Restart to apply resolution and texture changes.",1).show();});
    }
    private static void put(Activity a,Runtime runtime,String key,String literal) {
        try{MobileConfig.put(a,key,literal);if(runtime!=null){String value=literal;
            if(value.startsWith("\"")&&value.endsWith("\""))value=value.substring(1,value.length()-1);runtime.changed(key,value);}}
        catch(IOException error){Toast.makeText(a,"Could not save settings: "+error.getMessage(),1).show();}
    }
    private static String label(Option o,String value) {
        if(value.equals("true")||value.equals("false")) {
            boolean on=value.equals("true");if(o.key.startsWith("disable_")||o.key.equals("hud_hidden"))on=!on;return on?"On":"Off";
        }
        if(o.key.equals("vulkan_texture_max_size")&&value.equals("0"))return "Original";
        if(o.key.equals("anisotropic_override"))return value.equals("-1")?"Game default":value.equals("0")?"Off":value.equals("3")?"4×":value.equals("4")?"8×":"16×";
        if(o.key.equals("fh1_render_scale_percent"))return value+"%";
        if(o.key.equals("present_effect"))return value.equals("fsr")?"FSR 1":value.equals("cas")?"CAS":"Bilinear";
        if(o.key.equals("pinyon_shift_ai_opponents"))return value.equals("fair")?"No rubber-banding":value.equals("hard")?"Hard":"Original";
        return value;
    }
    static String summary(Activity a) {
        int p;try{p=Integer.parseInt(MobileConfig.get(a,"fh1_render_scale_percent","100"));}catch(Exception e){p=100;}
        String driver=MobileConfig.get(a,"android_gpu_driver","auto");
        return "Vulkan  ·  "+p+"% ("+(1280*p/100)+" × "+(720*p/100)+")  ·  "+MobileConfig.get(a,"pinyon_shift_fh1_render_fps_limit","30")+" FPS  ·  "
            +(driver.isEmpty()?"System driver":driver.equals("auto")?"Automatic driver":driver);
    }
    private MobileSettings(){}
}
