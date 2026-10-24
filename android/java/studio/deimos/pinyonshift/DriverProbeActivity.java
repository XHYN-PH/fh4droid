package studio.deimos.pinyonshift;
import android.app.Activity;
import android.content.Intent;
import android.os.*;
import android.widget.*;
import java.io.File;

/** A fresh process for every test; custom loader hooks never enter the launcher. */
public final class DriverProbeActivity extends Activity {
    static native String nativeProbe(String nativeDir,String driversRoot,String internalRoot,String selection,String environment,boolean noPreamble);
    private final Handler handler=new Handler(Looper.getMainLooper());private boolean done;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);LinearLayout body=FestivalUi.column(this);body.setPadding(40,40,40,40);body.setBackgroundColor(FestivalUi.BG);
        body.addView(FestivalUi.text(this,"Testing graphics driver…",24,FestivalUi.TEXT));body.addView(new ProgressBar(this));setContentView(body);
        handler.postDelayed(()->finishProbe("{\"ok\":false,\"error\":\"Driver test timed out.\"}"),20000);
        final String selected=getIntent().getStringExtra("driver");
        new Thread(()->{String result;try{System.loadLibrary("c++_shared");System.loadLibrary("rexruntime");System.loadLibrary("fh4droid_android");DriverStore.installBundled(this);
            result=nativeProbe(getApplicationInfo().nativeLibraryDir,DriverStore.root(this).getAbsolutePath(),getFilesDir().getAbsolutePath(),selected==null?"":selected,MobileConfig.get(this,"android_gpu_driver_env",""),!MobileConfig.get(this,"android_gpu_driver_no_preamble","true").equals("false"));
        }catch(Exception|LinkageError e){result="{\"ok\":false,\"error\":\""+e.getClass().getSimpleName()+": driver could not be tested.\"}";}
            final String report=result;handler.post(()->finishProbe(report));},"DriverPreflight").start();
    }
    private void finishProbe(String result){if(done)return;done=true;handler.removeCallbacksAndMessages(null);setResult(RESULT_OK,new Intent().putExtra("report",result));finish();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();android.os.Process.killProcess(android.os.Process.myPid());}
}
