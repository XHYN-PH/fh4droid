package studio.deimos.pinyonshift;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Bounded GDFX inspection; matches the SDK disc reader, without loading the game. */
final class DiscImage {
    static final class Entry {final long offset,size;Entry(long o,long s){offset=o;size=s;}}
    private final FileChannel channel;private final long size,partition;
    final Map<String,Entry> files=new LinkedHashMap<>();
    DiscImage(FileChannel channel)throws IOException{
        this.channel=channel;size=channel.size();long found=-1;
        for(long offset:new long[]{0,0xfb20,0x20600,0x2080000,0xfd90000}){
            long h=offset+32*2048;if(h+2048>size)continue;
            byte[] magic=read(h,20);if(new String(magic,StandardCharsets.US_ASCII).equals("MICROSOFT*XBOX*MEDIA")){found=offset;break;}
        }
        if(found<0)throw new IOException("Not a supported Xbox 360 disc image.");
        partition=found;ByteBuffer header=ByteBuffer.wrap(read(partition+32*2048,32)).order(ByteOrder.LITTLE_ENDIAN);
        long sector=Integer.toUnsignedLong(header.getInt(20)),length=Integer.toUnsignedLong(header.getInt(24));
        directory(sector,length,"",0,new HashSet<Long>());
        if(!files.containsKey("default.xex"))throw new IOException("The disc contains no default.xex.");
    }
    private byte[] read(long offset,int count)throws IOException{
        FileOperation.check();
        if(offset<0||count<0||offset>size-count)throw new IOException("Disc image is truncated.");
        ByteBuffer out=ByteBuffer.allocate(count);while(out.hasRemaining()){int n=channel.read(out,offset+out.position());if(n<=0)throw new IOException("Cannot read this disc image.");}return out.array();
    }
    private void directory(long sector,long bytes,String prefix,int depth,Set<Long> ancestors)throws IOException{
        if(depth>32||bytes<14||bytes>32*1024*1024||!ancestors.add(sector))throw new IOException("Invalid disc directory.");
        byte[] data=read(partition+sector*2048,(int)bytes);ByteBuffer table=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        Deque<Integer> pending=new ArrayDeque<>();Set<Integer> visited=new HashSet<>();pending.add(0);
        while(!pending.isEmpty()){
            int at=pending.removeFirst();if(!visited.add(at)||at<0||at>data.length-14)throw new IOException("Invalid disc directory entry.");
            int left=Short.toUnsignedInt(table.getShort(at))*4,right=Short.toUnsignedInt(table.getShort(at+2))*4;
            if(left!=0)pending.add(left);if(right!=0)pending.add(right);
            long fileSector=Integer.toUnsignedLong(table.getInt(at+4)),length=Integer.toUnsignedLong(table.getInt(at+8));
            int flags=data[at+12]&255,n=data[at+13]&255;
            if(n==0||at+14+n>data.length)throw new IOException("Invalid disc filename.");
            String name=new String(data,at+14,n,StandardCharsets.US_ASCII);
            if(name.equals(".")||name.equals("..")||name.indexOf('/')>=0||name.indexOf('\\')>=0||name.indexOf('\0')>=0)throw new IOException("Invalid disc filename.");
            String path=prefix+name.toLowerCase(Locale.ROOT);long offset=partition+fileSector*2048;
            if(offset<0||offset>size-length)throw new IOException("Disc image has missing data.");
            if((flags&16)!=0){if(length>0)directory(fileSector,length,path+"/",depth+1,new HashSet<>(ancestors));}
            else{if(files.size()>=100000||files.put(path,new Entry(offset,length))!=null)throw new IOException("Duplicate or excessive disc files.");}
        }
    }
    void copy(String name,File output,String expectedHash)throws IOException{
        Entry entry=files.get(name.toLowerCase(Locale.ROOT));if(entry==null)throw new IOException("Missing "+name+" on this disc.");
        try{MessageDigest hash=MessageDigest.getInstance("SHA-256");output.getParentFile().mkdirs();
            try(OutputStream out=new FileOutputStream(output)){long done=0;while(done<entry.size){byte[] b=read(entry.offset+done,(int)Math.min(65536,entry.size-done));hash.update(b);out.write(b);done+=b.length;FileOperation.progress(name,done,entry.size);}}
            if(!hex(hash.digest()).equalsIgnoreCase(expectedHash)){output.delete();throw new IOException("Unsupported "+name+". Use the supported base-game disc.");}
        }catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    String hash(String name)throws IOException{
        Entry entry=files.get(name.toLowerCase(Locale.ROOT));if(entry==null)throw new IOException("Missing "+name+" on this disc.");
        try{MessageDigest d=MessageDigest.getInstance("SHA-256");long done=0;
            while(done<entry.size){byte[] b=read(entry.offset+done,(int)Math.min(65536,entry.size-done));d.update(b);done+=b.length;FileOperation.progress(name,done,entry.size);}
            return hex(d.digest());
        }catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    static String hex(byte[] data){StringBuilder s=new StringBuilder();for(byte b:data)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    static String hash(File file)throws IOException{
        try{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];long done=0;for(int n;(n=in.read(b))!=-1;){d.update(b,0,n);done+=n;FileOperation.progress(file.getName(),done,file.length());}}return hex(d.digest());}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
}
