package studio.deimos.pinyonshift;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Mobile front end for upstream's mod manifests and ordered game overlays. */
final class ModStore {
    static final String GENERATED="zz-db-patches",BASE_BUILD="DB40DF605ADE49A6";
    static File state(Context c){return new File(MobileConfig.base(c),"state");}
    static File root(Context c){return new File(state(c),"mods");}
    static String name(String value)throws IOException{if(value==null||!value.matches("[A-Za-z0-9_-]{1,64}")||value.equals(GENERATED)||value.equals("zz-archive-patches"))throw new IOException("Invalid or reserved mod name.");return value;}
    static List<String> enabled(Context c){
        List<String> out=new ArrayList<>();for(String value:MobileConfig.get(c,"enabled_mods","").split("[,;]")){value=value.trim();if(value.matches("[A-Za-z0-9_-]{1,64}")&&!out.contains(value))out.add(value);}return out;
    }
    static List<JSONObject> list(Context c)throws Exception{
        List<JSONObject> out=new ArrayList<>();File[] dirs=root(c).listFiles();if(dirs==null)return out;List<String> on=enabled(c);
        Arrays.sort(dirs,Comparator.comparing(File::getName,String.CASE_INSENSITIVE_ORDER));
        for(File dir:dirs){if(!dir.isDirectory()||dir.getName().equals(GENERATED)||dir.getName().equals("zz-archive-patches")||dir.getName().startsWith("."))continue;
            try{JSONObject item=manifest(dir);if(item.optBoolean("always_enabled")&&new File(dir,".pinyon-builtin").isFile())continue;item.put("enabled",on.contains(dir.getName()));item.put("order",on.indexOf(dir.getName()));out.add(item);}
            catch(Exception error){JSONObject bad=new JSONObject();bad.put("name",dir.getName());bad.put("title",dir.getName());bad.put("problem",error.getMessage());out.add(bad);}}
        return out;
    }
    static JSONObject manifest(File dir)throws Exception{
        name(dir.getName());if(Files.isSymbolicLink(dir.toPath()))throw new IOException("Mod folder is invalid.");
        JSONObject data=ContentNative.manifest(new File(dir,"mod.toml"));
        if(!dir.getName().equals(data.optString("name"))||data.optInt("abi",-1)!=1)throw new IOException("Mod name or ABI is incompatible.");
        String build=data.optString("game_version",BASE_BUILD);if(!build.equals(BASE_BUILD))throw new IOException("This APK uses the base game; this mod targets another build.");
        String library=data.optString("library","");if(!library.isEmpty()){
            File binary=new File(dir,ContentFiles.safePath(library));if(!binary.getCanonicalPath().startsWith(dir.getCanonicalPath()+File.separator)||!binary.isFile()||Files.isSymbolicLink(binary.toPath()))throw new IOException("Mod library path is invalid or missing.");
        }
        if(!data.has("title"))data.put("title",dir.getName());return data;
    }
    static void importDirectory(Context c,File source)throws Exception{importDirectory(c,source,false);}
    private static void importDirectory(Context c,File source,boolean builtin)throws Exception{
        ContentFiles.locked(c,()->{
            List<File> manifests=new ArrayList<>();for(File file:ContentFiles.files(source))if(file.getName().equals("mod.toml"))manifests.add(file);
            if(manifests.size()!=1)throw new IOException("Choose one mod containing a single mod.toml manifest.");
            JSONObject data=ContentNative.manifest(manifests.get(0));String id=name(data.optString("name"));if(new File(root(c),id).exists())throw new IOException("This mod is already installed. Disable and remove it before importing a replacement.");File stage=ContentFiles.temporary(c,"mod-");
            try{File content=new File(stage,id);copyImportedDirectory(manifests.get(0).getParentFile(),content,builtin);manifest(content);validateNativeFiles(content);ContentFiles.publish(content,new File(root(c),id));}
            finally{ContentFiles.delete(stage);}return null;
        });
    }
    static void copyImportedDirectory(File source,File destination,boolean builtin)throws IOException{
        ContentFiles.copyTree(source,destination);
        if(!builtin)ContentFiles.delete(new File(destination,".pinyon-builtin"));
    }
    static void importZip(Context c,File archive)throws Exception{
        File stage=ContentFiles.temporary(c,"mod-zip-");try{ContentFiles.unzip(archive,stage);importDirectory(c,stage);}finally{ContentFiles.delete(stage);}
    }
    private static void validateNativeFiles(File dir)throws IOException{
        for(File file:ContentFiles.files(dir)){
            String lower=file.getName().toLowerCase(Locale.ROOT);
            if(lower.endsWith(".dll")||lower.endsWith(".dylib")||lower.endsWith(".exe"))throw new IOException("This mod contains a desktop binary. Use an Android ARM64 build.");
            if(lower.endsWith(".so")){byte[] head=new byte[20];try(DataInputStream in=new DataInputStream(new FileInputStream(file))){in.readFully(head);}if(head[0]!=127||head[1]!='E'||head[2]!='L'||head[3]!='F'||head[4]!=2||head[5]!=1||(head[18]&255)!=183||head[19]!=0)throw new IOException("Native mod must be an Android ARM64 library.");}
        }
    }
    static void installBuiltin(Context c,String id)throws Exception{
        if(!id.equals("festival_pacing")&&!id.equals("hidden_cars"))throw new IOException("Unknown built-in mod.");
        File stage=ContentFiles.temporary(c,"builtin-");try{copyAssetTree(c,"builtin-mods/"+id,new File(stage,id));ContentFiles.atomic(new File(stage,id+"/.pinyon-builtin"),new byte[0]);importDirectory(c,stage,true);}finally{ContentFiles.delete(stage);}
    }
    private static void copyAssetTree(Context c,String asset,File destination)throws IOException{
        String[] entries=c.getAssets().list(asset);if(entries!=null&&entries.length>0){Files.createDirectories(destination.toPath());for(String child:entries)copyAssetTree(c,asset+"/"+child,new File(destination,child));}
        else{Files.createDirectories(destination.getParentFile().toPath());try(InputStream in=c.getAssets().open(asset);OutputStream out=new FileOutputStream(destination)){ContentFiles.copy(in,out,1024*1024);}}
    }
    static void setEnabled(Context c,String id,boolean value)throws Exception{
        ContentFiles.locked(c,()->{name(id);List<String> on=enabled(c);on.remove(GENERATED);
            if(value){JSONObject data=manifest(new File(root(c),id));validateSupported(c,id,data);if(!on.contains(id))on.add(id);}else on.remove(id);
            validateDependencies(c,on);rebuild(c,on,null);return null;});
    }
    static void move(Context c,String id,int direction)throws Exception{
        ContentFiles.locked(c,()->{name(id);List<String> on=enabled(c);on.remove(GENERATED);int index=on.indexOf(id),next=index+direction;if(index<0||next<0||next>=on.size())return null;Collections.swap(on,index,next);rebuild(c,on,null);return null;});
    }
    static void disableAll(Context c)throws Exception{
        ContentFiles.locked(c,()->{commit(c,new ArrayList<>(),null,null,new File(root(c),GENERATED));return null;});
    }
    static void remove(Context c,String id)throws Exception{
        ContentFiles.locked(c,()->{removeDirectory(root(c),id,enabled(c));return null;});
    }
    static void removeDirectory(File mods,String id,List<String> enabled)throws Exception{
        name(id);File dir=new File(mods,id);
        if(enabled.contains(id))throw new IOException("Disable the mod before removing it.");
        // Damaged user manifests must not prevent recovery. Only trusted
        // built-ins need their always-enabled protection checked here.
        if(new File(dir,".pinyon-builtin").isFile()&&manifest(dir).optBoolean("always_enabled"))throw new IOException("This built-in mod must remain installed.");
        ContentFiles.delete(dir);
    }
    private static void validateSupported(Context c,String id,JSONObject data)throws Exception{
        File dir=new File(root(c),id);
        for(String feature:new String[]{"members","add","merge","merge-update","strings"})if(new File(dir,feature).isDirectory())throw new IOException("This mod needs archive preparation with the PC mod tools. Import its prepared game overlays.");
        if(new File(dir,"code").isDirectory()){
            boolean built=false;for(File file:ContentFiles.files(new File(dir,"code")))if(file.getName().endsWith(".so"))built=true;
            if(!built)throw new IOException("Native mod source needs an Android ARM64 build first.");
        }
    }
    private static void validateDependencies(Context c,List<String> on)throws Exception{
        for(String id:on){if(id.equals(GENERATED)||id.equals("zz-archive-patches"))continue;JSONObject data=manifest(new File(root(c),id));
            JSONArray needed=data.optJSONArray("requires");if(needed!=null)for(int i=0;i<needed.length();i++)if(!on.contains(needed.getString(i)))throw new IOException(id+" requires "+needed.getString(i)+".");
            JSONArray conflicts=data.optJSONArray("conflicts");if(conflicts!=null)for(int i=0;i<conflicts.length();i++)if(on.contains(conflicts.getString(i)))throw new IOException(id+" conflicts with "+conflicts.getString(i)+".");}
    }
    static JSONObject options(Context c)throws Exception{
        File file=new File(state(c),"config/mod_options.json");return file.isFile()?new JSONObject(ContentFiles.text(file,262144)):new JSONObject();
    }
    static void setOption(Context c,String id,String key,String value)throws Exception{
        ContentFiles.locked(c,()->{name(id);JSONObject data=manifest(new File(root(c),id));boolean valid=false;JSONArray options=data.optJSONArray("option");
            if(options!=null)for(int i=0;i<options.length();i++){JSONObject option=options.getJSONObject(i);if(!key.equals(option.optString("key")))continue;JSONArray choices=option.getJSONArray("choices");for(int j=0;j<choices.length();j++){Object item=choices.get(j);String choice=item instanceof JSONObject?((JSONObject)item).getString("value"):String.valueOf(item);if(choice.equals(value))valid=true;}}
            if(!valid)throw new IOException("Unknown mod option or choice.");JSONObject values=options(c),selected=values.optJSONObject(id);if(selected==null)selected=new JSONObject();selected.put(key,value);values.put(id,selected);
            List<String> on=enabled(c);on.remove(GENERATED);rebuild(c,on,values);return null;});
    }
    private static Map<String,String> optionValues(JSONObject manifest,JSONObject saved,String id)throws Exception{
        Map<String,String> out=new LinkedHashMap<>();JSONArray options=manifest.optJSONArray("option");JSONObject selected=saved.optJSONObject(id);if(options==null)return out;if(options.length()>8)throw new IOException("Too many mod options.");
        for(int i=0;i<options.length();i++){JSONObject option=options.getJSONObject(i);String key=option.getString("key"),value=selected==null?option.getString("default"):selected.optString(key,option.getString("default"));JSONArray choices=option.getJSONArray("choices");boolean valid=false;
            for(int j=0;j<choices.length();j++){Object choice=choices.get(j);if(value.equals(choice instanceof JSONObject?((JSONObject)choice).getString("value"):String.valueOf(choice)))valid=true;}
            if(!valid)throw new IOException("Saved mod option is no longer valid.");out.put(key,value);}
        return out;
    }
    static void refreshDatabaseLocked(Context c)throws Exception{List<String> on=enabled(c);on.remove(GENERATED);rebuild(c,on,null);}
    private static void rebuild(Context c,List<String> on,JSONObject changedOptions)throws Exception{
        JSONObject saved=changedOptions==null?options(c):changedOptions;List<File> scripts=new ArrayList<>();List<Map<String,String>> choices=new ArrayList<>();
        List<String> reverse=new ArrayList<>(on);Collections.reverse(reverse);
        for(String id:reverse){if(id.equals("zz-archive-patches"))continue;JSONObject data=manifest(new File(root(c),id));validateSupported(c,id,data);File db=new File(root(c),id+"/db");if(!db.isDirectory())continue;
            List<File> files=ContentFiles.files(db);files.sort(Comparator.comparing(File::getPath));Map<String,String> values=optionValues(data,saved,id);for(File file:files)if(file.getName().endsWith(".sql")){scripts.add(file);choices.add(values);}}
        File generated=new File(root(c),GENERATED),stage=ContentFiles.temporary(c,"database-"),prepared=null;
        try{
            if(!scripts.isEmpty()){
                File database=null;boolean rallyHidden=false;
                for(String id:on){if(id.equals(GENERATED)||id.equals("zz-archive-patches"))continue;
                    if(database==null)database=ContentFiles.treeFile(new File(root(c),id+"/game"),"media/db/gamedb.slt");
                    JSONArray hidden=manifest(new File(root(c),id)).optJSONArray("hide_dlc");if(hidden!=null)for(int i=0;i<hidden.length();i++)if(DlcStore.RALLY.equalsIgnoreCase(hidden.getString(i)))rallyHidden=true;
                }
                if(database==null&&DlcStore.content(c,DlcStore.RALLY,true).isDirectory()&&!rallyHidden){
                    database=ContentFiles.treeFile(new File(state(c),"cache/rally_adapter/game"),"media/db/gamedb.slt");
                    if(database==null)throw new IOException("Prepare and import Rally's adapter files before using database mods with Rally.");
                }
                if(database==null)database=baseDatabase(c,stage);
                if(!database.isFile()||Files.isSymbolicLink(database.toPath()))throw new IOException("The game database is unavailable. Import the game files again.");
                File payload=new File(stage,GENERATED),copy=new File(payload,"game/media/db/gamedb.slt");Files.createDirectories(copy.getParentFile().toPath());
                try(InputStream in=new FileInputStream(database);OutputStream out=new FileOutputStream(copy)){ContentFiles.copy(in,out,512L*1024*1024);}
                SQLiteDatabase sql=SQLiteDatabase.openDatabase(copy.getPath(),null,SQLiteDatabase.OPEN_READWRITE|SQLiteDatabase.NO_LOCALIZED_COLLATORS);
                try{sql.beginTransaction();for(int i=0;i<scripts.size();i++){FileOperation.check();String text=ModSql.substitute(ContentFiles.text(scripts.get(i),1024*1024),choices.get(i));for(String statement:ModSql.statements(text))sql.execSQL(statement);}sql.setTransactionSuccessful();}
                finally{if(sql.inTransaction())sql.endTransaction();sql.close();}
                ContentFiles.atomic(new File(payload,"mod.toml"),("name = \""+GENERATED+"\"\nversion = \"1.0.0\"\nabi = 1\ngame_version = \""+BASE_BUILD+"\"\n").getBytes(StandardCharsets.UTF_8));
                prepared=payload;on.add(0,GENERATED);
            }
            commit(c,on,changedOptions,prepared,generated);
        }finally{ContentFiles.delete(stage);}
    }
    private static File baseDatabase(Context c,File stage)throws Exception{
        File file=ContentFiles.treeFile(new File(MobileConfig.base(c),"game/base"),"media/db/gamedb.slt");if(file!=null)return file;
        String uri=c.getSharedPreferences("mobile",0).getString("iso_uri","");if(uri.isEmpty())throw new IOException("Game database is missing. Import the complete game folder.");
        GameFiles.Expected expected=GameFiles.catalog(c).get("media/db/gamedb.slt");File cached=new File(stage,"base-gamedb.slt");
        try(android.os.ParcelFileDescriptor fd=c.getContentResolver().openFileDescriptor(android.net.Uri.parse(uri),"r")){
            if(fd==null)throw new IOException("The selected ISO is unavailable.");
            try(FileInputStream input=new FileInputStream(fd.getFileDescriptor())){DiscImage disc=new DiscImage(input.getChannel());disc.copy("media/db/gamedb.slt",cached,expected.hash);}
        }
        return cached;
    }
    private static void commit(Context c,List<String> on,JSONObject options,File prepared,File generated)throws IOException{
        commitFiles(MobileConfig.file(c),new File(state(c),"config/mod_options.json"),root(c),on,
            options==null?null:options.toString().getBytes(StandardCharsets.UTF_8),prepared,generated);
    }
    static void commitFiles(File config,File optionFile,File mods,List<String> on,byte[] options,File prepared,File generated)throws IOException{
        byte[] oldConfig=config.isFile()?Files.readAllBytes(config.toPath()):null,oldOptions=optionFile.isFile()?Files.readAllBytes(optionFile.toPath()):null;
        Files.createDirectories(mods.toPath());File backup=new File(mods,".database-rollback-"+UUID.randomUUID());boolean previous=generated.exists(),installed=false;
        if(previous)Files.move(generated.toPath(),backup.toPath(),StandardCopyOption.ATOMIC_MOVE);
        try{
            if(prepared!=null){Files.move(prepared.toPath(),generated.toPath(),StandardCopyOption.ATOMIC_MOVE);installed=true;}
            MobileConfig.putFile(config,Collections.singletonMap("enabled_mods",MobileConfig.quote(String.join(",",on))));
            if(options!=null)ContentFiles.atomic(optionFile,options);
        }catch(IOException|RuntimeException error){
            try{if(installed)ContentFiles.delete(generated);if(previous)Files.move(backup.toPath(),generated.toPath(),StandardCopyOption.ATOMIC_MOVE);
                if(oldConfig==null)Files.deleteIfExists(config.toPath());else ContentFiles.atomic(config,oldConfig);
                if(options!=null){if(oldOptions==null)Files.deleteIfExists(optionFile.toPath());else ContentFiles.atomic(optionFile,oldOptions);}
            }catch(IOException restore){error.addSuppressed(restore);}throw error;
        }
        if(previous)try{ContentFiles.delete(backup);}catch(IOException cleanup){android.util.Log.w("FH4Droid","Old generated database retained after a successful update",cleanup);}
    }
    private ModStore(){}
}
