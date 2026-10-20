package studio.deimos.pinyonshift;
import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

final class DriverStore {
    static File root(Context c){return new File(MobileConfig.base(c),"state/drivers");}
    static void installBundled(Context c)throws IOException{
        String[] names=c.getAssets().list("drivers");if(names==null)return;
        for(String name:names){File dir=new File(root(c),name);dir.mkdirs();String[] entries=c.getAssets().list("drivers/"+name);if(entries==null)continue;
            for(String entry:entries){File to=new File(dir,entry);
                // Always finish the complete copy before replacing an older asset.
                if(to.isFile())continue;
                File temp=File.createTempFile("driver-",".install-part",dir);
                try(InputStream in=c.getAssets().open("drivers/"+name+"/"+entry);OutputStream out=new FileOutputStream(temp)){byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);}
                Files.move(temp.toPath(),to.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            }
        }
    }
    static String importZip(Context c,InputStream input)throws IOException{
        root(c).mkdirs();File stage=Files.createTempDirectory(root(c).toPath(),".import-").toFile();boolean installed=false;
        try{long total=0;int count=0;Set<String> names=new HashSet<>();byte[] b=new byte[65536];
            try(ZipInputStream zip=new ZipInputStream(input)){for(ZipEntry e;(e=zip.getNextEntry())!=null;){
                if(++count>64)throw new IOException("Too many driver files.");String name=e.getName();
                Path dest=stage.toPath().resolve(name).normalize();
                if(name.indexOf('\\')>=0||name.indexOf('\0')>=0||dest.equals(stage.toPath())||!dest.startsWith(stage.toPath())||!names.add(dest.toString()))throw new IOException("Invalid driver package path.");
                if(e.isDirectory()){Files.createDirectories(dest);continue;}Files.createDirectories(dest.getParent());
                try(OutputStream out=Files.newOutputStream(dest,StandardOpenOption.CREATE_NEW)){for(int n;(n=zip.read(b))!=-1;){if((total+=n)>256L*1024*1024)throw new IOException("Driver ZIP is too large.");out.write(b,0,n);}}
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
    private DriverStore(){}
}
