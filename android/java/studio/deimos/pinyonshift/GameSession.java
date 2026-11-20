package studio.deimos.pinyonshift;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** A marker survives native crashes and OS kills; normal game exits clear it. */
final class GameSession {
    private static File file(Context c){return new File(MobileConfig.base(c),"state/config/android-session.txt");}
    static void record(Context c,boolean running,boolean logsEnabled){
        recordFile(file(c),running,logsEnabled);
    }
    static void recordFile(File f,boolean running){
        recordFile(f,running,true);
    }
    private static void recordFile(File f,boolean running,boolean logsEnabled){
        f.getParentFile().mkdirs();File part=new File(f.getPath()+".part");
        try(FileOutputStream out=new FileOutputStream(part)){
            out.write(((running?"running":"closed")+"\n"+System.currentTimeMillis()+"\n"+(logsEnabled?"logged":"quiet")+"\n").getBytes(StandardCharsets.UTF_8));out.getFD().sync();
        }catch(IOException ignored){return;}
        try{Files.move(part.toPath(),f.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(IOException ignored){}
    }
    static long interrupted(Context c){
        if(GameFiles.running(c))return 0;
        File marker=file(c);
        return interruptedFile(marker.exists()?marker:new File(MobileConfig.base(c),"state/logs/android-session.txt"));
    }
    static boolean lastSessionQuiet(Context c){
        File marker=file(c);
        try{
            return marker.isFile() && marker.length()<=256
                && new String(Files.readAllBytes(marker.toPath()),StandardCharsets.UTF_8).endsWith("\nquiet\n");
        }catch(IOException error){return false;}
    }
    static long interruptedFile(File f){
        try{String text=new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);return text.startsWith("running\n")?f.lastModified():0;}
        catch(IOException ignored){return 0;}
    }
    static String recoveryMessage(Context c,long interrupted) {
        android.app.ActivityManager manager=c.getSystemService(android.app.ActivityManager.class);
        if (manager!=null) try {
            for (android.app.ApplicationExitInfo exit : manager.getHistoricalProcessExitReasons(null,0,16)) {
                if (exit.getTimestamp()<interrupted || !(c.getPackageName()+":game").equals(exit.getProcessName())) continue;
                switch(exit.getReason()) {
                    case android.app.ApplicationExitInfo.REASON_LOW_MEMORY:
                        return "Android stopped the game under memory pressure. Try closing other apps or reducing Texture size in Settings → Graphics.";
                    case android.app.ApplicationExitInfo.REASON_ANR:
                        return "Android reported that the game stopped responding. Export a report and include what was on screen.";
                    case android.app.ApplicationExitInfo.REASON_CRASH_NATIVE:
                    case android.app.ApplicationExitInfo.REASON_CRASH:
                        return "Android recorded a game crash. Export a report for diagnosis, or compare one launch using the system driver.";
                    default: break;
                }
                break;
            }
        } catch (RuntimeException ignored) {}
        return "The game closed before recording a clean exit. Export a report if this happens again.";
    }
    private GameSession(){}
}
