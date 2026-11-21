package studio.deimos.pinyonshift;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.view.Display;
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
    static final String NO_SESSION_LOGS = "no_session_logs";
    private static native void nativeHostMenu(boolean active);
    private static native void nativeOpenSettings();
    private static native void nativeAction(String action);
    private static native void nativeApplySetting(String name,String value);
    private static native void nativeReloadTouch(String file,boolean controller);
    private static native void nativeController(boolean connected);
    private static native String nativeSettingValue(String key);
    private static native boolean nativeBridgeReady();
    private static native void nativeTilt(float value,boolean enabled);
    private static native long nativeFrameCount();
    private static native long[] nativePerformance();
    private static native void nativeCpuHintTarget(int fps);
    private static native long[] nativeFrameHistory();
    private static native boolean nativeAudioOutputActive();
    private static native long[] nativePreparation();
    private static native int nativeCoarseShadingSupport();
    private static native String nativeAchievements();
    private static native String nativePressedControls();
    private static native boolean nativeMenuContext();
    private boolean nativeReady,gameResumed;
    private boolean closing;
    private android.app.AlertDialog closingDialog;
    private String buildCommit="unknown";
    private final android.os.Handler uiHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final ArrayDeque<DrawerPage> drawerPages=new ArrayDeque<>();
    private android.widget.FrameLayout drawer;
    private android.widget.TextView fpsLabel;
    private android.widget.TextView preparationLabel;
    private android.widget.LinearLayout preparationPanel;
    private android.widget.ProgressBar preparationProgress;
    private boolean startupPreparationFailed;
    private boolean updateShaderPreparation() {
        if (preparationPanel == null) return false;
        long[] progress = nativePreparation();
        if (progress == null || progress.length < 5) return false;
        long phase = progress[4];
        if (phase >= 3 && !startupPreparationFailed) {
            startupPreparationFailed = true;
            boolean cacheFailure = phase == 3;
            new AlertDialog.Builder(this).setTitle(cacheFailure ? "Graphics cache could not be saved" : "Shader preparation failed")
                .setMessage(cacheFailure
                    ? "Check free storage and open Game files → Storage → Graphics cache to clear the cache before retrying."
                    : "Open More → Graphics drivers to try the system driver, or share a problem report if the failure continues.")
                .setCancelable(false).setPositiveButton("Return to launcher", (dialog, which) -> finish()).show();
        }
        boolean preparing = phase < 2 || phase == 2 && progress[1] > progress[0] && progress[3] == 0;
        preparationPanel.setVisibility(preparing ? View.VISIBLE : View.GONE);
        if (preparing) {
            long stage = progress.length > 5 ? progress[5] : 0;
            String message = stage == 1 ? "Reading graphics caches" : stage == 2 ? "Preparing shaders"
                : stage == 3 ? "Building graphics pipelines" : stage == 4 ? "Saving the graphics cache"
                : "Starting graphics";
            boolean countable = stage == 3 && progress[1] > 0;
            preparationProgress.setIndeterminate(!countable);
            if (countable) {
                message += " · " + progress[0] + " / " + progress[1];
                preparationProgress.setProgress((int)Math.min(1000,progress[0] * 1000 / progress[1]));
            }
            if (!message.contentEquals(preparationLabel.getText())) preparationLabel.setText(message);
        }
        return preparing;
    }
    private MobileDriveAssist driveAssist;
    private android.os.Vibrator phoneVibrator;
    private int rumbleAmplitude;private long lastRumbleMs,fpsTime,fpsFrames;
    private float lastFrameRateHint = -1, lastDisplayRefresh = -1;
    private float requestedRefreshHz = -1;
    private boolean lastBatterySaver;
    private long nextFrameRateRequest;
    private android.view.Surface hintedSurface;
    private final android.view.SurfaceHolder.Callback pacingSurface = new android.view.SurfaceHolder.Callback() {
        public void surfaceCreated(android.view.SurfaceHolder holder) { invalidateFrameRateHint(); }
        public void surfaceChanged(android.view.SurfaceHolder holder, int format, int width, int height) { invalidateFrameRateHint(); }
        public void surfaceDestroyed(android.view.SurfaceHolder holder) {
            hintedSurface = null;
            lastFrameRateHint = -1;
            requestedRefreshHz = -1;
        }
    };
    private android.hardware.display.DisplayManager displays;
    private final android.hardware.display.DisplayManager.DisplayListener displayListener =
        new android.hardware.display.DisplayManager.DisplayListener() {
            public void onDisplayAdded(int id) {}
            public void onDisplayRemoved(int id) {}
            public void onDisplayChanged(int id) {
                Display display = mSurface == null ? null : mSurface.getDisplay();
                if (display != null && display.getDisplayId() == id) refreshFrameRateHint();
            }
        };
    private long lastReportCapture;private boolean lastReportedMenu;
    private boolean driverGameplayRecorded;
    private boolean framePacingEnabled;
    private long lastPerformanceRecord;
    private long overlayGeneration;
    private int cpuHintTarget;
    private final java.util.concurrent.atomic.AtomicBoolean samplePending = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile String cachedSession;
    private volatile long sessionLookupTime;
    private final java.util.concurrent.ExecutorService telemetry=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"MobileTelemetry"));
    private static final class DrawerPage{final String title;final android.view.View body;DrawerPage(String title,android.view.View body){this.title=title;this.body=body;}}
    private final Runnable metricsTick=new Runnable(){public void run(){
        if(!gameResumed||!bridgeLoaded||isFinishing())return;
        boolean preparing = updateShaderPreparation();
        if(!nativeReady){nativeReady=nativeBridgeReady();if(!nativeReady){uiHandler.postDelayed(this,200);return;}nativeHostMenu(closing||gameMenu!=null&&gameMenu.isShowing());reloadControls();refreshMobilePreferences();captureReportState();}
        android.content.SharedPreferences preferences = getSharedPreferences("mobile", 0);
        boolean overlay = fpsLabel != null && preferences.getBoolean("show_fps", false);
        boolean diagnostic = sessionLoggingEnabled() && getIntent().getBooleanExtra("debug_report", false);
        long now = android.os.SystemClock.elapsedRealtime(), frames = nativeFrameCount();
        if (!driverGameplayRecorded && frames >= 300 && !nativeMenuContext() && comparisonState == null) {
            driverGameplayRecorded = true;
            telemetry.execute(() -> DriverRecovery.recordGameplay(PinyonShiftActivity.this));
        }
        if (fpsTime != 0 && now > fpsTime) {
            float fps = Math.max(0, frames - fpsFrames) * 1000f / (now - fpsTime);
            samplePerformance(fps, overlay, diagnostic);
        }
        if (!overlay && fpsLabel != null && fpsLabel.getVisibility() != View.GONE) fpsLabel.setVisibility(View.GONE);
        if(diagnostic&&(now-lastReportCapture>=5000||lastReportedMenu!=nativeMenuContext())){captureReportState();lastReportCapture=now;}
        fpsTime=now;fpsFrames=frames;refreshFrameRateHint();
        uiHandler.postDelayed(this, preparing ? 250 : overlay || diagnostic ? 1000 : 5000);
    }};
    private void updateFpsLabel(android.content.SharedPreferences preferences, float fps, long[] sample, int thermal) {
        boolean frameTime = preferences.getBoolean("show_frame_time", false);
        boolean memory = preferences.getBoolean("show_memory_heat", false);
        String text = String.format(Locale.ROOT, "%.1f FPS", fps);
        if (frameTime && sample != null && sample.length > 0 && sample[0] > 0) {
            text += String.format(Locale.ROOT, " · %.1f ms", sample[0] / 1000.0);
        }
        if (memory) {
            text += "\n" + (sample != null && sample.length > 1 ? sample[1] + " MB RAM · " : "")
                + ReportFiles.thermalLabel(thermal);
        }
        if (!text.contentEquals(fpsLabel.getText())) fpsLabel.setText(text);
        if (fpsLabel.getVisibility() != View.VISIBLE) fpsLabel.setVisibility(View.VISIBLE);
    }
    private boolean bridgeLoaded;
    private boolean touchHaptics;
    private Dialog gameMenu;
    private android.os.ParcelFileDescriptor gameIso;
    private String launchError;
    private File comparisonState;
    private String cpuBaseline="armv8.2-a";private boolean validationBuild;
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
        String trial=getIntent()==null?null:getIntent().getStringExtra("comparison_id");
        if (trial!=null) {
            try {
                File parent=new File(base,"mobile-comparisons").getCanonicalFile();
                File wanted=new File(parent,trial);
                if (!trial.matches("[a-f0-9]{32}") || !wanted.equals(wanted.getCanonicalFile())
                    || !new File(wanted,"config/pinyon_shift.toml").isFile())
                    throw new IOException("Prepare a private benchmark state before starting the route.");
                comparisonState=wanted;
            } catch (IOException | SecurityException error) { launchError=error.getMessage(); }
        } else {
            try{ContentFiles.recoverForLaunch(this);}catch(Exception error){launchError="Content recovery could not finish: "+error.getMessage();}
        }
        setDefaultEnvironment("HOME", getFilesDir().getAbsolutePath());
        setDefaultEnvironment("PINYON_SHIFT_STATE_ROOT",
                (comparisonState==null?new File(base,"state"):comparisonState).getAbsolutePath());
        try {
            setDefaultEnvironment("PINYON_SHIFT_GAME_ROOT", GameFiles.root(this).getAbsolutePath());
        } catch (IOException | SecurityException error) {
            launchError = error.getMessage();
        }
        String isoUri=getSharedPreferences("mobile",0).getString("iso_uri","");
        if(!isoUri.isEmpty()){
            try{
                gameIso=getContentResolver().openFileDescriptor(Uri.parse(isoUri),"r");
                if(gameIso==null)throw new IOException("The disc image is unavailable.");
                android.system.Os.lseek(gameIso.getFileDescriptor(),0,android.system.OsConstants.SEEK_SET);
                setEnvironment("PINYON_SHIFT_GAME_ISO_FD",Integer.toString(gameIso.getFd()));
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
        if(launchError==null && comparisonState==null)applyPendingMods(base);
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
        try{org.json.JSONObject provenance=new org.json.JSONObject(new String(Files.readAllBytes(manifest.toPath()),java.nio.charset.StandardCharsets.UTF_8));buildCommit=provenance.optString("pinyon_shift_commit","unknown");cpuBaseline=provenance.optString("cpu_baseline","armv8.2-a");validationBuild=provenance.optBoolean("validation_layer",false);}catch(Exception ignored){}
        Intent intent = getIntent();
        Bundle extras = intent != null ? intent.getExtras() : null;
        if (extras != null) {
            for (String key : extras.keySet()) {
                if (key.startsWith("env.")) {
                    if(comparisonState!=null && key.equals("env.PINYON_SHIFT_STATE_ROOT"))continue;
                    Object value = extras.get(key);
                    if (value != null) {
                        setEnvironment(key.substring(4), value.toString());
                    }
                }
            }
        }
        setEnvironment("PINYON_SHIFT_ANDROID_NO_SESSION_LOGS",sessionLoggingEnabled()?"0":"1");
        setEnvironment("FH4DROID_ANDROID_SKIP_MODS",getIntent().getBooleanExtra("skip_mods",false)?"1":"0");
        recordDisplayEnvironment();
        if (trial==null) {
            if(!TouchLayout.file(this).isFile())try{TouchLayout initial=new TouchLayout();initial.modernDefaults(this);initial.save(this);}catch(IOException error){Log.w(TAG,"Could not save the initial touch layout",error);}
            GameSession.record(this,true,sessionLoggingEnabled());
        }
        super.onCreate(savedInstanceState);
        // A race played on a controller touches nothing, and a screen that
        // times out sends the game to the background, where it pauses (a
        // scripted route stalls the same way). Only while this window shows.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(bridgeLoaded){
            addGameMenuButton();
            if (mSurface != null) mSurface.getHolder().addCallback(pacingSurface);
            displays = getSystemService(android.hardware.display.DisplayManager.class);
            if (displays != null) displays.registerDisplayListener(displayListener, uiHandler);
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

    // Native logging survives ROMs which silence logcat. This is a launch
    // snapshot, not a measurement of the panel's HDR output.
    private void recordDisplayEnvironment() {
        Display display = getDisplay();
        if (display == null) return;
        Display.Mode mode = display.getMode();
        setEnvironment("REX_ANDROID_DISPLAY_DIAGNOSTICS", String.format(Locale.ROOT,
                "%s / Android %s; display=%d mode=%d %dx%d@%.2f rotation=%d; "
                        + "brightness=%d brightness_mode=%d window_brightness=%.3f color_mode=%d",
                Build.MODEL, Build.VERSION.RELEASE, display.getDisplayId(), mode.getModeId(),
                mode.getPhysicalWidth(), mode.getPhysicalHeight(), mode.getRefreshRate(),
                display.getRotation(),
                Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, -1),
                Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, -1),
                getWindow().getAttributes().screenBrightness, getWindow().getColorMode()));
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
        if (!cpuBaseline.equals("armv8-a") && !cpuHasFeatures("atomics", "asimdrdm")) {
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

    // The save sync folder in the app's media folder on internal storage,
    // made when missing; null when there is none.
    @SuppressWarnings("deprecation")
    private File syncFolder() {
        File[] media = getExternalMediaDirs();
        if (media == null || media.length == 0 || media[0] == null) return null;
        File sync = new File(media[0], "sync");
        if (!sync.isDirectory() && !sync.mkdirs() && !sync.isDirectory()) {
            Log.w(TAG, "Cannot create the save sync folder " + sync);
            return null;
        }
        return sync;
    }

    // The intent's args, then the lines of state/config/android-arguments.txt,
    // stored locally (including 1000 Club flags for developer v4 builds).
    @Override
    protected String[] getArguments() {
        Intent intent = getIntent();
        String[] given = intent != null ? intent.getStringArrayExtra("args") : null;
        List<String> arguments = new ArrayList<>();
        // Save sync (docs/SAVE_SYNC.md): Android/media/<package>/sync, unlike
        // the files folder under Android/data, is open to a sync app with
        // all-files access (Syncthing-Fork), so pointing one at it shares the
        // save. The launcher only selects that folder after the player enables sync.
        // Set it first so the intent's args or a --pinyon_shift_sync_folder=
        // line in the arguments file override it (an empty value turns sync
        // off); scripted routes turn it off in the game.
        if (comparisonState==null && getSharedPreferences("mobile", 0).getBoolean("save_folder_sync", false)) {
            File sync = syncFolder();
            if (sync != null) {
                arguments.add("--pinyon_shift_sync_folder=" + sync.getAbsolutePath());
                arguments.add("--pinyon_shift_device_name=" + Build.MODEL);
            }
        }
        if (given != null) Collections.addAll(arguments, given);
        File external = getExternalFilesDir(null);
        File file = new File(external != null ? external : getFilesDir(), SetupActivity.ARGUMENTS_FILE);
        if (comparisonState==null && file.isFile()) {
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
        if(!sessionLoggingEnabled()){
            arguments.add("--log_level=off");
            arguments.add("--perf_log_csv=");
            arguments.add("--perf_thread_cpu=");
            arguments.add("--perf_critical_path_trace=false");
            arguments.add("--pinyon_shift_capture_performance=false");
            arguments.add("--fh1_native_gpu_profile=false");
            arguments.add("--android_audio_bank_diagnostics=false");
        }else if(comparisonState!=null){
            arguments.add("--pinyon_shift_capture_performance=true");
            arguments.add("--android_audio_bank_diagnostics=true");
            arguments.add("--perf_log_max_mb=64");
            arguments.add("--pinyon_shift_sync_folder=");
            arguments.add("--pinyon_shift_skip_opening_movies=true");
            arguments.add("--pinyon_shift_repair_car_cards=false");
        }else if(getIntent().getBooleanExtra("debug_report",false)){
            arguments.add("--pinyon_shift_capture_performance=true");
            arguments.add("--fh1_native_gpu_profile="+getSharedPreferences("mobile",0).getBoolean("detailed_gpu",false));
            arguments.add("--perf_log_max_mb="+(getSharedPreferences("mobile",0).getBoolean("extended_capture",false)?64:16));
            arguments.add("--perf_thread_cpu=Guest;GPU;Vulkan;XMA Decoder;Audio");
            arguments.add("--android_audio_bank_diagnostics=true");
        }else{arguments.add("--pinyon_shift_capture_performance=false");arguments.add("--fh1_native_gpu_profile=false");}
        java.util.Map<String,String> settings=launchSettings();
        arguments.add("--android_vblank_display_lock="+getSharedPreferences("mobile",0).getBoolean("match_refresh",true));
        arguments.add("--android_gpu_turbo="+settings.getOrDefault("android_gpu_turbo","false"));
        arguments.add("--android_gpu_driver_no_preamble="+settings.getOrDefault("android_gpu_driver_no_preamble","true"));
        arguments.add("--android_gpu_driver_env="+settings.getOrDefault("android_gpu_driver_env",""));
        if(validationBuild){arguments.add("--vulkan_validation_enabled=true");arguments.add("--vulkan_validation_sync=true");}
        android.app.ActivityManager manager=getSystemService(android.app.ActivityManager.class);
        android.app.ActivityManager.MemoryInfo memory=new android.app.ActivityManager.MemoryInfo();
        if(manager!=null)manager.getMemoryInfo(memory);
        boolean lowRam=manager!=null&&manager.isLowRamDevice();
        int pool=PerformancePolicy.configured(settings.getOrDefault("vulkan_texture_memory_prealloc_mb","-1"),PerformancePolicy.texturePoolMb(memory.totalMem,lowRam),0,512);
        int workers=PerformancePolicy.configured(settings.getOrDefault("vulkan_pipeline_creation_threads","-1"),PerformancePolicy.shaderWorkers(memory.totalMem,lowRam,Runtime.getRuntime().availableProcessors()),0,16);
        arguments.add("--vulkan_texture_memory_prealloc_mb="+pool);
        arguments.add("--vulkan_pipeline_creation_threads="+workers);
        arguments.add("--vulkan_aot_prewarm_threads="+PerformancePolicy.configured(settings.getOrDefault("vulkan_aot_prewarm_threads","-1"),PerformancePolicy.shaderWorkers(memory.totalMem,lowRam,Runtime.getRuntime().availableProcessors()),1,16));
        if (comparisonState==null && getSharedPreferences("mobile", 0).getBoolean("audio_compatibility", false)) {
            arguments.add("--pinyon_shift_native_audio_output=false");
            arguments.add("--pinyon_shift_fmod_stereo=false");
            arguments.add("--pinyon_shift_fmod_native_parameq=false");
            arguments.add("--xma_decode_kicks_inline=true");
            arguments.add("--pinyon_shift_audio_update_interval=1");
        }
        arguments.add("--store_shaders=true");
        arguments.add("--vulkan_aot_pipelines=true");
        arguments.add("--vulkan_pipeline_cache_persist=true");
        arguments.add("--vulkan_pipeline_prewarm_background=false");
        if(sessionLoggingEnabled())recordLaunchSettings(arguments);
        return arguments.toArray(new String[0]);
    }
    boolean sessionLoggingEnabled(){
        return getIntent()==null || !getIntent().getBooleanExtra(NO_SESSION_LOGS,false);
    }
    private java.util.Map<String,String> launchSettings(){
        return comparisonState==null?DriverStore.sessionSettings(MobileConfig.read(this),
                getIntent().getStringExtra("driver_flag_trial"))
            :MobileConfig.readFile(new File(comparisonState,"config/pinyon_shift.toml"));
    }
    private File reportFile(String name) {
        File state=comparisonState==null?new File(MobileConfig.base(this),"state"):comparisonState;
        return new File(state,"logs/"+name);
    }

    private boolean hasController(){
        for(int id:android.view.InputDevice.getDeviceIds())
            if(ControllerPrompts.isGamepad(android.view.InputDevice.getDevice(id)))return true;
        return false;
    }
    private void updateController(){if(bridgeLoaded)nativeController(hasController());}
    private void addGameMenuButton(){
        android.widget.ImageButton menu=new android.widget.ImageButton(this);menu.setContentDescription("Open FH4Droid game menu");menu.setImageDrawable(new MenuIcon(this,"menu",FestivalUi.TEXT));FestivalUi.glassBackground(this,menu,false,24);menu.setPadding(FestivalUi.dp(this,12),FestivalUi.dp(this,12),FestivalUi.dp(this,12),FestivalUi.dp(this,12));
        android.widget.RelativeLayout.LayoutParams params=new android.widget.RelativeLayout.LayoutParams(FestivalUi.dp(this,48),FestivalUi.dp(this,48));params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);params.setMargins(0,FestivalUi.dp(this,8),FestivalUi.dp(this,12),0);mLayout.addView(menu,params);menu.setOnClickListener(v->showGameMenu());menu.setOnApplyWindowInsetsListener((view,insets)->{android.graphics.Rect safe=PlatformCompat.safeInsets(insets);android.widget.RelativeLayout.LayoutParams p=(android.widget.RelativeLayout.LayoutParams)view.getLayoutParams();p.topMargin=FestivalUi.dp(this,8)+safe.top;p.rightMargin=FestivalUi.dp(this,12)+safe.right;view.setLayoutParams(p);return insets;});
        fpsLabel=FestivalUi.text(this,"— FPS",13,android.graphics.Color.WHITE);fpsLabel.setVisibility(android.view.View.GONE);android.widget.RelativeLayout.LayoutParams fps=new android.widget.RelativeLayout.LayoutParams(-2,-2);fps.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT);fps.setMargins(FestivalUi.dp(this,12),FestivalUi.dp(this,8),0,0);mLayout.addView(fpsLabel,fps);
        phoneVibrator=PlatformCompat.vibrator(this);
        preparationPanel=FestivalUi.column(this);
        preparationPanel.setBackground(FestivalUi.surface(this,FestivalUi.PANEL,8));
        preparationPanel.setPadding(FestivalUi.dp(this,12),FestivalUi.dp(this,8),FestivalUi.dp(this,12),FestivalUi.dp(this,4));
        preparationLabel=FestivalUi.text(this,"",13,FestivalUi.TEXT);
        preparationLabel.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        preparationLabel.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        preparationPanel.addView(preparationLabel);
        preparationProgress=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        preparationProgress.setMax(1000);
        preparationPanel.addView(preparationProgress,new android.widget.LinearLayout.LayoutParams(-1,-2));
        preparationPanel.setVisibility(View.GONE);
        android.widget.RelativeLayout.LayoutParams prep=new android.widget.RelativeLayout.LayoutParams(FestivalUi.dp(this,224),-2);
        prep.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);
        prep.topMargin=FestivalUi.dp(this,32);
        mLayout.addView(preparationPanel,prep);
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
        refreshCpuHintTarget();
    }
    private void refreshCpuHintTarget() {
        int target = nativeReady && gameResumed && !closing && (gameMenu==null || !gameMenu.isShowing())
            && (comparisonState!=null?getIntent().getBooleanExtra("comparison_cpu_hints",false)
                :getSharedPreferences("mobile",0).getBoolean("cpu_hints",false))
            ? Math.round(PerformancePolicy.presentationTarget(nativeSettingValue("pinyon_shift_fh1_render_fps_limit"),"0")) : 0;
        if (target!=cpuHintTarget) {
            cpuHintTarget=target;
            nativeCpuHintTarget(target);
        }
    }
    void calibrateTilt(){if(driveAssist==null||!driveAssist.calibrate())FestivalUi.explain(this,"Tilt calibration","Enable phone tilt while playing, then hold the phone in your normal driving position.");else toast("Tilt centre calibrated.");}
    int coarseShadingSupport(){return nativeReady?nativeCoarseShadingSupport():-1;}
    void resumeDriving(){if(!closing&&gameMenu!=null)gameMenu.dismiss();}
    void openNativeAchievements(){resumeDriving();if(nativeReady)nativeAction("achievements");}
    void openNativeSettings(){resumeDriving();if(nativeReady)nativeOpenSettings();}
    private void showGameMenu(){
        if(closing||comparisonState!=null||isFinishing()||isDestroyed()||gameMenu!=null)return;
        if(!nativeReady){toast("The game is still starting.");return;}
        if(sessionLoggingEnabled())ActionBreadcrumbs.record(this,"Game menu","opening");
        drawerPages.clear();
        drawer=new android.widget.FrameLayout(this);
        Dialog dialog=new Dialog(this);
        gameMenu=dialog;
        dialog.setOnKeyListener((d,key,event)->{
            if(ControllerPrompts.handle(this,dialog.getWindow(),key,event,this::backPanel))return true;
            if(key!=android.view.KeyEvent.KEYCODE_BACK)return false;
            if(event.getAction()==android.view.KeyEvent.ACTION_UP)backPanel();
            return true;
        });
        dialog.setOnDismissListener(d->gameMenuDismissed(dialog));
        MobileSettings.Runtime runtime=settingsRuntime();
        Runnable editor=()->{resumeDriving();startActivity(new Intent(this,TouchEditorActivity.class).putExtra("return_to_game",true));};
        GameMenuView body=new GameMenuView(this,label->{switch(label){
          case "Resume":resumeDriving();break;
          case "Settings":SettingsHub.show(this,runtime,editor,()->{});break;
          case "Touch controls":SettingsHub.page(this,"Controls",runtime,editor);break;
          case "Controller":SettingsHub.page(this,"Controller",runtime,editor);break;
          case "More":{
              android.widget.LinearLayout more=FestivalUi.column(this);
              FestivalUi.action(this,more,"Profile",()->ProfilePage.show(this,null,()->FestivalUi.explain(this,"Save backups","Quit to the launcher to export, import or restore saves.")));
              FestivalUi.action(this,more,"Achievements",()->AchievementsPage.show(this,nativeAchievements()));
              FestivalUi.action(this,more,"Diagnostics",()->{
                  android.widget.LinearLayout details=FestivalUi.column(this);
                  if(sessionLoggingEnabled())FestivalUi.action(this,details,"Capture a problem",this::markDiagnosticIssue);
                  else details.addView(FestivalUi.text(this,
                      "Session logging is off. Use Play from the launcher to record a new session.",
                      14,FestivalUi.MUTED));
                  ProblemReports.action(this,details,sessionLoggingEnabled()?"Report a problem":"Share existing reports");
                  showPanel("Diagnostics",details);
              });
              FestivalUi.action(this,more,"Trainer · separate profile",()->TrainerPage.show(this,runtime));
              showPanel("More tools",more);
              break;
          }
          case "Quit to launcher":new android.app.AlertDialog.Builder(this).setTitle("Quit the game?").setMessage("Wait for the game's save indicator to finish before quitting.").setNegativeButton("Keep playing",null).setPositiveButton("Quit",(d,w)->requestQuit()).show();break;
        }});
        try {
            dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            dialog.setCanceledOnTouchOutside(true);
            dialog.setContentView(drawer);
            android.view.Window window=dialog.getWindow();
            window.setBackgroundDrawable(FestivalUi.surface(this,FestivalUi.PANEL,16));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.35f);
            window.setGravity(android.view.Gravity.RIGHT|android.view.Gravity.CENTER_VERTICAL);
            MenuMotion.drawerWindow(window);
            showPanel("Game menu",body);
            dialog.show();
        } catch (RuntimeException error) {
            Log.e(TAG,"Could not open the in-game menu",error);
            if (dialog.isShowing()) dialog.dismiss();
            gameMenuDismissed(dialog);
            if (sessionLoggingEnabled())
                ActionBreadcrumbs.record(this,"Game menu","window failed: "+error.getClass().getSimpleName());
            if (!isFinishing()&&!isDestroyed()) toast("The game menu could not open.");
            return;
        }
        if (sessionLoggingEnabled()) ActionBreadcrumbs.record(this,"Game menu","window shown");
        nativeHostMenu(true);
        stopPhoneRumble();
        refreshCpuHintTarget();
    }
    private void gameMenuDismissed(Dialog dialog){
        if(gameMenu!=dialog)return;
        gameMenu=null;
        drawerPages.clear();
        drawer=null;
        if(bridgeLoaded)nativeHostMenu(closing||!gameResumed);
        refreshCpuHintTarget();
        if(!isFinishing()&&!isDestroyed())FestivalUi.immersive(this);
    }
    @Override public void showPanel(String title,android.view.View body){
        if(drawer==null)return;
        drawerPages.push(new DrawerPage(title,body));
        renderDrawer(false);
    }
    @Override public void replacePanel(String title,android.view.View body){if(!drawerPages.isEmpty())drawerPages.pop();showPanel(title,body);}
    private void renderDrawer(boolean back){
        if(drawer==null||drawerPages.isEmpty())return;
        FestivalUi.rememberTree(drawer);
        if(drawer.getChildCount()>0)MenuMotion.stop(drawer.getChildAt(0));
        drawer.removeAllViews();
        DrawerPage page=drawerPages.peek();
        boolean menu=page.body instanceof GameMenuView;
        android.view.View panel=menu?page.body:FestivalUi.panel(this,page.title,page.body,this::backPanel);
        if(panel.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)panel.getParent()).removeView(panel);
        drawer.addView(panel,new android.widget.FrameLayout.LayoutParams(-1,menu?-2:-1));
        resizeGameMenu();
        if(menu)page.body.post(()->{if(page.body.isAttachedToWindow())FestivalUi.restorePage(page.body);});
        FestivalUi.refreshTree(panel);
        if(gameMenu!=null&&gameMenu.isShowing())MenuMotion.page(panel,back);
    }
    private void resizeGameMenu(){
        if(gameMenu==null||drawerPages.isEmpty())return;
        View page=drawerPages.peek().body;
        View decor=getWindow().peekDecorView();
        int pixels=decor!=null&&decor.getWidth()>0?decor.getWidth():getResources().getDisplayMetrics().widthPixels;
        int available=Math.max(1,pixels-FestivalUi.dp(this,24));
        int width=Boolean.TRUE.equals(page.getTag(FestivalUi.FULL_PAGE))
            ?available:Math.min(FestivalUi.dp(this,500),available);
        gameMenu.getWindow().setLayout(width,page instanceof GameMenuView
            ?WindowManager.LayoutParams.WRAP_CONTENT:WindowManager.LayoutParams.MATCH_PARENT);
    }
    @Override public void backPanel(){if(drawerPages.size()>1){drawerPages.pop();renderDrawer(true);}else resumeDriving();}
    @Override public void onBackPressed(){if(gameMenu!=null&&gameMenu.isShowing())backPanel();else showGameMenu();}
    public void gameRumble(int left,int right){uiHandler.post(()->{
        if(phoneVibrator==null||!phoneVibrator.hasVibrator()||!gameResumed||gameMenu!=null&&gameMenu.isShowing()||!getSharedPreferences("mobile",0).getBoolean("phone_rumble",true)){stopPhoneRumble();return;}
        int strength=Math.max(0,Math.min(100,getSharedPreferences("mobile",0).getInt("phone_rumble_strength",60)));
        int amplitude=(int)Math.min(255,(Math.min(65535,Math.max(0,left))*.65+Math.min(65535,Math.max(0,right))*.35)*strength*255/6553500);
        if(amplitude==0){stopPhoneRumble();return;}long now=android.os.SystemClock.elapsedRealtime();
        if(Math.abs(amplitude-rumbleAmplitude)>=8||now-lastRumbleMs>=60){phoneVibrator.vibrate(android.os.VibrationEffect.createOneShot(120,phoneVibrator.hasAmplitudeControl()?Math.max(1,amplitude):android.os.VibrationEffect.DEFAULT_AMPLITUDE));rumbleAmplitude=amplitude;lastRumbleMs=now;}
    });}
    private void stopPhoneRumble(){if(phoneVibrator!=null)phoneVibrator.cancel();rumbleAmplitude=0;}
    private void invalidateFrameRateHint() {
        hintedSurface = null;
        lastFrameRateHint = -1;
        requestedRefreshHz = -1;
        nextFrameRateRequest = 0;
        refreshFrameRateHint();
    }
    void refreshFrameRateHint() {
        if (!nativeReady || !gameResumed || mSurface == null) return;
        refreshCpuHintTarget();
        android.view.Surface surface = mSurface.getHolder().getSurface();
        if (!surface.isValid()) return;
        boolean match = getSharedPreferences("mobile", 0).getBoolean("match_refresh", true);
        if (match != framePacingEnabled) {
            framePacingEnabled = match;
            nativeApplySetting("android_vblank_display_lock", Boolean.toString(match));
        }
        float target = match ? presentationTarget() : 0;
        boolean savingPower = batterySaver();
        boolean sameRequest = surface == hintedSurface && target == lastFrameRateHint
            && savingPower == lastBatterySaver;
        Display display = mSurface.getDisplay();
        float refresh = display == null ? -1 : display.getRefreshRate();
        if (sameRequest && Math.abs(refresh - lastDisplayRefresh) < .1f) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (sameRequest && now < nextFrameRateRequest) return;
        nextFrameRateRequest = now + 1000;
        Display.Mode preferred = null;
        if (target > 0 && display != null) {
            Display.Mode current = display.getMode();
            for (Display.Mode candidate : display.getSupportedModes()) {
                if (candidate.getPhysicalWidth() == current.getPhysicalWidth()
                        && candidate.getPhysicalHeight() == current.getPhysicalHeight()
                        && PerformancePolicy.preferDisplayMode(target, candidate.getRefreshRate(),
                            preferred == null ? 0 : preferred.getRefreshRate(), savingPower)) {
                    preferred = candidate;
                }
            }
        }
        float cadence = preferred != null ? preferred.getRefreshRate() : savingPower ? 0 : target;
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        int modeId = preferred == null ? 0 : preferred.getModeId();
        if (attrs.preferredDisplayModeId != modeId || attrs.preferredRefreshRate != 0) {
            attrs.preferredDisplayModeId = modeId;
            attrs.preferredRefreshRate = 0;
            getWindow().setAttributes(attrs);
        }
        boolean hinted = PlatformCompat.frameRate(surface, cadence);
        requestedRefreshHz = hinted || preferred != null ? cadence : -1;
        if (hinted) {
            hintedSurface = surface;
            lastFrameRateHint = target;
            lastDisplayRefresh = refresh;
            lastBatterySaver = savingPower;
        }
    }
    private float presentationTarget() {
        return PerformancePolicy.presentationTarget(nativeSettingValue("pinyon_shift_fh1_render_fps_limit"),
            nativeSettingValue("host_present_fps_limit"));
    }
    private boolean batterySaver() {
        PowerManager power = getSystemService(PowerManager.class);
        return power != null && power.isPowerSaveMode();
    }
    private void recordLaunchSettings(List<String> args){try{org.json.JSONObject data=new org.json.JSONObject();data.put("captured_ms",System.currentTimeMillis());data.put("source_commit",buildCommit);data.put("settings",new org.json.JSONObject(MobileConfig.reportSettings(launchSettings())));data.put("arguments",new org.json.JSONArray(args));
        data.put("resolved_driver",getIntent().getStringExtra("verified_driver"));
        data.put("driver_flag_trial",getIntent().getStringExtra("driver_flag_trial"));
        data.put("mods_skipped",getIntent().getBooleanExtra("skip_mods",false));
        android.content.SharedPreferences preferences = getSharedPreferences("mobile", 0);
        data.put("game_source", !preferences.getString("iso_uri", "").isEmpty() ? "iso" : GameFiles.linked(this) ? "linked-folder" : "copied-folder");
        data.put("cpu_hints_requested",comparisonState!=null?getIntent().getBooleanExtra("comparison_cpu_hints",false):preferences.getBoolean("cpu_hints",false));
        try {
            Display display=mSurface==null?getDisplay():mSurface.getDisplay();
            if(display!=null)data.put("display_hz",display.getRefreshRate());
        } catch (UnsupportedOperationException | IllegalStateException ignored) {
            // The launch snapshot is still useful if the display has not attached.
        }
        writeReportJson("android-launch-settings.json",data);}catch(Exception e){Log.w(TAG,"Could not record launch settings",e);}}
    void captureReportState(){captureReportState(null);}
    void captureReportState(Runnable afterCapture){
        if(!sessionLoggingEnabled()||!nativeReady){if(afterCapture!=null)afterCapture.run();return;}
        try{org.json.JSONObject live=new org.json.JSONObject();for(MobileSettings.Option option:MobileSettings.OPTIONS){String value=nativeSettingValue(option.key);if(value!=null)live.put(option.key,value);}
            for(String key:new String[]{"fh1_render_scale_percent","present_letterbox","pinyon_shift_hor_plus","pinyon_shift_fov_scale","fh1_msaa_single_sample","fh1_msaa_2x","pinyon_shift_cheats","android_gpu_turbo","android_gpu_driver_no_preamble","android_gpu_driver_env","vulkan_fh1_native_executor","vulkan_dynamic_rendering","pinyon_shift_save_backups"}){String value=nativeSettingValue(key);if(value!=null)live.put(key,value);}
            org.json.JSONObject data=new org.json.JSONObject();data.put("captured_ms",System.currentTimeMillis());data.put("source_commit",buildCommit);data.put("live",live);lastReportedMenu=nativeMenuContext();data.put("menu_context",lastReportedMenu);data.put("frame",nativeFrameCount());data.put("snapshot_interval_ms",getIntent().getBooleanExtra("debug_report",false)?5000:0);data.put("shader_preparation",new org.json.JSONArray(nativePreparation()));data.put("paused",!gameResumed);
            Display display = mSurface == null ? null : mSurface.getDisplay();
            data.put("target_fps", presentationTarget());
            data.put("requested_refresh_hz", requestedRefreshHz < 0 ? org.json.JSONObject.NULL : requestedRefreshHz);
            data.put("observed_refresh_hz", display == null ? org.json.JSONObject.NULL : display.getRefreshRate());
            data.put("battery_saver", batterySaver());
            android.view.View view=getWindow().getDecorView();android.graphics.Rect safe=PlatformCompat.safeInsets(view.getRootWindowInsets());data.put("window_width",view.getWidth());data.put("window_height",view.getHeight());data.put("safe_insets",new org.json.JSONArray(new int[]{safe.left,safe.top,safe.right,safe.bottom}));
            telemetry.execute(()->{
                try{
                    data.put("session",currentSession());
                    data.put("profile",LocalProfile.configuredProfile(launchSettings()));
                    writeReportJson("android-live-settings.json",data);
                }catch(Exception error){Log.w(TAG,"Could not write live settings snapshot",error);}
                finally{if(afterCapture!=null)afterCapture.run();}
            });
        }catch(Exception error){
            Log.w(TAG,"Could not snapshot live settings",error);
            if(afterCapture!=null)afterCapture.run();
        }
    }
    private long latestIncident;
    private static final String[] FRAME_HISTORY_FIELDS = {
        "time_ms", "source_frame", "frame_time_us", "guest_gpu_ns", "process_cpu_ns",
        "xma_frames", "xma_no_space", "xma_no_progress", "xma_recoveries", "audio_latency_us", "page_recoveries"
    };
    private void markDiagnosticIssue() {
        if(!sessionLoggingEnabled())return;
        long timestamp = System.currentTimeMillis();
        long frame = nativeFrameCount();
        long[] history = nativeFrameHistory();
        boolean nativeAudio = nativeAudioOutputActive();
        boolean menu = nativeMenuContext();
        String session = currentSession();
        String[] issues = {"Silent impact", "Muffled engine", "Car paint or glass corruption", "Blank car tile", "Stutter", "Other"};
        new AlertDialog.Builder(this).setTitle("Capture a problem")
            .setItems(issues, (dialog, index) -> {
                android.widget.EditText note = new android.widget.EditText(this);
                note.setHint(index == 2 ? "Car, paint or livery, location and view" : "Optional details");
                note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(300)});
                new AlertDialog.Builder(this).setTitle(issues[index]).setView(note)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save capture", (confirmation, which) -> {
                        String description = note.getText().toString();
                        org.json.JSONObject settings = new org.json.JSONObject();
                        try {
                            for (String key : new String[]{"fh1_render_scale_percent", "disable_bloom", "present_effect",
                                    "android_gpu_driver", "android_gpu_driver_env", "android_gpu_driver_no_preamble",
                                    "vulkan_fh1_native_executor", "vulkan_dynamic_rendering", "vulkan_bc_transcode",
                                    "pinyon_shift_native_audio_output", "pinyon_shift_fmod_stereo",
                                    "pinyon_shift_audio_update_interval", "xma_decode_kicks_inline"}) {
                                settings.put(key, nativeSettingValue(key));
                            }
                        } catch (org.json.JSONException error) { Log.w(TAG, "Issue settings unavailable", error); }
                        captureReportState();
                        boolean visual = index == 2 || index == 3;
                        long incident = ++latestIncident;
                        telemetry.execute(() -> {
                            try {
                                org.json.JSONObject capture = new org.json.JSONObject();
                                capture.put("time_ms", timestamp);
                                capture.put("session", session);
                                capture.put("frame", frame);
                                capture.put("kind", issues[index]);
                                capture.put("note", description);
                                capture.put("in_game_menu_context", menu);
                                capture.put("mods_skipped", getIntent().getBooleanExtra("skip_mods",false));
                                capture.put("resolved_driver", getIntent().getStringExtra("verified_driver"));
                                capture.put("driver_flag_trial", getIntent().getStringExtra("driver_flag_trial"));
                                capture.put("audio_mode", nativeAudio ? "native" : "guest");
                                capture.put("live_settings", settings);
                                capture.put("frame_fields", new org.json.JSONArray(FRAME_HISTORY_FIELDS));
                                org.json.JSONArray frames = new org.json.JSONArray();
                                if (history != null) for (int start = 0; start + FRAME_HISTORY_FIELDS.length <= history.length; start += FRAME_HISTORY_FIELDS.length) {
                                    org.json.JSONArray row = new org.json.JSONArray();
                                    for (int field = 0; field < FRAME_HISTORY_FIELDS.length; field++) row.put(history[start + field]);
                                    frames.put(row);
                                }
                                capture.put("recent_frames", frames);
                                File previous = new File(MobileConfig.base(this), "state/logs/android-incident-frame.png");
                                Files.deleteIfExists(previous.toPath());
                                writeReportJson("android-incident.json", capture);
                                runOnUiThread(() -> {
                                    if (isDestroyed() || latestIncident != incident) return;
                                    if (!visual) toast("Capture saved.");
                                    else if (!captureIncidentFrame(incident)) toast("Capture saved without a game frame.");
                                });
                            } catch (Exception error) { Log.w(TAG, "Could not save problem capture", error); }
                        });
                    }).show();
            }).show();
    }
    private boolean captureIncidentFrame(long incident) {
        if (mSurface == null || mSurface.getWidth() < 1 || mSurface.getHeight() < 1) return false;
        android.view.Surface source = mSurface.getHolder().getSurface();
        if (!source.isValid()) return false;
        float scale = Math.min(1f, 960f / Math.max(mSurface.getWidth(), mSurface.getHeight()));
        android.graphics.Bitmap frame = android.graphics.Bitmap.createBitmap(
            Math.max(1, Math.round(mSurface.getWidth() * scale)),
            Math.max(1, Math.round(mSurface.getHeight() * scale)), android.graphics.Bitmap.Config.ARGB_8888);
        try {
            android.view.PixelCopy.request(source, frame, result -> {
                if (isDestroyed() || latestIncident != incident) {
                    frame.recycle();
                    return;
                }
                if (result != android.view.PixelCopy.SUCCESS) {
                    frame.recycle();
                    toast("Capture saved without a game frame.");
                    return;
                }
                telemetry.execute(() -> {
                    try {
                        File destination = new File(MobileConfig.base(this), "state/logs/android-incident-frame.png");
                        java.nio.file.Path stage = Files.createTempFile(destination.getParentFile().toPath(), "incident-", ".part");
                        try {
                            try (OutputStream output = Files.newOutputStream(stage)) {
                                if (!frame.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)) {
                                    throw new IOException("Could not encode game frame");
                                }
                            }
                            Files.move(stage, destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                        } finally { Files.deleteIfExists(stage); }
                        finishIncidentFrame(incident, "Capture and game frame saved.");
                    } catch (IOException error) {
                        Log.w(TAG, "Could not save incident frame", error);
                        finishIncidentFrame(incident, "Capture saved without a game frame.");
                    }
                    finally { frame.recycle(); }
                });
            }, new android.os.Handler(android.os.Looper.getMainLooper()));
            return true;
        } catch (IllegalArgumentException error) {
            frame.recycle();
            Log.w(TAG, "Game surface unavailable for capture", error);
            return false;
        }
    }
    private void finishIncidentFrame(long incident, String message) {
        runOnUiThread(() -> {
            if (!isDestroyed() && latestIncident == incident) toast(message);
        });
    }
    private String currentSession(){long now=android.os.SystemClock.elapsedRealtime();if(cachedSession!=null)return cachedSession;if(now-sessionLookupTime<5000)return "starting-p"+android.os.Process.myPid();sessionLookupTime=now;File[] files=reportFile("").listFiles(f->f.getName().endsWith("-p"+android.os.Process.myPid()+".jsonl"));if(files==null||files.length==0)return "starting-p"+android.os.Process.myPid();Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));cachedSession=files[0].getName().replaceFirst("\\.jsonl$","");return cachedSession;}
    private void writeReportJson(String name,org.json.JSONObject data)throws Exception{File file=reportFile(name);Files.createDirectories(file.getParentFile().toPath());java.nio.file.Path stage=Files.createTempFile(file.getParentFile().toPath(),"snapshot-",".part");try{Files.write(stage,data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));Files.move(stage,file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(stage);}}
    private void samplePerformance(float fps,boolean overlay,boolean diagnostic){
        long now=android.os.SystemClock.elapsedRealtime();
        boolean record=sessionLoggingEnabled() && now-lastPerformanceRecord>=(diagnostic?1000:5000);
        android.content.SharedPreferences preferences=getSharedPreferences("mobile",0);
        boolean detailedOverlay=overlay && (preferences.getBoolean("show_frame_time",false)
            || preferences.getBoolean("show_memory_heat",false));
        if (overlay && !detailedOverlay) updateFpsLabel(preferences,fps,null,-1);
        if ((!record && !detailedOverlay) || !samplePending.compareAndSet(false,true)) return;
        if (record) lastPerformanceRecord=now;
        boolean menu=gameMenu!=null&&gameMenu.isShowing();
        long generation=overlayGeneration, timestamp=System.currentTimeMillis();
        try {
            telemetry.execute(()->{
                try {
                    long[] sample=nativePerformance();
                    android.os.PowerManager manager=getSystemService(android.os.PowerManager.class);
                    int thermal=manager==null?-1:manager.getCurrentThermalStatus();
                    if (detailedOverlay) uiHandler.post(()->{
                        if(gameResumed && generation==overlayGeneration && !isFinishing()
                            && preferences.getBoolean("show_fps",false))
                            updateFpsLabel(preferences,fps,sample,thermal);
                    });
                    if (record) writePerformanceSample(fps,diagnostic,menu,timestamp,thermal,sample);
                } catch (Exception error) { Log.w(TAG,"Could not sample performance",error); }
                finally { samplePending.set(false); }
            });
        } catch(java.util.concurrent.RejectedExecutionException ignored) { samplePending.set(false); }
    }
    private void writePerformanceSample(float fps,boolean diagnostic,boolean menu,long timestamp,int thermal,long[] sample)throws Exception {
        org.json.JSONObject event=new org.json.JSONObject();
        event.put("time_ms",timestamp).put("session",currentSession()).put("source_commit",buildCommit).put("fps",fps)
            .put("thermal_severity",thermal).put("thermal_status",ReportFiles.thermalLabel(thermal))
            .put("host_menu",menu).put("diagnostic",diagnostic);
        if(sample!=null){
            String[] fields={"sample_frame_time_us","resident_mb","device_usage_mb","device_budget_mb","sample_process_cpu_ns",
                "sample_guest_gpu_ns","sample_guest_gpu_count","texture_cache_mb"};
            for(int i=0;i<Math.min(fields.length,sample.length);i++)event.put(fields[i],sample[i]);
        }
        File file=reportFile("android-performance.jsonl");
        if(file.length()>262144)
            Files.move(file.toPath(),reportFile("android-performance-previous.jsonl").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try(OutputStream out=new FileOutputStream(file,true)){
            out.write((event.toString()+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event){
        if(bridgeLoaded&&gameResumed&&(gameMenu==null||!gameMenu.isShowing())&&event.getAction()==android.view.KeyEvent.ACTION_DOWN){
            if(ControllerPrompts.isGamepad(event.getDevice()))nativeController(true);
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean dispatchGenericMotionEvent(android.view.MotionEvent event){
        if(bridgeLoaded&&gameResumed&&(gameMenu==null||!gameMenu.isShowing())&&ControllerPrompts.isGamepad(event.getDevice())&&event.isFromSource(android.view.InputDevice.SOURCE_JOYSTICK)&&
            (Math.abs(event.getAxisValue(android.view.MotionEvent.AXIS_X))>.2f||Math.abs(event.getAxisValue(android.view.MotionEvent.AXIS_Y))>.2f||event.getAxisValue(android.view.MotionEvent.AXIS_LTRIGGER)>.1f||event.getAxisValue(android.view.MotionEvent.AXIS_RTRIGGER)>.1f))nativeController(true);
        return super.dispatchGenericMotionEvent(event);
    }
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){
        int action=event.getActionMasked();if(bridgeLoaded&&action==android.view.MotionEvent.ACTION_DOWN&&(gameMenu==null||!gameMenu.isShowing()))nativeController(false);
        if(touchHaptics&&bridgeLoaded&&(gameMenu==null||!gameMenu.isShowing())&&(action==android.view.MotionEvent.ACTION_DOWN||action==android.view.MotionEvent.ACTION_POINTER_DOWN))
            getWindow().getDecorView().performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        return super.dispatchTouchEvent(event);
    }
    @Override protected void onPause(){gameResumed=false;overlayGeneration++;refreshCpuHintTarget();captureReportState();if(driveAssist!=null)driveAssist.pause();if(bridgeLoaded)nativeHostMenu(true);uiHandler.removeCallbacksAndMessages(null);stopPhoneRumble();fpsTime=0;lastFrameRateHint=-1;super.onPause();}
    @Override public void onTrimMemory(int level){
        // SDL turns every super call into a full native low-memory purge.
        // Forward real pressure, keeping reloadable caches on UI_HIDDEN.
        if(PerformancePolicy.trim(level))super.onTrimMemory(level);
    }
    @Override protected void onResume(){UiPreferences.load(this);super.onResume();gameResumed=true;reloadControls();if(bridgeLoaded){nativeHostMenu(closing||gameMenu!=null&&gameMenu.isShowing());FestivalUi.immersive(this);uiHandler.removeCallbacks(metricsTick);uiHandler.post(metricsTick);if(nativeReady)refreshMobilePreferences();}}
    @Override protected void onDestroy(){
        gameResumed=false;captureReportState();if(driveAssist!=null)driveAssist.pause();uiHandler.removeCallbacksAndMessages(null);stopPhoneRumble();telemetry.shutdown();
        if (displays != null) displays.unregisterDisplayListener(displayListener);
        if (mSurface != null) mSurface.getHolder().removeCallback(pacingSurface);
        if(inputManager!=null)inputManager.unregisterInputDeviceListener(controllers);if(gameMenu!=null)gameMenu.dismiss();if(closingDialog!=null)closingDialog.dismiss();
        // Only native shutdown, after finishing save backups, qualifies a
        // clean exit. Activity destruction can also follow a forced close.
        super.onDestroy();
        if(gameIso!=null)try{gameIso.close();}catch(IOException ignored){}
        android.os.Process.killProcess(android.os.Process.myPid());
    }
    public void recordCleanExit(){if(comparisonState==null)GameSession.record(this,false,sessionLoggingEnabled());}
    private void requestQuit(){
        if(closing)return;closing=true;
        if(sessionLoggingEnabled())ActionBreadcrumbs.record(this,"Quit to launcher","finishing backup and cleanup");
        if(gameMenu!=null)gameMenu.dismiss();
        android.widget.LinearLayout body=FestivalUi.column(this);body.addView(FestivalUi.text(this,"Finishing save backup and closing the game…",18,FestivalUi.TEXT));body.addView(new android.widget.ProgressBar(this));
        closingDialog=new android.app.AlertDialog.Builder(this).setView(body).setCancelable(false).create();closingDialog.show();
        if(nativeReady)nativeAction("quit");else finish();
    }

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

    // Adreno 730 keeps the system driver after reported custom-driver regressions.
    // Other Adreno 7xx and 8xx: Mesa Turnip Gen8 V37 (measured on the Adreno 740
    // and 830 against the system driver, and on the 740 against newer Turnip
    // builds). Others keep the system's.
    private static String recommendedDriver(String adreno) {
        return adreno!=null && !adreno.equals("730") && (adreno.startsWith("7") || adreno.startsWith("8")) ? "turnip-gen8-v37" : null;
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
