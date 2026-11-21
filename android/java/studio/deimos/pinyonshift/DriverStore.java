package studio.deimos.pinyonshift;
import android.content.Context;
import org.json.JSONObject;
import org.json.JSONArray;
import android.app.Activity;
import android.widget.LinearLayout;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

final class DriverStore {
    static File root(Context c){return new File(MobileConfig.base(c),"state/drivers");}
    static Map<String,String> sessionSettings(Map<String,String> saved, String trial) {
        if (trial == null) return saved;
        Map<String,String> values = new LinkedHashMap<>(saved);
        values.put("android_gpu_driver_env", trial.equals("sysmem") ? "TU_DEBUG=sysmem" : "");
        values.put("android_gpu_driver_no_preamble", Boolean.toString(trial.equals("nopreamble")));
        return values;
    }
    static String probeKey(Context c,String selection,Map<String,String> settings) {
        try {
            android.content.pm.PackageInfo info=c.getPackageManager().getPackageInfo(c.getPackageName(),0);
            JSONArray key=new JSONArray();
            key.put(2).put(info.getLongVersionCode()).put(info.lastUpdateTime).put(android.os.Build.FINGERPRINT);
            key.put(selection).put(settings.getOrDefault("android_gpu_driver_env",""))
                .put(settings.getOrDefault("android_gpu_driver_no_preamble","true"))
                .put(settings.getOrDefault("vulkan_require_vertex_pipeline_stores_and_atomics","true"));
            String directory=selection.equals("auto")?"turnip-gen8-v37":selection;
            if (!directory.isEmpty()) {
                if (!directory.matches("[A-Za-z0-9_-]{1,100}")) return null;
                File folder=new File(root(c),directory);
                if (!folder.exists() && selection.equals("auto")) return key.put("no bundle").toString();
                String dll=library(folder);
                String identity=FileIdentity.stamp(new File(folder,dll));
                if (identity==null) return null;
                key.put(dll).put(identity).put(FileIdentity.stamp(new File(folder,"meta.json")));
            }
            return key.toString();
        } catch (IOException | android.content.pm.PackageManager.NameNotFoundException | SecurityException error) {
            return null;
        }
    }
    static String cachedSelection(Context c,String selection,Map<String,String> settings) {
        String key=probeKey(c,selection,settings);
        if (key==null) return null;
        try {
            JSONObject report=new JSONObject(AdrenoSettings.reportDetails(c));
            if (report.optBoolean("ok") && key.equals(report.optString("probe_key"))
                && report.has("effective_driver")) return report.getString("effective_driver");
        } catch (org.json.JSONException ignored) {}
        return null;
    }
    static synchronized void installBundled(Context c)throws IOException{
        String revision;
        try {
            android.content.pm.PackageInfo info=c.getPackageManager().getPackageInfo(c.getPackageName(),0);
            revision=info.getLongVersionCode()+":"+info.lastUpdateTime;
        } catch (android.content.pm.PackageManager.NameNotFoundException error) { revision=null; }
        android.content.SharedPreferences installed=c.getSharedPreferences("bundled-driver-assets",0);
        android.content.SharedPreferences.Editor stamp=installed.edit();
        String[] names=c.getAssets().list("drivers");if(names==null)return;
        for(String name:names){File dir=new File(root(c),name);dir.mkdirs();String[] entries=c.getAssets().list("drivers/"+name);if(entries==null)continue;
            for(String entry:entries){File to=new File(dir,entry);
                String key=name+"/"+entry;
                String identity=FileIdentity.stamp(to);
                if (revision!=null && identity!=null && (revision+":"+identity).equals(installed.getString(key,""))) continue;
                // Always finish the complete copy before replacing an older asset.
                if(to.isFile()){
                    try(InputStream asset=c.getAssets().open("drivers/"+name+"/"+entry)){
                        if(GameFiles.hash(asset).equals(DiscImage.hash(to))){
                            if(revision!=null && identity!=null)stamp.putString(key,revision+":"+identity);
                            continue;
                        }
                    }
                }
                File temp=File.createTempFile("driver-",".install-part",dir);
                try{
                    try(InputStream in=c.getAssets().open("drivers/"+name+"/"+entry);OutputStream out=new FileOutputStream(temp)){byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);}
                    Files.move(temp.toPath(),to.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
                    identity=FileIdentity.stamp(to);
                    if(revision!=null && identity!=null)stamp.putString(key,revision+":"+identity);
                }finally{Files.deleteIfExists(temp.toPath());}
            }
        }
        stamp.commit();
    }
    static String importZip(Context c,InputStream input)throws IOException{
        root(c).mkdirs();File stage=Files.createTempDirectory(root(c).toPath(),".import-").toFile();boolean installed=false;
        try{long total=0;int count=0;Set<String> names=new HashSet<>();byte[] b=new byte[65536];
            try(ZipInputStream zip=new ZipInputStream(input)){for(ZipEntry e;(e=zip.getNextEntry())!=null;){
                FileOperation.check();
                if(++count>64)throw new IOException("Too many driver files.");String name=e.getName();
                Path dest=stage.toPath().resolve(name).normalize();
                if(name.indexOf('\\')>=0||name.indexOf('\0')>=0||dest.equals(stage.toPath())||!dest.startsWith(stage.toPath())||!names.add(dest.toString()))throw new IOException("Invalid driver package path.");
                if(e.isDirectory()){Files.createDirectories(dest);continue;}Files.createDirectories(dest.getParent());
                try(OutputStream out=Files.newOutputStream(dest,StandardOpenOption.CREATE_NEW)){for(int n;(n=zip.read(b))!=-1;){FileOperation.check();if((total+=n)>256L*1024*1024)throw new IOException("Driver ZIP is too large.");out.write(b,0,n);}}
            }}
            library(stage);String name="imported-"+UUID.randomUUID().toString().substring(0,8);
            Files.move(stage.toPath(),new File(root(c),name).toPath(),StandardCopyOption.ATOMIC_MOVE);installed=true;return name;
        }finally{if(!installed)SaveArchive.delete(stage);}
    }
    static String library(File dir)throws IOException{
        String name=null;File meta=new File(dir,"meta.json");
        if(meta.isFile()){if(meta.length()>65536)throw new IOException("Driver metadata is too large.");
            try{name=new JSONObject(new String(Files.readAllBytes(meta.toPath()),java.nio.charset.StandardCharsets.UTF_8)).optString("libraryName","");}
            catch(Exception e){throw new IOException("Invalid driver metadata.");}}
        if(name==null||name.isEmpty()){File[] files=dir.listFiles(f->f.isFile()&&f.getName().endsWith(".so"));if(files==null||files.length!=1)throw new IOException("The driver package must identify one Vulkan library.");name=files[0].getName();}
        if(name.contains("/")||name.contains("\\")||name.equals(".")||name.equals(".."))throw new IOException("Invalid driver library name.");
        File lib=new File(dir,name);try(DataInputStream in=new DataInputStream(new FileInputStream(lib))){byte[] elf=new byte[20];in.readFully(elf);
            if(elf[0]!=127||elf[1]!='E'||elf[2]!='L'||elf[3]!='F'||elf[4]!=2||elf[5]!=1||(elf[18]&255)!=183||elf[19]!=0)
                throw new IOException("This driver is not an ARM64 library.");}
        return name;
    }
    static String[] choices(Context c){
        File[] dirs=root(c).listFiles(f->f.isDirectory()&&!f.getName().startsWith("."));List<String> result=new ArrayList<>();result.add("");result.add("auto");
        if(dirs!=null){Arrays.sort(dirs,Comparator.comparing(File::getName));for(File f:dirs)result.add(f.getName());}return result.toArray(new String[0]);
    }
    static String displayName(Context context, String str) {
        String str2 = "";
        if (str == null || str.isEmpty()) {
            return "System driver · built into your phone";
        }
        if (str.equals("auto")) {
            return "Automatic · recommended";
        }
        if (str.equals("turnip-gen8-v37")) {
            return "Turnip Gen 8 · v37 (Adreno)";
        }
        try {
            File file = new File(root(context), str + "/meta.json");
            if (file.isFile() && file.length() < 65536) {
                JSONObject jSONObject = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
                String strTrim = jSONObject.optString("name", "").trim();
                String strTrim2 = jSONObject.optString("version", "").trim();
                if (!strTrim.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(strTrim);
                    if (!strTrim2.isEmpty() && !strTrim.contains(strTrim2)) {
                        str2 = " · " + strTrim2;
                    }
                    sb.append(str2);
                    return sb.toString();
                }
            }
        } catch (Exception unused) {
        }
        if (!str.startsWith("imported-")) {
            return str.replace('-', ' ').replace('_', ' ');
        }
        return "Imported driver · " + str.substring(9);
    }
    static String apiName(int i) {
        return ((i >>> 22) & 127) + "." + ((i >>> 12) & 1023) + "." + (i & 4095);
    }
    static void addReport(Activity activity, LinearLayout parent, String data) {
        JSONObject report;
        try {
            report = new JSONObject(data);
        } catch (org.json.JSONException error) {
            FestivalUi.card(activity, parent, "Last startup test", "Run Test driver to check the selected driver.");
            return;
        }
        boolean passed = report.optBoolean("ok");
        int color = passed ? 0xff8fdcb6 : 0xffffb978;
        int tint = passed ? 0x208fdcb6 : 0x20ffb978;
        LinearLayout card = FestivalUi.card(activity, parent, "Last startup test", null);
        android.widget.TextView status = FestivalUi.text(activity,
            passed ? "Startup checks passed" : "Driver needs attention", 15, color);
        int padding = FestivalUi.dp(activity, 8);
        status.setPadding(padding, padding, padding, padding);
        status.setBackground(FestivalUi.surface(activity, tint, 8));
        LinearLayout.LayoutParams statusSize = new LinearLayout.LayoutParams(-1, -2);
        statusSize.topMargin = padding;
        statusSize.bottomMargin = padding;
        card.addView(status, statusSize);
        if (report.has("effective_driver"))
            card.addView(FestivalUi.text(activity,
                displayName(activity, report.optString("effective_driver")), 15, FestivalUi.TEXT));
        String gpu = report.optString("gpu");
        String api = report.has("api") ? "Vulkan " + apiName(report.optInt("api")) : "";
        String device = gpu.isEmpty() ? api : api.isEmpty() ? gpu : gpu + " · " + api;
        if (!device.isEmpty()) card.addView(FestivalUi.text(activity, device, 13, FestivalUi.MUTED));
        if (!passed)
            card.addView(FestivalUi.text(activity, report.optString("error", "The test did not finish."),
                13, FestivalUi.MUTED));
        JSONArray checks = report.optJSONArray("checks");
        if (checks == null) return;
        for (int i = 0; i < checks.length(); i++) {
            JSONObject check = checks.optJSONObject(i);
            if (check == null) continue;
            boolean ok = check.optBoolean("ok");
            LinearLayout row = FestivalUi.column(activity);
            row.setPadding(padding, padding, padding, padding);
            row.setBackground(FestivalUi.surface(activity, 0x28000000, 8));
            row.addView(FestivalUi.text(activity, check.optString("name") + (ok ? " · Passed" : " · Failed"),
                13, ok ? 0xff8fdcb6 : 0xffffb978));
            String detail = check.optString("detail");
            if (!detail.isEmpty()) row.addView(FestivalUi.text(activity, detail, 12, FestivalUi.MUTED));
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
            size.topMargin = FestivalUi.dp(activity, 5);
            card.addView(row, size);
        }
    }
    static void removeImported(Context c,String name)throws IOException{
        if(!name.matches("imported-[a-f0-9]{8}"))throw new IOException("Only imported drivers can be removed.");
        File folder=new File(root(c),name);if(Files.isSymbolicLink(folder.toPath()))throw new IOException("Linked driver folder is not supported.");
        if(MobileConfig.get(c,"android_gpu_driver","auto").equals(name))MobileConfig.put(c,"android_gpu_driver",MobileConfig.quote(""));
        SaveArchive.delete(folder);
    }
    private DriverStore(){}
}
