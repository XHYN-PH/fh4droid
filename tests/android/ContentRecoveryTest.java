package studio.deimos.pinyonshift;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.Collections;

/** Kill a separate JVM at real publication boundaries; recover in this one. */
public final class ContentRecoveryTest {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void write(File file,String text)throws IOException{ContentFiles.atomic(file,text.getBytes(StandardCharsets.UTF_8));}
    private static void killed(File root,String point)throws Exception{
        ContentTransaction.run(root,()->{
            ContentTransaction transaction=ContentTransaction.current();
            File metadata=new File(root,"config/options.json");transaction.watch(metadata);
            if(point.equals("watch"))Runtime.getRuntime().halt(17);
            transaction.replace(new File(root,"staged"),new File(root,"installed"));
            if(point.equals("replace"))Runtime.getRuntime().halt(17);
            write(metadata,"new-options");
            if(point.equals("metadata"))Runtime.getRuntime().halt(17);
            transaction.move(new File(root,"disabled"),new File(root,"enabled"));
            if(point.equals("move"))Runtime.getRuntime().halt(17);
            return null;
        });Runtime.getRuntime().halt(17);
    }
    public static void main(String[] args)throws Exception{
        if(args.length==3&&args[0].equals("child")){killed(new File(args[1]),args[2]);return;}
        File parent=Files.createTempDirectory("fh4droid-recovery-").toFile();
        try{
            for(String point:new String[]{"watch","replace","metadata","move","committed"}){
                File root=new File(parent,point);write(new File(root,"config/options.json"),"old-options");write(new File(root,"installed/data"),"old-data");write(new File(root,"staged/data"),"new-data");write(new File(root,"disabled/data"),"dlc-data");
                Process child=new ProcessBuilder(new File(System.getProperty("java.home"),"bin/java").getPath(),"-cp",System.getProperty("java.class.path"),ContentRecoveryTest.class.getName(),"child",root.getPath(),point).redirectErrorStream(true).start();
                if(!child.waitFor(20,TimeUnit.SECONDS)){child.destroyForcibly();throw new AssertionError("publication child hung");}
                ByteArrayOutputStream output=new ByteArrayOutputStream();ContentFiles.copy(child.getInputStream(),output,65536);
                check(child.exitValue()==17,"publication child failed: "+output.toString("UTF-8"));
                ContentTransaction.recover(root);ContentTransaction.recover(root); // Recovery must be idempotent.
                boolean committed=point.equals("committed");
                check(ContentFiles.text(new File(root,"installed/data"),100).equals(committed?"new-data":"old-data"),"payload mismatch after "+point);
                check(ContentFiles.text(new File(root,"config/options.json"),100).equals(committed?"new-options":"old-options"),"metadata mismatch after "+point);
                check(new File(root,committed?"enabled/data":"disabled/data").isFile(),"DLC move mismatch after "+point);
                check(!new File(root,".android-content-transaction").exists(),"journal was not cleaned after "+point);
            }
            check(ContentFiles.requiredSpace(100)==100+64L*1024*1024,"staging margin missing");
            File isolated=new File(parent,"concurrent"),config=new File(isolated,"config/settings.toml");write(config,"enabled_mods = \"old\"\nvsync = true\n");
            try{ContentTransaction.run(isolated,()->{ContentTransaction.current().watchSetting(config,"enabled_mods");MobileConfig.putFile(config,Collections.singletonMap("enabled_mods","\"new\""));MobileConfig.putFile(config,Collections.singletonMap("vsync","false"));throw new IOException("publication failed");});throw new AssertionError("failed publication committed");}catch(IOException expected){checks++;}
            check(MobileConfig.readFile(config).get("enabled_mods").equals("old"),"mod selection was not recovered");
            check(MobileConfig.readFile(config).get("vsync").equals("false"),"recovery overwrote an unrelated settings update");
            try{ContentTransaction.run(isolated,()->{ContentTransaction.current().watch(config);write(config,"bad");try{ContentTransaction.run(isolated,()->{throw new IOException("nested failure");});}catch(IOException ignored){}return null;});throw new AssertionError("caught nested failure allowed commit");}catch(IOException expected){checks++;}
            check(MobileConfig.readFile(config).get("enabled_mods").equals("old"),"nested rollback lost settings");
            write(config,"\ufeffenabled_mods = \"pc_copy\"\nvsync = true\n");
            try{ContentTransaction.run(isolated,()->{ContentTransaction.current().watchSetting(config,"enabled_mods");MobileConfig.putFile(config,Collections.singletonMap("enabled_mods","\"new\""));throw new IOException("failed BOM-backed update");});throw new AssertionError("failed update committed");}catch(IOException expected){checks++;}
            check(MobileConfig.readFile(config).get("enabled_mods").equals("pc_copy"),"BOM-backed selection was not recovered");
            File newRoot=new File(parent,"new-target"),stage=new File(newRoot,"stage");write(new File(stage,"data"),"new");
            try{ContentTransaction.run(newRoot,()->{ContentTransaction.current().replace(stage,new File(newRoot,"installed"));throw new IOException("interrupted first install");});throw new AssertionError("failed first install committed");}catch(IOException expected){checks++;}
            check(!new File(newRoot,"installed").exists(),"failed first install left published content");
            try{ContentFiles.requiredSpace(Long.MAX_VALUE);throw new AssertionError("storage overflow accepted");}catch(IOException expected){checks++;}
            System.out.println("PASS: "+checks+" process-kill recovery and storage checks");
        }finally{ContentFiles.delete(parent);}
    }
}
