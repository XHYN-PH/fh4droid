package studio.deimos.pinyonshift;

import static org.junit.Assert.*;
import android.app.Activity;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=33,qualifiers="w960dp-h432dp-land-400dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class UiPolishTest {
  private Activity activity(){Activity a=Robolectric.buildActivity(Activity.class).setup().visible().get();a.getSharedPreferences("mobile",0).edit().clear().putBoolean("animations",false).commit();return a;}
  private LinearLayout settings(Activity a,String section)throws Exception{
    return settings(a,section,null);
  }
  private LinearLayout settings(Activity a,String section,MobileSettings.Runtime runtime)throws Exception{
    Method build=SettingsHub.class.getDeclaredMethod("buildPage",Activity.class,String.class,MobileSettings.Runtime.class,Runnable.class);
    build.setAccessible(true);
    return (LinearLayout)build.invoke(null,a,section,runtime,(Runnable)()->{});
  }
  private void arrange(View v,int w,int h){ShadowLooper.idleMainLooper();v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);}
  private void render(View v,int w,int h,String name)throws Exception{arrange(v,w,h);Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));new File("renders-integrated").mkdirs();try(OutputStream out=new FileOutputStream("renders-integrated/"+name+".png")){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
  private RacingLauncherView shell(Activity a)throws Exception{File source=new File(System.getProperty("launcher.source.root"));Bitmap bg=BitmapFactory.decodeFile(new File(source,"android/art/menu-background.png").toString());BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=4;Bitmap logo=BitmapFactory.decodeFile(new File(source,"android/art/menu-logo.png").toString(),opts);RacingLauncherView v=new RacingLauncherView(a,RacingLauncherView.Design.GRID,new RacingLauncherView.Model(),bg,logo,x->{});a.setContentView(v);return v;}
  @Test public void actualSettingsFocusAdjustmentAndPersistence()throws Exception{
    Activity a=activity();MobileConfig.put(a,"fh1_render_scale_percent","100");RacingLauncherView root=shell(a);LinearLayout settings=settings(a,"Graphics");root.showContent("Graphics",settings,false);arrange(root,2400,1080);
    View resolution=settings.findViewWithTag("fh1_render_scale_percent");assertNotNull(resolution);assertTrue(resolution.requestFocus());View panel=settings.findViewWithTag("details-panel"),scroll=settings.findViewWithTag("settings-scroll");Rect d=new Rect(),s=new Rect();assertTrue(panel.getGlobalVisibleRect(d));assertTrue(scroll.getGlobalVisibleRect(s));assertTrue(d.left>=s.right);
    render(root,2400,1080,"test5-settings-graphics");resolution.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT));resolution.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_LEFT));assertEquals("99",MobileConfig.get(a,"fh1_render_scale_percent",""));
    View frame=settings.findViewWithTag("pinyon_shift_fh1_render_fps_limit");assertTrue(frame.requestFocus());assertTrue(text(panel).contains("Higher targets"));render(root,2400,1080,"test5-settings-frame");
    LinearLayout reopened=settings(a,"Graphics");assertNotNull(reopened.findViewWithTag("fh1_render_scale_percent"));assertTrue(text(reopened).contains("99%"));
    for (String[] category : SettingsHub.CATEGORY_CARDS) {
      String section = category[2];
      LinearLayout next = settings(a, section);
      for (String[] other : SettingsHub.CATEGORY_CARDS)
        assertNull("Duplicate category navigation in " + section, find(next, other[2]));
      root.showContent(section, next, false);
      render(root,2400,1080,"test5-settings-"+section.toLowerCase(Locale.ROOT));
    }
  }
  @Test @Config(qualifiers="w640dp-h360dp-land-xxhdpi") public void compactDetailsRemainOnRight()throws Exception{Activity a=activity();RacingLauncherView root=shell(a);LinearLayout settings=settings(a,"Graphics");root.showContent("Graphics",settings,false);render(root,1920,1080,"test5-settings-compact");Rect d=new Rect(),s=new Rect();assertTrue(settings.findViewWithTag("details-panel").getGlobalVisibleRect(d));assertTrue(settings.findViewWithTag("settings-scroll").getGlobalVisibleRect(s));assertTrue(d.left>=s.right);}

  @Test public void everySettingsChoiceSavesFromItsCategoryAndAppliesAtTheRightTime()throws Exception{
    Activity a=activity();
    Map<String,String> running=new LinkedHashMap<>();
    MobileSettings.Runtime runtime=new MobileSettings.Runtime(){
      public void changed(String key,String value){running.put(key,value);}
      public String value(String key){return running.get(key);}
    };
    java.lang.reflect.Field catalog=SettingsHub.class.getDeclaredField("OPTIONS");
    catalog.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<MobileSettings.Option> options=(List<MobileSettings.Option>)catalog.get(null);
    Map<String,String> initial=new LinkedHashMap<>();
    for(MobileSettings.Option option:options){
      String value=option.values[1];
      initial.put(option.key,option.string?MobileConfig.quote(value):value);
    }
    initial.put("enabled_mods",MobileConfig.quote("immersive_camera"));
    MobileConfig.putAll(a,initial);
    AdrenoSettings.record(a,"auto","{\"ok\":true,\"coarse_shading_supported\":true}");
    Set<String> reached=new HashSet<>();
    int choices=0;
    for(String section:new String[]{"Graphics","Audio","Controls","Touch","Phone",
        "Controller","Interface","Gameplay","Advanced"}){
      LinearLayout page=settings(a,section,runtime);
      a.setContentView(page);
      arrange(page,2400,1080);
      for(MobileSettings.Option option:options){
        View row=page.findViewWithTag(option.key);
        if(row==null)continue;
        assertTrue("Duplicate option "+option.key,reached.add(option.key));
        assertTrue("Fixture could not enable "+option.key,row.isEnabled());
        assertTrue("Could not focus "+option.key,row.requestFocus());
        for(int i=0;i<option.values.length;i++){
          running.clear();
          int key=i==0?KeyEvent.KEYCODE_DPAD_LEFT:KeyEvent.KEYCODE_DPAD_RIGHT;
          assertTrue(option.key,row.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key)));
          assertTrue(option.key+" choice "+option.values[i],
              SettingValues.same(option.values[i],MobileConfig.get(a,option.key,"missing")));
          if(option.restart)assertTrue(option.key+" applied early",running.isEmpty());
          else{
            assertEquals(option.key,Collections.singleton(option.key),running.keySet());
            assertTrue(option.key,SettingValues.same(option.values[i],running.get(option.key)));
          }
          choices++;
        }
      }
    }
    assertEquals(options.size(),reached.size());
    System.out.println("Checked "+choices+" Android setting choices across "+reached.size()+" options");
  }

  @Test public void resolutionAdjustsByArrowsStickAndFocusedSlider() throws Exception {
    Activity a=activity();
    MobileConfig.put(a,"fh1_render_scale_percent","90");
    RacingLauncherView root=shell(a);
    LinearLayout page=settings(a,"Graphics");
    root.showContent("Graphics",page,false);
    arrange(root,2400,1080);
    View resolution=page.findViewWithTag("fh1_render_scale_percent");
    resolution.requestFocus();
    press(a,KeyEvent.KEYCODE_DPAD_LEFT);
    assertEquals("89",MobileConfig.get(a,"fh1_render_scale_percent",""));
    findByDescription(resolution,"Increase or next value, Render resolution").performClick();
    assertEquals("90",MobileConfig.get(a,"fh1_render_scale_percent",""));
    moveStick(root,-.85f);
    assertEquals("89",MobileConfig.get(a,"fh1_render_scale_percent",""));
    moveStick(root,-.90f);
    assertEquals("89",MobileConfig.get(a,"fh1_render_scale_percent",""));
    moveStick(root,0);
    moveStick(root,.85f);
    assertEquals("90",MobileConfig.get(a,"fh1_render_scale_percent",""));
    resolution.performClick();
    android.app.Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();
    View decor=dialog.getWindow().getDecorView();
    arrange(decor,1400,850);
    assertTrue(decor.findFocus() instanceof SeekBar);
    dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT));
    dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_LEFT));
    assertEquals("89",MobileConfig.get(a,"fh1_render_scale_percent",""));
    moveStick(decor,1f);
    assertEquals("90",MobileConfig.get(a,"fh1_render_scale_percent",""));
    dialog.dismiss();
  }

  private void moveStick(View target,float x) {
    MotionEvent.PointerProperties finger=new MotionEvent.PointerProperties();
    finger.id=0;
    MotionEvent.PointerCoords axes=new MotionEvent.PointerCoords();
    axes.setAxisValue(MotionEvent.AXIS_X,x);
    MotionEvent event=MotionEvent.obtain(0,10,MotionEvent.ACTION_MOVE,1,
        new MotionEvent.PointerProperties[]{finger},new MotionEvent.PointerCoords[]{axes},0,0,1,1,
        0,0,InputDevice.SOURCE_JOYSTICK,0);
    target.dispatchGenericMotionEvent(event);
    event.recycle();
  }

  private View findByDescription(View node,String label) {
    if(label.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))return node;
    if(node instanceof ViewGroup)for(int i=0;i<((ViewGroup)node).getChildCount();i++){
      View result=findByDescription(((ViewGroup)node).getChildAt(i),label);
      if(result!=null)return result;
    }
    return null;
  }

  @Test public void controlsOptionsUseDistinctIconsAndPersistInputChoices() throws Exception {
    Activity a=activity();
    Set<String> icons=new HashSet<>();
    for(String section:new String[]{"Controls","Touch","Phone","Controller"}){
      LinearLayout page=settings(a,section);
      collectControlIcons(page,icons);
    }
    assertEquals("Controls needs a distinct icon for each row",23,icons.size());
    LinearLayout touch=settings(a,"Touch");
    a.setContentView(touch);
    arrange(touch,2200,1000);
    View steering=touch.findViewWithTag("touch-steering-mode");
    assertTrue(steering.requestFocus());
    press(a,KeyEvent.KEYCODE_DPAD_RIGHT);
    TouchLayout stored=new TouchLayout();
    stored.load(a);
    assertEquals("stick",stored.steering);
    press(a,KeyEvent.KEYCODE_DPAD_LEFT);
    stored.load(a);
    assertEquals("arrows",stored.steering);
    LinearLayout phone=settings(a,"Phone");
    a.setContentView(phone);
    arrange(phone,2200,1000);
    View range=findByDescription(phone,"Tilt steering range");
    assertTrue(range instanceof SeekBar);
    assertTrue(range.requestFocus());
    press(a,KeyEvent.KEYCODE_DPAD_RIGHT);
    assertEquals(36,a.getSharedPreferences("mobile",0).getInt("tilt_range",0));
  }

  private void collectControlIcons(View view,Set<String> seen) {
    if(view instanceof OutlineIcon){
      String kind=((OutlineIcon)view).kind;
      if(!kind.equals("chevron"))assertTrue(kind,seen.add(kind));
    }
    if(view instanceof Button){
      android.graphics.drawable.Drawable drawable=((Button)view).getCompoundDrawablesRelative()[0];
      if(drawable instanceof MenuIcon){
        String kind=org.robolectric.util.ReflectionHelpers.getField(drawable,"kind");
        assertTrue(kind,seen.add(kind));
      }
    }
    if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)
      collectControlIcons(((ViewGroup)view).getChildAt(i),seen);
  }

  @Test public void nearestFilterSavesWithoutChangingResolutionOrAntiAliasing()throws Exception{
    Activity a=activity();
    Map<String,String> initial=new HashMap<>();
    initial.put("fh1_render_scale_percent","90");
    initial.put("present_effect",MobileConfig.quote("bilinear"));
    initial.put("swap_post_effect",MobileConfig.quote("fxaa"));
    MobileConfig.putAll(a,initial);
    Map<String,String> running=new HashMap<>();
    LinearLayout page=settings(a,"Graphics",running::put);
    a.setContentView(page);
    arrange(page,2400,1080);
    View filter=page.findViewWithTag("present_effect");
    assertNotNull(filter);filter.performClick();
    android.app.Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();
    TextView off=find(dialog.getWindow().getDecorView(),"Off (nearest)");
    assertNotNull(off);off.performClick();
    assertEquals(Collections.singletonMap("present_effect","nearest"),running);
    assertEquals("nearest",MobileConfig.get(a,"present_effect",""));
    assertEquals("90",MobileConfig.get(a,"fh1_render_scale_percent",""));
    assertEquals("fxaa",MobileConfig.get(a,"swap_post_effect",""));
    assertTrue(text(settings(a,"Graphics")).contains("Off (nearest)"));
    LinearLayout search=settings(a,"All settings");
    android.widget.EditText field=(android.widget.EditText)((ViewGroup)((android.widget.ScrollView)
        search.findViewWithTag("settings-scroll")).getChildAt(0)).getChildAt(0);
    field.setText("FSR softness");
    assertNotNull(search.findViewWithTag("present_fsr_sharpness_reduction"));
    field.setText("music player");
    assertNotNull(find(search,"Use my music player"));
  }

  @Test public void inheritedBilinearUpgradesOnceAndExplicitFiltersAreKept() throws Exception {
    Activity a = activity();
    Map<String,String> initial = new HashMap<>();
    initial.put("present_effect", MobileConfig.quote("bilinear"));
    initial.put("fh1_render_scale_percent", "90");
    initial.put("swap_post_effect", MobileConfig.quote("fxaa"));
    MobileConfig.putAll(a, initial);
    GraphicsProfileStore.upgradeUpscalingDefault(a);
    assertEquals("auto", MobileConfig.get(a, "present_effect", ""));
    assertEquals("90", MobileConfig.get(a, "fh1_render_scale_percent", ""));
    assertEquals("fxaa", MobileConfig.get(a, "swap_post_effect", ""));
    assertTrue(text(settings(a, "Graphics")).contains("Automatic (sharp)"));

    MobileConfig.put(a, "present_effect", MobileConfig.quote("bilinear"));
    GraphicsProfileStore.upgradeUpscalingDefault(a);
    assertEquals("bilinear", MobileConfig.get(a, "present_effect", ""));
    a.getSharedPreferences("mobile",0).edit().remove("sharp_upscaling_default").commit();
    MobileConfig.put(a, "present_effect", MobileConfig.quote("nearest"));
    GraphicsProfileStore.upgradeUpscalingDefault(a);
    assertEquals("nearest", MobileConfig.get(a, "present_effect", ""));
  }

  @Test public void graphicsPresetsRefreshTheMountedRowsImmediately()throws Exception{
    Activity a=activity();
    LinearLayout page=settings(a,"Graphics");
    a.setContentView(page);
    arrange(page,2400,1080);
    View fsr=page.findViewWithTag("graphics-fsr");
    assertNotNull(fsr);fsr.performClick();
    android.app.Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();
    TextView quality=find(dialog.getWindow().getDecorView(),"Quality · 67%");
    assertNotNull(quality);quality.performClick();
    assertEquals("67",MobileConfig.get(a,"fh1_render_scale_percent",""));
    assertEquals("fsr",MobileConfig.get(a,"present_effect",""));
    assertEquals("0.5",MobileConfig.get(a,"present_fsr_sharpness_reduction",""));
    assertTrue(text(fsr).contains("Quality · 67%"));
    assertTrue(text(page.findViewWithTag("fh1_render_scale_percent")).contains("67%"));
    assertTrue(text(page.findViewWithTag("present_effect")).contains("FSR 1"));
  }

  @Test public void settingsSearchAndShouldersKeepOneCategoryPage() {
    LauncherActivity a = Robolectric.buildActivity(LauncherActivity.class).setup().visible().get();
    RacingLauncherView root = (RacingLauncherView) ((ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
    root.showPage("settings");
    arrange(root, 2400, 1080);
    assertTrue(root.requestActionFocus("graphics"));
    press(a, KeyEvent.KEYCODE_BUTTON_A);
    arrange(root, 2400, 1080);
    View resolution = root.findViewWithTag("fh1_render_scale_percent");
    assertNotNull(resolution);
    assertTrue(resolution.requestFocus());
    assertNull(find(root, "Audio"));

    find(root, "Search").performClick();
    arrange(root, 2400, 1080);
    assertNotNull(find(root, "Search & favourites"));
    assertNotNull(find(root, "Pinned only"));
    press(a, KeyEvent.KEYCODE_BUTTON_B);
    arrange(root, 2400, 1080);
    assertSame(resolution, root.findFocus());

    press(a, KeyEvent.KEYCODE_BUTTON_R1);
    arrange(root, 2400, 1080);
    assertNotNull(find(root, "Audio"));
    assertNull(find(root, "Graphics"));
    press(a, KeyEvent.KEYCODE_BUTTON_B);
    arrange(root, 2400, 1080);
    assertEquals("settings", root.page());
    assertEquals("graphics", root.focusedAction());
  }

  @Test public void settingsEntryOpensTheSixCategoryChooser() {
    Activity a = activity();
    SettingsHub.show(a, null, () -> {}, () -> {});
    View chooser = org.robolectric.shadows.ShadowDialog.getLatestDialog().getWindow().getDecorView();
    for (String[] category : SettingsHub.CATEGORY_CARDS) assertNotNull(find(chooser, category[2]));
    assertNull(chooser.findViewWithTag("fh1_render_scale_percent"));
  }

  private void press(Activity a, int key) {
    a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
    a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
  }
  @Test public void gameMenuSingleTapAndTextOnlyActions()throws Exception{Activity a=activity();List<String> actions=new ArrayList<>();GameMenuView menu=new GameMenuView(a,actions::add);a.setContentView(menu);render(menu,1250,850,"test5-game-menu");Button settings=menu.findViewWithTag("Settings");assertFalse(settings.hasFocus());long t=android.os.SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(t,t+30,action,settings.getWidth()/2f,settings.getHeight()/2f,0);settings.dispatchTouchEvent(e);e.recycle();}ShadowLooper.idleMainLooper();assertEquals(Collections.singletonList("Settings"),actions);for(String title:new String[]{"Resume","Settings","Touch controls","Controller","More","Quit to launcher"}){Button b=menu.findViewWithTag(title);assertNotNull(b);for(android.graphics.drawable.Drawable icon:b.getCompoundDrawables())assertNull(icon);Rect r=new Rect();assertTrue(b.getGlobalVisibleRect(r));assertEquals(b.getHeight(),r.height());}}
  @Test public void uiPreferencesSurviveProcessHandoff(){
    Activity a=activity();
    android.content.SharedPreferences prefs=a.getSharedPreferences("mobile",0);
    String[] switches={"animations","show_fps","show_frame_time","show_memory_heat","match_refresh","cpu_hints",
        "phone_rumble","phone_tilt","tilt_invert","sustained_mode","advanced_settings","detailed_gpu",
        "extended_capture","save_folder_sync","audio_compatibility"};
    for(boolean enabled:new boolean[]{false,true}){
      android.content.SharedPreferences.Editor edits=prefs.edit().putString("app_language","fil")
          .putFloat("ui_scale",.9f).putInt("phone_rumble_strength",85)
          .putInt("tilt_range",45).putInt("tilt_deadzone",7);
      for(String key:switches)edits.putBoolean(key,enabled);
      assertTrue(edits.commit());
      UiPreferences.save(a);
      assertTrue(prefs.edit().clear().commit());
      UiPreferences.load(a);
      for(String key:switches)assertEquals(key,enabled,prefs.getBoolean(key,!enabled));
      assertEquals("fil",prefs.getString("app_language",""));
      assertEquals(.9f,prefs.getFloat("ui_scale",0),.001f);
      assertEquals(85,prefs.getInt("phone_rumble_strength",0));
      assertEquals(45,prefs.getInt("tilt_range",0));
      assertEquals(7,prefs.getInt("tilt_deadzone",0));
    }
  }
  public static class SaveSyncActivity extends PinyonShiftActivity {
    File mediaDirectory;

    @Override public File[] getExternalMediaDirs() {
      return new File[] {mediaDirectory};
    }
  }

  @Test public void saveSyncRequiresOptInAfterProcessHandoff() {
    SaveSyncActivity game = Robolectric.buildActivity(SaveSyncActivity.class).get();
    game.setIntent(new android.content.Intent());
    game.mediaDirectory = new File(game.getCacheDir(), "media");
    assertTrue(game.mediaDirectory.mkdirs());
    File sync = new File(game.mediaDirectory, "sync");
    game.getSharedPreferences("mobile", 0).edit().clear().commit();
    assertFalse(Arrays.stream(game.getArguments()).anyMatch(arg -> arg.startsWith("--pinyon_shift_sync_folder=")));
    assertFalse(sync.exists());

    game.getSharedPreferences("mobile", 0).edit().putBoolean("save_folder_sync", true).commit();
    UiPreferences.save(game);
    game.getSharedPreferences("mobile", 0).edit().clear().commit();
    UiPreferences.load(game);
    assertTrue(Arrays.asList(game.getArguments()).contains("--pinyon_shift_sync_folder=" + sync.getAbsolutePath()));
    assertTrue(sync.isDirectory());
  }
  @Test public void playEnablesDetailedLoggingAndReportsOffersQuietPlay() throws Exception {
    LauncherActivity a=Robolectric.buildActivity(LauncherActivity.class).setup().visible().get();
    a.getSharedPreferences("mobile",0).edit().putBoolean("record_session_logs",false).commit();
    File executable = new File(GameFiles.root(a), "default.xex");
    assertTrue(executable.getParentFile().mkdirs() || executable.getParentFile().isDirectory());
    assertTrue(executable.createNewFile());
    android.app.ActivityManager manager = a.getSystemService(android.app.ActivityManager.class);
    android.app.ActivityManager.RunningAppProcessInfo process = new android.app.ActivityManager.RunningAppProcessInfo();
    process.processName = a.getPackageName() + ":game";
    Shadows.shadowOf(manager).setProcesses(Collections.singletonList(process));
    ReflectionHelpers.callInstanceMethod(a, "launcherAction", ClassParameter.from(String.class, "play"));
    android.content.Intent launch = Shadows.shadowOf(a).getNextStartedActivity();
    assertNotNull(launch);
    assertTrue(launch.getBooleanExtra("debug_report",false));
    assertFalse(launch.getBooleanExtra(PinyonShiftActivity.NO_SESSION_LOGS,false));
    Method reports=LauncherActivity.class.getDeclaredMethod("reports");
    reports.setAccessible(true);
    reports.invoke(a);
    View root=a.findViewById(android.R.id.content);
    render(root,2400,1080,"refinements-problem-reports");
    assertNotNull(find(root,"Play without logs"));
    assertNull(find(root,"Play with diagnostics"));
  }

  @Test public void aboutShowsRequirementsCreditsAndUpstreamLink() {
    LauncherActivity launcher = Robolectric.buildActivity(LauncherActivity.class).setup().visible().get();
    RacingLauncherView root = (RacingLauncherView) ((ViewGroup) launcher.findViewById(android.R.id.content)).getChildAt(0);
    root.showPage("more");
    arrange(root, 2400, 1080);
    assertTrue(root.findViewWithTag("about").performClick());
    arrange(root, 2400, 1080);
    assertTrue(text(root).contains("Android 13 or newer"));
    assertTrue(text(root).contains("arcanite24"));
    assertTrue(text(root).contains("Control artwork credits"));
    assertNull(root.findViewWithTag("launcher-footer"));
    View upstream = findByDescription(root, "Pinyon Shift. Upstream project");
    assertNotNull(upstream);
    assertTrue(upstream.performClick());
    assertEquals("https://github.com/arcanite24/pinyon-shift",
        org.robolectric.Shadows.shadowOf(launcher).getNextStartedActivity().getDataString());
  }
  @Test public void quietPlaySuppressesRecordingAndLeavesEarlierLogsAlone() throws Exception {
    SaveSyncActivity game=Robolectric.buildActivity(SaveSyncActivity.class).get();
    File log=new File(MobileConfig.base(game),"state/logs/android-launch-settings.json");
    log.getParentFile().mkdirs();
    byte[] earlier={12,34};
    java.nio.file.Files.write(log.toPath(),earlier);
    game.setIntent(new android.content.Intent().putExtra(PinyonShiftActivity.NO_SESSION_LOGS,true)
        .putExtra("debug_report",true));
    assertFalse(game.sessionLoggingEnabled());
    List<String> quiet=Arrays.asList(game.getArguments());
    assertTrue(quiet.contains("--log_level=off"));
    assertTrue(quiet.contains("--perf_log_csv="));
    assertTrue(quiet.contains("--pinyon_shift_capture_performance=false"));
    assertFalse(quiet.contains("--pinyon_shift_capture_performance=true"));
    assertArrayEquals(earlier,java.nio.file.Files.readAllBytes(log.toPath()));
    SettingsHub.apply(game,null,Collections.singletonMap("pinyon_shift_master_volume","80"),false);
    assertFalse(new File(log.getParentFile(),"android-settings-changes.jsonl").exists());
    GameSession.record(game,false,game.sessionLoggingEnabled());
    assertTrue(GameSession.lastSessionQuiet(game));
    assertEquals("80",MobileConfig.get(game,"pinyon_shift_master_volume",""));

    game.setIntent(new android.content.Intent().putExtra("debug_report",true));
    assertTrue(game.sessionLoggingEnabled());
    assertTrue(Arrays.asList(game.getArguments()).contains("--pinyon_shift_capture_performance=true"));
    assertNotEquals(earlier.length,log.length());
    GameSession.record(game,false,game.sessionLoggingEnabled());
    assertFalse(GameSession.lastSessionQuiet(game));
  }
  @Test public void adreno730AutomaticUsesSystem()throws Exception{Method m=PinyonShiftActivity.class.getDeclaredMethod("recommendedDriver",String.class);m.setAccessible(true);assertNull(m.invoke(null,"730"));assertNull(m.invoke(null,(Object)null));assertEquals("turnip-gen8-v37",m.invoke(null,"740"));}
  @Test public void profileAndAchievementsUseCompactPages()throws Exception{
    Activity a=activity();org.json.JSONArray achievements=new org.json.JSONArray();for(int i=0;i<13;i++)achievements.put(new org.json.JSONObject().put("id",i).put("name",String.format("Achievement %02d",i)).put("score",10).put("unlocked",i%2==0));AchievementsPage.show(a,new org.json.JSONObject().put("achievements",achievements).toString());android.app.Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();assertNotNull(dialog);View root=dialog.getWindow().getDecorView();arrange(root,2400,1080);assertTrue(text(root).contains("Achievement 00"));assertFalse(text(root).contains("Achievement 06"));TextView next=find(root,"Next");assertNotNull(next);next.performClick();assertTrue(text(root).contains("Achievement 06"));assertFalse(text(root).contains("Achievement 00"));find(root,"Unlocked").performClick();assertTrue(text(root).contains("Achievement 00"));assertFalse(text(root).contains("Achievement 01"));dialog.dismiss();
    ProfilePage.show(a,()->{},()->{});dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();assertNotNull(dialog);assertTrue(text(dialog.getWindow().getDecorView()).contains("Edit gamertag"));dialog.dismiss();
  }
  @Test public void achievementDetailsWrapAndSearchStaysCompact() throws Exception {
    Activity a = activity();
    String data = "{\"achievements\":[{\"id\":8,\"name\":\"Finish Line\",\"score\":20,"
        + "\"unlocked\":true,\"description\":\"Finish the opening race.\"}]}";
    AchievementsPage.show(a, data);
    android.app.Dialog list = org.robolectric.shadows.ShadowDialog.getLatestDialog();
    View root = list.getWindow().getDecorView();
    arrange(root, 2000, 950);
    TextView caption = find(root, "Finish Line");
    assertNotNull(caption);
    View achievement = (View) caption.getParent().getParent();
    ArrayList<View> matches = new ArrayList<>();
    root.findViewsWithText(matches, "Find an achievement", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
    assertEquals(1, matches.size());
    View search = matches.get(0);
    assertTrue(search.getWidth() <= FestivalUi.dp(a, 146));
    assertTrue(achievement.performClick());
    android.app.Dialog details = org.robolectric.shadows.ShadowDialog.getLatestDialog();
    assertNotSame(list, details);
    assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, details.getWindow().getAttributes().height);
    assertTrue(details.getWindow().getAttributes().width <= FestivalUi.dp(a, 420));
    assertTrue(text(details.getWindow().getDecorView()).contains("Finish the opening race."));
    details.dismiss();
    list.dismiss();
  }

  @Test public void backupActionsUseContentSizedDialog() {
    LauncherActivity launcher = Robolectric.buildActivity(LauncherActivity.class).setup().visible().get();
    SaveArchive.Backup backup = new SaveArchive.Backup(
        new File(launcher.getCacheDir(), "backup-fixture"), "Normal profile", true);
    ReflectionHelpers.callInstanceMethod(launcher, "showBackup", ClassParameter.from(SaveArchive.Backup.class, backup),
        ClassParameter.from(String.class, "November 20"));
    android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
    assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, dialog.getWindow().getAttributes().height);
    View root = dialog.getWindow().getDecorView();
    assertNotNull(find(root, "Export backup"));
    assertNotNull(find(root, "Restore backup"));
    assertNotNull(find(root, "Delete backup"));
    dialog.dismiss();
  }

  @Test public void driverPageIdentifiesSelectedAndActuallyTestedDrivers() throws Exception {
    LauncherActivity launcher = Robolectric.buildActivity(LauncherActivity.class).setup().visible().get();
    AdrenoSettings.record(launcher, "", "{\"ok\":true,\"effective_driver\":\"\","
        + "\"gpu\":\"Test GPU\",\"checks\":[{\"ok\":true,\"name\":\"Render and readback\","
        + "\"detail\":\"Colour readback passed\"}]}");
    ReflectionHelpers.callInstanceMethod(launcher, "drivers");
    View root = launcher.findViewById(android.R.id.content);
    render(root, 2400, 1080, "driver-status");
    assertNotNull(find(root, "Test driver"));
    assertNotNull(find(root, "Startup checks passed"));
    assertNotNull(find(root, "Render and readback · Passed"));
    assertNotNull(find(root, "Colour readback passed"));
  }
  @Test public void editorKeepsFullCanvasWithDockedInspectorAndOptionalGrid()throws Exception{
    org.robolectric.android.controller.ActivityController<TouchEditorActivity> controller=Robolectric.buildActivity(TouchEditorActivity.class);TouchEditorActivity a=controller.get();a.getPreferences(0).edit().clear().commit();File assets=new File("editor-fixture.zip");try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(new FileOutputStream(assets))){for(String name:new String[]{"menu-background.png","touch-controls.png"}){zip.putNextEntry(new java.util.zip.ZipEntry("assets/"+name));java.nio.file.Files.copy(new File(System.getProperty("launcher.source.root"),"android/art/"+name).toPath(),zip);zip.closeEntry();}}org.robolectric.util.ReflectionHelpers.callInstanceMethod(a.getAssets(),"addAssetPath",org.robolectric.util.ReflectionHelpers.ClassParameter.from(String.class,assets.getCanonicalPath()));controller.setup().visible();FrameLayout root=(FrameLayout)((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);arrange(root,2400,1080);View canvas=root.getChildAt(1);assertEquals(root.getWidth(),canvas.getWidth());assertEquals(root.getHeight(),canvas.getHeight());assertTrue(a.getPreferences(0).getBoolean("grid",true));render(root,2400,1080,"test5-touch-editor-grid");
    find(root,"Edit").performClick();android.app.AlertDialog dialog=(android.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();find(dialog.getWindow().getDecorView(),"Gridlines: On").performClick();assertFalse(a.getPreferences(0).getBoolean("grid",true));find(root,"Edit").performClick();dialog=(android.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();find(dialog.getWindow().getDecorView(),"Snapping: On").performClick();assertFalse(a.getPreferences(0).getBoolean("snap",true));
    find(root,"Properties").performClick();arrange(root,2400,1080);assertEquals(View.VISIBLE,root.getChildAt(2).getVisibility());assertTrue(root.getChildAt(3).getWidth() < root.getWidth());assertTrue(root.getChildAt(2).getWidth() < root.getWidth() * .5f);render(root,2400,1080,"refinements-touch-inspector");find(root,"Canvas").performClick();render(root,2400,1080,"test5-touch-editor-text");a.finish();
  }
  @Test public void editorAddsHiddenControlAndSavesOnlyItsOpacity()throws Exception{
    TouchEditorActivity a=Robolectric.buildActivity(TouchEditorActivity.class).setup().visible().get();
    View root=a.findViewById(android.R.id.content);
    arrange(root,2400,1080);
    int button=Arrays.asList(TouchLayout.LABELS).indexOf("B");
    TouchLayout original=org.robolectric.util.ReflectionHelpers.getField(a,"layout");
    original.hidden[button]=true;
    find(root,"Add button").performClick();
    android.app.Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();
    assertNotNull(dialog);
    TextView add=find(dialog.getWindow().getDecorView(),TouchArtwork.CAPTIONS[button]);
    assertNotNull(add);add.performClick();
    assertFalse(original.hidden[button]);
    find(root,"Properties").performClick();
    assertNotNull(find(root,"Control opacity"));
    original.controlOpacity[button]=.28f;
    find(root,"Save").performClick();
    TouchLayout loaded=new TouchLayout();loaded.load(a);
    assertFalse(loaded.hidden[button]);
    assertEquals(.28f,loaded.backgroundOpacity(button),.001f);
    assertEquals(loaded.opacity,loaded.backgroundOpacity(0),.001f);
  }
  private TextView find(View v,String caption){if(v instanceof TextView&&((TextView)v).getText().toString().equals(caption))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView result=find(((ViewGroup)v).getChildAt(i),caption);if(result!=null)return result;}return null;}
  private String text(View v){if(v instanceof TextView)return ((TextView)v).getText().toString();StringBuilder out=new StringBuilder();if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)out.append(text(((ViewGroup)v).getChildAt(i))).append(' ');return out.toString();}
}
