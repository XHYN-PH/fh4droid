package studio.deimos.pinyonshift;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class ProblemReports {
    private static final AtomicBoolean EXPORTING=new AtomicBoolean();
    static void capture(Activity a){if(a instanceof LauncherActivity)((LauncherActivity)a).startDiagnosticRun();else FestivalUi.explain(a,"Performance comparison","Quit to the launcher, open Settings → Performance and choose Record a performance comparison. Drive the same route with each configuration, then export a problem report.");}
    private static void entry(ZipOutputStream zip,String name,byte[] data)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(data);zip.closeEntry();}
    private static org.json.JSONObject sessionBuild(File file){
        try(RandomAccessFile in=new RandomAccessFile(file,"r")){byte[] first=new byte[(int)Math.min(65536,in.length())];in.readFully(first);String line=new String(first,StandardCharsets.UTF_8).split("\\n",2)[0];return new org.json.JSONObject(line);}catch(Exception ignored){return new org.json.JSONObject();}
    }
    static void write(Context c,OutputStream output)throws IOException{
        try(ZipOutputStream zip=new ZipOutputStream(output)){
            String version="unknown";try{version=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception ignored){}
            byte[] manifest=null;String appCommit="unknown";
            try(InputStream in=c.getAssets().open("pinyon_shift_build.json");ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;){if(bytes.size()+n>1048576)throw new IOException("Build manifest is too large");bytes.write(b,0,n);}manifest=bytes.toByteArray();
                try{appCommit=new org.json.JSONObject(new String(manifest,StandardCharsets.UTF_8)).optString("pinyon_shift_commit","unknown");}catch(org.json.JSONException ignored){}
            }catch(FileNotFoundException ignored){}
            StringBuilder info=new StringBuilder("FH4Droid problem report\n");
            info.append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append("\nAndroid ").append(Build.VERSION.RELEASE).append(" (").append(Build.VERSION.SDK_INT).append(")\nABIs ").append(Arrays.toString(Build.SUPPORTED_ABIS)).append("\nApp ").append(version);
            ActivityManager manager=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
            if(manager!=null){ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();manager.getMemoryInfo(memory);info.append("\nRAM total ").append(memory.totalMem).append(", available ").append(memory.availMem).append(", pressure ").append(memory.lowMemory);
                for(ApplicationExitInfo exit:manager.getHistoricalProcessExitReasons(c.getPackageName(),0,20))if((c.getPackageName()+":game").equals(exit.getProcessName()))info.append("\nGame exit at ").append(exit.getTimestamp()).append(": ").append(ReportFiles.exitLabel(exit.getReason(),exit.getStatus())).append(", status ").append(exit.getStatus()).append(", PSS KiB ").append(exit.getPss());}
            PowerManager power=(PowerManager)c.getSystemService(Context.POWER_SERVICE);if(power!=null){int status=power.getCurrentThermalStatus();info.append("\nThermal status ").append(ReportFiles.thermalLabel(status)).append(" (severity ").append(status).append(", not degrees)");}
            info.append("\nSaved settings (may include next-launch changes)\n").append(MobileConfig.read(c));
            info.append("\nDriver test\n").append(AdrenoSettings.reportDetails(c));
            TouchLayout touch=new TouchLayout();touch.load(c);info.append("\nTouch controls\n").append(touch.serialize());
            info.append("\nPhone preferences\n").append(UiPreferences.sanitized(c.getSharedPreferences("mobile",0).getAll()));
            List<File> files=new ArrayList<>();File state=new File(MobileConfig.base(c),"state");collect(new File(state,"logs"),files,0);collect(new File(state,"crashes"),files,0);files.sort((a,b)->Long.compare(b.lastModified(),a.lastModified()));
            String latest="";Map<String,org.json.JSONObject> builds=new HashMap<>();
            for(File file:files){String session=ReportFiles.sessionName(file.getName());if(session!=null&&file.getName().endsWith(".jsonl")){if(session.compareTo(latest)>0)latest=session;builds.put(session,sessionBuild(file));}}
            info.append("\nLatest native session: ").append(latest.isEmpty()?"No native session recorded":latest).append("\nThe history folder contains earlier sessions. Shared logs can contain several builds. See report-index.json for provenance and truncation.\n");
            entry(zip,"device-and-settings.txt",info.toString().getBytes(StandardCharsets.UTF_8));
            if(manifest!=null)entry(zip,"build-manifest.json",manifest);
            org.json.JSONObject index=new org.json.JSONObject();org.json.JSONArray exported=new org.json.JSONArray();
            try{index.put("schema",1);index.put("app_version",version);index.put("app_source_commit",appCommit);index.put("latest_session_matches_app",!appCommit.equals("unknown")&&builds.containsKey(latest)&&appCommit.equals(builds.get(latest).optString("pinyon_shift_commit")));index.put("exported_ms",System.currentTimeMillis());index.put("latest_session",latest);index.put("files",exported);}catch(org.json.JSONException error){throw new IOException(error);}
            long budget=16L*1024*1024;int count=0;
            for(File file:files){
                if(count>=12||budget<=0)break;int limit=(int)Math.min(2L*1024*1024,budget);long original=file.length();if(original<=0)continue;
                byte[] data=ReportFiles.tail(file,limit);if(data.length==0)continue;
                String session=ReportFiles.sessionName(file.getName());boolean sameBuild=session!=null&&builds.containsKey(session)&&!appCommit.equals("unknown")&&appCommit.equals(builds.get(session).optString("pinyon_shift_commit"));String kind=session==null?"shared":session.equals(latest)&&sameBuild?"current":"history";
                String name="logs/"+kind+"/"+(++count)+"-"+file.getName().replaceAll("[^a-zA-Z0-9._-]","_");entry(zip,name,data);budget-=data.length;
                try{org.json.JSONObject record=new org.json.JSONObject();record.put("entry",name);record.put("original_bytes",original);record.put("exported_bytes",data.length);record.put("tail_truncated",data.length<original);record.put("session",session==null?org.json.JSONObject.NULL:session);record.put("category",kind);
                    if(session!=null&&builds.containsKey(session)){org.json.JSONObject build=builds.get(session);record.put("source_commit",build.optString("pinyon_shift_commit","unknown"));record.put("build_label",build.optString("build_label","unknown"));}
                    if(file.getName().equals("runtime.log"))record.put("mixed_session_history",true);exported.put(record);
                }catch(org.json.JSONException error){throw new IOException(error);}
            }
            entry(zip,"report-index.json",index.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
    private static void collect(File dir,List<File> out,int depth){
        if(depth>3||out.size()>300||java.nio.file.Files.isSymbolicLink(dir.toPath()))return;File[] files=dir.listFiles();if(files==null)return;
        for(File f:files){if(java.nio.file.Files.isSymbolicLink(f.toPath()))continue;if(f.isDirectory())collect(f,out,depth+1);
            else if(f.getName().matches(".*\\.(log|txt|csv|json|jsonl)$"))out.add(f);}
    }
    private static String reason(int reason){
        switch(reason){
            case ApplicationExitInfo.REASON_ANR:return "App not responding";
            case ApplicationExitInfo.REASON_LOW_MEMORY:return "Android low-memory kill";
            case ApplicationExitInfo.REASON_CRASH_NATIVE:return "Native crash";
            case ApplicationExitInfo.REASON_CRASH:return "Java crash";
            case ApplicationExitInfo.REASON_USER_REQUESTED:return "User requested exit";
            default:return "Android exit reason "+reason;
        }
    }
    static void action(Activity a,android.widget.LinearLayout parent,String title){
        android.widget.Button button=FestivalUi.action(a,parent,title,()->{});button.setOnClickListener(v->share(a,button));
    }
    static void share(Activity a){share(a,null);}
    private static void share(Activity a,android.widget.Button button){
        if(a.isFinishing()||a.isDestroyed())return;
        if(!EXPORTING.compareAndSet(false,true)){android.widget.Toast.makeText(a,"A report is already being prepared.",0).show();return;}
        if(a instanceof PinyonShiftActivity)((PinyonShiftActivity)a).captureReportState();
        final CharSequence label=button==null?null:button.getText();
        if(button!=null){button.setEnabled(false);button.setText("Preparing report…");}
        final Context app=a.getApplicationContext();
        new Thread(()->{
            File result=null;String failure=null;
            try{result=ReportFiles.create(app.getCacheDir(),out->write(app,out));}
            catch(IOException|RuntimeException error){failure=error.getMessage()==null?"The report could not be created.":error.getMessage();}
            final File file=result;final String error=failure;
            a.runOnUiThread(()->{
                EXPORTING.set(false);
                if(a.isFinishing()||a.isDestroyed())return;
                if(button!=null){button.setEnabled(true);button.setText(label);}
                if(error!=null){android.widget.Toast.makeText(a,error,1).show();return;}
                Uri uri=new Uri.Builder().scheme("content").authority(a.getPackageName()+".reports").appendPath(file.getName()).build();
                Intent send=new Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM,uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(ClipData.newRawUri("FH4Droid report",uri));
                try{a.startActivity(Intent.createChooser(send,"Share problem report"));}catch(ActivityNotFoundException e){android.widget.Toast.makeText(a,"No app available for sharing.",1).show();}
            });
        },"ReportExport").start();
    }
    private ProblemReports(){}
}
