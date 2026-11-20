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
    private static Path ownedSave(File state, String owner, String contents) throws IOException {
        Path profile = new File(state, "user/"+owner+"/4D5309C9/00000001/ForzaProfile").toPath();
        Files.createDirectories(profile);
        Path save = profile.resolve("ForzaProfile");
        Files.write(save, contents.getBytes(StandardCharsets.UTF_8));
        Files.write(profile.resolve("VersionFlags"), new byte[]{'c','m','s','s',0,0,0,0,0,0,0,0});
        return save;
    }
    private static void crossPhoneRestore(Path temp) throws Exception {
        String sender = "E030000012345678", receiver = "E030000087654321";
        File source = temp.resolve("source-phone").toFile();
        File target = temp.resolve("receiving-phone").toFile();
        Path sourceSave = ownedSave(source, sender, "source-progress");
        Path targetSave = ownedSave(target, receiver, "previous-progress");
        File sourceSettings = new File(source, "config/pinyon_shift.toml");
        File targetSettings = new File(target, "config/pinyon_shift.toml");
        MobileConfig.putFile(sourceSettings, Collections.singletonMap("user_xuid", MobileConfig.quote(sender)));
        Map<String,String> settings = new HashMap<>();
        settings.put("user_xuid", MobileConfig.quote(receiver));
        settings.put("pinyon_shift_master_volume", "72");
        MobileConfig.putFile(targetSettings, settings);

        byte[] transfer = export(source, SaveArchive.NORMAL);
        SaveArchive.importInto(target, new ByteArrayInputStream(transfer));
        check(MobileConfig.readFile(targetSettings).get("user_xuid").equals(sender),
            "cross-phone import selects the source profile");
        Path imported = target.toPath().resolve(source.toPath().relativize(sourceSave));
        check(Arrays.equals(Files.readAllBytes(imported), Files.readAllBytes(sourceSave))
            && MobileConfig.readFile(targetSettings).get("pinyon_shift_master_volume").equals("72"),
            "profile transfer retains save bytes and the receiving phone's settings");
        SaveArchive.restore(target);
        check(MobileConfig.readFile(targetSettings).get("user_xuid").equals(receiver)
            && new String(Files.readAllBytes(targetSave), StandardCharsets.UTF_8).equals("previous-progress"),
            "restoring the pre-import checkpoint returns its original profile selection");

        ByteArrayOutputStream legacy = new ByteArrayOutputStream();
        SaveArchive.export(new File(source, SaveArchive.NORMAL), legacy);
        File fresh = temp.resolve("fresh-phone").toFile();
        SaveArchive.importInto(fresh, new ByteArrayInputStream(legacy.toByteArray()));
        check(MobileConfig.readFile(new File(fresh, "config/pinyon_shift.toml")).get("user_xuid").equals(sender),
            "older save ZIPs with one known owner restore into a fresh phone");

        Files.write(sourceSave.resolveSibling("VersionFlags"), new byte[]{'c','m','s','s',0,0,0,4,0,0,0,2});
        boolean rejected = false;
        try { SaveArchive.importInto(target, new ByteArrayInputStream(export(source, SaveArchive.NORMAL))); }
        catch (IOException expected) { rejected = expected.getMessage().contains("title update"); }
        check(rejected && Files.exists(targetSave) && !Files.exists(imported)
            && MobileConfig.readFile(targetSettings).get("user_xuid").equals(receiver),
            "a title-update save is blocked before it can replace live progress");
    }
    private static void forzaProfileImport(Path temp) throws Exception {
        File target = temp.resolve("console-import-state").toFile();
        String localOwner = "E030000087654321", packageOwner = "E030000012345678";
        Path normal = ownedSave(target, localOwner, "keep-normal");
        put(target, SaveArchive.MODDED, "old-modded");
        File settings = new File(target, "config/pinyon_shift.toml");
        MobileConfig.putFile(settings, Collections.singletonMap("user_xuid", MobileConfig.quote(localOwner)));
        Path dlc = target.toPath().resolve("user-modded/4D5309C9/00000002/installed/payload");
        Files.createDirectories(dlc.getParent());
        Files.write(dlc, new byte[]{11});

        File stage = Files.createTempDirectory(target.toPath(), "save-import-").toFile();
        Path incoming = stage.toPath().resolve("profile");
        Files.createDirectory(incoming);
        Files.write(incoming.resolve("ForzaProfile"), new byte[]{21});
        Files.write(incoming.resolve("PlayerDatabase"), new byte[]{31});
        Path flags = incoming.resolve("VersionFlags");
        Files.write(flags, new byte[]{'c','m','s','s',0,0,0,4,0,0,0,1});
        long xuid = Long.parseUnsignedLong(packageOwner, 16);
        boolean rejected = false;
        try { SaveArchive.installForzaProfile(target, stage, SaveArchive.MODDED, xuid); }
        catch (IOException expected) { rejected = expected.getMessage().contains("title update"); }
        check(rejected && get(target, SaveArchive.MODDED).equals("old-modded")
                && SaveArchive.latest(target) == null
                && MobileConfig.readFile(settings).get("user_xuid").equals(localOwner),
            "console title-update save is rejected before modifying progress or settings");

        Files.write(flags, new byte[]{'c','m','s','s',0,0,0,0,0,0,0,0});
        SaveArchive.installForzaProfile(target, stage, SaveArchive.MODDED, xuid);
        Path imported = target.toPath().resolve("user-modded/" + packageOwner + "/4D5309C9/00000001/ForzaProfile");
        check(Files.readAllBytes(imported.resolve("ForzaProfile"))[0] == 21
                && Files.readAllBytes(imported.resolve("PlayerDatabase"))[0] == 31,
            "console import installs the complete profile in the selected slot");
        check(Files.exists(normal) && Files.readAllBytes(dlc)[0] == 11
                && MobileConfig.readFile(settings).get("user_xuid").equals(localOwner)
                && ContentFiles.text(new File(target, "user-modded/profile-id.txt"), 64).equals(packageOwner),
            "modded console import preserves the normal player and keeps a scoped owner");
        File receiver = temp.resolve("console-import-receiver").toFile();
        Path receiverNormal = ownedSave(receiver, localOwner, "receiving-normal");
        File receiverSettings = new File(receiver, "config/pinyon_shift.toml");
        MobileConfig.putFile(receiverSettings, Collections.singletonMap("user_xuid", MobileConfig.quote(localOwner)));
        SaveArchive.importInto(receiver, new ByteArrayInputStream(export(target, SaveArchive.MODDED)));
        check(Files.exists(receiverNormal)
                && MobileConfig.readFile(receiverSettings).get("user_xuid").equals(localOwner)
                && ContentFiles.text(new File(receiver, "user-modded/profile-id.txt"), 64).equals(packageOwner),
            "exporting a scoped modded profile keeps that ownership when restored");
        SaveArchive.restore(target);
        check(Files.notExists(imported) && Files.exists(normal)
                && get(target, SaveArchive.MODDED).equals("old-modded")
                && MobileConfig.readFile(settings).get("user_xuid").equals(localOwner),
            "console import can be undone with the previous owner and saves intact");

        SaveArchive.resetProfiles(target, SaveArchive.MODDED);
        check(new File(target, SaveArchive.MODDED).isDirectory()
                && Files.notExists(new File(target, "user-modded/profile/save").toPath())
                && Files.exists(normal) && Files.readAllBytes(dlc)[0] == 11,
            "clear retains a modded root and preserves normal saves and DLC");
        SaveArchive.restore(target);
        check(get(target, SaveArchive.MODDED).equals("old-modded"), "clear can be undone");
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
            // DLC shares the user tree but must neither inflate backups nor be replaced by them.
            Path dlc=new File(state,"user/4D5309C9/00000002/example/payload").toPath();Files.createDirectories(dlc.getParent());try(RandomAccessFile large=new RandomAccessFile(dlc.toFile(),"rw")){large.setLength(600L*1024*1024);}
            Path disabled=new File(state,"user/4D5309C9/Disabled/00000002/example/data").toPath();Files.createDirectories(disabled.getParent());Files.write(disabled,new byte[]{17});
            Path header=new File(state,"user/4D5309C9/Headers/00000002/example.header").toPath();Files.createDirectories(header.getParent());Files.write(header,new byte[]{42});
            byte[] small=export(state,SaveArchive.NORMAL);check(small.length<10000,"DLC is excluded before applying save archive budgets");
            SaveArchive.importInto(state,new ByteArrayInputStream(small));check(Files.size(dlc)==600L*1024*1024&&Files.readAllBytes(disabled)[0]==17&&Files.readAllBytes(header)[0]==42,"save import retains active, disabled and licensed DLC");
            SaveArchive.restore(state);check(Files.size(dlc)==600L*1024*1024&&Files.readAllBytes(header)[0]==42,"save restore retains installed DLC");
            SaveArchive.importInto(state, new ByteArrayInputStream(archive("FH4Droid base-game saves v2\n",
                "user/profile/save", "user/4D5309C9/00000002/old/data",
                "user/" + DlcStore.TITLE + "/Disabled/00000002/old/data")));
            check(get(state, SaveArchive.NORMAL).equals("imported") && Files.size(dlc) == 600L * 1024 * 1024
                    && !new File(state, "user/4D5309C9/00000002/old").exists()
                    && !new File(state, "user/" + DlcStore.TITLE + "/Disabled/00000002/old").exists(),
                    "older mixed archives import save data and preserve installed DLC");
            boolean dlcOnly = false;
            try {
                SaveArchive.importInto(state, new ByteArrayInputStream(archive("FH4Droid base-game saves v2\n",
                    "user/4D5309C9/00000002/old/data")));
            } catch (IOException expected) { dlcOnly = true; }
            check(dlcOnly && get(state, SaveArchive.NORMAL).equals("imported"), "DLC alone never replaces live saves");

            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive(
                    "FH4Droid base-game saves v2\n", "user/profile/save")))) {
                zip.getNextEntry();
                ZipEntry entry = zip.getNextEntry();
                check(entry.getSize() == -1, "streamed ZIP entry size is initially unknown");
                boolean limited = false;
                try {
                    SaveArchive.readEntry(zip, entry, null, new byte[4], 7, SaveArchive.IMPORT_LIMIT);
                } catch (IOException expected) { limited = expected.getMessage().equals(SaveArchive.IMPORT_LIMIT); }
                check(limited, "unknown expanded size is enforced while reading");
            }

            File installed = temp.resolve("installed-content-state").toFile();
            put(installed, SaveArchive.NORMAL, "normal");
            put(installed, SaveArchive.MODDED, "modded");
            Path packagePath = installed.toPath().resolve("user/" + DlcStore.TITLE + "/00000002/example/payload");
            Files.createDirectories(packagePath.getParent());
            try (RandomAccessFile large = new RandomAccessFile(packagePath.toFile(), "rw")) {
                large.setLength(600L * 1024 * 1024);
            }
            check(export(installed, SaveArchive.BOTH).length < 10000, "current DLC path stays outside both-profile exports");
            SaveArchive.resetProfiles(installed, SaveArchive.NORMAL);
            check(!new File(installed, "user/profile/save").exists() && get(installed, SaveArchive.MODDED).equals("modded")
                    && Files.size(packagePath) == 600L * 1024 * 1024, "normal reset preserves DLC and modded progress");
            SaveArchive.Backup backup = SaveArchive.backups(installed).get(0);
            SaveArchive.restoreBackup(installed, backup);
            check(get(installed, SaveArchive.NORMAL).equals("normal") && get(installed, SaveArchive.MODDED).equals("modded")
                    && Files.size(packagePath) == 600L * 1024 * 1024, "restore a selected reset backup without replacing DLC");
            SaveArchive.deleteBackup(installed, backup);
            check(!backup.folder.exists() && get(installed, SaveArchive.NORMAL).equals("normal"), "backup deletion keeps live progress");
            File absentState = temp.resolve("previously-absent-profile").toFile();
            SaveArchive.importInto(absentState, new ByteArrayInputStream(archive(
                "FH4Droid base-game saves v2\n", "user/profile/save")));
            SaveArchive.Backup absent=SaveArchive.backups(absentState).get(0);
            SaveArchive.restoreBackup(absentState,absent);
            check(!new File(absentState,SaveArchive.NORMAL).exists() && absent.folder.isDirectory(),
                "selected snapshot can restore a missing profile and retains its backup");
            crossPhoneRestore(temp);
            forzaProfileImport(temp);
            System.out.println("PASS: "+checks+" profile identity and scoped save archive checks");
        }finally{SaveArchive.delete(temp.toFile());}
    }
}
