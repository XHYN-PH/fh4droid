package studio.deimos.pinyonshift;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;

/** Every shared URI names one completed report which later exports never rewrite. */
final class ReportFiles {
    interface Writer {void write(OutputStream out)throws IOException;}
    static File create(File cache,Writer writer)throws IOException{
        Path dir=new File(cache,"reports").toPath();Files.createDirectories(dir);
        Path part=Files.createTempFile(dir,"report-",".part");
        Path complete=dir.resolve("fh4droid-report-"+UUID.randomUUID()+".zip");
        try{
            try(OutputStream out=Files.newOutputStream(part)){writer.write(out);}
            Files.move(part,complete,StandardCopyOption.ATOMIC_MOVE);
            return complete.toFile();
        }finally{Files.deleteIfExists(part);}
    }
    static File resolve(File cache,String name)throws FileNotFoundException{
        // Retain read access for reports shared before upgrading the app.
        if(name==null||!(name.equals("fh4droid-report.zip")||name.matches("fh4droid-report-[0-9a-f-]{36}\\.zip")))throw new FileNotFoundException("Unknown report");
        File file=new File(new File(cache,"reports"),name);
        if(!file.isFile()||Files.isSymbolicLink(file.toPath()))throw new FileNotFoundException("Report unavailable");
        return file;
    }
    private ReportFiles(){}
}
