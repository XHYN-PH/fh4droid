package studio.deimos.pinyonshift;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Portable regressions for content integrity and failed/cancelled operations. */
public final class UpstreamContentTest {
    private static int checks;
    interface Action{void run()throws Exception;}
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void rejected(Action action,String why)throws Exception{try{action.run();throw new AssertionError(why);}catch(IOException expected){checks++;}}
    private static void write(File file,String value)throws Exception{Files.createDirectories(file.getParentFile().toPath());Files.write(file.toPath(),value.getBytes(StandardCharsets.UTF_8));}
    private static File zip(File root,String id,String... names)throws Exception{
        File file=new File(root,id+".zip");try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(file))){for(String name:names){zip.putNextEntry(new ZipEntry(name));zip.write("road".getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}return file;
    }
    public static void main(String[] args)throws Exception{
        File root=Files.createTempDirectory("fh4droid-content-").toFile();
        try{
            for(String path:new String[]{"../outside","/absolute","C:/drive","a\\b","a/./b","a//b","a/../b","bad. ","bad\nname"})rejected(()->ContentFiles.safePath(path),"unsafe path accepted: "+path);
            check(ContentFiles.safePath("media/db/gamedb.slt").equals("media/db/gamedb.slt"),"valid path changed");
            File extracted=new File(root,"good");ContentFiles.unzip(zip(root,"good-zip","mod.toml","game/media/file.bin"),extracted);
            check(new File(extracted,"game/media/file.bin").length()==4,"valid ZIP contents missing");
            rejected(()->ContentFiles.unzip(zip(root,"escape","../escaped.txt"),new File(root,"escape-out")),"ZIP escaped root");
            check(!new File(root,"escaped.txt").exists(),"escaped ZIP file was written");
            rejected(()->ContentFiles.unzip(zip(root,"duplicate","A.txt","a.txt"),new File(root,"duplicate-out")),"case-colliding archive accepted");
            rejected(()->ContentFiles.copy(new ByteArrayInputStream(new byte[5]),new ByteArrayOutputStream(),4),"copy exceeded its byte limit");
            Thread.currentThread().interrupt();try{rejected(()->ContentFiles.copy(new ByteArrayInputStream(new byte[4]),new ByteArrayOutputStream(),10),"cancelled copy continued");}finally{Thread.interrupted();}

            File current=new File(root,"installed");write(new File(current,"keep.txt"),"previous");
            rejected(()->ContentFiles.publish(new File(root,"missing-stage"),current),"missing replacement was published");
            check(new String(Files.readAllBytes(new File(current,"keep.txt").toPath()),StandardCharsets.UTF_8).equals("previous"),"failed replacement lost previous content");
            File next=new File(root,"new-stage");write(new File(next,"new.txt"),"next");ContentFiles.publish(next,current);
            check(new File(current,"new.txt").isFile()&&!new File(current,"keep.txt").exists(),"successful publication did not replace content");
            File game=new File(root,"case-game");write(new File(game,"Media/DB/GameDB.slt"),"db");check(ContentFiles.treeFile(game,"media/db/gamedb.slt").isFile(),"game overlays were matched case-sensitively");

            File payload=new File(root,"payload");write(new File(payload,"Alpha/z.bin"),"ABC");write(new File(payload,"Alpha.txt"),"road");write(new File(payload,"\u00dc.txt"),"driver");
            // Independently generated with Python PureWindowsPath ordering and
            // json.dumps(sort_keys=True, separators=(',', ':')).
            check(ContentFiles.payloadDigest(payload,ContentFiles.files(payload)).equals("377CDE313E9C8C61B9F6AC344512EBF7515849CB8D57BF659E3FB7FEA8921A77"),"DLC digest differs from the upstream catalog algorithm");
            check(ContentFiles.windowsPathCompare("Alpha/z","Alpha.txt")<0,"Windows component order was lost");

            List<String> sql=ModSql.statements("-- heading\nUPDATE t SET value='a;''b'; /* ; */ UPDATE \"t;name\" SET v=2; SELECT 3 -- tail\n");
            check(sql.size()==3,"quoted/comment semicolons split SQL");check(sql.get(0).contains("'a;''b'"),"SQL literal changed");
            check(ModSql.statements("\uFEFFUPDATE t SET v=1;").size()==1,"UTF-8 BOM broke SQL import");
            check(ModSql.substitute("UPDATE t SET v={{ prices }};",Collections.singletonMap("prices","0.5")).contains("v=0.5"),"mod option was not substituted");
            rejected(()->ModSql.substitute("SELECT {{missing}};",Collections.emptyMap()),"missing option accepted");
            rejected(()->ModSql.substitute("SELECT {{x}};",Collections.singletonMap("x","1; DELETE")),"invalid option token accepted");
            rejected(()->ModSql.statements("UPDATE t SET v='unfinished"),"unfinished SQL quote accepted");
            rejected(()->ModSql.statements("/* unfinished"),"unfinished SQL comment accepted");
            rejected(()->ModSql.statements("ATTACH DATABASE 'elsewhere' AS other;"),"external database attachment accepted");
            rejected(()->ModSql.statements("CREATE TRIGGER x AFTER INSERT ON t BEGIN SELECT 1; END;"),"unprepared trigger accepted");

            File pipelines=new File(root,"prepared.xpso");ByteBuffer data=ByteBuffer.allocate(16+12+4+58).order(ByteOrder.LITTLE_ENDIAN);
            data.put(new byte[]{'X','E','A','O'}).putInt(0x20261018).putInt(1).putInt(1).putLong(123).putInt(1).putInt(0);Files.write(pipelines.toPath(),data.array());ShaderPreparation.validate(pipelines);checks++;
            byte[] valid=data.array().clone();Files.write(pipelines.toPath(),Arrays.copyOf(valid,valid.length-1));rejected(()->ShaderPreparation.validate(pipelines),"truncated pipeline list accepted");
            byte[] bad=valid.clone();ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putInt(24,0x7fffffff);Files.write(pipelines.toPath(),bad);rejected(()->ShaderPreparation.validate(pipelines),"oversized shader allocation accepted");
            bad=valid.clone();bad[4]=0;Files.write(pipelines.toPath(),bad);rejected(()->ShaderPreparation.validate(pipelines),"incompatible pipeline version accepted");
            bad=valid.clone();ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putInt(8,0);Files.write(pipelines.toPath(),bad);rejected(()->ShaderPreparation.validate(pipelines),"empty shader list accepted");
            System.out.println("PASS: "+checks+" upstream content integrity, cancellation, SQL and pipeline checks");
        }finally{ContentFiles.delete(root);}
    }
}
