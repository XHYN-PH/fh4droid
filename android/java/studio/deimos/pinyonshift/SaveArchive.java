package studio.deimos.pinyonshift;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Save-only archives, validated in staging before the live tree is touched. */
final class SaveArchive {
    private static final long LIMIT=512L*1024*1024;
    private static final int FILES=8192;
    private static final String MARKER="FH4Droid base-game saves v1\n";
    static void export(File root,OutputStream stream)throws IOException{
        if(!root.isDirectory())throw new IOException("No saves found yet.");
        try(ZipOutputStream zip=new ZipOutputStream(stream)){
            zip.putNextEntry(new ZipEntry("fh4droid-save.txt"));zip.write(MARKER.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            long[] budget={0,0};write(root,root,zip,budget);
            if(budget[0]==0)throw new IOException("No saves found yet.");
        }
    }
    private static void write(File root,File dir,ZipOutputStream zip,long[] budget)throws IOException{
        File[] files=dir.listFiles();if(files==null)throw new IOException("Cannot read save folder.");
        Arrays.sort(files,Comparator.comparing(File::getName));
        for(File f:files){if(Files.isSymbolicLink(f.toPath()))throw new IOException("Save links are not supported.");
            if(f.isDirectory()){write(root,f,zip,budget);continue;}
            if(!f.isFile())continue;
            if(++budget[0]>FILES||(budget[1]+=f.length())>LIMIT)throw new IOException("Save data exceeds the export limit.");
            zip.putNextEntry(new ZipEntry("user/"+root.toPath().relativize(f.toPath()).toString().replace(File.separatorChar,'/')));
            Files.copy(f.toPath(),zip);zip.closeEntry();
        }
    }
    static void importInto(File state,InputStream input)throws IOException{
        File user=new File(state,"user");state.mkdirs();File stage=Files.createTempDirectory(state.toPath(),"save-import-").toFile();
        boolean installed=false;
        try{
            boolean marker=false;long bytes=0;int count=0;Set<String> names=new HashSet<>();byte[] b=new byte[65536];
            try(ZipInputStream zip=new ZipInputStream(input)){
                for(ZipEntry e;(e=zip.getNextEntry())!=null;){
                    String name=e.getName();if(!names.add(name))throw new IOException("Duplicate save entry.");
                    if(name.equals("fh4droid-save.txt")){
                        ByteArrayOutputStream tag=new ByteArrayOutputStream();int n;
                        while((n=zip.read(b))!=-1){if(tag.size()+n>1024)throw new IOException("Invalid save information.");tag.write(b,0,n);}
                        if(!MARKER.equals(new String(tag.toByteArray(),StandardCharsets.UTF_8)))throw new IOException("This backup is not for the base-game build.");
                        marker=true;continue;
                    }
                    if(!name.startsWith("user/")||name.indexOf('\\')>=0||name.indexOf('\0')>=0)throw new IOException("Choose an FH4Droid save export.");
                    String relative=name.substring(5);Path dest=stage.toPath().resolve(relative).normalize();
                    if(relative.isEmpty()||dest.equals(stage.toPath())||!dest.startsWith(stage.toPath())||++count>FILES)throw new IOException("Invalid save path or too many files.");
                    if(e.isDirectory()){Files.createDirectories(dest);continue;}
                    Files.createDirectories(dest.getParent());
                    try(OutputStream out=Files.newOutputStream(dest,StandardOpenOption.CREATE_NEW)){
                        for(int n;(n=zip.read(b))!=-1;){if((bytes+=n)>LIMIT)throw new IOException("Save archive is too large.");out.write(b,0,n);}
                    }
                }
            }
            if(!marker||bytes==0)throw new IOException("The archive contains no compatible saves.");
            File backup=new File(state,"backups/android-import-"+System.currentTimeMillis());backup.getParentFile().mkdirs();
            if(user.exists())Files.move(user.toPath(),backup.toPath(),StandardCopyOption.ATOMIC_MOVE);
            try{Files.move(stage.toPath(),user.toPath(),StandardCopyOption.ATOMIC_MOVE);installed=true;}
            catch(IOException error){if(backup.exists()&&!user.exists())Files.move(backup.toPath(),user.toPath(),StandardCopyOption.ATOMIC_MOVE);throw error;}
        }finally{if(!installed)delete(stage);}
    }
    static File latest(File state){
        File[] dirs=new File(state,"backups").listFiles(f->f.isDirectory()&&f.getName().startsWith("android-import-"));
        if(dirs==null||dirs.length==0)return null;Arrays.sort(dirs,Comparator.comparing(File::getName));return dirs[dirs.length-1];
    }
    static void restore(File state)throws IOException{
        File backup=latest(state);if(backup==null)throw new IOException("No previous imported-save backup found.");
        File user=new File(state,"user"),before=new File(state,"backups/android-import-"+System.currentTimeMillis());
        if(user.exists())Files.move(user.toPath(),before.toPath(),StandardCopyOption.ATOMIC_MOVE);
        try{Files.move(backup.toPath(),user.toPath(),StandardCopyOption.ATOMIC_MOVE);}
        catch(IOException error){if(before.exists()&&!user.exists())Files.move(before.toPath(),user.toPath(),StandardCopyOption.ATOMIC_MOVE);throw error;}
    }
    static void delete(File file)throws IOException{
        if(!Files.exists(file.toPath(),LinkOption.NOFOLLOW_LINKS))return;
        if(file.isDirectory()&&!Files.isSymbolicLink(file.toPath())){File[] children=file.listFiles();if(children==null)throw new IOException("Cannot read temporary folder.");for(File child:children)delete(child);}
        Files.delete(file.toPath());
    }
    private SaveArchive(){}
}
