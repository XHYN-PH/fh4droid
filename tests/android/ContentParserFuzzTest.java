package studio.deimos.pinyonshift;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** Bounded, deterministic malformed-input corpus; unexpected exceptions fail. */
public final class ContentParserFuzzTest {
    public static void main(String[] args)throws Exception{
        Random random=new Random(0x46483444L);int checks=0;
        File root=Files.createTempDirectory("fh4droid-parser-corpus-").toFile();
        try{
            File file=new File(root,"pipelines.xpso");
            for(int test=0;test<1000;test++){
                byte[] bytes=new byte[random.nextInt(192)];random.nextBytes(bytes);
                if(bytes.length>=16&&test%2==0){ByteBuffer header=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);header.put(new byte[]{'X','E','A','O'}).putInt(0x20261018).putInt(random.nextInt(20)).putInt(random.nextInt(20));}
                Files.write(file.toPath(),bytes);try{ShaderPreparation.validate(file);}catch(IOException expected){}checks++;
            }
            for(int test=0;test<5000;test++){
                StringBuilder text=new StringBuilder();for(int i=0,n=random.nextInt(128);i<n;i++)text.append((char)random.nextInt(128));String input=text.toString();
                try{String safe=ContentFiles.safePath(input);if(safe.startsWith("/")||safe.contains("\\")||safe.contains(":"))throw new AssertionError("unsafe accepted path");for(String part:safe.split("/",-1))if(part.equals("..")||part.equals(".")||part.isEmpty())throw new AssertionError("unsafe accepted component");}catch(IOException expected){}checks++;
                try{ModSql.statements(input);}catch(IOException expected){}checks++;
            }
            System.out.println("PASS: "+checks+" malformed pipeline, path and SQL corpus cases");
        }finally{ContentFiles.delete(root);}
    }
}
