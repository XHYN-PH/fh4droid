package studio.deimos.pinyonshift;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Shared bounded file operations for the mod and DLC managers. */
final class ContentFiles {
    static final long MAX_BYTES=8L*1024*1024*1024;
    static final int MAX_FILES=100000;
    interface Work<T>{T run()throws Exception;}
    static <T>T locked(Context c,Work<T> work)throws Exception{
        File state=new File(MobileConfig.base(c),"state");Files.createDirectories(state.toPath());
        try(RandomAccessFile file=new RandomAccessFile(new File(state,"android-content.lock"),"rw");
            FileChannel channel=file.getChannel();FileLock lock=channel.tryLock()){
            if(lock==null)throw new IOException("Another content operation is running.");
            if(GameFiles.running(c))throw new IOException("Quit the game before changing mods or DLC.");
            return work.run();
        }catch(OverlappingFileLockException error){throw new IOException("Another content operation is running.",error);}
    }
    static File temporary(Context c,String prefix)throws IOException{
        File root=new File(MobileConfig.base(c),"state/cache/content-imports");Files.createDirectories(root.toPath());
        return Files.createTempDirectory(root.toPath(),prefix).toFile();
    }
    static String safePath(String name)throws IOException{
        if(name==null||name.isEmpty()||name.indexOf('\\')>=0||name.indexOf(':')>=0||name.startsWith("/")||name.length()>1024)
            throw new IOException("Invalid content path.");
        String value=name.endsWith("/")?name.substring(0,name.length()-1):name;
        if(value.split("/",-1).length>64)throw new IOException("Content path is too deep.");
        for(String part:value.split("/",-1)){
            if(part.isEmpty()||part.equals(".")||part.equals("..")||part.endsWith(".")||part.endsWith(" "))throw new IOException("Invalid content path.");
            for(int i=0;i<part.length();i++)if(part.charAt(i)<32||part.charAt(i)==127)throw new IOException("Invalid content name.");
        }
        return value;
    }
    static long copy(InputStream input,OutputStream output,long limit)throws IOException{
        byte[] bytes=new byte[128*1024];long total=0;
        for(int n;(n=input.read(bytes))!=-1;){FileOperation.check();if(n==0)continue;total+=n;if(total>limit)throw new IOException("Content exceeds the size limit.");output.write(bytes,0,n);}
        return total;
    }
    static void unzip(File archive,File root)throws IOException{
        Set<String> seen=new HashSet<>();long remaining=MAX_BYTES;int count=0;
        try(ZipFile zip=new ZipFile(archive)){
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()){
                FileOperation.check();ZipEntry entry=entries.nextElement();String name=safePath(entry.getName());
                if(++count>MAX_FILES||!seen.add(name.toLowerCase(Locale.ROOT)))throw new IOException("Too many or duplicate archive entries.");
                File target=new File(root,name);if(!target.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator))throw new IOException("Invalid archive path.");
                if(entry.isDirectory()){Files.createDirectories(target.toPath());continue;}
                if(entry.getSize()>remaining)throw new IOException("Archive exceeds the size limit.");
                Files.createDirectories(target.getParentFile().toPath());
                try(InputStream in=zip.getInputStream(entry);OutputStream out=new FileOutputStream(target)){remaining-=copy(in,out,remaining);}
                FileOperation.progress(name,MAX_BYTES-remaining,0);
            }
        }
    }
    static void folder(Context c,Uri tree,File root)throws IOException{
        Uri document=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        copyDocument(c,tree,DocumentsContract.getDocumentId(document),root,0,new long[]{MAX_BYTES,MAX_FILES},new HashSet<>());
    }
    private static void copyDocument(Context c,Uri tree,String id,File root,int depth,long[] budget,Set<String> visited)throws IOException{
        if(depth>64||!visited.add(id))throw new IOException("Folder nesting is invalid.");
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
        try(Cursor cursor=c.getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){
            if(cursor==null)throw new IOException("Folder is unavailable.");Set<String> names=new HashSet<>();
            while(cursor.moveToNext()){
                FileOperation.check();String childId=cursor.getString(0),name=safePath(cursor.getString(1));
                if(name.indexOf('/')>=0||!names.add(name.toLowerCase(Locale.ROOT))||--budget[1]<0)throw new IOException("Invalid or duplicate folder entry.");
                File target=new File(root,name);
                if(DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2))){Files.createDirectories(target.toPath());copyDocument(c,tree,childId,target,depth+1,budget,visited);}
                else{Files.createDirectories(target.getParentFile().toPath());try(InputStream in=c.getContentResolver().openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree,childId));OutputStream out=new FileOutputStream(target)){
                    if(in==null)throw new IOException("File is unavailable.");budget[0]-=copy(in,out,budget[0]);}FileOperation.progress(name,MAX_BYTES-budget[0],0);}
            }
        }
    }
    static List<File> files(File root)throws IOException{
        List<File> out=new ArrayList<>();walk(root,out,0);return out;
    }
    private static void walk(File root,List<File> out,int depth)throws IOException{
        if(depth>64||Files.isSymbolicLink(root.toPath()))throw new IOException("Invalid content folder.");
        File[] children=root.listFiles();if(children==null)throw new IOException("Content folder is unavailable.");
        for(File file:children){FileOperation.check();safePath(file.getName());if(Files.isSymbolicLink(file.toPath()))throw new IOException("Content contains a symbolic link.");if(file.isDirectory())walk(file,out,depth+1);else if(file.isFile()){if(out.size()>=MAX_FILES)throw new IOException("Too many files.");out.add(file);}}
    }
    static File treeFile(File root,String relative)throws IOException{
        if(!root.isDirectory())return null;File current=root;
        for(String part:safePath(relative).split("/")){
            if(Files.isSymbolicLink(current.toPath()))throw new IOException("Content path contains a symbolic link.");
            File[] entries=current.listFiles();if(entries==null)return null;File found=null;
            for(File entry:entries)if(entry.getName().equalsIgnoreCase(part)){if(found!=null)throw new IOException("Content has duplicate case-insensitive paths.");found=entry;}
            if(found==null)return null;current=found;
        }
        if(Files.isSymbolicLink(current.toPath()))throw new IOException("Content path contains a symbolic link.");return current.isFile()?current:null;
    }
    static void copyTree(File source,File destination)throws IOException{
        Files.createDirectories(destination.toPath());long remaining=MAX_BYTES;
        for(File file:files(source)){
            String relative=source.toPath().relativize(file.toPath()).toString();File target=new File(destination,relative);Files.createDirectories(target.getParentFile().toPath());
            try(InputStream in=new FileInputStream(file);OutputStream out=new FileOutputStream(target)){remaining-=copy(in,out,remaining);}
        }
    }
    static void publish(File source,File destination)throws IOException{
        Files.createDirectories(destination.getParentFile().toPath());
        if(Files.isSymbolicLink(destination.toPath()))throw new IOException("Content destination is invalid.");
        File backup=new File(destination.getParentFile(),"."+destination.getName()+"-rollback-"+UUID.randomUUID());boolean moved=destination.exists();
        if(moved)Files.move(destination.toPath(),backup.toPath(),StandardCopyOption.ATOMIC_MOVE);
        try{Files.move(source.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE);}
        catch(IOException error){if(moved)try{Files.move(backup.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE);}catch(IOException restore){error.addSuppressed(restore);}throw error;}
        if(moved)try{delete(backup);}catch(IOException cleanup){android.util.Log.w("FH4Droid","Previous content retained after a successful update",cleanup);}
    }
    static void atomic(File target,byte[] bytes)throws IOException{
        Files.createDirectories(target.getParentFile().toPath());Path stage=Files.createTempFile(target.getParentFile().toPath(),"content-",".part");
        try{try(FileOutputStream out=new FileOutputStream(stage.toFile())){out.write(bytes);out.getFD().sync();}Files.move(stage,target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(stage);}
    }
    static void delete(File root)throws IOException{
        if(!root.exists()&&!Files.isSymbolicLink(root.toPath()))return;
        if(root.isDirectory()&&!Files.isSymbolicLink(root.toPath())){File[] files=root.listFiles();if(files==null)throw new IOException("Content folder is unavailable.");for(File file:files)delete(file);}
        Files.deleteIfExists(root.toPath());
    }
    static String text(File file,int limit)throws IOException{if(!file.isFile()||Files.isSymbolicLink(file.toPath())||file.length()>limit)throw new IOException("Content metadata is unavailable or too large.");return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);}
    static String hash(File file)throws IOException{
        try{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] bytes=new byte[128*1024];for(int n;(n=in.read(bytes))!=-1;){FileOperation.check();digest.update(bytes,0,n);}}return hex(digest.digest());}
        catch(java.security.NoSuchAlgorithmException error){throw new IOException(error);}
    }
    static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02X",b&255));return out.toString();}
    static String json(String text){StringBuilder out=new StringBuilder("\"");for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c=='"'||c=='\\')out.append('\\').append(c);else if(c<32||c>126)out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else out.append(c);}return out.append('"').toString();}
    static String payloadDigest(File root,List<File> files)throws IOException{
        try{files.sort((a,b)->windowsPathCompare(root.toPath().relativize(a.toPath()).toString().replace(File.separatorChar,'/'),root.toPath().relativize(b.toPath()).toString().replace(File.separatorChar,'/')));
            MessageDigest hash=MessageDigest.getInstance("SHA-256");hash.update((byte)'[');boolean first=true;
            for(File file:files){String path=root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar,'/');if(!first)hash.update((byte)',');first=false;
                String row="{\"path\":"+json(path)+",\"sha256\":"+json(hash(file))+",\"size\":"+file.length()+"}";hash.update(row.getBytes(StandardCharsets.UTF_8));}
            hash.update((byte)']');return hex(hash.digest());
        }catch(java.security.NoSuchAlgorithmException error){throw new IOException(error);}
    }
    static int windowsPathCompare(String a,String b){String[] x=a.toLowerCase(Locale.ROOT).split("/"),y=b.toLowerCase(Locale.ROOT).split("/");for(int i=0;i<Math.min(x.length,y.length);i++){int value=x[i].compareTo(y[i]);if(value!=0)return value;}return Integer.compare(x.length,y.length);}
    private ContentFiles(){}
}
