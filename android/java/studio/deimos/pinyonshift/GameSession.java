package studio.deimos.pinyonshift;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** A marker survives native crashes and OS kills; normal game exits clear it. */
final class GameSession {
    private static File file(Context c){return new File(MobileConfig.base(c),"state/logs/android-session.txt");}
    static void record(Context c,boolean running){
        File f=file(c);f.getParentFile().mkdirs();File part=new File(f.getPath()+".part");
        try(FileOutputStream out=new FileOutputStream(part)){
            out.write(((running?"running":"closed")+"\n"+System.currentTimeMillis()+"\n").getBytes(StandardCharsets.UTF_8));out.getFD().sync();
        }catch(IOException ignored){return;}
        try{Files.move(part.toPath(),f.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(IOException ignored){}
    }
    static long interrupted(Context c){
        if(GameFiles.running(c))return 0;
        try{File f=file(c);String text=new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);return text.startsWith("running\n")?f.lastModified():0;}
        catch(IOException ignored){return 0;}
    }
    private GameSession(){}
}
