package studio.deimos.pinyonshift;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Small last-action snapshot for follower reports; no selected URI or path. */
final class ActionBreadcrumbs {
    static void record(Context context,String action,String phase){
        try{
            org.json.JSONObject event=new org.json.JSONObject();event.put("time_ms",System.currentTimeMillis());event.put("pid",android.os.Process.myPid());
            event.put("action",clean(action));event.put("phase",clean(phase));
            ContentFiles.atomic(new File(MobileConfig.base(context),"state/logs/android-last-action.json"),event.toString().getBytes(StandardCharsets.UTF_8));
        }catch(Exception error){android.util.Log.w("FH4Droid","Could not record action breadcrumb",error);}
    }
    private static String clean(String text){return text==null?"":text.replaceAll("[\\p{Cntrl}]"," ").substring(0,Math.min(160,text.length()));}
    private ActionBreadcrumbs(){}
}
