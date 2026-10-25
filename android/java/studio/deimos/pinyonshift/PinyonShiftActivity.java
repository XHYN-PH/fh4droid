package studio.deimos.pinyonshift;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.view.WindowManager;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.libsdl.app.SDLActivity;

/**
 * SDL's activity with the game's paths and arguments (AP-1.4, AP-3.3).
 *
 * The game reads its folders from the environment, as tools/pinyon.py sets
 * them on a PC: PINYON_SHIFT_GAME_ROOT holds the files extracted from the
 * player's disc and PINYON_SHIFT_STATE_ROOT the saves, settings, caches, logs
 * and mods. Both live in the app's external files folder, which needs no
 * permission and is reachable over adb and MTP, so a save copies between the
 * device and a PC as a folder. Process environment set here, before SDL
 * starts the native thread, is what the game's getenv sees.
 *
 * Tooling can override any variable with a string extra named env.NAME and
 * pass game arguments as a string-array extra named args:
 *
 *   adb shell am start -n studio.deimos.pinyonshift/.PinyonShiftActivity \
 *       --es env.PINYON_SHIFT_FH1_RENDER_TEST_SCRIPT /sdcard/.../route.fh1test \
 *       --esa args --gpu_backend=null
 */
public class PinyonShiftActivity extends SDLActivity implements FestivalUi.PanelHost {
    private static native void nativeHostMenu(boolean active);
    private static native void nativeOpenSettings();
    private static native void nativeApplySetting(String name,String value);
    private static native void nativeReloadTouch(String file,boolean controller);
    private static native void nativeController(boolean connected);
    private static native String nativeSettingValue(String key);
    private static native boolean nativeBridgeReady();
    private static native void nativeTilt(float value,boolean enabled);
    private static native long nativeFrameCount();
    private static native String nativeAchievements();
    private static native String nativePressedControls();
    private android.widget.TextView pressedLabel;
    private long lastButtonFeedback;
    private final Runnable buttonFeedback=new Runnable(){public void run(){
        if(!gameResumed)return;
        if(pressedLabel!=null){
            boolean enabled=nativeReady&&getSharedPreferences("mobile",0).getBoolean("press_names",true)&&(gameMenu==null||!gameMenu.isShowing());
            if(enabled){String codes=nativePressedControls();List<String> names=new ArrayList<>();for(String code:codes.split("\\|")){int index=Arrays.asList(TouchIcons.NAMES).indexOf(code);if(index>=0)names.add(TouchIcons.TITLES[index]);}
                long now=android.os.SystemClock.elapsedRealtime();if(!names.isEmpty()){String text=String.join(" + ",names);if(!text.contentEquals(pressedLabel.getText()))pressedLabel.setText(text);pressedLabel.setVisibility(View.VISIBLE);lastButtonFeedback=now;}else if(now-lastButtonFeedback>450)pressedLabel.setVisibility(View.GONE);
            }else pressedLabel.setVisibility(View.GONE);
        }
        uiHandler.postDelayed(this,80);
    }};
    private boolean nativeReady,gameResumed;
    private String buildCommit="unknown";
    private final android.os.Handler uiHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final ArrayDeque<DrawerPage> drawerPages=new ArrayDeque<>();
    private android.widget.FrameLayout drawer;
    private android.widget.TextView fpsLabel;
    private MobileDriveAssist driveAssist;
    private android.os.Vibrator phoneVibrator;
    private int rumbleAmplitude;private long lastRumbleMs,fpsTime,fpsFrames;
    private float lastFrameRateHint=-1;
    private final java.util.concurrent.ExecutorService telemetry=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"MobileTelemetry"));
    private static final class DrawerPage{final String title;final android.view.View body;DrawerPage(String title,android.view.View body){this.title=title;this.body=body;}}
    private final Runnable metricsTick=new Runnable(){public void run(){
        if(!gameResumed||!bridgeLoaded||isFinishing())return;
        if(!nativeReady){nativeReady=nativeBridgeReady();if(!nativeReady){uiHandler.postDelayed(this,200);return;}nativeHostMenu(gameMenu!=null&&gameMenu.isShowing());reloadControls();refreshMobilePreferences();captureReportState();}
        long now=android.os.SystemClock.elapsedRealtime(),frames=nativeFrameCount();
        if(fpsTime!=0&&now>fpsTime){float fps=Math.max(0,frames-fpsFrames)*1000f/(now-fpsTime);if(fpsLabel!=null){fpsLabel.setVisibility(getSharedPreferences("mobile",0).getBoolean("show_fps",false)?android.view.View.VISIBLE:android.view.View.GONE);fpsLabel.setText(String.format(Locale.ROOT,"%.1f FPS",fps));}recordPerformance(fps);}
        fpsTime=now;fpsFrames=frames;refreshFrameRateHint();uiHandler.postDelayed(this,1000);
    }};
    private boolean bridgeLoaded;
    private boolean touchHaptics;
    private android.app.AlertDialog gameMenu;
    private android.os.ParcelFileDescriptor gameIso;
    private String launchError;
    private android.hardware.input.InputManager inputManager;
    private final android.hardware.input.InputManager.InputDeviceListener controllers=new android.hardware.input.InputManager.InputDeviceListener(){
        public void onInputDeviceAdded(int id){updateController();}
        public void onInputDeviceRemoved(int id){updateController();}
        public void onInputDeviceChanged(int id){updateController();}
    };
    private static final String TAG = "PinyonShift";
    private static final int REQUEST_GPU_DRIVER_ZIP = 0x5053;
    private File driversDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiPreferences.load(this);
        File external = getExternalFilesDir(null);
        File base = external != null ? external : getFilesDir();
        setDefaultEnvironment("HOME", getFilesDir().getAbsolutePath());
        setDefaultEnvironment("PINYON_SHIFT_STATE_ROOT",
                new File(base, "state").getAbsolutePath());
        setDefaultEnvironment("PINYON_SHIFT_GAME_ROOT",
                new File(base, "game/base").getAbsolutePath());
        String isoUri=getSharedPreferences("mobile",0).getString("iso_uri","");
        if(!isoUri.isEmpty()){
            try{
                gameIso=getContentResolver().openFileDescriptor(Uri.parse(isoUri),"r");
                if(gameIso==null)throw new IOException("The disc image is unavailable.");
                android.system.Os.lseek(gameIso.getFileDescriptor(),0,android.system.OsConstants.SEEK_SET);
                setEnvironment("PINYON_SHIFT_GAME_ISO","/proc/self/fd/"+gameIso.getFd());
            }catch(Exception error){
                if(gameIso!=null)try{gameIso.close();}catch(IOException ignored){}
                launchError="Disc image unavailable. Choose the ISO again in Game files and keep it in local storage while playing.";
            }
        }
        // Custom GPU drivers (Mesa Turnip) the player copied in, chosen with
        // android_gpu_driver; the runtime copies the chosen one inside.
        driversDir = new File(base, "state/drivers");
        setDefaultEnvironment("REX_ANDROID_DRIVERS_DIR", driversDir.getAbsolutePath());
        // Drivers packaged with the game (Mesa Turnip builds known to work),
        // and the one recommended for this GPU, which android_gpu_driver
        // "auto" (the default) loads.
        installBundledDrivers();
        String adreno = adrenoModel();
        if (adreno != null) {
            setDefaultEnvironment("PINYON_SHIFT_ADRENO_MODEL", adreno);
            String recommended = recommendedDriver(adreno);
            if (recommended != null) {
                setDefaultEnvironment("REX_ANDROID_RECOMMENDED_DRIVER", recommended);
            }
        }
        // Rally, once tools/prepare-fh1-rally.py's overlay was copied into
        // state/cache (the PC launcher verifies it before the same flag);
        // the game still checks the enabled package and the stage mapping.
        if (new File(base, "state/cache/rally_adapter/rally-stage.toml").isFile()) {
            setDefaultEnvironment("PINYON_SHIFT_RALLY_PREPARED", "1");
        }
        applyPendingMods(base);
        // The build's provenance, packaged as an asset, for logs and crash
        // reports (the game reads it beside the executable elsewhere).
        File manifest = new File(getFilesDir(), "pinyon_shift_build.json");
        try (InputStream in = getAssets().open("pinyon_shift_build.json");
             OutputStream out = new FileOutputStream(manifest)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = in.read(buffer)) > 0; ) {
                out.write(buffer, 0, read);
            }
            setEnvironment("PINYON_SHIFT_BUILD_MANIFEST", manifest.getAbsolutePath());
        } catch (IOException error) {
            Log.w(TAG, "no build manifest in the package", error);
        }
        try{buildCommit=new org.json.JSONObject(new String(Files.readAllBytes(manifest.toPath()),java.nio.charset.StandardCharsets.UTF_8)).optString("pinyon_shift_commit","unknown");}catch(Exception ignored){}
        Intent intent = getIntent();
        Bundle extras = intent != null ? intent.getExtras() : null;
        if (extras != null) {
            for (String key : extras.keySet()) {
                if (key.startsWith("env.")) {
                    Object value = extras.get(key);
                    if (value != null) {
                        setEnvironment(key.substring(4), value.toString());
                    }
                }
            }
        }
        if(!TouchLayout.file(this).isFile())try{TouchLayout initial=new TouchLayout();initial.modernDefaults(this);initial.save(this);}catch(IOException error){Log.w(TAG,"Could not save the initial touch layout",error);}
        GameSession.record(this,true);
        super.onCreate(savedInstanceState);
        // A race played on a controller touches nothing, and a screen that
        // times out sends the game to the background, where it pauses (a
        // scripted route stalls the same way). Only while this window shows.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(bridgeLoaded){
            addGameMenuButton();
            inputManager=(android.hardware.input.InputManager)getSystemService(INPUT_SERVICE);
            if(inputManager!=null)inputManager.registerInputDeviceListener(controllers,new android.os.Handler(android.os.Looper.getMainLooper()));
            nativeReloadTouch(TouchLayout.file(this).getAbsolutePath(),hasController());
        }
        // Scripted routes started from the PC: over the lock screen, with the
        // screen on, so a device that slept between runs does not hold the
        // game behind the lock. A launch from the home screen never asks.
        if (getIntent().getBooleanExtra("show_when_locked", false)) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
    }

    // SDL may request sensor rotation when it creates the game window.
    @Override public void setOrientationBis(int width,int height,boolean resizable,String hint){
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    // The C++ runtime first, then the ReXGlue runtime, which holds SDL and
    // registers its Java natives when loaded, then the game (libmain.so),
    // whose SDL_main SDL calls.
    @Override
    protected String[] getLibraries() {
        return new String[] {"c++_shared", "rexruntime", "main"};
    }

    // The native libraries are built for ARMv8.2-A (NN-1.11): an older core
    // would crash on the first LSE atomic, possibly in a library's static
    // initializer. SDL shows a link error from here as a dialog instead.
    @Override
    public void loadLibraries() {
        if(launchError!=null)throw new UnsatisfiedLinkError(launchError);
        if (!cpuHasFeatures("atomics", "asimdrdm")) {
            throw new UnsatisfiedLinkError("Pinyon Shift needs an ARMv8.2 CPU "
                    + "(Cortex-A55, Cortex-A75 or newer); this device's CPU is older.");
        }
        super.loadLibraries();
        bridgeLoaded=true;
    }

    // Whether the kernel lists every given feature for the CPU. True when
    // /proc/cpuinfo cannot be read, so an unusual kernel does not lock out a
    // CPU that would run the game.
    private static boolean cpuHasFeatures(String... features) {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            for (String line; (line = reader.readLine()) != null; ) {
                if (!line.startsWith("Features")) continue;
                List<String> listed = java.util.Arrays.asList(line.substring(line.indexOf(':') + 1).trim().split("\\s+"));
                for (String feature : features) {
                    if (!listed.contains(feature)) return false;
                }
                return true;
            }
        } catch (IOException ignored) {
            // Unreadable: let the libraries load.
        }
        return true;
    }

    // The intent's args, then the lines of state/config/android-arguments.txt,
    // stored locally (including 1000 Club flags for developer v4 builds).
    @Override
    protected String[] getArguments() {
        Intent intent = getIntent();
        String[] given = intent != null ? intent.getStringArrayExtra("args") : null;
        List<String> arguments = new ArrayList<>();
        if (given != null) Collections.addAll(arguments, given);
        File external = getExternalFilesDir(null);
        File file = new File(external != null ? external : getFilesDir(), SetupActivity.ARGUMENTS_FILE);
        if (file.isFile()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                for (String line; (line = reader.readLine()) != null; ) {
                    if (line.trim().startsWith("--")) arguments.add(line.trim());
                }
            } catch (IOException error) {
                Log.w(TAG, "launch arguments not read", error);
            }
        }
        String verified=getIntent().getStringExtra("verified_driver");
        if(verified!=null)arguments.add("--android_gpu_driver="+verified);
        if(getIntent().getBooleanExtra("debug_report",false)){
            arguments.add("--pinyon_shift_capture_performance=true");
            arguments.add("--fh1_native_gpu_profile="+getSharedPreferences("mobile",0).getBoolean("detailed_gpu",false));
            arguments.add("--perf_log_max_mb=16");
        }else{arguments.add("--pinyon_shift_capture_performance=false");arguments.add("--fh1_native_gpu_profile=false");}
        arguments.add("--android_gpu_turbo="+MobileConfig.get(this,"android_gpu_turbo","false"));
        arguments.add("--android_gpu_driver_no_preamble="+MobileConfig.get(this,"android_gpu_driver_no_preamble","true"));
        arguments.add("--android_gpu_driver_env="+MobileConfig.get(this,"android_gpu_driver_env",""));
        recordLaunchSettings(arguments);
        return arguments.toArray(new String[0]);
    }

    private boolean hasController(){
        for(int id:android.view.InputDevice.getDeviceIds()){android.view.InputDevice device=android.view.InputDevice.getDevice(id);
            if(device!=null&&!device.isVirtual()&&((device.getSources()&android.view.InputDevice.SOURCE_GAMEPAD)==android.view.InputDevice.SOURCE_GAMEPAD
                ||(device.getSources()&android.view.InputDevice.SOURCE_JOYSTICK)==android.view.InputDevice.SOURCE_JOYSTICK))return true;}
        return false;
    }
    private void updateController(){if(bridgeLoaded)nativeController(hasController());}
    private void addGameMenuButton(){
        android.widget.Button menu=FestivalUi.button(this,"Menu",false);menu.setContentDescription("Open FH4Droid game menu");
        android.widget.RelativeLayout.LayoutParams params=new android.widget.RelativeLayout.LayoutParams(-2,-2);params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);params.setMargins(0,FestivalUi.dp(this,8),FestivalUi.dp(this,12),0);mLayout.addView(menu,params);menu.setOnClickListener(v->showGameMenu());
        fpsLabel=FestivalUi.text(this,"— FPS",13,android.graphics.Color.WHITE);fpsLabel.setVisibility(android.view.View.GONE);android.widget.RelativeLayout.LayoutParams fps=new android.widget.RelativeLayout.LayoutParams(-2,-2);fps.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT);fps.setMargins(FestivalUi.dp(this,12),FestivalUi.dp(this,8),0,0);mLayout.addView(fpsLabel,fps);
        pressedLabel=FestivalUi.text(this,"",13,android.graphics.Color.WHITE);pressedLabel.setTextSize(13);pressedLabel.setPadding(FestivalUi.dp(this,10),FestivalUi.dp(this,3),FestivalUi.dp(this,10),FestivalUi.dp(this,3));pressedLabel.setBackground(FestivalUi.surface(this,0xbb101419,8));pressedLabel.setVisibility(View.GONE);pressedLabel.setMaxLines(2);pressedLabel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        android.widget.RelativeLayout.LayoutParams feedback=new android.widget.RelativeLayout.LayoutParams(-2,-2);feedback.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);feedback.setMargins(0,FestivalUi.dp(this,8),0,0);mLayout.addView(pressedLabel,feedback);
        android.os.VibratorManager manager=getSystemService(android.os.VibratorManager.class);phoneVibrator=manager==null?null:manager.getDefaultVibrator();
    }
    private MobileSettings.Runtime settingsRuntime(){return new MobileSettings.Runtime(){
        public void changed(String key,String value){if(!nativeReady)return;nativeApplySetting(key,value);if(key.equals("pinyon_shift_fh1_render_fps_limit")&&driveAssist!=null)driveAssist.applyTarget();refreshFrameRateHint();captureReportState();}
        public String value(String key){return nativeReady?nativeSettingValue(key):null;}
    };}
    void reloadControls(){TouchLayout layout=new TouchLayout();layout.load(this);touchHaptics=layout.haptics;if(bridgeLoaded)nativeReloadTouch(TouchLayout.file(this).getAbsolutePath(),hasController());}
    void refreshMobilePreferences(){
        if(!nativeReady)return;
        if(driveAssist==null)driveAssist=new MobileDriveAssist(this,value->nativeTilt(value,driveAssist!=null&&driveAssist.tiltEnabled()),target->{nativeApplySetting("pinyon_shift_fh1_render_fps_limit",Integer.toString(target));refreshFrameRateHint();});
        if(gameResumed)driveAssist.resume();else driveAssist.reload();
        if(!getSharedPreferences("mobile",0).getBoolean("phone_rumble",true))stopPhoneRumble();
    }
    void calibrateTilt(){if(driveAssist==null||!driveAssist.calibrate())FestivalUi.explain(this,"Tilt calibration","Enable phone tilt while playing, then hold the phone in your normal driving position.");else toast("Tilt centre calibrated.");}
    void resumeDriving(){if(gameMenu!=null)gameMenu.dismiss();}
    void openNativeSettings(){resumeDriving();if(nativeReady)nativeOpenSettings();}
    private void showGameMenu(){
        if(!nativeReady){toast("The game is still starting.");return;}if(isFinishing()||gameMenu!=null&&gameMenu.isShowing())return;
        nativeHostMenu(true);stopPhoneRumble();drawerPages.clear();drawer=new android.widget.FrameLayout(this);
        gameMenu=new android.app.AlertDialog.Builder(this).setView(drawer).create();gameMenu.setCanceledOnTouchOutside(true);
        gameMenu.setOnKeyListener((d,key,event)->{if(key!=android.view.KeyEvent.KEYCODE_BACK)return false;if(event.getAction()==android.view.KeyEvent.ACTION_UP)backPanel();return true;});
        gameMenu.setOnDismissListener(d->{drawerPages.clear();if(bridgeLoaded)nativeHostMenu(!gameResumed);FestivalUi.immersive(this);});
        gameMenu.setOnShowListener(d->{android.view.Window w=gameMenu.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(android.view.Gravity.RIGHT|android.view.Gravity.TOP);w.setDimAmount(.22f);boolean wide=getResources().getConfiguration().screenWidthDp>=650&&getResources().getConfiguration().fontScale<1.5f;w.setLayout(wide?Math.min(FestivalUi.dp(this,560),(int)(getResources().getDisplayMetrics().widthPixels*.58f)):(int)(getResources().getDisplayMetrics().widthPixels*.96f),-1);}FestivalUi.slideIn(drawer);});
        android.widget.LinearLayout body=FestivalUi.column(this);MobileSettings.Runtime runtime=settingsRuntime();
        Runnable editor=()->{resumeDriving();startActivity(new Intent(this,TouchEditorActivity.class).putExtra("return_to_game",true));};
        Button resume=FestivalUi.button(this,"Resume",true);body.addView(resume);resume.setOnClickListener(v->resumeDriving());
        FestivalUi.action(this,body,"Settings",()->SettingsHub.show(this,runtime,editor,()->{}));
        FestivalUi.action(this,body,"Controls",()->SettingsHub.page(this,"Controls",runtime,editor));
        FestivalUi.action(this,body,"More",()->{
            android.widget.LinearLayout more=FestivalUi.column(this);
            FestivalUi.action(this,more,"Achievements",()->AchievementsPage.show(this,nativeAchievements()));
            FestivalUi.action(this,more,"Trainer · separate profile",()->TrainerPage.show(this,runtime));
            ProblemReports.action(this,more,"Report a problem");FestivalUi.action(this,more,"Advanced settings",()->SettingsHub.page(this,"Advanced",runtime,editor));showPanel("More",more);
        });
        FestivalUi.action(this,body,"Quit to launcher",()->new android.app.AlertDialog.Builder(this).setTitle("Quit the game?").setMessage("Wait for the game's save indicator to finish before quitting.").setNegativeButton("Keep playing",null).setPositiveButton("Quit",(d,w)->{recordCleanExit();resumeDriving();finish();}).show());
        showPanel("Paused",body);gameMenu.show();
    }
    @Override public void showPanel(String title,android.view.View body){if(drawer!=null)FestivalUi.rememberTree(drawer);drawerPages.push(new DrawerPage(title,body));renderDrawer();}
    @Override public void replacePanel(String title,android.view.View body){if(!drawerPages.isEmpty())drawerPages.pop();showPanel(title,body);}
    private void renderDrawer(){if(drawer==null||drawerPages.isEmpty())return;FestivalUi.rememberTree(drawer);drawer.removeAllViews();DrawerPage page=drawerPages.peek();android.view.View panel=FestivalUi.panel(this,page.title,page.body,this::backPanel);drawer.addView(panel,new android.widget.FrameLayout.LayoutParams(-1,-1));FestivalUi.slideIn(panel);}
    @Override public void backPanel(){if(drawerPages.size()>1){drawerPages.pop();renderDrawer();}else resumeDriving();}
    @Override public void onBackPressed(){if(gameMenu!=null&&gameMenu.isShowing())backPanel();else showGameMenu();}
    public void gameRumble(int left,int right){uiHandler.post(()->{
        if(phoneVibrator==null||!phoneVibrator.hasVibrator()||!gameResumed||gameMenu!=null&&gameMenu.isShowing()||!getSharedPreferences("mobile",0).getBoolean("phone_rumble",true)){stopPhoneRumble();return;}
        int strength=Math.max(0,Math.min(100,getSharedPreferences("mobile",0).getInt("phone_rumble_strength",60)));
        int amplitude=(int)Math.min(255,(Math.min(65535,Math.max(0,left))*.65+Math.min(65535,Math.max(0,right))*.35)*strength*255/6553500);
        if(amplitude==0){stopPhoneRumble();return;}long now=android.os.SystemClock.elapsedRealtime();
        if(Math.abs(amplitude-rumbleAmplitude)>=8||now-lastRumbleMs>=60){phoneVibrator.vibrate(android.os.VibrationEffect.createOneShot(120,phoneVibrator.hasAmplitudeControl()?Math.max(1,amplitude):android.os.VibrationEffect.DEFAULT_AMPLITUDE));rumbleAmplitude=amplitude;lastRumbleMs=now;}
    });}
    private void stopPhoneRumble(){if(phoneVibrator!=null)phoneVibrator.cancel();rumbleAmplitude=0;}
    void refreshFrameRateHint(){
        if(!nativeReady||mSurface==null||!mSurface.getHolder().getSurface().isValid())return;
        float target=0;try{if(getSharedPreferences("mobile",0).getBoolean("match_refresh",true))target=Float.parseFloat(nativeSettingValue("pinyon_shift_fh1_render_fps_limit"));}catch(Exception ignored){}
        if(!Float.isFinite(target))target=0;target=Math.max(0,Math.min(240,target));if(target==lastFrameRateHint)return;
        try{mSurface.getHolder().getSurface().setFrameRate(target,android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,android.view.Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);lastFrameRateHint=target;}catch(IllegalArgumentException|IllegalStateException ignored){}
    }
    private void recordLaunchSettings(List<String> args){try{org.json.JSONObject data=new org.json.JSONObject();data.put("captured_ms",System.currentTimeMillis());data.put("source_commit",buildCommit);data.put("settings",new org.json.JSONObject(MobileConfig.read(this)));data.put("arguments",new org.json.JSONArray(args));writeReportJson("android-launch-settings.json",data);}catch(Exception e){Log.w(TAG,"Could not record launch settings",e);}}
    void captureReportState(){
        if(!nativeReady)return;
        try{org.json.JSONObject live=new org.json.JSONObject();for(MobileSettings.Option option:MobileSettings.OPTIONS){String value=nativeSettingValue(option.key);if(value!=null)live.put(option.key,value);}
            for(String key:new String[]{"fh1_render_scale_percent","present_letterbox","pinyon_shift_hor_plus","pinyon_shift_fov_scale","fh1_msaa_single_sample","fh1_msaa_2x","pinyon_shift_cheats","android_gpu_turbo","android_gpu_driver_no_preamble","android_gpu_driver_env"}){String value=nativeSettingValue(key);if(value!=null)live.put(key,value);}
            org.json.JSONObject data=new org.json.JSONObject();data.put("captured_ms",System.currentTimeMillis());data.put("source_commit",buildCommit);data.put("session",currentSession());data.put("live",live);data.put("surface_frame_rate_hint",lastFrameRateHint);writeReportJson("android-live-settings.json",data);
        }catch(Exception error){Log.w(TAG,"Could not snapshot live settings",error);}
    }
    private String currentSession(){File[] files=new File(MobileConfig.base(this),"state/logs").listFiles(f->f.getName().endsWith("-p"+android.os.Process.myPid()+".jsonl"));if(files==null||files.length==0)return "starting-p"+android.os.Process.myPid();Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));return files[0].getName().replaceFirst("\\.jsonl$","");}
    private void writeReportJson(String name,org.json.JSONObject data)throws Exception{File file=new File(MobileConfig.base(this),"state/logs/"+name);Files.createDirectories(file.getParentFile().toPath());java.nio.file.Path stage=Files.createTempFile(file.getParentFile().toPath(),"snapshot-",".part");try{Files.write(stage,data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));Files.move(stage,file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(stage);}}
    private void recordPerformance(float fps){
        android.os.PowerManager power=getSystemService(android.os.PowerManager.class);final int thermal=power==null?-1:power.getCurrentThermalStatus();final boolean menu=gameMenu!=null&&gameMenu.isShowing();final String session=currentSession();
        try{telemetry.execute(()->{try{org.json.JSONObject event=new org.json.JSONObject();event.put("time_ms",System.currentTimeMillis());event.put("session",session);event.put("source_commit",buildCommit);event.put("fps",fps);event.put("thermal_severity",thermal);event.put("thermal_status",ReportFiles.thermalLabel(thermal));event.put("host_menu",menu);File file=new File(MobileConfig.base(this),"state/logs/android-performance.jsonl");if(file.length()>262144)Files.move(file.toPath(),new File(file.getParentFile(),"android-performance-previous.jsonl").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);try(OutputStream out=new FileOutputStream(file,true)){out.write((event.toString()+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));}}catch(Exception error){Log.w(TAG,"Could not record performance sample",error);}});}catch(java.util.concurrent.RejectedExecutionException ignored){}
    }
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){
        int action=event.getActionMasked();if(touchHaptics&&bridgeLoaded&&(gameMenu==null||!gameMenu.isShowing())&&(action==android.view.MotionEvent.ACTION_DOWN||action==android.view.MotionEvent.ACTION_POINTER_DOWN))
            getWindow().getDecorView().performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        return super.dispatchTouchEvent(event);
    }
    @Override protected void onPause(){gameResumed=false;if(driveAssist!=null)driveAssist.pause();if(bridgeLoaded)nativeHostMenu(true);uiHandler.removeCallbacksAndMessages(null);if(pressedLabel!=null)pressedLabel.setVisibility(View.GONE);stopPhoneRumble();fpsTime=0;lastFrameRateHint=-1;super.onPause();}
    @Override protected void onResume(){UiPreferences.load(this);super.onResume();gameResumed=true;reloadControls();if(bridgeLoaded){nativeHostMenu(gameMenu!=null&&gameMenu.isShowing());FestivalUi.immersive(this);uiHandler.removeCallbacks(metricsTick);uiHandler.post(metricsTick);uiHandler.removeCallbacks(buttonFeedback);uiHandler.post(buttonFeedback);if(nativeReady)refreshMobilePreferences();}}
    @Override protected void onDestroy(){
        gameResumed=false;if(driveAssist!=null)driveAssist.pause();uiHandler.removeCallbacksAndMessages(null);stopPhoneRumble();telemetry.shutdown();
        if(inputManager!=null)inputManager.unregisterInputDeviceListener(controllers);if(gameMenu!=null)gameMenu.dismiss();
        if(isFinishing())recordCleanExit();super.onDestroy();
        if(gameIso!=null)try{gameIso.close();}catch(IOException ignored){}
        android.os.Process.killProcess(android.os.Process.myPid());
    }
    public void recordCleanExit(){GameSession.record(this,false);}

    // A pending enabled_mods list from an earlier installation, before the game had
    // written its settings file; the game reads the list at start.
    private static void applyPendingMods(File base) {
        File pending = new File(base, SetupActivity.PENDING_MODS_FILE);
        File config = new File(base, SetupActivity.CONFIG_FILE);
        if (!pending.isFile() || !config.isFile()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(pending))) {
            String mods = reader.readLine();
            SetupActivity.applyEnabledMods(config, mods != null ? mods.trim() : "");
            if (!pending.delete()) Log.w(TAG, "pending mods not removed");
        } catch (IOException error) {
            Log.w(TAG, "pending mods not applied", error);
        }
    }

    // The Adreno GPU's model number ("740"), from the kernel driver, or from
    // the SoC where that is not readable; null when not an Adreno.
    private static String adrenoModel() {
        try (BufferedReader reader = new BufferedReader(
                new FileReader("/sys/class/kgsl/kgsl-3d0/gpu_model"))) {
            String line = reader.readLine();
            Matcher match = line != null ? Pattern.compile("Adreno(\\d{3})").matcher(line) : null;
            if (match != null && match.find()) {
                return match.group(1);
            }
        } catch (IOException ignored) {
            // Not readable here: the SoC below.
        }
        if (Build.VERSION.SDK_INT >= 31) {
            switch (Build.SOC_MODEL) {
                case "SM8450": case "SM8475": return "730";
                case "SM8550": case "QCS8550": return "740";
                case "SM8635": return "735";
                case "SM8650": return "750";
                case "SM7475": return "725";
                case "SM7550": return "720";
                case "SM7675": return "732";
                case "SM8750": return "830";
                default: break;
            }
        }
        return null;
    }

    // Adreno 7xx and 8xx: Mesa Turnip Gen8 V37 (measured on the Adreno 740
    // and 830 against the system driver, and on the 740 against newer Turnip
    // builds). Others keep the system's.
    private static String recommendedDriver(String adreno) {
        return adreno.startsWith("7") || adreno.startsWith("8") ? "turnip-gen8-v37" : null;
    }

    // Copies each driver folder under the package's assets/drivers into
    // state/drivers, where a file is missing or differs in size.
    private void installBundledDrivers() {
        try{DriverStore.installBundled(this);}catch(IOException error){Log.w(TAG,"bundled GPU drivers not installed",error);}
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[65536];
        for (int read; (read = in.read(buffer)) > 0; ) {
            out.write(buffer, 0, read);
        }
    }

    // SETTINGS > GRAPHICS > IMPORT DRIVER (.ZIP): the system document picker,
    // then the chosen adrenotools package unpacked into state/drivers.
    // Called from the game's native thread.
    public void importGpuDriver() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"application/zip",
                        "application/x-zip-compressed", "application/octet-stream"});
                try {
                    startActivityForResult(intent, REQUEST_GPU_DRIVER_ZIP);
                } catch (Exception error) {
                    Log.e(TAG, "no document picker", error);
                    toast("No file picker available on this device");
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_GPU_DRIVER_ZIP) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String result;
                try {
                    result = "GPU driver " + unpackDriver(uri)
                            + " imported: choose it in SETTINGS > GRAPHICS > GPU DRIVER and restart";
                } catch (IOException error) {
                    Log.e(TAG, "GPU driver import failed", error);
                    result = "GPU driver not imported: " + error.getMessage();
                }
                toast(result);
            }
        }, "GpuDriverImport").start();
    }

    // Unpacks an adrenotools driver zip (meta.json and the driver .so, the
    // files at any depth) into state/drivers/<the zip's name>.
    private String unpackDriver(Uri uri) throws IOException {
        String display = "driver";
        try (Cursor cursor = getContentResolver().query(uri,
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                display = cursor.getString(0);
            }
        }
        String name = display.replaceAll("(?i)\\.zip$", "")
                .replaceAll("[^A-Za-z0-9._-]+", "-").toLowerCase(Locale.ROOT);
        if (name.isEmpty() || name.startsWith(".")) name = "driver";
        File folder = new File(driversDir, name);
        File staging = new File(driversDir, "." + name + ".importing");
        deleteTree(staging);
        staging.mkdirs();
        boolean library = false;
        try (InputStream in = getContentResolver().openInputStream(uri);
             ZipInputStream zip = new ZipInputStream(in)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (entry.isDirectory()) continue;
                String file = new File(entry.getName()).getName();
                if (file.isEmpty() || file.startsWith(".")) continue;
                library |= file.endsWith(".so");
                try (OutputStream out = new FileOutputStream(new File(staging, file))) {
                    copy(zip, out);
                }
            }
        }
        if (!library) {
            deleteTree(staging);
            throw new IOException("the zip holds no driver library (.so)");
        }
        deleteTree(folder);
        if (!staging.renameTo(folder)) {
            throw new IOException("cannot create " + folder);
        }
        return name;
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    private void toast(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(PinyonShiftActivity.this, text, Toast.LENGTH_LONG).show();
            }
        });
    }

    private static void setDefaultEnvironment(String name, String value) {
        if (Os.getenv(name) == null) {
            setEnvironment(name, value);
        }
    }

    private static void setEnvironment(String name, String value) {
        try {
            Os.setenv(name, value, true);
        } catch (ErrnoException error) {
            Log.e(TAG, "setenv " + name + " failed", error);
        }
    }
}
