package studio.deimos.pinyonshift;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
public final class R7CompatibilityTest {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static byte[] zip(String id,boolean v4,boolean unsafe)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out)){
            String prefix="content/"+id+"/4D5309C9/00000001/ForzaProfile/";
            zip.putNextEntry(new ZipEntry(prefix+"ForzaProfile"));zip.write(new byte[]{1,2,3});zip.closeEntry();
            zip.putNextEntry(new ZipEntry(prefix+"VersionFlags"));byte[] flags=new byte[12];System.arraycopy("cmss".getBytes(StandardCharsets.US_ASCII),0,flags,0,4);if(v4)flags[7]=4;zip.write(flags);zip.closeEntry();
            if(unsafe){zip.putNextEntry(new ZipEntry(prefix+"../outside"));zip.write(1);zip.closeEntry();}
        }return out.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        check(GpuCompatibility.family(0x13b5,"Mali-G52").startsWith("ARM"),"Mali vendor support");
        check(GpuCompatibility.family(0,"Immortalis-G720").startsWith("ARM"),"Immortalis naming support");
        check(GpuCompatibility.family(0x1002,"Samsung Xclipse 920").equals("Samsung Xclipse"),"Xclipse on AMD vendor ID");
        check(GpuCompatibility.family(0x144d,"unknown").equals("Samsung Xclipse"),"Samsung vendor identification");
        check(!AdrenoSettings.isAdreno(0x144d,"Xclipse 940")&&!AdrenoSettings.isTurnip(0x13b5,"Mali-G52",18),"Adreno controls cannot be selected for Mali or Xclipse");
        check(GpuCompatibility.guidance(0x13b5,"Mali-G52").contains("exact GPU"),"PanVK not advertised as universal Mali driver");
        check(GpuCompatibility.guidance(0x144d,"Xclipse").contains("system Vulkan"),"Xclipse system driver recommendation");
        String start="M2_EVENT {\"session\":\"current\",\"event\":\"logging.ready\"}\n";
        String summary=RuntimeWarnings.summarize("[warning] old\n"+start+"[warning] XMA packet\n[error] bad draw\nM2_EVENT {\"session\":\"other\",\"event\":\"process.start\"}\n[error] old\n","current",false);
        check(summary.contains("1 warnings, 1 errors")&&summary.contains("1 XMA"),"counts stop at session boundary and exclude old warnings");
        check(RuntimeWarnings.summarize("[warning] old","current",true).contains("unavailable"),"truncated logs do not fabricate current-only counts");
        check(RuntimeWarnings.summarize(start+"[warn] test\n","current",true).contains("truncated"),"partial shared history is explicit");
        TouchLayout layout=new TouchLayout();layout.mapping[2]=5;layout.mapping[0]=1;TouchLayout copy=new TouchLayout();copy.parse(layout.serialize());
        check(copy.mapping[2]==5&&copy.mapping[0]==1,"remapped buttons survive round-trip");
        copy.parse("map LS RT\nmap A LS\nmap UNKNOWN Y\n");check(copy.mapping[10]==10&&copy.mapping[2]==5,"stick/steering mappings cannot create invalid digital controls");
        copy.reset();check(copy.mapping[0]==0&&copy.mapping[2]==2,"reset restores every default action");
        check(AppLanguage.translate("fil","Settings").equals("Mga setting")&&AppLanguage.translate("en","Settings").equals("Settings"),"app language is explicitly selected");
        check(AppLanguage.translate("fil","Untranslated help").equals("Untranslated help"),"detailed help has a safe English fallback");
        for(String id:new String[]{"","0","0000000000000000","E0F0000012345678","not-a-valid-xuid!"})check(!XeniaSaveImport.usableXuid(id),"invalid guest identity rejected");
        check(XeniaSaveImport.usableXuid("E030000012345678"),"source XUID kept as supported by desktop importer");
        Path temp=Files.createTempDirectory("fh4droid-xenia-");try{
            File state=temp.resolve("state").toFile();Files.createDirectories(new File(state,"config").toPath());File config=new File(state,"config/pinyon_shift.toml");Files.write(config.toPath(),"user_xuid = \"B13EBABEBABEBABE\"\nuser_name = \"Keep Me\"\n".getBytes(StandardCharsets.UTF_8));
            File nativeSave=new File(state,"user/B13EBABEBABEBABE/4D5309C9/old");Files.createDirectories(nativeSave.toPath().getParent());Files.write(nativeSave.toPath(),new byte[]{8});
            try(XeniaSaveImport.Prepared save=XeniaSaveImport.prepare(temp.toFile(),new ByteArrayInputStream(zip("E030000012345678",false,false)))){XeniaSaveImport.install(state,save);}
            check(new File(state,"user/E030000012345678/4D5309C9/00000001/ForzaProfile/ForzaProfile").isFile()&&nativeSave.isFile(),"Xenia import is beside current native save");
            check(MobileConfig.readFile(config).get("user_xuid").equals("E030000012345678")&&MobileConfig.readFile(config).get("user_name").equals("Keep Me"),"only selected XUID changes");
            File[] backups=new File(state,"backups").listFiles();check(backups!=null&&backups.length==1&&new File(backups[0],"pinyon_shift.toml").isFile(),"previous configuration backed up before selecting profile");
            boolean refused=false;try(XeniaSaveImport.Prepared save=XeniaSaveImport.prepare(temp.toFile(),new ByteArrayInputStream(zip("E030000012345678",false,false)))){XeniaSaveImport.install(state,save);}catch(IOException expected){refused=true;}check(refused&&nativeSave.isFile(),"existing Xenia profiles are not overwritten");
            for(boolean v4:new boolean[]{true,false}){refused=false;try(XeniaSaveImport.Prepared save=XeniaSaveImport.prepare(temp.toFile(),new ByteArrayInputStream(zip("E030000012345678",v4,!v4)))){}catch(IOException expected){refused=true;}check(refused,"v4 and traversal saves rejected before installation");}
        }finally{SaveArchive.delete(temp.toFile());}
        System.out.println("PASS: "+checks+" GPU, diagnostics, remapping, language and Xenia import checks");
    }
}
