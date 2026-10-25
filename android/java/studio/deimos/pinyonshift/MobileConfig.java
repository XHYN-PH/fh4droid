package studio.deimos.pinyonshift;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Edits individual host settings without replacing unrelated PC/game settings. */
final class MobileConfig {
    static File base(Context c){File f=c.getExternalFilesDir(null);return f!=null?f:c.getFilesDir();}
    static File file(Context c){return new File(base(c),SetupActivity.CONFIG_FILE);}
    static synchronized Map<String,String> read(Context c){return readFile(file(c));}
    static Map<String,String> readFile(File file){
        Map<String,String> values=new LinkedHashMap<>();
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
    static synchronized void put(Context c,String key,String literal)throws IOException{
        putAll(c,Collections.singletonMap(key,literal));
    }
    static synchronized void putAll(Context c,Map<String,String> updates)throws IOException{putFile(file(c),updates);}
    static synchronized void recordChange(Context c,String key,String literal,boolean live){
        try{
            File log=new File(base(c),"state/logs/android-settings-changes.jsonl");
            Files.createDirectories(log.getParentFile().toPath());
            if(log.length()>262144)Files.move(log.toPath(),new File(log.getPath()+".old").toPath(),StandardCopyOption.REPLACE_EXISTING);
            org.json.JSONObject event=new org.json.JSONObject();event.put("time_ms",System.currentTimeMillis());
            event.put("setting",key);event.put("value",literal);event.put("application",live?"live requested":"next launch");
            try(FileOutputStream out=new FileOutputStream(log,true)){out.write((event.toString()+"\n").getBytes(StandardCharsets.UTF_8));}
        }catch(Exception error){android.util.Log.w("FH4Droid","Could not record settings change",error);}
    }
    static void putFile(File f,Map<String,String> updates)throws IOException{
        for(Map.Entry<String,String> e:updates.entrySet())if(!e.getKey().matches("[a-zA-Z0-9_]+")||e.getValue()==null||e.getValue().indexOf('\n')>=0||e.getValue().indexOf('\r')>=0)throw new IOException("Invalid setting");
        Files.createDirectories(f.getParentFile().toPath());
        String text=f.isFile()?new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8):defaults();
        if(text.startsWith("\ufeff"))text=text.substring(1);
        for(Map.Entry<String,String> e:updates.entrySet()){
            String key=e.getKey(),literal=e.getValue();Pattern row=Pattern.compile("(?m)^[\\t ]*"+Pattern.quote(key)+"[\\t ]*=.*$");Matcher m=row.matcher(text);
            text=m.find()?m.replaceAll(Matcher.quoteReplacement(key+" = "+literal)):text+"\n"+key+" = "+literal+"\n";
        }
        Path temp=Files.createTempFile(f.getParentFile().toPath(),"settings-",".android-part");
        try{
            try(FileOutputStream out=new FileOutputStream(temp.toFile())){out.write(text.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
            Files.move(temp,f.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
    static String quote(String s){StringBuilder out=new StringBuilder("\"");for(int i=0;i<s.length();i++){
        char c=s.charAt(i);switch(c){case '\\':out.append("\\\\");break;case '\"':out.append("\\\"");break;
            case '\b':out.append("\\b");break;case '\t':out.append("\\t");break;case '\n':out.append("\\n");break;
            case '\f':out.append("\\f");break;case '\r':out.append("\\r");break;
            default:if(c<32||c==127)out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else out.append(c);
        }}return out.append('\"').toString();}
    static String defaults(){return "pinyon_shift_config_schema = 29\ninput_backend = \"sdl\"\nmnk_mode = true\ngpu_backend = \"vulkan\"\ngpu_record_thread = true\nvsync = true\n"
        +"pinyon_shift_fh1_render_fps_limit = 30\npinyon_shift_fh1_source_presentation = true\npinyon_shift_capture_performance = false\n"
        +"present_effect = \"bilinear\"\nswap_post_effect = \"none\"\nfh1_msaa_single_sample = true\nclear_memory_page_state = false\n"
        +"draw_resolution_scale_x = 1\ndraw_resolution_scale_y = 1\ndisable_bloom = true\ndisable_motion_blur = true\ndisable_depth_of_field = true\n"
        +"anisotropic_override = 3\npinyon_shift_fh1_shadows = false\n";}
    private MobileConfig(){}
}
