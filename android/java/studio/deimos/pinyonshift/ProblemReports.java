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

final class ProblemReports {
    static void write(Context c,OutputStream output)throws IOException{
        try(ZipOutputStream zip=new ZipOutputStream(output)){
            StringBuilder info=new StringBuilder("FH4Droid problem report\n");
            info.append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append("\nAndroid ").append(Build.VERSION.RELEASE)
                .append(" (").append(Build.VERSION.SDK_INT).append(")\nABIs ").append(Arrays.toString(Build.SUPPORTED_ABIS));
            try{info.append("\nApp ").append(c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);}catch(Exception ignored){}
            ActivityManager manager=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
            if(manager!=null){ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();manager.getMemoryInfo(memory);
                info.append("\nRAM total ").append(memory.totalMem).append(", available ").append(memory.availMem).append(", pressure ").append(memory.lowMemory);
                for(ApplicationExitInfo exit:manager.getHistoricalProcessExitReasons(c.getPackageName(),0,20))
                    if((c.getPackageName()+":game").equals(exit.getProcessName()))
                        info.append("\nGame exit at ").append(exit.getTimestamp()).append(": ").append(reason(exit.getReason()))
                            .append(", status ").append(exit.getStatus()).append(", PSS KiB ").append(exit.getPss());}
            PowerManager power=(PowerManager)c.getSystemService(Context.POWER_SERVICE);
            if(power!=null)info.append("\nThermal severity ").append(power.getCurrentThermalStatus()).append(" (not degrees)");
            info.append("\nSettings\n").append(MobileConfig.read(c)).append("\nDriver test\n")
                .append(c.getSharedPreferences("mobile",0).getString("last_probe","No driver test recorded")).append("\n");
            zip.putNextEntry(new ZipEntry("device-and-settings.txt"));zip.write(info.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            List<File> files=new ArrayList<>();File state=new File(MobileConfig.base(c),"state");
            collect(new File(state,"logs"),files,0);collect(new File(state,"crashes"),files,0);
            files.sort((a,b)->Long.compare(b.lastModified(),a.lastModified()));long budget=16L*1024*1024;int count=0;
            for(File file:files){if(count>=12||budget<=0)break;int bytes=(int)Math.min(Math.min(file.length(),2L*1024*1024),budget);if(bytes<=0)continue;
                zip.putNextEntry(new ZipEntry("logs/"+(++count)+"-"+file.getName().replaceAll("[^a-zA-Z0-9._-]","_")));
                try(RandomAccessFile in=new RandomAccessFile(file,"r")){in.seek(Math.max(0,file.length()-bytes));byte[] b=new byte[65536];int left=bytes;
                    while(left>0){int n=in.read(b,0,Math.min(left,b.length));if(n<0)break;zip.write(b,0,n);left-=n;}}
                zip.closeEntry();budget-=bytes;
            }
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
    static void share(Activity a){
        new Thread(()->{try{
            File dir=new File(a.getCacheDir(),"reports");dir.mkdirs();File file=new File(dir,"fh4droid-report.zip");
            try(OutputStream out=new FileOutputStream(file)){write(a,out);}
            a.runOnUiThread(()->{Uri uri=Uri.parse("content://"+a.getPackageName()+".reports/fh4droid-report.zip");
                Intent send=new Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM,uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(ClipData.newRawUri("FH4Droid report",uri));
                try{a.startActivity(Intent.createChooser(send,"Share problem report"));}catch(ActivityNotFoundException e){android.widget.Toast.makeText(a,"No app available for sharing.",1).show();}});
        }catch(IOException error){a.runOnUiThread(()->android.widget.Toast.makeText(a,error.getMessage(),1).show());}},"ReportExport").start();
    }
    private ProblemReports(){}
}
