package studio.deimos.pinyonshift;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.*;

/** Every shared URI names one completed report which later exports never rewrite. */
final class ReportFiles {
    static String thermalLabel(int level){String[] labels={"Normal","Light","Moderate","Severe","Critical","Emergency","Shutdown"};return level>=0&&level<labels.length?labels[level]:"Unknown ("+level+")";}
    static String exitLabel(int reason,int status){
        switch(reason){case 1:return status==0?"Normal app exit":"App exited with code "+status;
            case 2:return "Terminated by signal";case 3:return "Android low-memory kill";case 4:return "Java crash";case 5:return "Native crash";case 6:return "App not responding";
            case 7:return "Initialization failed";case 8:return "Permissions changed";case 9:return "Resource limit";case 10:return "User requested exit";case 11:return "User stopped app";
            case 12:return "Dependency stopped";case 14:return "Android freezer stopped process";case 15:return "App state changed";case 16:return "App updated";default:return "Android exit reason "+reason;}
    }
    static String sessionName(String name){Matcher m=Pattern.compile("[0-9]{8}T[0-9]{6}Z-p[0-9]+").matcher(name);return m.find()?m.group():null;}
    /** Snapshot a bounded tail, retaining CSV headers and whole text records. */
    static byte[] tail(File file,int budget)throws IOException{
        if(budget<=0)return new byte[0];
        try(RandomAccessFile in=new RandomAccessFile(file,"r")){
            long length=in.length();boolean csv=file.getName().endsWith(".csv");
            boolean lines=csv||file.getName().endsWith(".jsonl")||file.getName().endsWith(".log");
            if(file.getName().endsWith(".json")&&length>budget){byte[] note=("{\"truncated\":true,\"original_bytes\":"+length+",\"note\":\"JSON exceeded the report limit\"}\n").getBytes(StandardCharsets.UTF_8);return note.length<=budget?note:new byte[0];}
            if(!lines&&length<=budget){byte[] all=new byte[(int)length];in.readFully(all);return all;}
            long end=length;
            if(lines&&length>0){
                int size=(int)Math.min(length,Math.max(65536,budget));byte[] endBlock=new byte[size];in.seek(length-size);in.readFully(endBlock);
                int last=size-1;while(last>=0&&endBlock[last]!='\n')last--;
                end=last<0?0:length-size+last+1;
            }
            byte[] header=new byte[0];
            if(csv){
                int size=(int)Math.min(end,65536);byte[] first=new byte[size];in.seek(0);in.readFully(first);
                int newline=0;while(newline<size&&first[newline]!='\n')newline++;
                if(newline<size){if(newline+1>budget)return new byte[0];header=Arrays.copyOf(first,newline+1);}
            }
            if(end<=budget){byte[] all=new byte[(int)end];in.seek(0);in.readFully(all);return all;}
            long begin=Math.max(header.length,end-(budget-header.length));
            if(lines&&begin>header.length){
                in.seek(begin-1);
                if(in.read()!='\n'){
                    byte[] block=new byte[(int)(end-begin)];in.seek(begin);in.readFully(block);int n=0;while(n<block.length&&block[n]!='\n')n++;begin+=Math.min(block.length,n+1);
                }
            }
            int count=(int)Math.max(0,end-begin);byte[] result=Arrays.copyOf(header,header.length+count);
            in.seek(begin);in.readFully(result,header.length,count);return result;
        }
    }
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
