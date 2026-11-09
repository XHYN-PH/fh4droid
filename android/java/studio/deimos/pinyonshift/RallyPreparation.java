package studio.deimos.pinyonshift;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Import the verified Rally adapter made from the player's files by upstream setup. */
final class RallyPreparation {
    static void importFolder(Context c,File source)throws Exception{
        ContentFiles.locked(c,()->{
            File root=validate(c,source,false),stage=ContentFiles.temporary(c,"rally-");
            File target=new File(DlcStore.state(c),"cache/rally_adapter"),backup=new File(target.getParentFile(),"rally-rollback-"+UUID.randomUUID());boolean previous=target.exists(),installed=false;
            try{
                File adapter=new File(stage,"adapter");ContentFiles.copyTree(root,adapter);
                if(previous)Files.move(target.toPath(),backup.toPath(),StandardCopyOption.ATOMIC_MOVE);
                try{Files.move(adapter.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE);installed=true;if(DlcStore.content(c,DlcStore.RALLY,true).isDirectory())ModStore.refreshDatabaseLocked(c);}
                catch(Exception error){try{if(installed)ContentFiles.delete(target);if(previous)Files.move(backup.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE);}catch(IOException restore){error.addSuppressed(restore);}throw error;}
                if(previous)try{ContentFiles.delete(backup);}catch(IOException cleanup){android.util.Log.w("FH4Droid","Previous Rally cache retained",cleanup);}
            }finally{ContentFiles.delete(stage);}return null;
        });
    }
    static void verifyInstalled(Context c)throws Exception{validate(c,new File(DlcStore.state(c),"cache/rally_adapter"),true);}
    private static File validate(Context c,File source,boolean packageVerified)throws Exception{
            List<File> metadata=new ArrayList<>();for(File file:ContentFiles.files(source))if(file.getName().equals("preparation.json"))metadata.add(file);
            if(metadata.size()!=1)throw new IOException("Choose the prepared Rally adapter folder containing preparation.json.");
            File root=metadata.get(0).getParentFile();JSONObject data=new JSONObject(ContentFiles.text(metadata.get(0),2*1024*1024));
            if(!data.optString("schema").equals("pinyon-shift.rally-preparation.v1")||!data.optBoolean("entry_ready"))throw new IOException("Rally preparation metadata is incompatible.");
            JSONObject recipe=data.getJSONObject("recipe");if(!DlcStore.RALLY.equals(recipe.optString("package_id"))||recipe.optInt("recipe_version")!=12)throw new IOException("Prepare Rally with the upstream 0.8.2 tools first.");
            if(!packageVerified)DlcStore.verify(c,DlcStore.RALLY);JSONObject installed=new JSONObject(ContentFiles.text(DlcStore.record(c,DlcStore.RALLY),65536));
            if(!recipe.getString("package_sha256").equals(installed.getString("sha256"))||!recipe.getString("payload_sha256").equals(installed.getString("payload_sha256"))||!recipe.getString("header_sha256").equals(installed.getString("header_sha256")))throw new IOException("This adapter was prepared from a different Rally package.");
            JSONObject base=recipe.getJSONObject("base");String dump="forza-horizon-usa-ms-2505-retail-base";if(!dump.equals(base.optString("dump_id")))throw new IOException("Rally adapter targets another game dump.");
            JSONObject catalog;
            try(InputStream input=c.getAssets().open("supported-dumps.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){ContentFiles.copy(input,out,2*1024*1024);catalog=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}
            JSONObject expected=null;JSONArray dumps=catalog.getJSONArray("dumps");for(int i=0;i<dumps.length();i++)if(dump.equals(dumps.getJSONObject(i).optString("id")))expected=dumps.getJSONObject(i);
            if(expected==null)throw new IOException("Supported base dump metadata is unavailable.");
            Map<String,JSONObject> inputs=new HashMap<>();for(String group:new String[]{"executables","rally_adapter_inputs"}){JSONArray list=expected.getJSONArray(group);for(int i=0;i<list.length();i++){JSONObject item=list.getJSONObject(i);inputs.put(item.getString("guest_path"),item);}}
            JSONArray used=base.getJSONArray("inputs");if(used.length()!=inputs.size())throw new IOException("Rally base inputs are incomplete.");Set<String> seen=new HashSet<>();
            for(int i=0;i<used.length();i++){JSONObject input=used.getJSONObject(i);String path=input.getString("guest_path");JSONObject match=inputs.get(path);if(match==null||!seen.add(path)||match.getLong("size_bytes")!=input.getLong("size_bytes")||!match.getString("sha256").equals(input.getString("sha256")))throw new IOException("Rally base inputs differ from the supported dump.");}
            List<File> artifacts=ContentFiles.files(root);artifacts.remove(metadata.get(0));if(!ContentFiles.payloadDigest(root,artifacts).equals(data.getString("artifact_sha256")))throw new IOException("Prepared Rally files are incomplete or changed.");
            if(ContentFiles.treeFile(root,"game/media/db/gamedb.slt")==null||ContentFiles.treeFile(root,"rally-stage.toml")==null)throw new IOException("Prepared Rally adapter is incomplete.");
            return root;
    }
    private RallyPreparation(){}
}
