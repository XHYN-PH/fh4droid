package studio.deimos.pinyonshift;
import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.*;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.*;
import java.io.File;
import java.util.Map;

/** A fresh process for every test; custom loader hooks never enter the launcher. */
public final class DriverProbeActivity extends Activity {
    static native String nativeProbe(String nativeDir,String driversRoot,String internalRoot,String selection,String environment,boolean noPreamble,boolean requireVertexStores,Surface surface);
    private final Handler handler=new Handler(Looper.getMainLooper());private boolean done;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        final String selected=getIntent().getStringExtra("driver");
        FrameLayout root = new FrameLayout(this);
        root.setPadding(FestivalUi.dp(this, 16), FestivalUi.dp(this, 16), FestivalUi.dp(this, 16), FestivalUi.dp(this, 16));
        root.setBackgroundColor(FestivalUi.BG);
        LinearLayout body = FestivalUi.column(this);
        int width = Math.max(1, Math.min(FestivalUi.dp(this, 420),
            getResources().getDisplayMetrics().widthPixels - FestivalUi.dp(this, 32)));
        root.addView(body, new FrameLayout.LayoutParams(width, -2, Gravity.CENTER));
        LinearLayout status = FestivalUi.card(this, body, "Testing graphics driver",
            DriverStore.displayName(this, selected == null ? "" : selected));
        status.addView(FestivalUi.text(this, "Checking formats, rendering and the first display frame…", 13, FestivalUi.TEXT));
        SurfaceView preview = new SurfaceView(this);
        status.addView(preview, new LinearLayout.LayoutParams(-1, FestivalUi.dp(this, 20)));
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(ColorStateList.valueOf(FestivalUi.RED));
        status.addView(progress, new LinearLayout.LayoutParams(-1, -2));
        handler.postDelayed(()->finishProbe("{\"ok\":false,\"error\":\"Driver test timed out.\"}"),20000);
        preview.getHolder().addCallback(new SurfaceHolder.Callback() {
            private boolean started;
            @Override public void surfaceCreated(SurfaceHolder holder) {}
            @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                if (!started && width > 0 && height > 0 && holder.getSurface().isValid()) {
                    started = true;
                    runProbe(selected, holder.getSurface());
                }
            }
            @Override public void surfaceDestroyed(SurfaceHolder holder) {}
        });
        setContentView(root);
    }
    private void runProbe(String selected, Surface surface) {
        new Thread(()->{String result;try{System.loadLibrary("c++_shared");System.loadLibrary("rexruntime");System.loadLibrary("fh4droid_android");DriverStore.installBundled(this);
            String choice=selected==null?"":selected;
            String trial=getIntent().getStringExtra("driver_flag_trial");
            Map<String,String> settings=DriverStore.sessionSettings(MobileConfig.read(this),trial);
            String key=DriverStore.probeKey(this,choice,settings);
            result=nativeProbe(getApplicationInfo().nativeLibraryDir,DriverStore.root(this).getAbsolutePath(),getFilesDir().getAbsolutePath(),choice,settings.getOrDefault("android_gpu_driver_env",""),!settings.getOrDefault("android_gpu_driver_no_preamble","true").equals("false"),!settings.getOrDefault("vulkan_require_vertex_pipeline_stores_and_atomics","true").equals("false"),surface);
            if (trial==null && key!=null && key.equals(DriverStore.probeKey(this,choice,MobileConfig.read(this))))
                result=new org.json.JSONObject(result).put("probe_key",key).toString();
        }catch(Exception|LinkageError e){result="{\"ok\":false,\"error\":\""+e.getClass().getSimpleName()+": driver could not be tested.\"}";}
            final String report=result;handler.post(()->finishProbe(report));},"DriverPreflight").start();
    }
    private void finishProbe(String result){if(done)return;done=true;handler.removeCallbacksAndMessages(null);setResult(RESULT_OK,new Intent().putExtra("report",result));finish();}
    @Override public void onBackPressed() {
        done = true;
        handler.removeCallbacksAndMessages(null);
        setResult(RESULT_CANCELED, new Intent().putExtra("probe_cancelled", true));
        finish();
    }
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();android.os.Process.killProcess(android.os.Process.myPid());}
}
