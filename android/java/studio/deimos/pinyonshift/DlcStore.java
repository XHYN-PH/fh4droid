package studio.deimos.pinyonshift;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Android import of the same catalog-verified Marketplace content as the PC tools. */
final class DlcStore {
    static final String RALLY="6F6992766050D818245ADD408031E280FB5F4E634D";
    static final String UDLC="4D5309C900000729",TRAFFIC="4D5309C900000732";
    static final String TITLE="0000000000000000/4D5309C9";
    static JSONArray catalog(Context c)throws Exception{
        try(InputStream in=c.getAssets().open("supported-dlc.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            ContentFiles.copy(in,out,2*1024*1024);return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)).getJSONArray("packages");
        }
    }
    static JSONObject packageInfo(Context c,String id)throws Exception{
        if(!id.matches("[A-F0-9]{16,42}"))throw new IOException("Invalid DLC package ID.");
        JSONArray catalog=catalog(c);for(int i=0;i<catalog.length();i++){JSONObject item=catalog.getJSONObject(i);if(id.equals(item.getString("package_id")))return item;}
        throw new IOException("This DLC is not in the verified catalog.");
    }
    static File state(Context c){return new File(MobileConfig.base(c),"state");}
    static File content(Context c,String id,boolean enabled){return new File(state(c),"user/"+TITLE+"/"+(enabled?"":"Disabled/")+"00000002/"+id);}
    static File header(Context c,String id){return new File(state(c),"user/"+TITLE+"/Headers/00000002/"+id+".header");}
    static File record(Context c,String id){return new File(state(c),"dlc/"+id+".json");}
    static List<JSONObject> list(Context c)throws Exception{
        List<JSONObject> out=new ArrayList<>();JSONArray catalog=catalog(c);
        for(int i=0;i<catalog.length();i++){
            JSONObject item=new JSONObject(catalog.getJSONObject(i).toString());String id=item.getString("package_id");
            boolean active=content(c,id,true).isDirectory(),inactive=content(c,id,false).isDirectory();
            item.put("installed",active||inactive);item.put("enabled",active);
            item.put("conflict",active&&inactive);out.add(item);
        }return out;
    }
    static String importFiles(Context c,List<File> sources)throws Exception{
        return ContentFiles.locked(c,()->{int[] found={0};List<String> skipped=new ArrayList<>();long[] budget={ContentFiles.MAX_BYTES,ContentFiles.MAX_FILES};File stage=ContentFiles.temporary(c,"dlc-");
            try{for(File source:sources){if(source.isDirectory())for(File file:ContentFiles.files(source))scanSafely(c,file,file.getName(),stage,0,found,budget,skipped);else scanSafely(c,source,source.getName(),stage,0,found,budget,skipped);}
                String report=found[0]+" verified DLC package(s) imported or already installed.";if(!skipped.isEmpty())report+="\n\nSkipped inputs:\n"+String.join("\n",skipped);if(found[0]==0&&skipped.isEmpty())report="No supported Marketplace package was found. Choose original packages or ZIPs containing them.";return report;
            }finally{ContentFiles.delete(stage);}});
    }
    private static void scanSafely(Context c,File source,String name,File stage,int depth,int[] found,long[] budget,List<String> skipped)throws Exception{
        try{scan(c,source,stage,depth,found,budget,skipped);}catch(Exception error){FileOperation.check();if(skipped.size()<20)skipped.add(name+": "+error.getMessage());else if(skipped.size()==20)skipped.add("Additional skipped inputs omitted.");}
    }
    private static void scan(Context c,File source,File stage,int depth,int[] found,long[] budget,List<String> skipped)throws Exception{
        FileOperation.check();
        byte[] magic=new byte[4];try(InputStream in=new FileInputStream(source)){if(in.read(magic)!=4)return;}
        if(magic[0]=='P'&&magic[1]=='K'){
            if(depth>=3)throw new IOException("DLC ZIPs may be nested at most three levels.");
            try(ZipFile zip=new ZipFile(source)){
                Enumeration<? extends ZipEntry> entries=zip.entries();int count=0;
                while(entries.hasMoreElements()){
                    FileOperation.check();ZipEntry entry=entries.nextElement();if(++count>ContentFiles.MAX_FILES||--budget[1]<0)throw new IOException("Too many DLC archive entries.");if(entry.isDirectory())continue;
                    if(entry.getSize()>budget[0])throw new IOException("DLC archives exceed the size limit.");
                    File file=new File(stage,UUID.randomUUID()+".input");
                    try{try(InputStream in=zip.getInputStream(entry);OutputStream out=new FileOutputStream(file)){budget[0]-=ContentFiles.copy(in,out,budget[0]);}scanSafely(c,file,entry.getName(),stage,depth+1,found,budget,skipped);}
                    finally{Files.deleteIfExists(file.toPath());}
                }
            }return;
        }
        String type=new String(magic,StandardCharsets.US_ASCII);if(!type.equals("CON ")&&!type.equals("LIVE")&&!type.equals("PIRS"))return;
        if(source.length()<0x971a||source.length()>2L*1024*1024*1024)throw new IOException("DLC package size is invalid.");
        byte[] metadata=new byte[0x971a];try(DataInputStream in=new DataInputStream(new FileInputStream(source))){in.readFully(metadata);}
        if(!ContentFiles.hex(Arrays.copyOfRange(metadata,0x360,0x364)).equals("4D5309C9")||!ContentFiles.hex(Arrays.copyOfRange(metadata,0x344,0x348)).equals("00000002"))throw new IOException("Package is not Forza Horizon Marketplace DLC.");
        String contentId=ContentFiles.hex(Arrays.copyOfRange(metadata,0x32c,0x340));JSONObject item=null;JSONArray catalog=catalog(c);
        for(int i=0;i<catalog.length();i++){JSONObject candidate=catalog.getJSONObject(i);if(contentId.equals(candidate.optString("content_id"))){item=candidate;break;}}
        if(item==null)throw new IOException("This DLC package is not in the verified catalog.");
        FileOperation.progress(item.getString("display_name"),0,source.length());String sha=ContentFiles.hash(source);JSONObject variant=null;
        JSONArray rejected=item.optJSONArray("rejected");if(rejected!=null)for(int i=0;i<rejected.length();i++){JSONObject bad=rejected.getJSONObject(i);if(sha.equals(bad.optString("sha256")))throw new IOException(bad.optString("reason","Unsupported DLC package."));}
        JSONArray accepted=item.getJSONArray("accepted");for(int i=0;i<accepted.length();i++){JSONObject value=accepted.getJSONObject(i);if(value.getLong("size")==source.length()&&sha.equals(value.getString("sha256"))){variant=value;break;}}
        if(variant==null)throw new IOException(item.getString("display_name")+": package SHA-256 does not match a verified input.");
        String id=item.getString("package_id");
        if(content(c,id,true).exists()||content(c,id,false).exists()){
            verify(c,id);JSONObject previous=new JSONObject(ContentFiles.text(record(c,id),65536));JSONArray variants=previous.optJSONArray("variants");if(variants==null)variants=new JSONArray().put(previous.getString("sha256"));
            boolean known=false;for(int i=0;i<variants.length();i++)if(sha.equals(variants.getString(i)))known=true;
            if(!known){variants.put(sha);previous.put("variants",variants);long mask=variantMask(item,previous);byte[] merged=Arrays.copyOf(Files.readAllBytes(header(c,id).toPath()),332);for(int i=0;i<4;i++)merged[328+i]=(byte)(mask>>>(8*i));previous.put("license_mask",Long.toHexString(mask).toUpperCase(Locale.ROOT));previous.put("header_sha256",ContentFiles.hex(MessageDigest.getInstance("SHA-256").digest(merged)));metadata(c,id,merged,previous,()->null);}
            found[0]++;return;
        }
        File extracted=new File(stage,"extracted-"+UUID.randomUUID());ContentNative.extractDlc(source,extracted,id);FileOperation.check();
        File payload=new File(extracted,TITLE+"/00000002/"+id),savedHeader=new File(extracted,TITLE+"/Headers/00000002/"+id+".header");
        String digest=payloadDigest(payload,item);if(!digest.equals(item.getString("payload_sha256")))throw new IOException("Extracted DLC content failed verification.");
        byte[] header=Files.readAllBytes(savedHeader.toPath());if((header.length!=328&&header.length!=332)||license(header)!=Long.parseLong(variant.getString("license_mask"),16))throw new IOException("Extracted DLC licence failed verification.");
        JSONObject record=new JSONObject();record.put("package_id",id);record.put("display_name",item.getString("display_name"));record.put("sha256",sha);record.put("variants",new JSONArray().put(sha));record.put("payload_sha256",digest);record.put("license_mask",variant.getString("license_mask"));record.put("header_sha256",ContentFiles.hash(savedHeader));
        metadata(c,id,header,record,()->{ContentFiles.publish(payload,content(c,id,false));return null;});ContentFiles.delete(extracted);found[0]++;
    }
    private static void metadata(Context c,String id,byte[] header,JSONObject record,ContentFiles.Work<Void> publish)throws Exception{
        File headerFile=header(c,id),recordFile=record(c,id);byte[] oldHeader=headerFile.isFile()?Files.readAllBytes(headerFile.toPath()):null,oldRecord=recordFile.isFile()?Files.readAllBytes(recordFile.toPath()):null;
        try{ContentFiles.atomic(headerFile,header);ContentFiles.atomic(recordFile,record.toString().getBytes(StandardCharsets.UTF_8));publish.run();}
        catch(Exception error){try{if(oldHeader==null)Files.deleteIfExists(headerFile.toPath());else ContentFiles.atomic(headerFile,oldHeader);if(oldRecord==null)Files.deleteIfExists(recordFile.toPath());else ContentFiles.atomic(recordFile,oldRecord);}catch(IOException restore){error.addSuppressed(restore);}throw error;}
    }
    static String payloadDigest(File root,JSONObject expected)throws Exception{
        List<File> files=ContentFiles.files(root);if(files.size()!=expected.getInt("file_count"))throw new IOException("DLC file count differs from the catalog.");
        long total=0;for(File file:files){total+=file.length();if(total>expected.getLong("expanded_size"))throw new IOException("DLC size exceeds the catalog.");}
        if(total!=expected.getLong("expanded_size"))throw new IOException("DLC size differs from the catalog.");return ContentFiles.payloadDigest(root,files);
    }

    private static long license(byte[] header){if(header.length<332)return 0;return (header[328]&255L)|((header[329]&255L)<<8)|((header[330]&255L)<<16)|((header[331]&255L)<<24);}
    static void verify(Context c,String id)throws Exception{
        JSONObject item=packageInfo(c,id);File active=content(c,id,true),inactive=content(c,id,false);if(active.exists()&&inactive.exists())throw new IOException("Both enabled and disabled DLC copies exist.");
        JSONObject record=new JSONObject(ContentFiles.text(record(c,id),65536));File data=active.isDirectory()?active:inactive;
        if(!record.optString("payload_sha256").equals(item.getString("payload_sha256"))||!payloadDigest(data,item).equals(item.getString("payload_sha256")))throw new IOException("Installed DLC is incomplete or changed. Reimport it.");
        if(header(c,id).length()!=328&&header(c,id).length()!=332)throw new IOException("Installed DLC header is damaged.");
        byte[] header=Files.readAllBytes(header(c,id).toPath());if((header.length!=328&&header.length!=332)||!ContentFiles.hash(header(c,id)).equals(record.getString("header_sha256"))||license(header)!=Long.parseLong(record.getString("license_mask"),16))throw new IOException("Installed DLC header is damaged.");
        long mask=variantMask(item,record);if(mask!=license(header)||mask!=Long.parseLong(record.getString("license_mask"),16))throw new IOException("DLC import record is not a verified variant.");
    }
    private static long variantMask(JSONObject item,JSONObject record)throws Exception{
        JSONArray requested=record.optJSONArray("variants");if(requested==null)requested=new JSONArray().put(record.getString("sha256"));Set<String> seen=new HashSet<>();long mask=0;
        for(int i=0;i<requested.length();i++){String sha=requested.getString(i);if(!seen.add(sha))throw new IOException("Duplicate DLC licence variant.");boolean found=false;JSONArray accepted=item.getJSONArray("accepted");
            for(int j=0;j<accepted.length();j++){JSONObject value=accepted.getJSONObject(j);if(sha.equals(value.getString("sha256"))){mask|=Long.parseLong(value.getString("license_mask"),16);found=true;break;}}
            if(!found)throw new IOException("DLC licence variant is unknown.");
        }
        if(!seen.contains(record.getString("sha256")))throw new IOException("Primary DLC licence variant is missing.");return mask;
    }

    static void enabled(Context c,String id,boolean enabled)throws Exception{
        ContentFiles.locked(c,()->{JSONObject item=packageInfo(c,id);verify(c,id);
            JSONArray requires=item.optJSONArray("requires");if(enabled&&requires!=null)for(int i=0;i<requires.length();i++)if(!content(c,requires.getString(i),true).isDirectory())throw new IOException("Enable "+packageInfo(c,requires.getString(i)).getString("display_name")+" first.");
            if(!enabled)for(JSONObject dependent:list(c)){JSONArray needed=dependent.optJSONArray("requires");if(!dependent.optBoolean("enabled")||needed==null)continue;for(int i=0;i<needed.length();i++)if(id.equals(needed.getString(i)))throw new IOException("Disable "+dependent.getString("display_name")+" first.");}
            if(enabled&&id.equals(RALLY)){if(!new File(state(c),"cache/rally_adapter/preparation.json").isFile())throw new IOException("Import the prepared Rally adapter folder before enabling Rally.");RallyPreparation.verifyInstalled(c);}
            File source=content(c,id,!enabled),target=content(c,id,enabled);if(target.exists())return null;Files.createDirectories(target.getParentFile().toPath());Files.move(source.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE);
            try{if(id.equals(RALLY))ModStore.refreshDatabaseLocked(c);}catch(Exception error){try{Files.move(target.toPath(),source.toPath(),StandardCopyOption.ATOMIC_MOVE);}catch(IOException restore){error.addSuppressed(restore);}throw error;}return null;});
    }
    private DlcStore(){}
}
