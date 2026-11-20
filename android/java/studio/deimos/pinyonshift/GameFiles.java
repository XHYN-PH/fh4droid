package studio.deimos.pinyonshift;
import android.app.ActivityManager;
import android.content.Context;
import android.net.Uri;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import java.io.*;
import java.util.*;
import org.json.*;
import java.nio.charset.StandardCharsets;

final class GameFiles {
    static final List<String> ISO_SUPPORT=Collections.unmodifiableList(Arrays.asList(
        "media/ui/Fonts.zip","media/ui/textures/Horizon.zip",
        "media/audio/AudioEngineConfig.xml","media/audio/AudioMixerSnapshots.xml"));
    static final Map<String,String> EXECUTABLES=new LinkedHashMap<>();
    static{
        EXECUTABLES.put("default.xex","db40df605ade49a612b35a7a24c38f6004bcb17a88ed6b48288de16df9e3987c");
        EXECUTABLES.put("SpeechFacade_default.xex","d54f6aae6ae2fd6da9b8c37d48f0a6bb295caa18ad82d0d9df66026141a41594");
        EXECUTABLES.put("XMediaFacade_default.xex","ed285beefeafcdc90cf64d6085d19c874e3f1e15072c12cdf9ba2237c88e2ce5");
    }
    static boolean running(Context c){
        ActivityManager manager=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes=manager==null?null:manager.getRunningAppProcesses();
        if(processes!=null)for(ActivityManager.RunningAppProcessInfo p:processes)if(p.processName.equals(c.getPackageName()+":game"))return true;
        return false;
    }
    static final String LINKED_FOLDER = "linked_game_folder";
    static final String ISO_URI = "iso_uri";
    static final String ISO_CACHE = "iso_cache_folder";
    static String iso(Context c) {
        return c.getSharedPreferences("mobile", 0).getString(ISO_URI, "");
    }
    static File isoCache(Context context) throws IOException {
        String cache = context.getSharedPreferences("mobile", 0).getString(ISO_CACHE, "");
        File game = new File(MobileConfig.base(context), "game");
        if (cache.isEmpty()) return new File(game,"base");
        if (!cache.matches("\\.iso-support-[a-f0-9-]{36}")) throw new IOException("Choose the ISO again to repair its local files.");
        File result = new File(game, cache);
        if (java.nio.file.Files.isSymbolicLink(result.toPath())) throw new IOException("Choose the ISO again to repair its local files.");
        return result;
    }
    static void selectSource(Context context, String iso, String cache, String folder) throws IOException {
        android.content.SharedPreferences prefs = context.getSharedPreferences("mobile",0);
        String previousIso = prefs.getString(ISO_URI,"");
        String previousCache = prefs.getString(ISO_CACHE,"");
        String previousFolder = prefs.getString(LINKED_FOLDER,"");
        if (!sourceEditor(prefs, iso, cache, folder).commit()) {
            sourceEditor(prefs, previousIso, previousCache, previousFolder).commit();
            throw new IOException("Could not save the game source. Try again.");
        }
        if (!previousCache.equals(cache) && previousCache.matches("\\.iso-support-[a-f0-9-]{36}")) {
            File old = new File(MobileConfig.base(context),"game/"+previousCache);
            try { ContentFiles.delete(old); } catch (IOException | SecurityException ignored) {}
        }
        releaseSource(context,previousIso,iso);
        releaseSource(context,previousFolder,folder);
    }
    private static android.content.SharedPreferences.Editor sourceEditor(
            android.content.SharedPreferences prefs,String iso,String cache,String folder) {
        return prefs.edit().putString(ISO_URI,iso).putString(ISO_CACHE,cache).putString(LINKED_FOLDER,folder);
    }
    static void releaseSource(Context c,String previous,String current) {
        if (!previous.isEmpty() && !previous.equals(current)) try {
            c.getContentResolver().releasePersistableUriPermission(Uri.parse(previous), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {}
    }
    static boolean linked(Context context) {
        return !context.getSharedPreferences("mobile", 0).getString(LINKED_FOLDER, "").isEmpty();
    }
    static File root(Context context) throws IOException {
        String folder = context.getSharedPreferences("mobile", 0).getString(LINKED_FOLDER, "");
        if (!iso(context).isEmpty()) return isoCache(context);
        if (folder.isEmpty()) return new File(MobileConfig.base(context), "game/base");
        Uri tree = Uri.parse(folder);
        boolean permitted = context.getContentResolver().getPersistedUriPermissions().stream()
            .anyMatch(permission -> permission.isReadPermission() && tree.equals(permission.getUri()));
        if (!permitted) throw new IOException("Access to the linked folder expired. Choose it again in Game files.");
        return directFolder(context, tree);
    }
    static File directFolder(Context context, Uri tree) throws IOException {
        if (!"com.android.externalstorage.documents".equals(tree.getAuthority())
                || !DocumentsContract.isTreeUri(tree)) {
            throw new IOException("This provider needs a copy. Choose Copy extracted folder instead.");
        }
        String[] id = DocumentsContract.getTreeDocumentId(tree).split(":", 2);
        if (id.length != 2 || id[1].isEmpty() || id[1].startsWith("/")) {
            throw new IOException("Choose the folder containing default.xex.");
        }
        StorageManager storage = context.getSystemService(StorageManager.class);
        if (storage == null) throw new IOException("Local storage is unavailable.");
        for (StorageVolume volume : storage.getStorageVolumes()) {
            String identifier = volume.isPrimary() ? "primary" : volume.getUuid();
            File directory = volume.getDirectory();
            if (directory == null || !id[0].equalsIgnoreCase(identifier)) continue;
            File volumeRoot = directory.getCanonicalFile();
            File folder = new File(volumeRoot, id[1]).getCanonicalFile();
            if (folder.toPath().startsWith(volumeRoot.toPath()) && !folder.equals(volumeRoot)
                    && folder.isDirectory() && folder.canRead() && folder.canExecute()) return folder;
        }
        throw new IOException("Android cannot read this folder directly. Choose Copy extracted folder instead.");
    }
    static boolean ready(Context context) {
        try {
            return new File(root(context), "default.xex").isFile() &&
                (linked(context) || !iso(context).isEmpty()
                    || !new File(MobileConfig.base(context), "game/.copy-incomplete").exists());
        } catch (IOException | SecurityException error) { return false; }
    }
    static final class Expected {
        final String path,hash;final long size;
        final boolean cached;
        Expected(String p,long s,String h){this(p,s,h,false);}
        Expected(String p,long s,String h,boolean cached){path=p;size=s;hash=h;this.cached=cached;}
    }
    static Map<String,Expected> catalog(Context c)throws IOException{
        try(InputStream in=c.getAssets().open("supported-game.json")){
            ByteArrayOutputStream text=new ByteArrayOutputStream();byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)text.write(b,0,n);
            JSONArray files=new JSONObject(new String(text.toByteArray(),StandardCharsets.UTF_8)).getJSONArray("files");Map<String,Expected> result=new LinkedHashMap<>();
            for(int i=0;i<files.length();i++){JSONObject f=files.getJSONObject(i);String path=f.getString("guest_path");
                result.put(path.toLowerCase(Locale.ROOT),new Expected(path,f.getLong("size_bytes"),f.getString("sha256").toLowerCase(Locale.ROOT)));}
            return result;
        }catch(JSONException e){throw new IOException("The game catalog is unavailable.",e);}
    }
    static String hash(InputStream in)throws IOException{
        try{java.security.MessageDigest d=java.security.MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)d.update(b,0,n);return DiscImage.hex(d.digest());}
        catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    static void verifyComplete(Context context) throws IOException {
        Map<String,Expected> catalog=catalog(context);
        if(context.getSharedPreferences("mobile",0).getString("iso_uri","").isEmpty()){
            verifyFolder(root(context),catalog.values());
            return;
        }
        verifyIso(context,catalog.values(),isoSupport(catalog,true));
    }
    static void verifyCollisionAudio(Context context) throws IOException {
        List<Expected> files = new ArrayList<>();
        for (Expected expected : catalog(context).values()) {
            if (expected.path.startsWith("media/audio/collisions/")) files.add(expected);
        }
        if (files.isEmpty()) throw new IOException("The collision audio catalog is unavailable.");
        verifyCatalog(context, files);
    }
    private static void verifyCatalog(Context context, Collection<Expected> files) throws IOException {
        String uri = context.getSharedPreferences("mobile", 0).getString("iso_uri", "");
        if (uri.isEmpty()) {
            verifyFolder(root(context), files);
            return;
        }
        verifyIso(context,files,Collections.emptyList());
    }
    private static List<Expected> isoSupport(Map<String,Expected> catalog,boolean executables)throws IOException{
        List<Expected> cached=new ArrayList<>();
        List<String> paths=new ArrayList<>(ISO_SUPPORT);
        if(executables)paths.addAll(EXECUTABLES.keySet());
        for(String path:paths){
            Expected wanted=catalog.get(path.toLowerCase(Locale.ROOT));
            if(wanted==null)throw new IOException("The game catalog is missing "+path+".");
            cached.add(new Expected(wanted.path,wanted.size,wanted.hash,true));
        }
        return cached;
    }
    private static void verifyIso(Context context,Collection<Expected> files,List<Expected> cached)throws IOException{
        String uri=context.getSharedPreferences("mobile",0).getString("iso_uri","");
        File folder=root(context).getCanonicalFile();
        List<Expected> checks=new ArrayList<>(cached);
        checks.addAll(files);
        try (FileInputStream input = openIso(context,uri)) {
            DiscImage disc = new DiscImage(input.getChannel());
            verifyFiles(checks, expected -> expected.cached ? hashFolder(folder,expected,true) : disc.hash(expected.path));
        }
    }
    static void verifyFolder(File folder, Collection<Expected> files) throws IOException {
        File canonical = folder.getCanonicalFile();
        verifyFiles(files, expected -> hashFolder(canonical,expected,false));
    }
    private static String hashFolder(File canonical,Expected expected,boolean cached)throws IOException{
        File file=new File(canonical,expected.path);
        String help=cached?". Choose the ISO again in Game files to rebuild its local files."
                          :". Check the selected folder.";
        if(!file.getCanonicalFile().equals(file)||!file.isFile()||file.length()!=expected.size)
            throw new IOException("Missing or damaged "+expected.path+help);
        String hash=DiscImage.hash(file);
        if(!hash.equalsIgnoreCase(expected.hash))throw new IOException("Missing or damaged "+expected.path+help);
        return hash;
    }
    private interface HashFile { String hash(Expected expected) throws IOException; }
    private static void verifyFiles(Collection<Expected> files, HashFile hash) throws IOException {
        long total = 0;
        for (Expected expected : files) total += expected.size;
        long complete = 0;
        int number = 0;
        try {
            for (Expected expected : files) {
                String title = "Checking " + (++number) + " / " + files.size() + " · " + expected.path;
                FileOperation.segment(title, complete, total);
                FileOperation.progress(title, 0, expected.size);
                if (!hash.hash(expected).equalsIgnoreCase(expected.hash)) {
                    throw new IOException("Missing or damaged " + expected.path + ". Check the source files.");
                }
                complete += expected.size;
            }
        } finally {
            FileOperation.clearSegment();
        }
        FileOperation.check();
    }
    static void verify(Context c)throws IOException{
        File game=root(c).getCanonicalFile();
        String iso=iso(c);
        Map<String,Expected> catalog = catalog(c);
        if(iso.isEmpty()){
            if(!linked(c)&&new File(MobileConfig.base(c),"game/.copy-incomplete").exists())throw new IOException("Game import is incomplete. Resume it in Game files.");
            verifyExecutables(game);
            for(String path:new String[]{"media/db/gamedb.slt","media/gamemodes.zip","media/ui/Fonts.zip"}){
                Expected expected=catalog.get(path.toLowerCase(Locale.ROOT));File file=new File(game,path);
                if(expected==null||!file.isFile()||file.length()!=expected.size)throw new IOException("Missing or incomplete "+path+". Import the complete game folder.");
            }
            return;
        }
        List<Expected> local = isoSupport(catalog,true);
        android.content.SharedPreferences cache = c.getSharedPreferences("verified-iso",0);
        try (FileInputStream input = openIso(c,iso)) {
            String before = isoIdentity(input,iso,game,local);
            FileOperation.check();
            if (before != null && before.equals(cache.getString("stamp",""))) return;
            verifyExecutables(game);
            for (Expected asset : isoSupport(catalog,false)) hashFolder(game,asset,true);
            DiscImage disc = new DiscImage(input.getChannel());
            for (Map.Entry<String,String> executable : EXECUTABLES.entrySet())
                if (!disc.hash(executable.getKey()).equals(executable.getValue()))
                    throw new IOException("The selected ISO has changed. Choose the supported disc image again.");
            String after = isoIdentity(input,iso,game,local);
            if (before != null && !before.equals(after)) throw new IOException("Game files changed during verification. Check the source again.");
            FileOperation.check();
            cache.edit().putString("stamp", before == null || after == null ? "" : after).apply();
        }
    }
    static FileInputStream openIso(Context context,String uri)throws IOException {
        android.os.ParcelFileDescriptor fd = context.getContentResolver().openFileDescriptor(Uri.parse(uri),"r");
        if (fd == null) throw new IOException("The selected ISO is unavailable.");
        return new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd);
    }
    private static void verifyExecutables(File game) throws IOException {
        for (Map.Entry<String,String> entry : EXECUTABLES.entrySet()) {
            File file = new File(game,entry.getKey());
            if (!file.isFile()) throw new IOException("Missing "+entry.getKey()+". Import your complete game.");
            if (!DiscImage.hash(file).equalsIgnoreCase(entry.getValue()))
                throw new IOException("Unsupported or damaged "+entry.getKey()+". This build uses the supported base-game disc.");
        }
    }
    private static String isoIdentity(FileInputStream disc, String uri, File game,
                                      List<Expected> files) throws IOException {
        if (!"com.android.externalstorage.documents".equals(Uri.parse(uri).getAuthority())) return null;
        String source = FileIdentity.stamp(disc.getFD());
        if (source == null) return null;
        StringBuilder key = new StringBuilder(uri).append('\n').append(source).append('\n').append(game);
        for (Expected expected : files) {
            File file = new File(game,expected.path);
            if (!file.equals(file.getCanonicalFile())) return null;
            String identity = FileIdentity.stamp(file);
            if (identity == null) return null;
            key.append('\n').append(expected.hash).append(' ').append(identity);
        }
        return key.toString();
    }
    private GameFiles(){}
}
