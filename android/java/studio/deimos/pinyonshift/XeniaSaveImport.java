package studio.deimos.pinyonshift;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
/** Import a single owned Xenia profile beside existing native profiles. */
final class XeniaSaveImport {
    static final String TITLE="4D5309C9";
    static boolean usableXuid(String id){
        if(id==null||!id.matches("[0-9a-fA-F]{16}"))return false;
        long bits=Long.parseUnsignedLong(id,16);return bits!=0&&(bits&0x00C0000000000000L)==0;
    }
    static final class Prepared implements AutoCloseable {
        final File stage,title;final String xuid;
        Prepared(File s,File t,String x){stage=s;title=t;xuid=x;}
        public void close()throws IOException{SaveArchive.delete(stage);}
    }
    static Prepared prepare(File scratch,InputStream input)throws IOException{
        Files.createDirectories(scratch.toPath());File stage=Files.createTempDirectory(scratch.toPath(),"xenia-import-").toFile();
        boolean success=false;
        try{
            String id=null;long bytes=0;int count=0;Set<String> seen=new HashSet<>();
            try(ZipInputStream zip=new ZipInputStream(input)){
                for(ZipEntry e;(e=zip.getNextEntry())!=null;){
                    FileOperation.check();if(++count>8192)throw new IOException("Too many Xenia ZIP entries.");String name=e.getName();if(name.indexOf('\\')>=0||name.length()>2048)throw new IOException("Invalid Xenia ZIP path.");
                    String[] parts=name.split("/",-1);if(parts.length>64)throw new IOException("Xenia ZIP folders are too deep.");
                    for(int i=0;i<parts.length;i++)if(parts[i].equals(".")||parts[i].equals("..")||parts[i].indexOf(':')>=0||parts[i].indexOf('\0')>=0||parts[i].isEmpty()&&i<parts.length-1)throw new IOException("Invalid Xenia ZIP path.");
                    int title=-1;for(int i=1;i<parts.length;i++)if(parts[i].equalsIgnoreCase(TITLE)){if(title>=0)throw new IOException("Ambiguous title folder.");title=i;}
                    if(title<0||e.isDirectory()){byte[] ignored=new byte[8192];for(int n;(n=zip.read(ignored))!=-1;){FileOperation.check();if((bytes+=n)>512L*1024*1024)throw new IOException("Xenia saves exceed 512 MB.");}continue;}
                    String candidate=parts[title-1].toUpperCase(Locale.ROOT);if(!usableXuid(candidate))throw new IOException("Keep the original 16-digit XUID folder above "+TITLE+".");
                    if(id!=null&&!id.equals(candidate))throw new IOException("Choose a ZIP with one Xenia profile.");id=candidate;
                    String relative=String.join("/",Arrays.copyOfRange(parts,title+1,parts.length));if(relative.isEmpty()||!seen.add(relative.toLowerCase(Locale.ROOT)))throw new IOException("Duplicate or excessive Xenia files.");
                    File dest=new File(stage,TITLE+"/"+relative);Files.createDirectories(dest.toPath().getParent());
                    try(OutputStream out=Files.newOutputStream(dest.toPath(),StandardOpenOption.CREATE_NEW)){byte[] buffer=new byte[65536];for(int n;(n=zip.read(buffer))!=-1;){FileOperation.check();if((bytes+=n)>512L*1024*1024)throw new IOException("Xenia saves exceed 512 MB.");out.write(buffer,0,n);}}
                }
            }
            File title=new File(stage,TITLE),profile=findIgnoreCase(title,"00000001","ForzaProfile","ForzaProfile");
            if(id==null||profile==null||!profile.isFile()||profile.length()==0)throw new IOException("No Forza Horizon profile. ZIP content/<XUID>/4D5309C9 with its folders intact.");
            File flags=findIgnoreCase(title,"00000001","ForzaProfile","VersionFlags");
            if(flags!=null){byte[] header=new byte[12];try(InputStream in=new FileInputStream(flags)){int read=0;while(read<header.length){int n=in.read(header,read,header.length-read);if(n<0)break;read+=n;}if(read<12||!new String(header,0,4,StandardCharsets.US_ASCII).equals("cmss"))throw new IOException("Invalid save version information.");}
                for(int i=4;i<12;i++)if(header[i]!=0)throw new IOException("This profile was saved by title update v4. This APK uses the base game; use a base-game save. Nothing was imported.");}
            success=true;return new Prepared(stage,title,id);
        }finally{if(!success)SaveArchive.delete(stage);}
    }
    private static File findIgnoreCase(File root,String... parts){for(String part:parts){File[] files=root.listFiles(f->f.getName().equalsIgnoreCase(part));if(files==null||files.length!=1)return null;root=files[0];}return root;}
    static void install(File state,Prepared save)throws IOException{
        FileOperation.check();
        File user=new File(state,"user"),target=new File(user,save.xuid+"/"+TITLE),config=new File(state,"config/pinyon_shift.toml");
        if(Files.isSymbolicLink(state.toPath())||Files.isSymbolicLink(user.toPath())||Files.isSymbolicLink(target.toPath().getParent())||Files.isSymbolicLink(config.toPath())||Files.isSymbolicLink(config.toPath().getParent())||target.exists())throw new IOException("That profile already exists or uses a linked path. Existing saves were kept.");
        File[] existing=user.listFiles(f->f.getName().equalsIgnoreCase(save.xuid));if(existing!=null&&existing.length>0)throw new IOException("That profile already exists. Existing saves were kept.");
        File backups=new File(state,"backups");if(Files.isSymbolicLink(backups.toPath()))throw new IOException("Linked backup folder.");Files.createDirectories(backups.toPath());
        File backup=Files.createTempDirectory(backups.toPath(),"android-xenia-").toFile();
        if(config.isFile())Files.copy(config.toPath(),new File(backup,"pinyon_shift.toml").toPath());
        Files.write(new File(backup,"import.txt").toPath(),("XUID "+save.xuid+"\nExisting native saves kept.\n").getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(target.toPath().getParent());
        Files.move(save.title.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE);
        try{MobileConfig.putFile(config,Collections.singletonMap("user_xuid",MobileConfig.quote(save.xuid)));}
        catch(IOException error){try{Files.move(target.toPath(),save.title.toPath(),StandardCopyOption.ATOMIC_MOVE);}catch(IOException recovery){error.addSuppressed(recovery);}throw error;}
    }
    private XeniaSaveImport(){}
}
