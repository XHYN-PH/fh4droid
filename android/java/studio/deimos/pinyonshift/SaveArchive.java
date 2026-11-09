package studio.deimos.pinyonshift;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Profile-scoped archives, fully validated before live saves are moved. */
final class SaveArchive {
    private static final long LIMIT=512L*1024*1024;
    private static final int FILES=8192, DEPTH=64;
    private static final String V1="FH4Droid base-game saves v1\n", V2="FH4Droid base-game saves v2\n";
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
        List<String> roots=scopes(scope);
        try(ZipOutputStream zip=new ZipOutputStream(stream)){
            entry(zip,"fh4droid-save.txt",V2);long[] budget={0,0,0};
            for(String name:roots){File root=new File(state,name);if(Files.exists(root.toPath(),LinkOption.NOFOLLOW_LINKS))write(root,root,name,zip,budget,0);}
            if(budget[2]==0)throw new IOException("No saves found in the selected profiles.");
        }
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
            if(++budget[0]>FILES)throw new IOException("Too many save entries.");
            if(Files.isSymbolicLink(file.toPath()))throw new IOException("Save links are not supported.");
            if(file.isDirectory()){write(root,file,prefix,zip,budget,depth+1);continue;}
            if(!file.isFile())continue;
            budget[2]++;
            String name=prefix+"/"+root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar,'/');validateName(name);
            zip.putNextEntry(new ZipEntry(name));
            try(InputStream in=new FileInputStream(file)){
                byte[] buffer=new byte[65536];long done=0;
                for(int n;(n=in.read(buffer))!=-1;){FileOperation.check();if((budget[1]+=n)>LIMIT)throw new IOException("Save data exceeds the export limit.");zip.write(buffer,0,n);done+=n;FileOperation.progress(file.getName(),done,file.length());}
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
    static synchronized void importInto(File state,InputStream input)throws IOException{
        Files.createDirectories(state.toPath());File stage=Files.createTempDirectory(state.toPath(),"save-import-").toFile();
        try{
            String marker=null;long bytes=0;int count=0;Set<String> names=new HashSet<>(),roots=new LinkedHashSet<>();byte[] buffer=new byte[65536];
            try(ZipInputStream zip=new ZipInputStream(input)){
                for(ZipEntry e;(e=zip.getNextEntry())!=null;){
                    FileOperation.check();String name=e.getName();
                    if(name.equals("fh4droid-save.txt")){
                        if(marker!=null)throw new IOException("Duplicate save information.");
                        ByteArrayOutputStream tag=new ByteArrayOutputStream();
                        for(int n;(n=zip.read(buffer))!=-1;){if(tag.size()+n>1024)throw new IOException("Invalid save information.");tag.write(buffer,0,n);}
                        marker=new String(tag.toByteArray(),StandardCharsets.UTF_8);
                        if(!V1.equals(marker)&&!V2.equals(marker))throw new IOException("This backup is not for the base-game build.");
                        continue;
                    }
                    String clean=validateName(name);
                    if(++count>FILES||!names.add(clean))throw new IOException("Duplicate save entry or too many files.");
                    String root=clean.substring(0,clean.indexOf('/'));Path dest=stage.toPath().resolve(clean).normalize();
                    if(!dest.startsWith(stage.toPath()))throw new IOException("Invalid save path.");
                    if(e.isDirectory()){Files.createDirectories(dest);continue;}
                    Files.createDirectories(dest.getParent());
                    try(OutputStream out=Files.newOutputStream(dest,StandardOpenOption.CREATE_NEW)){
                        for(int n;(n=zip.read(buffer))!=-1;){FileOperation.check();if((bytes+=n)>LIMIT)throw new IOException("Save archive is too large.");out.write(buffer,0,n);}
                    }roots.add(root);
                }
            }
            if(marker==null||bytes==0||roots.isEmpty())throw new IOException("The archive contains no compatible saves.");
            if(V1.equals(marker)&&!roots.equals(Collections.singleton(NORMAL)))throw new IOException("A legacy archive can contain only the normal profile.");
            FileOperation.check();switchRoots(state,stage,new ArrayList<>(roots));
        }finally{delete(stage);}
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
    private static void move(File from,File to)throws IOException{
        Files.createDirectories(to.toPath().getParent());Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE);
    }
    private static void switchRoots(File state,File stage,List<String> roots)throws IOException{
        if(Files.isSymbolicLink(state.toPath())||Files.isSymbolicLink(stage.toPath()))throw new IOException("Linked save roots are not supported.");
        for(String root:roots)if(!rootName(root)||Files.isSymbolicLink(new File(state,root).toPath())||Files.isSymbolicLink(new File(stage,root).toPath()))throw new IOException("Invalid or linked profile folder.");
        for(String root:roots)for(File folder:new File[]{new File(state,root),new File(stage,root)})if(folder.exists()&&!folder.isDirectory())throw new IOException("A profile path is not a folder.");
        File backup=newBackup(state),payload=new File(backup,"payload");List<String> moved=new ArrayList<>(),installed=new ArrayList<>();
        try{
            Files.write(new File(backup,"roots.txt").toPath(),String.join("\n",roots).getBytes(StandardCharsets.UTF_8));
            for(String root:roots){File live=new File(state,root);if(live.exists()){move(live,new File(payload,root));moved.add(root);}}
            for(String root:roots){File incoming=new File(stage,root);if(incoming.exists()){move(incoming,new File(state,root));installed.add(root);}}
        }catch(IOException error){
            boolean rolledBack=true;Collections.reverse(installed);Collections.reverse(moved);
            for(String root:installed)try{move(new File(state,root),new File(stage,root));}catch(IOException rollback){error.addSuppressed(rollback);rolledBack=false;}
            for(String root:moved)try{move(new File(payload,root),new File(state,root));}catch(IOException rollback){error.addSuppressed(rollback);rolledBack=false;}
            if(rolledBack)delete(backup);else throw new IOException("Save rollback needs recovery from "+backup.getName()+". Existing backups were retained.",error);
            throw error;
        }
    }
    static File latest(File state){
        File[] dirs=new File(state,"backups").listFiles(f->f.isDirectory()&&!Files.isSymbolicLink(f.toPath())&&f.getName().startsWith("android-import-"));
        if(dirs==null||dirs.length==0)return null;Arrays.sort(dirs,Comparator.comparing(File::getName));return dirs[dirs.length-1];
    }
    static synchronized void restore(File state)throws IOException{
        File backup=latest(state);if(backup==null)throw new IOException("No previous imported-save backup found.");File marker=new File(backup,"roots.txt");
        if(Files.isSymbolicLink(marker.toPath()))throw new IOException("Linked backup metadata is not supported.");
        if(marker.isFile()){
            if(marker.length()>100)throw new IOException("Invalid backup information.");
            List<String> roots=Files.readAllLines(marker.toPath(),StandardCharsets.UTF_8);
            if(roots.isEmpty()||roots.size()>2||new HashSet<>(roots).size()!=roots.size())throw new IOException("Invalid backup profiles.");
            switchRoots(state,new File(backup,"payload"),roots);delete(backup);
        }else{
            File stage=Files.createTempDirectory(state.toPath(),"save-restore-").toFile();
            try{
                move(backup,new File(stage,NORMAL));
                try{switchRoots(state,stage,Collections.singletonList(NORMAL));}
                catch(IOException error){File old=new File(stage,NORMAL);if(old.exists())move(old,backup);throw error;}
            }finally{File[] remaining=stage.listFiles();if(remaining!=null&&remaining.length==0)delete(stage);}
        }
    }
    static void delete(File file)throws IOException{
        if(!Files.exists(file.toPath(),LinkOption.NOFOLLOW_LINKS))return;
        if(file.isDirectory()&&!Files.isSymbolicLink(file.toPath())){File[] children=file.listFiles();if(children==null)throw new IOException("Cannot read temporary folder.");for(File child:children)delete(child);}
        Files.delete(file.toPath());
    }
    private SaveArchive(){}
}
