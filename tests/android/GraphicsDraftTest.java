package studio.deimos.pinyonshift;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class GraphicsDraftTest {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        Path temp=Files.createTempDirectory("fh4droid-graphics-draft-");
        try{
            Map<String,String> source=new LinkedHashMap<>();source.put("user_name","KeepMe");source.put("user_xuid","E0000123456789AB");source.put("pinyon_shift_cheats","true");source.put("fh1_render_scale_percent","90");source.put("present_effect","fsr");
            Map<String,String> capture=GraphicsProfileStore.snapshot(source);
            check(!capture.containsKey("user_name")&&!capture.containsKey("user_xuid")&&!capture.containsKey("pinyon_shift_cheats"),"graphics profiles exclude save identity and trainer flags");
            check(capture.get("fh1_render_scale_percent").equals("90")&&capture.get("present_effect").equals("\"fsr\""),"capture preserves custom graphics and string types");
            File file=temp.resolve("profiles/My graphics.toml").toFile();GraphicsProfileStore.save(file,capture);
            check(GraphicsProfileStore.load(file).equals(capture),"graphics profile round-trip");
            check(!GraphicsProfileStore.describe(capture).contains("pinyon_shift_"),"player-facing descriptions use setting names");
            Map<String,String> bad=new LinkedHashMap<>(source);bad.put("fh1_render_scale_percent","NaN");boolean rejected=false;
            try{GraphicsProfileStore.snapshot(bad);}catch(IOException expected){rejected=true;}check(rejected,"non-finite graphics cannot enter a saved profile");
            bad.put("fh1_render_scale_percent","50\nuser_xuid = 1");rejected=false;try{GraphicsProfileStore.snapshot(bad);}catch(IOException expected){rejected=true;}check(rejected,"graphics values cannot inject unrelated config");
            bad.put("fh1_render_scale_percent","50d");rejected=false;try{GraphicsProfileStore.snapshot(bad);}catch(IOException expected){rejected=true;}check(rejected,"Java-only numeric suffixes cannot enter TOML profiles");
            bad.put("fh1_render_scale_percent","5e1");check(GraphicsProfileStore.snapshot(bad).get("fh1_render_scale_percent").equals("50"),"numeric snapshots use canonical TOML-compatible values");
            for(String name:new String[]{"../escape","/outside",".","", "a\\b"}){rejected=false;try{GraphicsProfileStore.name(name);}catch(IOException expected){rejected=true;}check(rejected,"profile names stay local");}
            LayoutHistory history=new LayoutHistory("original");history.record("one");history.record("two");check(history.undo().equals("one")&&history.undo().equals("original"),"undo preserves intermediate drafts");
            check(history.redo().equals("one"),"redo restores a draft");history.record("replacement");check(!history.canRedo(),"editing an undone draft removes only its redo branch");
            for(int i=0;i<1000;i++)history.record("step"+i);int undo=0;while(history.canUndo()){history.undo();undo++;}check(undo==64,"editor history stays bounded across long sessions");
            TouchLayout layout=new TouchLayout();layout.steeringReturnMs=80;layout.steeringLock=.65f;TouchLayout copy=new TouchLayout();copy.parse(layout.serialize());
            check(copy.steeringReturnMs==80&&Math.abs(copy.steeringLock-.65)<.001,"separate arrow tuning survives save/load");
            copy.parse("steering_lock NaN\nsteering_return_ms 9000\n");check(copy.steeringLock==.65f&&copy.steeringReturnMs==500,"invalid arrow values retain safe bounds");
            layout.values[0][0]=layout.values[1][0]=.7f;layout.values[0][1]=layout.values[1][1]=.8f;
            check(LayoutWarnings.check(layout,2400,1080,0,0,0,0,48).stream().anyMatch(x->x.contains("RT and LT")),"editor identifies competing pedal hit areas");
            Map<String,String> budget=GraphicsProfiles.preset(5);check(budget.get("fh1_render_scale_percent").equals("50")&&budget.get("fh1_msaa_single_sample").equals("true")&&budget.get("pinyon_shift_fh1_render_fps_limit").equals("30"),"budget preset coordinates resolution, AA and target rate");
            check(!budget.containsKey("user_xuid")&&!budget.containsKey("android_gpu_driver"),"budget selection preserves profiles and driver choice");
            System.out.println("PASS: "+checks+" named graphics, editor history and budget-profile checks");
        }finally{SaveArchive.delete(temp.toFile());}
    }
}
