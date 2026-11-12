package studio.deimos.pinyonshift;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class ConfigConcurrencyTest {
    public static void main(String[] args)throws Exception{
        if(args.length==3){File file=new File(args[0]);for(int i=0;i<Integer.parseInt(args[2]);i++)MobileConfig.putFile(file,Collections.singletonMap(args[1]+i,Integer.toString(i)));return;}
        File root=Files.createTempDirectory("fh4droid-config-writers-").toFile();
        try{
            File config=new File(root,"settings.toml");Files.write(config.toPath(),"unrelated = 77\n".getBytes("UTF-8"));
            List<Process> children=new ArrayList<>();for(String prefix:new String[]{"first_","second_"})children.add(new ProcessBuilder(new File(System.getProperty("java.home"),"bin/java").getPath(),"-cp",System.getProperty("java.class.path"),ConfigConcurrencyTest.class.getName(),config.getPath(),prefix,"50").inheritIO().start());
            for(Process child:children){if(!child.waitFor(30,TimeUnit.SECONDS)){child.destroyForcibly();throw new AssertionError("settings writer timed out");}if(child.exitValue()!=0)throw new AssertionError("settings writer failed");}
            Map<String,String> values=MobileConfig.readFile(config);for(String prefix:new String[]{"first_","second_"})for(int i=0;i<50;i++)if(!Integer.toString(i).equals(values.get(prefix+i)))throw new AssertionError("lost concurrent update "+prefix+i);
            if(!"77".equals(values.get("unrelated")))throw new AssertionError("unrelated setting overwritten");
            System.out.println("PASS: 101 concurrent Java settings checks");
        }finally{ContentFiles.delete(root);}
    }
}
