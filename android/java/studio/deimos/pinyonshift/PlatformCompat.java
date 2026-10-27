package studio.deimos.pinyonshift;

import android.app.*;
import android.graphics.Rect;
import android.os.*;
import android.view.*;

/** Keep newer Android APIs behind version gates and isolated verifier-safe classes. */
final class PlatformCompat {
    static Rect safeInsets(WindowInsets insets){
        if(insets==null)return new Rect();
        if(Build.VERSION.SDK_INT>=30)return Api30.insets(insets);
        int l=insets.getSystemWindowInsetLeft(),t=insets.getSystemWindowInsetTop(),r=insets.getSystemWindowInsetRight(),b=insets.getSystemWindowInsetBottom();
        DisplayCutout cutout=insets.getDisplayCutout();
        if(cutout!=null){l=Math.max(l,cutout.getSafeInsetLeft());t=Math.max(t,cutout.getSafeInsetTop());r=Math.max(r,cutout.getSafeInsetRight());b=Math.max(b,cutout.getSafeInsetBottom());}
        return new Rect(l,t,r,b);
    }
    @SuppressWarnings("deprecation") static Vibrator vibrator(Activity a){
        return Build.VERSION.SDK_INT>=31?Api31.vibrator(a):(Vibrator)a.getSystemService(android.content.Context.VIBRATOR_SERVICE);
    }
    static boolean frameRate(Surface surface,float target){
        if(Build.VERSION.SDK_INT<30)return false;
        try{if(Build.VERSION.SDK_INT>=31)Api31.frameRate(surface,target);else Api30.frameRate(surface,target);return true;}
        catch(IllegalArgumentException|IllegalStateException error){return false;}
    }
    static void exitHistory(ActivityManager manager,String name,StringBuilder info){if(Build.VERSION.SDK_INT>=30)Api30.exits(manager,name,info);}
    private static final class Api30 {
        static Rect insets(WindowInsets insets){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());return new Rect(i.left,i.top,i.right,i.bottom);}
        static void frameRate(Surface surface,float target){surface.setFrameRate(target,Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);}
        static void exits(ActivityManager manager,String name,StringBuilder info){
            for(ApplicationExitInfo exit:manager.getHistoricalProcessExitReasons(name,0,20))if((name+":game").equals(exit.getProcessName()))
                info.append("\nGame exit at ").append(exit.getTimestamp()).append(": ").append(ReportFiles.exitLabel(exit.getReason(),exit.getStatus())).append(", status ").append(exit.getStatus()).append(", PSS KiB ").append(exit.getPss());
        }
    }
    private static final class Api31 {
        static Vibrator vibrator(Activity a){VibratorManager manager=a.getSystemService(VibratorManager.class);return manager==null?null:manager.getDefaultVibrator();}
        static void frameRate(Surface surface,float target){surface.setFrameRate(target,Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);}
    }
    private PlatformCompat(){}
}
