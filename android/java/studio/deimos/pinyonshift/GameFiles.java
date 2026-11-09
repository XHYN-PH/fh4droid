package studio.deimos.pinyonshift;
import android.app.ActivityManager;
import android.content.Context;
import java.io.*;
import java.util.*;
import org.json.*;
import java.nio.charset.StandardCharsets;

final class GameFiles {
    static final Map<String,String> EXECUTABLES=new LinkedHashMap<>();
    static{
        EXECUTABLES.put("default.xex","db40df605ade49a612b35a7a24c38f6004bcb17a88ed6b48288de16df9e3987c");
        EXECUTABLES.put("SpeechFacade_default.xex","d54f6aae6ae2fd6da9b8c37d48f0a6bb295caa18ad82d0d9df66026141a41594");
        EXECUTABLES.put("XMediaFacade_default.xex","ed285beefeafcdc90cf64d6085d19c874e3f1e15072c12cdf9ba2237c88e2ce5");
    }
    static boolean running(Context c){
        ActivityManager manager=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes=manager==null?null:manager.getRunningAppProcesses();
        if(processes!=null)for(ActivityManager.RunningAppProcessInfo p:processes)if(p.processName.equals(c.getPackageName()+":game"))return true;
        return false;
    }
    static boolean ready(Context c){
        File base=MobileConfig.base(c);return new File(base,"game/base/default.xex").isFile()&&!new File(base,"game/.copy-incomplete").exists();
    }
    static final class Expected {
        final String path,hash;final long size;
        Expected(String p,long s,String h){path=p;size=s;hash=h;}
    }
    static Map<String,Expected> catalog(Context c)throws IOException{
        try(InputStream in=c.getAssets().open("supported-game.json")){
            ByteArrayOutputStream text=new ByteArrayOutputStream();byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)text.write(b,0,n);
            JSONArray files=new JSONObject(new String(text.toByteArray(),StandardCharsets.UTF_8)).getJSONArray("files");Map<String,Expected> result=new LinkedHashMap<>();
            for(int i=0;i<files.length();i++){JSONObject f=files.getJSONObject(i);String path=f.getString("guest_path");
                result.put(path.toLowerCase(Locale.ROOT),new Expected(path,f.getLong("size_bytes"),f.getString("sha256").toLowerCase(Locale.ROOT)));}
            return result;
        }catch(JSONException e){throw new IOException("The game catalog is unavailable.",e);}
    }
    static String hash(InputStream in)throws IOException{
        try{java.security.MessageDigest d=java.security.MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)d.update(b,0,n);return DiscImage.hex(d.digest());}
        catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    static void verifyComplete(Context c)throws IOException{
        String uri=c.getSharedPreferences("mobile",0).getString("iso_uri","");
        if(!uri.isEmpty()){
            try(android.os.ParcelFileDescriptor fd=c.getContentResolver().openFileDescriptor(android.net.Uri.parse(uri),"r")){
                if(fd==null)throw new IOException("The ISO is unavailable.");
                try(FileInputStream in=new FileInputStream(fd.getFileDescriptor())){DiscImage disc=new DiscImage(in.getChannel());
                    for(Expected e:catalog(c).values())if(!disc.hash(e.path).equalsIgnoreCase(e.hash))throw new IOException("Missing or damaged "+e.path);}
            }
        }else for(Expected e:catalog(c).values()){
            File f=new File(MobileConfig.base(c),"game/base/"+e.path);
            if(!f.isFile()||f.length()!=e.size||!DiscImage.hash(f).equals(e.hash))throw new IOException("Missing or damaged "+e.path+". Import the folder again to repair it.");
        }
    }
    static void verify(Context c)throws IOException{
        File base=MobileConfig.base(c);if(new File(base,"game/.copy-incomplete").exists())throw new IOException("Game import is incomplete. Resume it in Game files.");
        for(Map.Entry<String,String> entry:EXECUTABLES.entrySet()){
            File file=new File(base,"game/base/"+entry.getKey());
            if(!file.isFile())throw new IOException("Missing "+entry.getKey()+". Import your complete game.");
            if(!DiscImage.hash(file).equals(entry.getValue()))throw new IOException("Unsupported or damaged "+entry.getKey()+". This build uses the supported base-game disc.");
        }
        String iso=c.getSharedPreferences("mobile",0).getString("iso_uri","");
        if(iso.isEmpty()){
            for(String path:new String[]{"media/db/gamedb.slt","media/gamemodes.zip","media/ui/Fonts.zip"}){
                Expected expected=catalog(c).get(path.toLowerCase(Locale.ROOT));File file=new File(base,"game/base/"+path);
                if(!file.isFile()||file.length()!=expected.size)throw new IOException("Missing or incomplete "+path+". Import the complete game folder.");
            }
        }else{
            try(android.os.ParcelFileDescriptor fd=c.getContentResolver().openFileDescriptor(android.net.Uri.parse(iso),"r")){
                if(fd==null)throw new IOException("The selected ISO is unavailable.");
                try(FileInputStream in=new FileInputStream(fd.getFileDescriptor())){DiscImage disc=new DiscImage(in.getChannel());
                    for(Map.Entry<String,String> executable:EXECUTABLES.entrySet())
                        if(!disc.hash(executable.getKey()).equals(executable.getValue()))throw new IOException("The selected ISO has changed. Choose the supported disc image again.");}
            }
        }
    }
    private GameFiles(){}
}
