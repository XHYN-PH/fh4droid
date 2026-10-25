package studio.deimos.pinyonshift;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Reproduces report corruption, layout migration and input/thermal edge cases. */
public final class MobileRefinementsTest {
  private static int checks;
  private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
  private static String tail(Path file,int budget)throws Exception{return new String(ReportFiles.tail(file.toFile(),budget),StandardCharsets.UTF_8);}
  public static void main(String[] args)throws Exception{
    Path root=Files.createTempDirectory("fh4droid-refinements-");
    try{
      Path csv=root.resolve("run.perf.csv");String header="frame_time_us,fps,draw_calls\n";
      StringBuilder rows=new StringBuilder(header);for(int i=0;i<100;i++)rows.append("33333,30,").append(i).append('\n');rows.append("incomplete,31");Files.write(csv,rows.toString().getBytes(StandardCharsets.UTF_8));
      String trimmed=tail(csv,130);check(trimmed.startsWith(header),"trimmed performance report retains its header");
      check(trimmed.endsWith("33333,30,99\n")&&!trimmed.contains("incomplete"),"partial final frame is dropped");
      check(trimmed.getBytes(StandardCharsets.UTF_8).length<=130,"header and tail obey the byte limit");
      for(String row:trimmed.substring(header.length()).split("\n"))check(row.matches("33333,30,[0-9]+"),"first retained frame is a whole record");
      check(tail(csv,10000).equals(rows.substring(0,rows.lastIndexOf("\n")+1)),"small CSV also drops an interrupted final write");
      check(tail(csv,4).isEmpty(),"an oversized header is never partially emitted");
      check(tail(csv,0).isEmpty(),"zero budget exports no bytes");
      Path jsonl=root.resolve("session.jsonl");Files.write(jsonl,"{\"event\":1}\n{\"event\":2}\n{\"partial\":".getBytes(StandardCharsets.UTF_8));
      check(tail(jsonl,18).equals("{\"event\":2}\n"),"JSONL tail contains only complete records");
      Path json=root.resolve("settings.json");Files.write(json,"{\"valid\":true}".getBytes(StandardCharsets.UTF_8));check(tail(json,100).equals("{\"valid\":true}"),"complete JSON needs no trailing newline");
      check("20261025T005207Z-p17867".equals(ReportFiles.sessionName("4-20261025T005207Z-p17867.perf.csv")),"CSV and events resolve to the same native session");
      check(ReportFiles.sessionName("android-settings-changes.jsonl")==null,"shared logs do not acquire a fictional session");
      check(ReportFiles.exitLabel(1,0).equals("Normal app exit"),"normal self-exit is not a crash");
      check(!ReportFiles.exitLabel(1,7).equals("Normal app exit"),"nonzero self-exit is not labelled normal");
      check(ReportFiles.thermalLabel(2).equals("Moderate"),"thermal severity is described rather than treated as degrees");
      check(SettingValues.same("1","1.000000")&&SettingValues.same("\"fsr\"","fsr"),"formatting differences do not create false pending changes");
      check(!SettingValues.same("NaN","1")&&!SettingValues.same("fsr","bilinear"),"different or invalid values remain distinguishable");
      check(DrivingMath.axis(.08f,.1f,1,1)==0&&DrivingMath.axis(.8f,.1f,.8f,1)==1,"controller inner and outer calibration");
      check(DrivingMath.axis(Float.NaN,0,1,1)==0,"invalid controller input releases the axis");
      check(Math.abs(DrivingMath.tilt(-179,179,35))<.1f,"tilt handles angle wraparound");
      check(DrivingMath.tilt(Float.POSITIVE_INFINITY,0,35)==0,"invalid sensor input releases steering");
      check(Float.isFinite(DrivingMath.tilt(Float.MAX_VALUE,-Float.MAX_VALUE,35)),"extreme finite sensor values normalize without an unbounded loop");
      check(DrivingMath.sustainedTarget(0,false,4)==0&&DrivingMath.sustainedTarget(20,false,4)==20,"disabled thermal mode preserves custom limits");
      check(DrivingMath.sustainedTarget(0,true,3)==30&&DrivingMath.sustainedTarget(60,true,3)==30,"severe heat bounds uncapped and high targets");
      check(DrivingMath.sustainedTarget(20,true,3)==20&&DrivingMath.sustainedTarget(40,true,0)==40,"thermal mode never raises a low limit and restores the requested limit");
      Map<String,Object> raw=new HashMap<>();raw.put("ui_scale",Float.NaN);raw.put("phone_rumble_strength",999);raw.put("tilt_range",-90);raw.put("iso_uri","private");raw.put("phone_tilt","true");raw.put("show_fps",true);
      Map<String,Object> clean=UiPreferences.sanitized(raw);check(!clean.containsKey("ui_scale")&&!clean.containsKey("iso_uri")&&!clean.containsKey("phone_tilt"),"handoff excludes private, invalid and wrong-type values");
      check(clean.get("phone_rumble_strength").equals(100)&&clean.get("tilt_range").equals(15)&&Boolean.TRUE.equals(clean.get("show_fps")),"valid phone preferences survive within bounds");
      check(AdrenoSettings.isTurnip(0x5143,"FD750",18)&&!AdrenoSettings.isTurnip(0x5143,"Adreno 750",8)&&!AdrenoSettings.isTurnip(0x13b5,"Mali",18),"experimental controls require the actual vendor and driver IDs");
      String configured=AdrenoSettings.configure("TU_DEBUG=gmem,perf;IR3_SHADER_DEBUG=nouboopt",1,true,false);
      check(configured.equals("TU_DEBUG=perf,sysmem,nolrz;IR3_SHADER_DEBUG=nouboopt"),"changing memory mode preserves independent expert entries");
      check(AdrenoSettings.configure(configured,0,false,false).equals("TU_DEBUG=perf;IR3_SHADER_DEBUG=nouboopt"),"Adreno reset removes only managed workarounds");
      TouchLayout layout=new TouchLayout();layout.steering="stick";layout.leftMode=2;layout.rightMode=1;layout.timeout[5]=3;layout.idleSeconds=0;layout.drivingLabels=false;layout.buttonHints=false;layout.highContrast=true;layout.analogPedals=true;
      TouchLayout restored=new TouchLayout();restored.parse(layout.serialize());
      check(restored.steering.equals("stick")&&restored.leftMode==2&&restored.rightMode==1,"steering and both stick modes survive saving");
      check(restored.timeout[5]==3&&restored.idleSeconds==0&&!restored.drivingLabels&&!restored.buttonHints&&restored.highContrast&&restored.analogPedals,"new and existing touch preferences roundtrip together");
      restored.parse("timeout Y NaN\nidle_seconds Infinity\nleft_mode 999\nright_mode -9\n");check(restored.timeout[5]==3&&restored.idleSeconds==0&&restored.leftMode==2&&restored.rightMode==0,"invalid timers and modes are rejected or bounded");
      layout.iconOpacity=.81f;layout.opacity=.24f;layout.values[0][0]=.672f;
      restored=new TouchLayout();restored.parse(layout.serialize());
      check(restored.iconOpacity==.81f&&restored.opacity==.24f,"icon opacity is independent of backing opacity");
      check(restored.values[0][0]==.672f,"saving appearance preserves custom positions");
      restored.parse("icon_opacity NaN\n");check(restored.iconOpacity==.81f,"invalid icon opacity retains the saved value");
      TouchLayout modern=new TouchLayout();modern.modernDefaults();
      check(modern.drivingLabels&&modern.buttonHints&&modern.steering.equals("arrows"),"modern preset includes action icons, names and arrow steering");
      for(int i=0;i<TouchLayout.LABELS.length;i++)check(!modern.hidden[i]||Arrays.asList("B","X","LB","RS","UP","DOWN","DPAD_LEFT","DPAD_RIGHT","L3","R3").contains(TouchLayout.LABELS[i]),"essential racing controls stay visible");
      for(float aspect:new float[]{4f/3f,16f/10f,16f/9f,20f/9f}){
        TouchLayout adapted=new TouchLayout();adapted.modernDefaults(aspect);
        // Cluster spacing in height units is stable; a tablet must not push
        // its wide brake pedal into the accelerator or overlap steering keys.
        check(Math.abs((adapted.values[12][0]-adapted.values[11][0])*aspect-(modern.values[12][0]-modern.values[11][0])*16f/9f)<.00001f,"steering spacing survives aspect changes");
        check(Math.abs((1-adapted.values[1][0])*aspect-(1-modern.values[1][0])*16f/9f)<.00001f,"brake keeps its distance from the right edge");
      }
      TouchLayout prior=new TouchLayout();prior.parse("RT .672 .753 .1 0\n");check(prior.values[0][0]==.672f&&prior.values[0][1]==.753f&&prior.iconOpacity==.95f,"older layouts keep their placement and receive readable icon opacity");
      TouchLayout compact=LayoutProfiles.template(false,true),full=LayoutProfiles.controllerTemplate(),left=LayoutProfiles.template(true,false);
      check(full.steering.equals("stick")&&!full.drivingLabels,"full controller template uses a steering stick and Xbox labels");
      check(compact.steering.equals("arrows")&&compact.drivingLabels,"driving template retains arrows and racing artwork");
      for(int i=0;i<TouchLayout.LABELS.length;i++)check(Math.abs(left.values[i][0]-(1-full.values[i][0]))<.00001f,"handed template mirrors every control");
      int look=Arrays.asList(TouchLayout.LABELS).indexOf("RS");check(compact.hidden[look]&&!full.hidden[look],"compact preset keeps optional full-controller buttons selectable");
      boolean invalid=false;try{LayoutProfiles.name("../outside");}catch(IOException expected){invalid=true;}check(invalid,"named layouts cannot escape their folder");
      Path legacy=root.resolve("legacy.toml");Files.write(legacy,"# existing settings\ncustom_option = 42\nenabled_mods = \"old\"\n".getBytes(StandardCharsets.UTF_8));
      String mods="radio\\folder\"pack";SetupActivity.applyEnabledMods(legacy.toFile(),mods);
      check(MobileConfig.readFile(legacy.toFile()).get("enabled_mods").equals(mods),"pending mod compatibility preserves quoted folder names");
      check(MobileConfig.readFile(legacy.toFile()).get("custom_option").equals("42")&&new String(Files.readAllBytes(legacy),StandardCharsets.UTF_8).startsWith("# existing settings"),"pending mod compatibility keeps unrelated settings and comments");
      SetupActivity.applyEnabledMods(legacy.toFile(),"");check(MobileConfig.readFile(legacy.toFile()).get("enabled_mods").isEmpty(),"old pending mods can be disabled atomically");
      Path absent=root.resolve("not-created.toml");invalid=false;try{SetupActivity.applyEnabledMods(absent.toFile(),"radio");}catch(FileNotFoundException expected){invalid=true;}check(invalid&&!Files.exists(absent),"pending mods wait until the game has created its settings");
      Path cache=Files.createDirectory(root.resolve("cache")),raster=Files.createDirectory(cache.resolve("raster-v3-80"));Files.write(raster.resolve("cache.bin"),new byte[]{1,2,3});
      Path saves=Files.createDirectory(cache.resolve("user"));Files.write(saves.resolve("save.bin"),new byte[]{7});
      check(GraphicsCache.size(raster.toFile())==3,"cache size is measured");GraphicsCache.clear(cache.toFile(),raster.toFile());check(!Files.exists(raster)&&Files.exists(saves.resolve("save.bin")),"graphics clearing preserves unrelated saves");
      invalid=false;try{GraphicsCache.clear(cache.toFile(),saves.toFile());}catch(IOException expected){invalid=true;}check(invalid,"non-graphics paths cannot be cleared");
    }finally{SaveArchive.delete(root.toFile());}
    System.out.println("PASS: "+checks+" mobile refinement regression checks");
  }
}
