package studio.deimos.pinyonshift;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Undo journal for content publication. Recover under android-content.lock
 * before another import or game launch. A process kill rolls back the entire
 * current publication; earlier committed DLC packages remain installed. */
final class ContentTransaction {
    private static final String JOURNAL=".android-content-transaction";
    private static final ThreadLocal<ContentTransaction> ACTIVE=new ThreadLocal<>();
    private final File root, directory;
    private final Properties data=new Properties();
    private int count;
    private boolean rollbackOnly;

    private ContentTransaction(File root)throws IOException {
        this.root=root.getCanonicalFile();
        directory=new File(this.root,JOURNAL);
        Files.createDirectories(directory.toPath());
        save();
    }
    static synchronized <T>T run(File root,ContentFiles.Work<T> work)throws Exception {
        if(ACTIVE.get()!=null){try{return work.run();}catch(Exception error){ACTIVE.get().rollbackOnly=true;throw error;}}
        recover(root);
        ContentTransaction transaction=new ContentTransaction(root);
        ACTIVE.set(transaction);
        try {
            T result=work.run();
            if(transaction.rollbackOnly)throw new IOException("A nested content operation failed; changes were recovered.");
            transaction.data.setProperty("committed","true");transaction.save();
            // The committed marker is durable before deleting any undo data.
            try{ContentFiles.delete(transaction.directory);}catch(IOException cleanup){android.util.Log.w("FH4Droid","Committed content journal retained for cleanup",cleanup);}
            return result;
        } catch(Exception error) {
            try{recover(root);}catch(IOException restore){error.addSuppressed(restore);}
            throw error;
        } finally { ACTIVE.remove(); }
    }
    static ContentTransaction current()throws IOException {
        ContentTransaction transaction=ACTIVE.get();
        if(transaction==null)throw new IOException("Content publication has no recovery transaction.");
        return transaction;
    }
    private String relative(File file)throws IOException {
        File canonical=file.getCanonicalFile();String prefix=root.getPath()+File.separator;
        if(!canonical.getPath().startsWith(prefix)||Files.isSymbolicLink(file.toPath()))throw new IOException("Invalid transaction destination.");
        String path=root.toPath().relativize(canonical.toPath()).toString().replace(File.separatorChar,'/');
        if(path.startsWith(JOURNAL+"/")||path.equals(JOURNAL))throw new IOException("Invalid transaction destination.");
        return ContentFiles.safePath(path);
    }
    private void save()throws IOException {
        data.setProperty("schema","1");data.setProperty("count",Integer.toString(count));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();data.store(bytes,"FH4Droid content undo journal");
        ContentFiles.atomic(new File(directory,"journal.properties"),bytes.toByteArray());
        phase(root,"true".equals(data.getProperty("committed"))?"committed":count==0?"prepared":"journaled "+data.getProperty((count-1)+".type"),count);
    }
    private static void phase(File root,String phase,int count){
        if(!root.getName().equals("state"))return;
        try{String json="{\"time_ms\":"+System.currentTimeMillis()+",\"phase\":"+ContentFiles.json(phase)+",\"records\":"+count+"}";ContentFiles.atomic(new File(root,"logs/android-content-transaction.json"),json.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        catch(IOException error){android.util.Log.w("FH4Droid","Could not record transaction phase",error);}
    }
    private int record(String type,File target,File source)throws IOException {
        int id=count;String prefix=id+".";
        data.setProperty(prefix+"type",type);data.setProperty(prefix+"target",relative(target));
        data.setProperty(prefix+"previous",Boolean.toString(target.exists()));
        if(source!=null)data.setProperty(prefix+"source",relative(source));
        count++;save();return id;
    }
    void watch(File target)throws IOException {
        watch(target,null);
    }
    void watchSetting(File target,String key)throws IOException{
        if(!key.matches("[a-zA-Z0-9_]+"))throw new IOException("Invalid transaction setting.");
        watch(target,key);
    }
    private void watch(File target,String key)throws IOException {
        String path=relative(target);
        for(int i=0;i<count;i++)if(path.equals(data.getProperty(i+".target")))return;
        if(target.exists()&&!target.isFile())throw new IOException("Content metadata is not a file.");
        if(target.length()>2*1024*1024)throw new IOException("Content metadata is too large for recovery.");
        // Metadata snapshots precede the write-ahead record. A kill before
        // the record cannot have changed the destination yet.
        if(target.isFile())ContentFiles.atomic(new File(directory,count+".backup"),Files.readAllBytes(target.toPath()));
        if(key!=null)data.setProperty(count+".key",key);
        record(key==null?"file":"setting",target,null);
    }
    void replace(File source,File target)throws IOException {
        Files.createDirectories(target.getParentFile().toPath());
        int id=record("replace",target,null);
        if(target.exists())Files.move(target.toPath(),new File(directory,id+".backup").toPath(),StandardCopyOption.ATOMIC_MOVE);
        if(source!=null)Files.move(source.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE);
    }
    void move(File source,File target)throws IOException {
        if(target.exists()||!source.exists())throw new IOException("Content move conflicts with installed files.");
        Files.createDirectories(target.getParentFile().toPath());record("move",target,source);
        Files.move(source.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE);
    }
    static boolean pending(File root){return new File(root,JOURNAL).exists();}
    static synchronized void recover(File root)throws IOException {
        root=root.getCanonicalFile();File directory=new File(root,JOURNAL);
        if(!directory.exists())return;
        if(Files.isSymbolicLink(directory.toPath()))throw new IOException("Content recovery journal is linked.");
        File journal=new File(directory,"journal.properties");
        if(!journal.exists()) {ContentFiles.delete(directory);return;} // No published operation yet.
        if(journal.length()>1024*1024)throw new IOException("Content recovery journal is too large.");
        Properties data=new Properties();try(InputStream input=new FileInputStream(journal)){data.load(input);}
        if(!"1".equals(data.getProperty("schema")))throw new IOException("Unknown content recovery journal.");
        int count;try{count=Integer.parseInt(data.getProperty("count"));}catch(RuntimeException bad){throw new IOException("Invalid content recovery journal.",bad);}
        if(count<0||count>1000)throw new IOException("Invalid content recovery record count.");
        phase(root,"recovering",count);
        if(!"true".equals(data.getProperty("committed")))for(int i=count-1;i>=0;i--){
            String prefix=i+".",type=data.getProperty(prefix+"type");File target=resolve(root,data.getProperty(prefix+"target")),backup=new File(directory,i+".backup");
            if("setting".equals(type)){
                boolean previous="true".equals(data.getProperty(prefix+"previous"));
                if(previous&&!backup.isFile())throw new IOException("Content recovery snapshot is missing.");
                MobileConfig.restoreSetting(target,data.getProperty(prefix+"key",""),previous?backup:null);
            }else if("file".equals(type)){
                if("true".equals(data.getProperty(prefix+"previous"))){if(!backup.isFile())throw new IOException("Content recovery snapshot is missing.");ContentFiles.atomic(target,Files.readAllBytes(backup.toPath()));}
                else if(target.exists()||Files.isSymbolicLink(target.toPath()))Files.deleteIfExists(target.toPath());
            }else if("replace".equals(type)){
                if(backup.exists()){ContentFiles.delete(target);Files.createDirectories(target.getParentFile().toPath());Files.move(backup.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE);}
                else if(!"true".equals(data.getProperty(prefix+"previous")))ContentFiles.delete(target);
            }else if("move".equals(type)){
                File source=resolve(root,data.getProperty(prefix+"source"));
                if(target.exists()){if(source.exists())throw new IOException("Content recovery conflicts with existing files.");Files.createDirectories(source.getParentFile().toPath());Files.move(target.toPath(),source.toPath(),StandardCopyOption.ATOMIC_MOVE);}
            }else throw new IOException("Unknown content recovery operation.");
            // Persist the reverse cursor before an earlier operation can reuse its paths.
            data.setProperty("count",Integer.toString(i));
            ByteArrayOutputStream progress=new ByteArrayOutputStream();
            data.store(progress,"FH4Droid content undo journal");
            ContentFiles.atomic(journal,progress.toByteArray());
        }
        ContentFiles.delete(directory);
        phase(root,"true".equals(data.getProperty("committed"))?"committed cleanup":"recovered",count);
    }
    private static File resolve(File root,String path)throws IOException {
        if(path==null)throw new IOException("Content recovery path is missing.");
        File file=new File(root,ContentFiles.safePath(path));String base=root.getCanonicalPath()+File.separator;
        if(!file.getCanonicalPath().startsWith(base)||Files.isSymbolicLink(file.toPath()))throw new IOException("Invalid content recovery path.");
        return file;
    }
    static File commonRoot(File a,File b)throws IOException {
        File root=a.getCanonicalFile().getParentFile();String other=b.getCanonicalPath();
        while(root!=null&&!other.startsWith(root.getPath()+File.separator))root=root.getParentFile();
        if(root==null||root.getParentFile()==null)throw new IOException("Content destinations do not share a private root.");
        return root;
    }
}
