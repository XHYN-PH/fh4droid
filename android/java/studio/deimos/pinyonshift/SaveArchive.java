package studio.deimos.pinyonshift;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Profile-scoped archives, fully validated before live saves are moved. */
final class SaveArchive {
    static final long SAVE_LIMIT = 4L * 1024 * 1024 * 1024;
    static final long SKIPPED_LIMIT = ContentFiles.MAX_BYTES;
    private static final long SPACE_CHECK_INTERVAL = 4L * 1024 * 1024;
    static final String IMPORT_LIMIT = "Save data expands beyond the 4 GiB import limit.";
    static final String SKIPPED_LIMIT_MESSAGE = "Archive content outside the saves expands beyond the 8 GiB scan limit.";
    private static final int FILES=8192, DEPTH=64;
    private static final String V1="FH4Droid base-game saves v1\n", V2="FH4Droid base-game saves v2\n",
        V3="FH4Droid base-game saves v3\n";
    private static final String DEFAULT_PROFILE="B13EBABEBABEBABE";
    private static final String PROFILE_MARKER="profile-id.txt";
    private static final String CONFIG_FILE="config/pinyon_shift.toml";
    private static final String PROFILE_DIRECTORY=XeniaSaveImport.TITLE+"/00000001/ForzaProfile";
    // Installed Marketplace payloads are content, never save progress.
    private static final String[] CONTENT = {
        "0000000000000000/4D5309C9/00000002", "0000000000000000/4D5309C9/Disabled/00000002",
        "0000000000000000/4D5309C9/Headers/00000002",
        "4D5309C9/00000002", "4D5309C9/Disabled/00000002", "4D5309C9/Headers/00000002"
    };
    static boolean contentPath(String relative){for(String path:CONTENT)if(relative.equalsIgnoreCase(path)||relative.toLowerCase(Locale.ROOT).startsWith(path.toLowerCase(Locale.ROOT)+"/"))return true;return false;}
    static final String NORMAL="user", MODDED="user-modded", BOTH="both";
    private static List<String> scopes(String scope)throws IOException{
        if(NORMAL.equals(scope)||MODDED.equals(scope))return Collections.singletonList(scope);
        if(BOTH.equals(scope))return Arrays.asList(NORMAL,MODDED);
        throw new IOException("Choose Normal, Modded or Both profiles.");
    }
    private static boolean rootName(String name){return NORMAL.equals(name)||MODDED.equals(name);}
    // Keep v1 exports readable by older releases; the new UI uses exportState.
    static void export(File root,OutputStream stream)throws IOException{
        try(ZipOutputStream zip=new ZipOutputStream(stream)){
            entry(zip,"fh4droid-save.txt",V1);long[] budget={0,0,0};write(root,root,NORMAL,zip,budget,0);
            if(budget[2]==0)throw new IOException("No saves found yet.");
        }
    }
    static void exportState(File state,String scope,OutputStream stream)throws IOException{
        writeArchive(state,scopes(scope),configuredOwner(state),stream);
    }
    private static void writeArchive(File root,List<String> roots,String preferred,OutputStream stream)throws IOException{
        Set<String> owners = profileOwners(root, roots, false);
        String owner = chooseOwner(owners, preferred);
        String marker = owner == null || DEFAULT_PROFILE.equals(owner) && owners.size() == 1
            ? V2 : V3 + owner + "\n";
        try(ZipOutputStream zip=new ZipOutputStream(stream)){
            entry(zip,"fh4droid-save.txt",marker);long[] budget={0,0,0};
            for(String name:roots){File folder=new File(root,name);if(Files.exists(folder.toPath(),LinkOption.NOFOLLOW_LINKS))write(folder,folder,name,zip,budget,0);}
            if(budget[2]==0)throw new IOException("No saves found in the selected profiles.");
        }
    }
    private static String configuredOwner(File state) {
        String value = MobileConfig.readFile(new File(state, CONFIG_FILE)).getOrDefault("user_xuid", DEFAULT_PROFILE);
        if (value.startsWith("0x") || value.startsWith("0X")) value = value.substring(2);
        return XeniaSaveImport.usableXuid(value) ? value.toUpperCase(Locale.ROOT) : DEFAULT_PROFILE;
    }
    private static Set<String> profileOwners(File payload, List<String> roots, boolean verifyVersions) throws IOException {
        Set<String> owners = new HashSet<>();
        for (String scope : roots) {
            File folder = new File(payload, scope);
            if (!directory(folder)) continue;
            if (verifyVersions) verifyBaseVersion(new File(folder, PROFILE_DIRECTORY + "/VersionFlags"));
            File[] profiles = folder.listFiles(file -> directory(file) && XeniaSaveImport.usableXuid(file.getName()));
            if (profiles == null) throw new IOException("Could not read the save profiles.");
            for (File profile : profiles) {
                File slot = new File(profile, PROFILE_DIRECTORY);
                if (verifyVersions) verifyBaseVersion(new File(slot, "VersionFlags"));
                File save = new File(slot, "ForzaProfile");
                if (Files.isRegularFile(save.toPath(), LinkOption.NOFOLLOW_LINKS) && save.length() > 0)
                    owners.add(profile.getName().toUpperCase(Locale.ROOT));
            }
        }
        return owners;
    }
    private static String chooseOwner(Set<String> owners, String preferred) {
        if (owners.contains(preferred)) return preferred;
        return owners.size() == 1 ? owners.iterator().next() : null;
    }
    private static String importedOwner(File state, File stage, List<String> roots, String archived) throws IOException {
        Set<String> owners = profileOwners(stage, roots, true);
        if (archived != null) {
            if (!owners.contains(archived)) throw new IOException("The backup's player profile is missing.");
            return archived;
        }
        String owner = chooseOwner(owners, configuredOwner(state));
        if (owner == null && !owners.isEmpty())
            throw new IOException("This older backup has several player profiles. Export it again from the source phone with the intended profile selected.");
        return owner;
    }
    static void verifyBaseVersion(File flags) throws IOException {
        if (!Files.exists(flags.toPath(), LinkOption.NOFOLLOW_LINKS)) return;
        byte[] header = new byte[12];
        if (!Files.isRegularFile(flags.toPath(), LinkOption.NOFOLLOW_LINKS) || flags.length() < header.length)
            throw new IOException("Invalid save version information.");
        try (DataInputStream in = new DataInputStream(new FileInputStream(flags))) {
            in.readFully(header);
        }
        if (!new String(header, 0, 4, StandardCharsets.US_ASCII).equals("cmss"))
            throw new IOException("Invalid save version information.");
        for (int i = 4; i < header.length; i++) {
            if (header[i] != 0)
                throw new IOException("This save was made with a title update. Import a base-game save for this APK. Existing saves were kept.");
        }
    }
    private static String backupOwner(File backup) throws IOException {
        File marker = new File(backup, PROFILE_MARKER);
        if (!Files.exists(marker.toPath(), LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(marker.toPath(), LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Invalid backup profile information.");
        String owner = ContentFiles.text(marker, 64).trim();
        if (!XeniaSaveImport.usableXuid(owner)) throw new IOException("Invalid backup profile information.");
        return owner.toUpperCase(Locale.ROOT);
    }
    private static void entry(ZipOutputStream zip,String name,String value)throws IOException{
        zip.putNextEntry(new ZipEntry(name));zip.write(value.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
    private static void write(File root,File dir,String prefix,ZipOutputStream zip,long[] budget,int depth)throws IOException{
        FileOperation.check();
        if(depth>DEPTH||Files.isSymbolicLink(dir.toPath()))throw new IOException("Save links or deeply nested folders are not supported.");
        File[] files=dir.listFiles();if(files==null)throw new IOException("Cannot read save folder.");
        Arrays.sort(files,Comparator.comparing(File::getName));
        for(File file:files){
            String relative=root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar,'/');
            if(contentPath(relative))continue;
            if(++budget[0]>FILES)throw new IOException("Too many save entries.");
            if(Files.isSymbolicLink(file.toPath()))throw new IOException("Save links are not supported.");
            if(file.isDirectory()){write(root,file,prefix,zip,budget,depth+1);continue;}
            if(!file.isFile())continue;
            String limit = "Save data exceeds the 4 GiB export limit.";
            if (file.length() > SAVE_LIMIT - budget[1]) throw new IOException(limit);
            budget[2]++;
            String name=prefix+"/"+root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar,'/');validateName(name);
            zip.putNextEntry(new ZipEntry(name));
            try(InputStream in=new FileInputStream(file)){
                byte[] buffer=new byte[65536];long done=0;
                for(int n;(n=in.read(buffer))!=-1;){FileOperation.check();if((budget[1]+=n)>SAVE_LIMIT)throw new IOException(limit);zip.write(buffer,0,n);done+=n;FileOperation.progress(file.getName(),done,file.length());}
            }zip.closeEntry();
        }
    }
    private static String validateName(String name)throws IOException{
        if(name.indexOf('\\')>=0||name.indexOf('\0')>=0||name.length()>2048)throw new IOException("Invalid save path.");
        String clean=name.endsWith("/")?name.substring(0,name.length()-1):name;String[] parts=clean.split("/",-1);
        if(parts.length<2||parts.length>DEPTH||!rootName(parts[0]))throw new IOException("Choose an FH4Droid save export.");
        for(String part:parts)if(part.isEmpty()||part.equals(".")||part.equals("..")||part.indexOf(':')>=0)throw new IOException("Invalid save path.");
        return clean;
    }
    /** Read and validate a full ZIP entry, staging it only when a target is supplied. */
    static long readEntry(ZipInputStream zip, ZipEntry entry, File target, byte[] buffer,
                          long remaining, String limitMessage) throws IOException {
        long declared = entry.getSize();
        if (declared > remaining) throw new IOException(limitMessage);
        if (target != null) {
            ContentFiles.requireSpace(target, Math.max(0, declared));
            Files.createDirectories(target.toPath().getParent());
        }
        long done = 0;
        long nextSpaceCheck = declared > 0 ? Math.min(declared, SPACE_CHECK_INTERVAL) : SPACE_CHECK_INTERVAL;
        String name = entry.getName();
        try (OutputStream output = target == null ? null
                : Files.newOutputStream(target.toPath(), StandardOpenOption.CREATE_NEW)) {
            for (int n; (n = zip.read(buffer)) != -1;) {
                FileOperation.check();
                if (n > remaining - done) throw new IOException(limitMessage);
                if (output != null) {
                    if (done + n > nextSpaceCheck) {
                        long chunk = declared < 0 ? SPACE_CHECK_INTERVAL
                            : Math.min(SPACE_CHECK_INTERVAL, Math.max(n, declared - done));
                        ContentFiles.requireSpace(target, chunk);
                        nextSpaceCheck = done + chunk;
                    }
                    output.write(buffer, 0, n);
                }
                done += n;
                FileOperation.progress(name, done, declared);
            }
        }
        return done;
    }
    static synchronized void importInto(File state,InputStream input)throws IOException{
        Files.createDirectories(state.toPath());File stage=Files.createTempDirectory(state.toPath(),"save-import-").toFile();
        try{
            String marker=null;long bytes=0,skipped=0;int count=0;Set<String> names=new HashSet<>(),roots=new LinkedHashSet<>();byte[] buffer=new byte[65536];
            try(ZipInputStream zip=new ZipInputStream(input)){
                for(ZipEntry e;(e=zip.getNextEntry())!=null;){
                    FileOperation.check();String name=e.getName();
                    if(name.equals("fh4droid-save.txt")){
                        if(marker!=null)throw new IOException("Duplicate save information.");
                        ByteArrayOutputStream tag=new ByteArrayOutputStream();
                        for(int n;(n=zip.read(buffer))!=-1;){if(tag.size()+n>1024)throw new IOException("Invalid save information.");tag.write(buffer,0,n);}
                        marker=new String(tag.toByteArray(),StandardCharsets.UTF_8);
                        if(!V1.equals(marker)&&!V2.equals(marker)&&!marker.matches(V3+"[0-9A-F]{16}\n"))
                            throw new IOException("This backup is not for the base-game build.");
                        continue;
                    }
                    String clean=validateName(name);
                    if(++count>FILES||!names.add(clean))throw new IOException("Duplicate save entry or too many files.");
                    int separator = clean.indexOf('/');
                    String root = clean.substring(0, separator);
                    if (contentPath(clean.substring(separator + 1))) {
                        skipped += readEntry(zip, e, null, buffer, SKIPPED_LIMIT - skipped, SKIPPED_LIMIT_MESSAGE);
                        continue;
                    }
                    Path dest=stage.toPath().resolve(clean).normalize();
                    if(!dest.startsWith(stage.toPath()))throw new IOException("Invalid save path.");
                    if (e.isDirectory()) {
                        readEntry(zip, e, null, buffer, 0, "Invalid save directory.");
                        Files.createDirectories(dest);
                        continue;
                    }
                    bytes += readEntry(zip, e, dest.toFile(), buffer, SAVE_LIMIT - bytes, IMPORT_LIMIT);
                    roots.add(root);
                }
            }
            if(marker==null||bytes==0||roots.isEmpty())throw new IOException("The archive contains no compatible saves.");
            if(V1.equals(marker)&&!roots.equals(Collections.singleton(NORMAL)))throw new IOException("A legacy archive can contain only the normal profile.");
            List<String> selected = new ArrayList<>(roots);
            String archived = marker.startsWith(V3) ? marker.substring(V3.length()).trim() : null;
            FileOperation.check();
            switchRoots(state,stage,selected,importedOwner(state,stage,selected,archived));
        }finally{if(!ContentTransaction.pending(state))delete(stage);}
    }
    private static File newBackup(File state)throws IOException{
        File parent=new File(state,"backups");
        if(Files.isSymbolicLink(parent.toPath()))throw new IOException("Backup links are not supported.");
        Files.createDirectories(parent.toPath());long stamp=System.currentTimeMillis();File[] old=parent.listFiles();
        if(old!=null)for(File file:old){String name=file.getName();if(name.matches("android-import-[0-9]{13}(?:-.*)?"))
            try{stamp=Math.max(stamp,Long.parseLong(name.substring(15,28))+1);}catch(NumberFormatException ignored){}
        }
        return Files.createTempDirectory(parent.toPath(),"android-import-"+stamp+"-").toFile();
    }
    private static void switchRoots(File state,File stage,List<String> roots,String owner)throws IOException{
        if(Files.isSymbolicLink(state.toPath())||Files.isSymbolicLink(stage.toPath()))throw new IOException("Linked save roots are not supported.");
        for(String root:roots)if(!rootName(root)||Files.isSymbolicLink(new File(state,root).toPath())||Files.isSymbolicLink(new File(stage,root).toPath()))throw new IOException("Invalid or linked profile folder.");
        for(String root:roots)for(File folder:new File[]{new File(state,root),new File(stage,root)})if(folder.exists()&&!folder.isDirectory())throw new IOException("A profile path is not a folder.");
        // A save import cannot replace installed packages, even from an old export.
        for(String root:roots)for(String path:CONTENT)if(Files.exists(new File(stage,root+"/"+path).toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("This backup contains installed DLC. Export saves using the current app first.");
        List<String> retained=new ArrayList<>();
        for(String root:roots)for(String path:CONTENT){File live=new File(state,root+"/"+path);if(Files.exists(live.toPath(),LinkOption.NOFOLLOW_LINKS)){if(Files.isSymbolicLink(live.toPath())||!live.isDirectory())throw new IOException("Invalid installed content folder.");for(File f=live;f!=null&&f.toPath().startsWith(state.toPath());f=f.getParentFile())if(Files.isSymbolicLink(f.toPath()))throw new IOException("Linked installed content is not supported.");File parent=new File(stage,root+"/"+path).getParentFile();for(File f=parent;f!=null&&f.toPath().startsWith(stage.toPath());f=f.getParentFile())if(Files.exists(f.toPath(),LinkOption.NOFOLLOW_LINKS)&&(Files.isSymbolicLink(f.toPath())||!f.isDirectory()))throw new IOException("Invalid save content parent.");retained.add(root+"/"+path);}}
        File backup=newBackup(state),payload=new File(backup,"payload");
        File marker=new File(backup,"roots.txt");
        File ownerMarker = new File(backup, PROFILE_MARKER);
        File config = new File(state, CONFIG_FILE);
        String previousOwner = configuredOwner(state);
        try{
            ContentTransaction.run(state,()->{
                ContentTransaction transaction=ContentTransaction.current();
                transaction.watch(marker);
                ContentFiles.atomic(marker,String.join("\n",roots).getBytes(StandardCharsets.UTF_8));
                transaction.watch(ownerMarker);
                ContentFiles.atomic(ownerMarker, (previousOwner+"\n").getBytes(StandardCharsets.UTF_8));
                // Move DLC out of the old roots before publishing either side of the swap.
                for(String path:retained)transaction.move(new File(state,path),new File(stage,path));
                for(String root:roots){
                    File live=new File(state,root);
                    if(live.exists())transaction.move(live,new File(payload,root));
                }
                for(String root:roots){
                    File incoming=new File(stage,root);
                    if(incoming.exists())transaction.move(incoming,new File(state,root));
                }
                if (owner != null && !owner.equals(previousOwner)) {
                    transaction.watchSetting(config, "user_xuid");
                    MobileConfig.putFile(config, Collections.singletonMap("user_xuid", MobileConfig.quote(owner)));
                }
                return null;
            });
        }catch(IOException error){
            if(!ContentTransaction.pending(state))delete(backup);
            throw error;
        }catch(Exception error){
            throw new IOException("Could not publish the save changes.",error);
        }
    }
    private static boolean hasBackupData(File folder){
        File marker=new File(folder,"roots.txt");
        if(Files.exists(marker.toPath(),LinkOption.NOFOLLOW_LINKS))
            return Files.isRegularFile(marker.toPath(),LinkOption.NOFOLLOW_LINKS);
        String[] files=folder.list((dir,name)->!name.equals("payload"));
        return files!=null&&files.length>0;
    }
    static File latest(File state){
        File[] dirs=new File(state,"backups").listFiles(f->directory(f)
                &&f.getName().startsWith("android-import-")&&hasBackupData(f));
        if(dirs==null||dirs.length==0)return null;
        Arrays.sort(dirs,Comparator.comparing(File::getName));
        return dirs[dirs.length-1];
    }
    static synchronized void restore(File state)throws IOException{
        File folder=latest(state);
        if(folder==null)throw new IOException("No previous imported-save backup found.");
        restoreBackup(state,new Backup(folder,"Before import or restore",false));
        delete(folder);
    }
    static final class Backup {
        final File folder;
        final String label;
        final boolean nativeSlot;
        Backup(File folder, String label, boolean nativeSlot) {
            this.folder = folder;
            this.label = label;
            this.nativeSlot = nativeSlot;
        }
        String scope() {
            if (nativeSlot) return "Normal saves";
            try {
                List<String> roots=backupRoots(new File(folder,"roots.txt"));
                return roots.size()>1?"Normal and modded saves":roots.contains(MODDED)?"Modded saves":"Normal saves";
            } catch (IOException error) { return "Normal saves"; }
        }
    }
    private static boolean directory(File file) {
        return Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS);
    }
    static List<Backup> backups(File state) {
        List<Backup> result = new ArrayList<>();
        File parent = new File(state, "backups");
        if (!directory(parent)) return result;
        File[] imports = parent.listFiles(file -> directory(file) && hasBackupData(file)
            && file.getName().matches("android-import-[0-9]{13}(?:-[A-Za-z0-9-]+)?"));
        if (imports != null) for (File folder : imports) {
            result.add(new Backup(folder, "Before import or restore", false));
        }
        File[] checkpoints=parent.listFiles(file->directory(file)
            && file.getName().matches("checkpoint-[0-9]{13}-[a-f0-9-]{36}"));
        if(checkpoints!=null) for(File folder:checkpoints) {
            try {
                String label=checkpointName(ContentFiles.text(new File(folder,"checkpoint.txt"),256));
                backupRoots(new File(folder,"roots.txt"));
                if (directory(new File(folder,"payload"))) result.add(new Backup(folder,label,false));
            } catch (IOException ignored) {}
        }
        File nativeParent = new File(parent, "saves");
        if (directory(nativeParent)) {
            File[] slots = nativeParent.listFiles(file -> directory(file)
                && file.getName().matches("[0-9]{8}T[0-9]{6}Z(?:-[A-Za-z0-9-]+)?")
                && directory(new File(file, NORMAL)));
            if (slots != null) for (File folder : slots) result.add(new Backup(folder, "Normal profile", true));
        }
        result.sort((a, b) -> Long.compare(b.folder.lastModified(), a.folder.lastModified()));
        return result;
    }
    static String checkpointName(String input)throws IOException {
        String name=input.trim();
        if(name.isEmpty()||name.length()>48||name.codePoints().anyMatch(Character::isISOControl))
            throw new IOException("Use a name from 1 to 48 characters without line breaks.");
        return name;
    }
    static synchronized void checkpoint(File state,String scope,String name)throws IOException{
        name=checkpointName(name);
        List<String> roots=scopes(scope);
        File backups=new File(state,"backups");
        if(Files.isSymbolicLink(state.toPath())||Files.isSymbolicLink(backups.toPath()))
            throw new IOException("Linked save folders are not supported.");
        Files.createDirectories(backups.toPath());
        Path stage=Files.createTempDirectory(backups.toPath(),".checkpoint-");
        try {
            long[] budget={0,0};
            List<String> copied=new ArrayList<>();
            for(String root:roots){
                File source=new File(state,root);
                if(!Files.exists(source.toPath(),LinkOption.NOFOLLOW_LINKS))continue;
                long previous=budget[1];
                stageBackup(source,source,stage.resolve("payload").resolve(root).toFile(),budget,0);
                if(budget[1]>previous)copied.add(root);
            }
            if(copied.isEmpty())throw new IOException("No saves found in the selected profiles.");
            Files.write(stage.resolve("checkpoint.txt"),name.getBytes(StandardCharsets.UTF_8));
            Files.write(stage.resolve("roots.txt"),copied,StandardCharsets.UTF_8);
            String owner = chooseOwner(profileOwners(stage.resolve("payload").toFile(), copied, false), configuredOwner(state));
            if (owner != null)
                Files.write(stage.resolve(PROFILE_MARKER), (owner+"\n").getBytes(StandardCharsets.UTF_8));
            FileOperation.check();
            Files.move(stage,backups.toPath().resolve("checkpoint-"+System.currentTimeMillis()+"-"+UUID.randomUUID()),
                StandardCopyOption.ATOMIC_MOVE);
        } finally { delete(stage.toFile()); }
    }
    private static Backup checkedBackup(File state, Backup selected) throws IOException {
        for (Backup backup : backups(state)) if (backup.folder.equals(selected.folder)) return backup;
        throw new IOException("This backup is no longer available.");
    }
    static void exportBackup(File state, Backup selected, OutputStream output) throws IOException {
        Backup backup = checkedBackup(state, selected);
        if (backup.nativeSlot) {
            writeArchive(backup.folder, Collections.singletonList(NORMAL), backupOwner(backup.folder), output);
        } else {
            File payload = new File(backup.folder, "payload");
            if (directory(payload)) writeArchive(payload, scopes(BOTH), backupOwner(backup.folder), output);
            else if (!new File(backup.folder, "roots.txt").exists()) export(backup.folder, output);
            else throw new IOException("The backup has no save data.");
        }
    }
    private static List<String> backupRoots(File marker)throws IOException {
        if(!Files.isRegularFile(marker.toPath(),LinkOption.NOFOLLOW_LINKS)||marker.length()>100)
            throw new IOException("Invalid backup information.");
        List<String> roots=Files.readAllLines(marker.toPath(),StandardCharsets.UTF_8);
        if(roots.isEmpty()||roots.size()>2||new HashSet<>(roots).size()!=roots.size()
                ||!roots.stream().allMatch(SaveArchive::rootName))throw new IOException("Invalid backup profiles.");
        return roots;
    }
    private static void stageBackup(File source,File dir,File target,long[] budget,int depth)throws IOException {
        FileOperation.check();
        if(depth>DEPTH||!directory(dir))throw new IOException("Invalid save backup folder.");
        File[] children=dir.listFiles();
        if(children==null)throw new IOException("Cannot read save backup folder.");
        for(File child:children){
            String relative=source.toPath().relativize(child.toPath()).toString().replace(File.separatorChar,'/');
            if(contentPath(relative))continue;
            ContentFiles.safePath(relative);
            if(directory(child)){
                stageBackup(source,child,target,budget,depth+1);
            }else if(Files.isRegularFile(child.toPath(),LinkOption.NOFOLLOW_LINKS)){
                if(++budget[1]>FILES||child.length()>SAVE_LIMIT-budget[0])throw new IOException(IMPORT_LIMIT);
                try(InputStream in=new FileInputStream(child)){
                    budget[0]+=ContentFiles.copyToFile(in,new File(target,relative),SAVE_LIMIT-budget[0]);
                }
            }else throw new IOException("Invalid file in save backup.");
        }
    }
    static synchronized void restoreBackup(File state, Backup selected) throws IOException {
        Backup backup=checkedBackup(state,selected);
        File marker=new File(backup.folder,"roots.txt");
        boolean scoped=!backup.nativeSlot&&Files.exists(marker.toPath(),LinkOption.NOFOLLOW_LINKS);
        List<String> roots=scoped?backupRoots(marker):Collections.singletonList(NORMAL);
        File payload=scoped?new File(backup.folder,"payload"):backup.folder;
        if(Files.isSymbolicLink(payload.toPath()))throw new IOException("Linked backup folders are not supported.");
        File stage=Files.createTempDirectory(state.toPath(),"save-restore-").toFile();
        try{
            long[] budget={0,0};
            for(String root:roots){
                File source=scoped||backup.nativeSlot?new File(payload,root):payload;
                if(Files.exists(source.toPath(),LinkOption.NOFOLLOW_LINKS))
                    stageBackup(source,source,new File(stage,root),budget,0);
            }
            if(!scoped&&budget[1]==0)throw new IOException("The backup has no save data.");
            FileOperation.check();
            String owner = backupOwner(backup.folder);
            if (owner == null) owner = importedOwner(state,stage,roots,null);
            else profileOwners(stage,roots,true);
            switchRoots(state,stage,roots,owner);
        }finally{
            if(!ContentTransaction.pending(state))delete(stage);
        }
    }
    static void deleteBackup(File state, Backup selected) throws IOException {
        Backup backup = checkedBackup(state, selected);
        // A pending native restore still needs the selected slot.
        if (backup.nativeSlot && new File(backup.folder.getParentFile(), "restore-pending.txt").exists()) {
            throw new IOException("Finish or cancel the queued restore first.");
        }
        delete(backup.folder);
    }
    static synchronized void resetProfiles(File state, String scope) throws IOException {
        List<String> roots = scopes(scope);
        if (roots.stream().noneMatch(name -> directory(new File(state, name)))) {
            throw new IOException("No saves found in the selected profiles.");
        }
        File stage = Files.createTempDirectory(state.toPath(), "save-reset-").toFile();
        try {
            FileOperation.check();
            switchRoots(state, stage, roots, null);
        } finally {
            if(!ContentTransaction.pending(state))delete(stage);
        }
    }
    // Called with the content lock held, after the journal has recovered.
    static void clearAbandonedStages(File state){
        File[] stages=state.listFiles(f->directory(f)
            &&f.getName().matches("(?:save-(?:import|restore|reset)|xenia-(?:import|publish))-[0-9]+"));
        if(stages==null)return;
        for(File stage:stages)try{delete(stage);}
        catch(IOException error){android.util.Log.w("FH4Droid","Could not remove abandoned save staging",error);}
        File[] checkpoints=new File(state,"backups").listFiles(f->directory(f)&&f.getName().matches("\\.checkpoint-[0-9]+"));
        if(checkpoints!=null) for(File stage:checkpoints)try{delete(stage);}
        catch(IOException error){android.util.Log.w("FH4Droid","Could not remove abandoned checkpoint",error);}
    }
    static void delete(File file)throws IOException{
        if(!Files.exists(file.toPath(),LinkOption.NOFOLLOW_LINKS))return;
        if(file.isDirectory()&&!Files.isSymbolicLink(file.toPath())){File[] children=file.listFiles();if(children==null)throw new IOException("Cannot read temporary folder.");for(File child:children)delete(child);}
        Files.delete(file.toPath());
    }
    private SaveArchive(){}
}
