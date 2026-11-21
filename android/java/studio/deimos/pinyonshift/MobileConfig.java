package studio.deimos.pinyonshift;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Edits individual host settings without replacing unrelated PC/game settings. */
final class MobileConfig {
    private static final boolean ANDROID = "Dalvik".equals(System.getProperty("java.vm.name"));
    private static final int MAX_SIZE = 2 * 1024 * 1024;
    private static final Pattern BARE_FRACTION =
        Pattern.compile("([\\t ]*[+-]?)(\\.[0-9]+(?:[eE][+-]?[0-9]+)?[\\t ]*(?:#.*)?)");
    static File base(Context c){File f=c.getExternalFilesDir(null);return f!=null?f:c.getFilesDir();}
    static File file(Context c){return new File(base(c),SetupActivity.CONFIG_FILE);}
    private static String cachedStamp;
    private static File cachedFile;
    private static Map<String,String> cachedValues;
    static synchronized Map<String,String> read(Context c){
        File file = file(c);
        if (!ANDROID || !file.isFile()) return readFile(file);
        String stamp = FileIdentity.stamp(file);
        if (stamp != null && stamp.equals(cachedStamp) && file.equals(cachedFile))
            return new LinkedHashMap<>(cachedValues);
        Map<String,String> values = readFile(file);
        if (stamp != null && stamp.equals(FileIdentity.stamp(file))) {
            cachedStamp = stamp;
            cachedFile = file;
            cachedValues = new LinkedHashMap<>(values);
        }
        return values;
    }
    static Map<String,String> readFile(File file){
        if (ANDROID) {
            if (!file.isFile() || file.length() > MAX_SIZE) return new LinkedHashMap<>();
            try { return ContentNative.readConfig(Files.readAllBytes(file.toPath())); }
            catch (IOException ignored) {
                File backup = new File(file.getPath()+".last-good");
                try {
                    if (backup.isFile() && backup.length() <= MAX_SIZE)
                        return ContentNative.readConfig(Files.readAllBytes(backup.toPath()));
                } catch (IOException ignoredBackup) {}
                return new LinkedHashMap<>();
            }
        }
        // Host storage tests run on a JVM without Android native libraries.
        Map<String,String> values=new LinkedHashMap<>();
        if(file.length()>2*1024*1024)return values;
        Pattern row=Pattern.compile("^\\s*([a-zA-Z0-9_]+)\\s*=\\s*(.*)$");
        try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8))){
            for(String line;(line=r.readLine())!=null;){if(line.startsWith("\ufeff"))line=line.substring(1);Matcher m=row.matcher(line);
                if(m.matches()){String value=value(m.group(2));if(value!=null)values.put(m.group(1),value);}}
        }catch(IOException ignored){}return values;
    }
    /** Single-line TOML basic/literal strings; comments only start outside strings. */
    private static String value(String text){
        text=text.trim();if(text.isEmpty())return null;char quote=text.charAt(0);
        if(quote!='\"'&&quote!='\''){int comment=text.indexOf('#');String v=(comment<0?text:text.substring(0,comment)).trim();return v.isEmpty()?null:v;}
        StringBuilder out=new StringBuilder();
        for(int i=1;i<text.length();){char c=text.charAt(i++);
            if(c==quote){String rest=text.substring(i).trim();return rest.isEmpty()||rest.startsWith("#")?out.toString():null;}
            if(c<32&&c!='\t'||c==127)return null;
            if(quote=='\"'&&c=='\\'){
                if(i>=text.length())return null;char escape=text.charAt(i++);
                switch(escape){
                    case 'b':c='\b';break;case 't':c='\t';break;case 'n':c='\n';break;case 'f':c='\f';break;case 'r':c='\r';break;
                    case '\"':c='\"';break;case '\\':c='\\';break;
                    case 'u':case 'U':{
                        int count=escape=='u'?4:8;if(i+count>text.length())return null;
                        String hex=text.substring(i,i+count);if(!hex.matches("[0-9a-fA-F]+"))return null;
                        long code=Long.parseLong(hex,16);if(code>0x10ffff||code>=0xd800&&code<=0xdfff)return null;
                        out.appendCodePoint((int)code);i+=count;continue;
                    }
                    default:return null;
                }
            }
            out.append(c);
        }
        return null;
    }
    static String get(Context c,String key,String fallback){return read(c).getOrDefault(key,fallback);}
    static Map<String,String> reportSettings(Map<String,String> settings) {
        Map<String,String> report = new LinkedHashMap<>(settings);
        report.remove("user_xuid");
        report.remove("user_name");
        report.remove("pinyon_shift_device_name");
        return report;
    }
    static synchronized void put(Context c,String key,String literal)throws IOException{
        putAll(c,Collections.singletonMap(key,literal));
    }
    static synchronized void putAll(Context c,Map<String,String> updates)throws IOException{putFile(file(c),updates);}
    static synchronized void recordChange(Context c,String key,String literal,String application){
        try{
            File log=new File(base(c),"state/logs/android-settings-changes.jsonl");
            Files.createDirectories(log.getParentFile().toPath());
            if(log.length()>262144)Files.move(log.toPath(),new File(log.getPath()+".old").toPath(),StandardCopyOption.REPLACE_EXISTING);
            org.json.JSONObject event=new org.json.JSONObject();event.put("time_ms",System.currentTimeMillis());
            event.put("setting",key);event.put("value",literal);event.put("application",application);
            try(FileOutputStream out=new FileOutputStream(log,true)){out.write((event.toString()+"\n").getBytes(StandardCharsets.UTF_8));}
        }catch(Exception error){android.util.Log.w("FH4Droid","Could not record settings change",error);}
    }
    static synchronized void putFile(File f,Map<String,String> updates)throws IOException{
        Files.createDirectories(f.getParentFile().toPath());
        try(RandomAccessFile lockFile=new RandomAccessFile(new File(f.getPath()+".lock"),"rw");
            FileChannel channel=lockFile.getChannel();FileLock lock=channel.lock()){
            putFileLocked(f,updates);
        }
    }
    private static void putFileLocked(File f,Map<String,String> updates)throws IOException{
        if(f.length()>MAX_SIZE)throw new IOException("Settings file is too large.");
        for(Map.Entry<String,String> e:updates.entrySet())if(!e.getKey().matches("[a-zA-Z0-9_]+")||e.getValue()==null||e.getValue().indexOf('\n')>=0||e.getValue().indexOf('\r')>=0)throw new IOException("Invalid setting");
        Files.createDirectories(f.getParentFile().toPath());
        String text=f.isFile()?new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8):defaults();
        if(!ANDROID && text.startsWith("\ufeff"))text=text.substring(1);
        StringBuilder changes = new StringBuilder();
        for(Map.Entry<String,String> e:updates.entrySet()){
            String key=e.getKey(),literal=e.getValue();
            Matcher fraction=BARE_FRACTION.matcher(literal);
            if(fraction.matches())literal=fraction.group(1)+"0"+fraction.group(2);
            if (ANDROID) changes.append(key).append(" = ").append(literal).append('\n');
            else {
                Pattern row=Pattern.compile("(?m)^[\\t ]*"+Pattern.quote(key)+"[\\t ]*=.*$");Matcher m=row.matcher(text);
                text=m.find()?m.replaceAll(Matcher.quoteReplacement(key+" = "+literal)):text+"\n"+key+" = "+literal+"\n";
            }
        }
        writeText(f,ANDROID ? ContentNative.updateConfig(text, changes.toString()) : text);
    }
    private static void writeText(File f,String text)throws IOException{
        replaceText(f, text);
        if (ANDROID) {
            try { replaceText(new File(f.getPath()+".last-good"), text); }
            catch (IOException error) { android.util.Log.w("FH4Droid", "Could not save the settings recovery copy", error); }
        }
    }
    private static void replaceText(File f,String text)throws IOException{
        Path temp=Files.createTempFile(f.getParentFile().toPath(),"settings-",".android-part");
        try{
            try(FileOutputStream out=new FileOutputStream(temp.toFile())){out.write(text.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
            Files.move(temp,f.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
    static synchronized void restoreSetting(File f,String key,File previous)throws IOException{
        if(!key.matches("[a-zA-Z0-9_]+"))throw new IOException("Invalid recovery setting.");
        Files.createDirectories(f.getParentFile().toPath());
        try(RandomAccessFile lockFile=new RandomAccessFile(new File(f.getPath()+".lock"),"rw");FileChannel channel=lockFile.getChannel();FileLock lock=channel.lock()){
            if (ANDROID) {
                String stored = previous == null ? "" : ContentFiles.text(previous, MAX_SIZE);
                String text = f.isFile() ? ContentFiles.text(f, MAX_SIZE) : defaults();
                String restored = ContentNative.restoreConfig(text, stored, key);
                if (!text.equals(restored)) writeText(f, restored);
                return;
            }
            Pattern row=Pattern.compile("(?m)^[\\t ]*"+Pattern.quote(key)+"[\\t ]*=([^\\r\\n]*)");
            String before=previous==null?"":ContentFiles.text(previous,2*1024*1024);if(before.startsWith("\ufeff"))before=before.substring(1);Matcher match=row.matcher(before);
            if(match.find())putFileLocked(f,Collections.singletonMap(key,match.group(1).trim()));
            else if(f.isFile())writeText(f,row.matcher(ContentFiles.text(f,2*1024*1024)).replaceAll(""));
        }
    }
    static String quote(String s){StringBuilder out=new StringBuilder("\"");for(int i=0;i<s.length();i++){
        char c=s.charAt(i);switch(c){case '\\':out.append("\\\\");break;case '\"':out.append("\\\"");break;
            case '\b':out.append("\\b");break;case '\t':out.append("\\t");break;case '\n':out.append("\\n");break;
            case '\f':out.append("\\f");break;case '\r':out.append("\\r");break;
            default:if(c<32||c==127)out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else out.append(c);
        }}return out.append('\"').toString();}
    static String defaults(){return "fh4droid_fresh_hardware_defaults = true\npinyon_shift_config_schema = 29\ninput_backend = \"sdl\"\nmnk_mode = true\ngpu_backend = \"vulkan\"\ngpu_record_thread = true\nvsync = true\n"
        +"pinyon_shift_fh1_render_fps_limit = 30\npinyon_shift_fh1_source_presentation = true\npinyon_shift_capture_performance = false\n"
        +"present_effect = \"bilinear\"\nswap_post_effect = \"none\"\nfh1_msaa_single_sample = true\nclear_memory_page_state = false\n"
        +"draw_resolution_scale_x = 1\ndraw_resolution_scale_y = 1\ndisable_bloom = true\ndisable_motion_blur = true\ndisable_depth_of_field = true\n"
        +"anisotropic_override = 3\npinyon_shift_fh1_shadows = false\n";}
    private MobileConfig(){}
}
