package studio.deimos.pinyonshift;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Real scoped save round-trips and rejected imports must preserve both save trees. */
public final class ProfileArchiveTest {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void put(File state,String tree,String value)throws Exception{
        Path p=new File(state,tree+"/profile/save").toPath();Files.createDirectories(p.getParent());Files.write(p,value.getBytes(StandardCharsets.UTF_8));
    }
    private static String get(File state,String tree)throws Exception{return new String(Files.readAllBytes(new File(state,tree+"/profile/save").toPath()),StandardCharsets.UTF_8);}
    private static byte[] export(File state,String scope)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();SaveArchive.exportState(state,scope,out);return out.toByteArray();}
    private static byte[] archive(String marker,String... names)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(bytes)){
            z.putNextEntry(new ZipEntry("fh4droid-save.txt"));z.write(marker.getBytes(StandardCharsets.UTF_8));z.closeEntry();
            for(String name:names){z.putNextEntry(new ZipEntry(name));z.write("imported".getBytes(StandardCharsets.UTF_8));z.closeEntry();}
        }return bytes.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        Path temp=Files.createTempDirectory("fh4droid-scoped-saves-");File state=temp.resolve("state").toFile();
        try{
            put(state,SaveArchive.NORMAL,"normal");put(state,SaveArchive.MODDED,"modded");byte[] both=export(state,SaveArchive.BOTH);
            Set<String> entries=new HashSet<>();try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(both))){for(ZipEntry e;(e=z.getNextEntry())!=null;)entries.add(e.getName());}
            check(entries.contains("user/profile/save")&&entries.contains("user-modded/profile/save"),"both profiles keep distinct archive paths");
            put(state,SaveArchive.NORMAL,"new-normal");put(state,SaveArchive.MODDED,"new-modded");
            SaveArchive.importInto(state,new ByteArrayInputStream(both));
            check(get(state,SaveArchive.NORMAL).equals("normal")&&get(state,SaveArchive.MODDED).equals("modded"),"both scopes round-trip together");
            SaveArchive.restore(state);
            check(get(state,SaveArchive.NORMAL).equals("new-normal")&&get(state,SaveArchive.MODDED).equals("new-modded"),"restore returns both previous profiles");
            SaveArchive.restore(state);
            check(get(state,SaveArchive.NORMAL).equals("normal")&&get(state,SaveArchive.MODDED).equals("modded"),"restore can be reversed without overwriting either profile");
            byte[] modded=export(state,SaveArchive.MODDED);put(state,SaveArchive.NORMAL,"keep-normal");put(state,SaveArchive.MODDED,"changed");
            SaveArchive.importInto(state,new ByteArrayInputStream(modded));
            check(get(state,SaveArchive.NORMAL).equals("keep-normal")&&get(state,SaveArchive.MODDED).equals("modded"),"modded-only import never replaces the normal tree");
            SaveArchive.restore(state);check(get(state,SaveArchive.NORMAL).equals("keep-normal")&&get(state,SaveArchive.MODDED).equals("changed"),"modded-only restore leaves normal progress alone");
            File empty=temp.resolve("new-state").toFile();SaveArchive.importInto(empty,new ByteArrayInputStream(modded));
            check(get(empty,SaveArchive.MODDED).equals("modded")&&!new File(empty,"user").exists(),"first modded import does not invent a normal profile");
            SaveArchive.restore(empty);check(!new File(empty,"user-modded").exists(),"restore reinstates absence of the original profile");
            SaveArchive.restore(empty);check(get(empty,SaveArchive.MODDED).equals("modded"),"a first-import restore is reversible");
            for(byte[] bad:new byte[][]{
                archive("FH4Droid base-game saves v1\n","user-modded/profile/save"),
                archive("FH4Droid base-game saves v2\n","user/profile/../../escape"),
                archive("FH4Droid base-game saves v2\n","user-modded//escape"),
                archive("FH4Droid base-game saves v2\n","user/./profile/save"),
                archive("FH4Droid base-game saves v2\n","user/profile/save","user/profile/save/child")}){
                boolean rejected=false;try{SaveArchive.importInto(state,new ByteArrayInputStream(bad));}catch(IOException expected){rejected=true;}
                check(rejected,"malformed scoped archive rejected");
                check(get(state,SaveArchive.NORMAL).equals("keep-normal")&&get(state,SaveArchive.MODDED).equals("changed"),"invalid import leaves both live profiles unchanged");
            }
            File blocked=temp.resolve("blocked").toFile();put(blocked,SaveArchive.NORMAL,"original");Files.write(new File(blocked,"backups").toPath(),new byte[]{1});
            boolean failed=false;try{SaveArchive.importInto(blocked,new ByteArrayInputStream(both));}catch(IOException expected){failed=true;}
            check(failed&&get(blocked,SaveArchive.NORMAL).equals("original")&&!new File(blocked,"user-modded").exists(),"backup-creation failure cannot partially install profiles");
            check(LocalProfile.gamertag("  Racer 85 ").equals("Racer 85"),"gamertag formatting does not alter profile ownership");
            for(String invalid:new String[]{"","    ","a\nb","racer!","1234567890123456","\u0000bad"}){
                boolean rejected=false;try{LocalProfile.gamertag(invalid);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"reject invalid gamertag");
            }
            check(LocalProfile.configuredProfile(Collections.singletonMap("pinyon_shift_cheats","true")).equals("Modded profile"),"cheats identify separate modded saves");
            check(LocalProfile.configuredProfile(Collections.singletonMap("enabled_mods","visual_mod")).contains("determined"),"do not guess save ownership for custom mods");
            File malformed=temp.resolve("profile-is-file").toFile();Files.createDirectories(malformed.toPath());File userFile=new File(malformed,"user");Files.write(userFile.toPath(),new byte[]{9});put(malformed,SaveArchive.MODDED,"keep-modded");
            boolean rejectedFile=false;try{SaveArchive.importInto(malformed,new ByteArrayInputStream(archive("FH4Droid base-game saves v2\n","user/profile/save","user-modded/profile/save")));}catch(IOException expected){rejectedFile=true;}
            check(rejectedFile&&Files.readAllBytes(userFile.toPath())[0]==9,"a non-directory live profile is preserved on import failure");
            check(get(malformed,SaveArchive.MODDED).equals("keep-modded"),"invalid normal profile cannot alter the other scoped save");
            System.out.println("PASS: "+checks+" profile identity and scoped save archive checks");
        }finally{SaveArchive.delete(temp.toFile());}
    }
}
