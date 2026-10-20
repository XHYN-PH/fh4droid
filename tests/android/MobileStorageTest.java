package studio.deimos.pinyonshift;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

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
    }
    public static void main(String[] args)throws Exception{
        Path temp=Files.createTempDirectory("fh4droid-storage-test-");
        try{saves(temp);discs(temp);layouts();System.out.println("PASS: "+checks+" mobile storage, ISO and layout checks");}
        finally{SaveArchive.delete(temp.toFile());}
    }
}
