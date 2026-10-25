package studio.deimos.pinyonshift;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.util.concurrent.*;

/** Runs real archive and disc operations on temporary files; no Android device needed. */
public final class MobileStorageTest {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private interface IO {void run()throws Exception;}
    private static void rejects(IO action,String message)throws Exception{
        boolean rejected=false;try{action.run();}catch(IOException expected){rejected=true;}check(rejected,message);
    }
    private static byte[] archive(String... entries)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)){
            zip.putNextEntry(new ZipEntry("fh4droid-save.txt"));zip.write("FH4Droid base-game saves v1\n".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            for(String path:entries){zip.putNextEntry(new ZipEntry(path));zip.write("incoming".getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
        }return bytes.toByteArray();
    }
    private static void saves(Path temp)throws Exception{
        File state=temp.resolve("state").toFile(),root=new File(state,"user");
        root.mkdirs();Path profile=root.toPath().resolve("profile/save.bin");Files.createDirectories(profile.getParent());Files.write(profile,"original".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream export=new ByteArrayOutputStream();SaveArchive.export(root,export);
        Files.write(profile,"newer".getBytes(StandardCharsets.UTF_8));
        SaveArchive.importInto(state,new ByteArrayInputStream(export.toByteArray()));
        check(new String(Files.readAllBytes(profile),StandardCharsets.UTF_8).equals("original"),"round-trip save");
        check(SaveArchive.latest(state)!=null,"previous saves retained");
        Thread.sleep(3);SaveArchive.restore(state);
        check(new String(Files.readAllBytes(profile),StandardCharsets.UTF_8).equals("newer"),"restore previous save");
        Thread.sleep(3);SaveArchive.restore(state);
        check(new String(Files.readAllBytes(profile),StandardCharsets.UTF_8).equals("original"),"restore is reversible");
        for(String path:new String[]{"user/../../escape","user//tmp/escape","user/./","other/save.bin","user/a\\b"}){
            rejects(()->SaveArchive.importInto(state,new ByteArrayInputStream(archive(path))),"reject unsafe path "+path);
            check(new String(Files.readAllBytes(profile),StandardCharsets.UTF_8).equals("original"),"invalid archive preserves live saves");
        }
        rejects(()->SaveArchive.importInto(state,new ByteArrayInputStream(archive("user/a/../collision","user/collision"))),"reject aliases to one destination");
        rejects(()->SaveArchive.importInto(state,new ByteArrayInputStream(new byte[]{1,2,3})),"reject invalid ZIP");
        File[] staging=state.listFiles(f->f.getName().startsWith("save-import-"));check(staging!=null&&staging.length==0,"failed import cleans staging");
        Path secret=temp.resolve("not-a-save");Files.write(secret,"private".getBytes(StandardCharsets.UTF_8));
        Files.createSymbolicLink(root.toPath().resolve("link"),secret);
        rejects(()->SaveArchive.export(root,new ByteArrayOutputStream()),"export rejects symlinks");
        Files.delete(root.toPath().resolve("link"));
        check(!Files.exists(temp.resolve("escape")),"archive cannot escape state");
    }
    private static byte[] disc(){
        byte[] bytes=new byte[60*2048];ByteBuffer image=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        image.position(32*2048);image.put("MICROSOFT*XBOX*MEDIA".getBytes(StandardCharsets.US_ASCII));image.putInt(40);image.putInt(2048);
        image.position(40*2048);image.putShort((short)0);image.putShort((short)0);image.putInt(50);image.putInt(5);image.put((byte)0x20);
        image.put((byte)11);image.put("default.xex".getBytes(StandardCharsets.US_ASCII));image.position(50*2048);image.put("hello".getBytes(StandardCharsets.US_ASCII));return bytes;
    }
    private static DiscImage open(Path path)throws Exception{return new DiscImage(new RandomAccessFile(path.toFile(),"r").getChannel());}
    private static void discs(Path temp)throws Exception{
        Path iso=temp.resolve("disc.iso");Files.write(iso,disc());
        try(RandomAccessFile in=new RandomAccessFile(iso.toFile(),"r")){
            DiscImage reader=new DiscImage(in.getChannel());check(reader.files.get("default.xex").size==5,"read disc directory");
            String hash="2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";
            File out=temp.resolve("default.xex").toFile();reader.copy("default.xex",out,hash);
            check(new String(Files.readAllBytes(out.toPath()),StandardCharsets.UTF_8).equals("hello"),"copy and hash a disc entry");
            check(reader.hash("DEFAULT.XEX").equals(hash),"case-independent disc lookup");
        }
        byte[] damaged=disc();ByteBuffer.wrap(damaged).order(ByteOrder.LITTLE_ENDIAN).putInt(40*2048+8,999999);Files.write(iso,damaged);
        rejects(()->{try(RandomAccessFile f=new RandomAccessFile(iso.toFile(),"r")){new DiscImage(f.getChannel());}},"reject entry beyond image");
        damaged=disc();ByteBuffer.wrap(damaged).order(ByteOrder.LITTLE_ENDIAN).putShort(40*2048+2,(short)0x7fff);Files.write(iso,damaged);
        rejects(()->{try(RandomAccessFile f=new RandomAccessFile(iso.toFile(),"r")){new DiscImage(f.getChannel());}},"reject directory pointer beyond table");
        damaged=disc();ByteBuffer.wrap(damaged).order(ByteOrder.LITTLE_ENDIAN).putInt(40*2048+4,40).putInt(40*2048+8,2048);damaged[40*2048+12]=16;Files.write(iso,damaged);
        rejects(()->{try(RandomAccessFile f=new RandomAccessFile(iso.toFile(),"r")){new DiscImage(f.getChannel());}},"reject directory cycle");
        Files.write(iso,Arrays.copyOf(disc(),32*2048+20));
        rejects(()->{try(RandomAccessFile f=new RandomAccessFile(iso.toFile(),"r")){new DiscImage(f.getChannel());}},"reject truncated header");
    }
    private static void layouts(){
        TouchLayout source=new TouchLayout();source.values[0][0]=.71f;source.values[1][2]=.2f;source.hidden[2]=true;source.opacity=.8f;source.autoHide=false;
        TouchLayout restored=new TouchLayout();restored.parse(source.serialize());
        check(Math.abs(restored.values[0][0]-.71f)<.0001f,"layout location round trip");
        check(restored.hidden[2]&&!restored.autoHide&&Math.abs(restored.opacity-.8f)<.0001f,"layout preferences round trip");
        restored.parse("RT NaN 0.5 0.1 0\nLT 5 -9 100 0\nopacity Infinity\n");
        check(Float.isFinite(restored.values[0][0])&&restored.values[1][0]==1&&restored.values[1][1]==0&&restored.values[1][2]<=.24f,"invalid layout values are bounded");
        int left=Arrays.asList(TouchLayout.LABELS).indexOf("LEFT"),right=Arrays.asList(TouchLayout.LABELS).indexOf("RIGHT"),stick=Arrays.asList(TouchLayout.LABELS).indexOf("LS");
        check(restored.steering.equals("arrows")&&restored.enabled(left)&&restored.enabled(right)&&!restored.enabled(stick),"arrows are the default steering mode");
        restored.values[left][0]=.32f;restored.steering="stick";
        TouchLayout saved=new TouchLayout();saved.parse(restored.serialize());
        check(saved.steering.equals("stick")&&saved.enabled(stick)&&!saved.enabled(left),"steering mode round trip");
        check(Math.abs(saved.values[left][0]-.32f)<.00001f,"inactive arrow position is preserved");
        saved.parse("steering unknown\n");check(saved.steering.equals("stick"),"invalid steering keeps the valid choice");
        saved.reset();check(saved.steering.equals("arrows")&&saved.autoHide,"reset restores arrows and controller hiding");
        saved.steeringResponseMs=240;saved.analogPedals=true;saved.highContrast=true;saved.haptics=true;
        TouchLayout tuned=new TouchLayout();tuned.parse(saved.serialize());
        check(tuned.steeringResponseMs==240&&tuned.analogPedals&&tuned.highContrast&&tuned.haptics,"driving and accessibility preferences round trip");
        tuned.parse("steering_response_ms 9999\n");check(tuned.steeringResponseMs==500,"steering response is bounded");
        TouchLayout legacy=new TouchLayout();legacy.parse("RT 0.8 0.8 0.1 0\nLS 0.2 0.7 0.15 0\n");
        check(legacy.steering.equals("arrows")&&Math.abs(legacy.values[0][0]-.8f)<.00001f&&Math.abs(legacy.values[stick][2]-.15f)<.00001f,"legacy positions survive the new layout format");
        check(Arrays.equals(TouchLayout.LABELS,TouchIcons.NAMES),"every editor control has a matching vector icon");
        TouchLayout upgraded=new TouchLayout();upgraded.parse("steering_buttons 1\nLEFT 0.6 0.5 0.045 0\nRIGHT 0.7 0.5 0.045 0\nSTEER-L 0.1 0.8 0.08 0\nSTEER-R 0.28 0.8 0.08 0\n");
        check(upgraded.steering.equals("arrows")&&Math.abs(upgraded.values[left][0]-.1f)<.00001f&&Math.abs(upgraded.values[right][0]-.28f)<.00001f,"upgrade keeps steering positions separate from old D-pad positions");
        int dpadLeft=Arrays.asList(TouchLayout.LABELS).indexOf("DPAD_LEFT"),dpadRight=Arrays.asList(TouchLayout.LABELS).indexOf("DPAD_RIGHT");
        check(Math.abs(upgraded.values[dpadLeft][0]-.6f)<.00001f&&Math.abs(upgraded.values[dpadRight][0]-.7f)<.00001f,"upgrade also retains the independent D-pad positions");
        upgraded.parse("steering_buttons 0\n");check(upgraded.steering.equals("stick"),"upgrade preserves the previous stick selection");
        TouchLayout resaved=new TouchLayout();resaved.parse(upgraded.serialize());check(resaved.steering.equals("stick")&&Math.abs(resaved.values[left][0]-.1f)<.00001f,"migrated layout round trips without changing arrow positions");
        for(int i=0;i<TouchIcons.NAMES.length;i++){
            check(TouchIcons.LINES[i].length>=4&&TouchIcons.LINES[i].length%4==0,"icon contains complete segments: "+TouchIcons.NAMES[i]);
            for(float[] paths:new float[][]{TouchIcons.LINES[i],TouchIcons.HINTS[i]})
                for(int j=0;j<paths.length;j++)check(Float.isFinite(paths[j])&&Math.abs(paths[j])<TouchIcons.BOUNDS[i][j%2],"icon and prompt hint stay inside their hit target: "+TouchIcons.NAMES[i]);
            if(!TouchIcons.RECT[i])for(float[] paths:new float[][]{TouchIcons.LINES[i],TouchIcons.HINTS[i]})
                for(int j=0;j<paths.length;j+=2)check(Math.hypot(paths[j]/TouchIcons.BOUNDS[i][0],paths[j+1]/TouchIcons.BOUNDS[i][1])<.98,"round button does not clip its icon or prompt hint: "+TouchIcons.NAMES[i]);
        }
    }
    private static void settings(Path temp)throws Exception{
        File file=temp.resolve("config/settings.toml").toFile();file.getParentFile().mkdirs();
        Files.write(file.toPath(),"# keep\ncustom = 77\nvsync = true\n".getBytes(StandardCharsets.UTF_8));
        Map<String,String> values=new LinkedHashMap<>();values.put("vsync","false");values.put("pinyon_shift_fh1_shadows","false");
        MobileConfig.putFile(file,values);String text=new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
        check(text.contains("custom = 77")&&text.contains("vsync = false")&&text.contains("pinyon_shift_fh1_shadows = false"),"settings transaction preserves unrelated values");
        byte[] before=Files.readAllBytes(file.toPath());values.put("bad key","true");
        rejects(()->MobileConfig.putFile(file,values),"invalid batch is rejected");
        check(Arrays.equals(before,Files.readAllBytes(file.toPath())),"invalid setting leaves entire file untouched");
        File directory=temp.resolve("config/not-a-file").toFile();directory.mkdirs();Files.write(directory.toPath().resolve("keep"),new byte[]{1});
        rejects(()->MobileConfig.putFile(directory,Collections.singletonMap("vsync","true")),"failed atomic replacement is reported");
        check(Files.exists(directory.toPath().resolve("keep")),"failed save preserves previous destination");
        File[] parts=file.getParentFile().listFiles(f->f.getName().endsWith(".android-part"));check(parts!=null&&parts.length==0,"failed settings save cleans staging");
        check(MobileConfig.defaults().contains("anisotropic_override = 3")&&MobileConfig.defaults().contains("pinyon_shift_fh1_shadows = false"),"new Android config uses native defaults");
        for(String value:new String[]{"TU_DEBUG=sysmem","CACHE=C:\\cache","LABEL=\"custom\"","VALUE=#inside","", "line\nbreak\tand\rreturn\b\f", "\0\u007f", "café 🚗"}){
            MobileConfig.putFile(file,Collections.singletonMap("android_gpu_driver_env",MobileConfig.quote(value)));
            check(value.equals(MobileConfig.readFile(file).get("android_gpu_driver_env")),"driver setting string round trip: "+value);
        }
        Files.write(file.toPath(),("quoted = \"a#b\\\"c\" # comment\nliteral = 'C:\\cache#here'\nunicode = \"\\u00e9\\U0001f697\"\ninvalid = \"\\q\"\nsurrogate = \"\\uD800\"\n").getBytes(StandardCharsets.UTF_8));
        Map<String,String> read=MobileConfig.readFile(file);
        check("a#b\"c".equals(read.get("quoted")),"quoted hash and escaped quote are preserved");
        check("C:\\cache#here".equals(read.get("literal")),"TOML literal strings keep backslashes and hashes");
        check("é🚗".equals(read.get("unicode")),"TOML Unicode escapes are decoded");
        check(!read.containsKey("invalid")&&!read.containsKey("surrogate"),"invalid TOML escapes are not used as settings");
    }
    private static void reports(Path temp)throws Exception{
        File cache=temp.resolve("report-cache").toFile();
        File first=ReportFiles.create(cache,out->out.write(new byte[]{1,2,3}));
        check(ReportFiles.resolve(cache,first.getName()).equals(first),"completed report can be resolved");
        rejects(()->ReportFiles.resolve(cache,"../escape.zip"),"report URI cannot escape the report folder");
        rejects(()->ReportFiles.resolve(cache,"report-draft.part"),"incomplete reports cannot be shared");
        rejects(()->ReportFiles.create(cache,out->{out.write(9);throw new IOException("simulated write failure");}),"report write failure is reported");
        check(Arrays.equals(Files.readAllBytes(first.toPath()),new byte[]{1,2,3}),"failed export preserves an existing shared report");
        CountDownLatch ready=new CountDownLatch(2),release=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            Callable<File> export=()->ReportFiles.create(cache,out->{ready.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("test timeout");}catch(InterruptedException e){throw new IOException(e);}out.write(7);});
            Future<File> one=pool.submit(export),two=pool.submit(export);
            check(ready.await(5,TimeUnit.SECONDS),"concurrent reports reach their independent staging files");
            File[] completed=new File(cache,"reports").listFiles(f->f.getName().endsWith(".zip"));
            check(completed!=null&&completed.length==1,"reports are only visible after completion");
            release.countDown();File a=one.get(5,TimeUnit.SECONDS),b=two.get(5,TimeUnit.SECONDS);
            check(!a.equals(b),"concurrent reports have distinct names");
            check(Files.readAllBytes(a.toPath())[0]==7&&Files.readAllBytes(b.toPath())[0]==7,"concurrent report contents are complete");
        }finally{release.countDown();pool.shutdownNow();}
        File[] parts=new File(cache,"reports").listFiles(f->f.getName().endsWith(".part"));check(parts!=null&&parts.length==0,"report staging is cleaned after success and failure");
    }
    private static void operations(Path temp)throws Exception{
        File session=temp.resolve("session/logs/android-session.txt").toFile();
        GameSession.recordFile(session,true);check(GameSession.interruptedFile(session)>0,"running session is recoverable");
        GameSession.recordFile(session,false);check(GameSession.interruptedFile(session)==0,"intentional exit clears recovery marker");
        FileOperation cancelled=new FileOperation(null);cancelled.cancel();
        check(cancelled.isCancelled(),"cancellation status survives alternative IO exception types");
        rejects(()->{try(FileOperation scope=cancelled.attach()){FileOperation.check();}},"operation cancelled before start cannot write");
        check(!Thread.currentThread().isInterrupted(),"cancelled scope does not leak interrupt state");
        try(FileOperation scope=new FileOperation(null).attach()){
            byte[] before=Files.readAllBytes(session.toPath());scope.cancel();
            rejects(()->DiscImage.hash(session),"hash work can be cancelled");
            check(Arrays.equals(before,Files.readAllBytes(session.toPath())),"cancelled checks do not modify input");
        }
        check(!Thread.currentThread().isInterrupted(),"finished scope clears its cancellation interrupt");
    }
    public static void main(String[] args)throws Exception{
        Path temp=Files.createTempDirectory("fh4droid-storage-test-");
        try{saves(temp);discs(temp);layouts();settings(temp);operations(temp);reports(temp);System.out.println("PASS: "+checks+" mobile storage, ISO, settings, operation, report and layout checks");}
        finally{SaveArchive.delete(temp.toFile());}
    }
}
