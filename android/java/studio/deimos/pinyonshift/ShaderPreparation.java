package studio.deimos.pinyonshift;

import android.content.Context;
import java.io.*;
import java.nio.file.*;

/** Bounded import of the pipeline list prepared from the player's own disc. */
final class ShaderPreparation {
    static final String FILE="4D5309C9.fbo.aot.xpso";
    static File installed(Context c){return new File(MobileConfig.base(c),"state/cache/shaders/shareable/"+FILE);}
    static void validate(File source)throws IOException{
        if(source.length()<16||source.length()>64L*1024*1024)throw new IOException("Pipeline list size is invalid.");
        try(RandomAccessFile file=new RandomAccessFile(source,"r")){
            if(file.readInt()!=0x5845414f||Integer.reverseBytes(file.readInt())!=0x20261018)throw new IOException("Pipeline list belongs to a different renderer version.");
            long shaders=Integer.toUnsignedLong(Integer.reverseBytes(file.readInt())),pipelines=Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()));
            if(shaders==0||pipelines==0||shaders>100000||pipelines>1000000)throw new IOException("Pipeline list counts are invalid.");
            for(long i=0;i<shaders;i++){
                FileOperation.check();if(file.length()-file.getFilePointer()<12)throw new IOException("Pipeline shader header is incomplete.");file.readLong();long words=Integer.reverseBytes(file.readInt())&0x7fffffffL;
                if(words==0||words>65536||words*4>file.length()-file.getFilePointer())throw new IOException("Pipeline shader is incomplete or too large.");file.seek(file.getFilePointer()+words*4);
            }
            if(file.length()-file.getFilePointer()!=pipelines*58)throw new IOException("Pipeline descriptions are incomplete.");
        }
    }
    static void install(Context c,File source)throws Exception{
        ContentFiles.locked(c,()->{validate(source);File target=installed(c);Files.createDirectories(target.getParentFile().toPath());Files.move(source.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);return null;});
    }
    private ShaderPreparation(){}
}
