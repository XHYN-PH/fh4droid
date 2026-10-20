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
    static synchronized Map<String,String> read(Context c){
        Map<String,String> values=new LinkedHashMap<>();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(file(c)),StandardCharsets.UTF_8))){
            for(String line;(line=r.readLine())!=null;){Matcher m=Pattern.compile("^\\s*([a-zA-Z0-9_]+)\\s*=\\s*(\"[^\"]*\"|[^#]*?)(?:\\s*#.*)?$").matcher(line);
                if(m.matches()){String v=m.group(2).trim();if(v.startsWith("\"")&&v.endsWith("\""))v=v.substring(1,v.length()-1);values.put(m.group(1),v);}}
        }catch(IOException ignored){}return values;
    }
    static String get(Context c,String key,String fallback){return read(c).getOrDefault(key,fallback);}
    static synchronized void put(Context c,String key,String literal)throws IOException{
        if(!key.matches("[a-zA-Z0-9_]+")||literal.indexOf('\n')>=0||literal.indexOf('\r')>=0)throw new IOException("Invalid setting");
        File f=file(c);f.getParentFile().mkdirs();
        String text=f.isFile()?new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8):defaults();
        Pattern row=Pattern.compile("(?m)^\\s*"+Pattern.quote(key)+"\\s*=.*$");Matcher m=row.matcher(text);
        text=m.find()?m.replaceAll(Matcher.quoteReplacement(key+" = "+literal)):text+"\n"+key+" = "+literal+"\n";
        File temp=new File(f.getParentFile(),f.getName()+".android-part");
        try(FileOutputStream out=new FileOutputStream(temp)){out.write(text.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
        Files.move(temp.toPath(),f.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    static String quote(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
    static String defaults(){return "pinyon_shift_config_schema = 29\ninput_backend = \"sdl\"\nmnk_mode = true\ngpu_backend = \"vulkan\"\ngpu_record_thread = true\nvsync = true\n"
        +"pinyon_shift_fh1_render_fps_limit = 30\npinyon_shift_fh1_source_presentation = true\npinyon_shift_capture_performance = false\n"
        +"present_effect = \"bilinear\"\nswap_post_effect = \"none\"\nfh1_msaa_single_sample = true\nclear_memory_page_state = false\n"
        +"draw_resolution_scale_x = 1\ndraw_resolution_scale_y = 1\ndisable_bloom = true\ndisable_motion_blur = true\ndisable_depth_of_field = true\n";}
    private MobileConfig(){}
}
