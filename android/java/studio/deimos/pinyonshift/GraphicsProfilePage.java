package studio.deimos.pinyonshift;

import android.app.*;
import android.widget.*;
import java.io.*;
import java.util.*;

final class GraphicsProfilePage {
    private static File folder(Activity a){return new File(MobileConfig.base(a),"state/config/graphics-profiles");}
    static File lastGood(android.content.Context c){return new File(MobileConfig.base(c),"state/config/last-good-graphics.toml");}
    private static Map<String,String> current(Activity a,MobileSettings.Runtime runtime)throws IOException{
        Map<String,String> settings=new LinkedHashMap<>(MobileConfig.read(a));
        if(runtime!=null)for(String key:GraphicsProfileStore.defaults().keySet()){String value=runtime.value(key);if(value==null)throw new IOException("Running graphics are not available yet. Wait for the game to finish starting.");settings.put(key,SettingValues.plain(value));}
        return GraphicsProfileStore.snapshot(settings);
    }
    static void show(Activity a,MobileSettings.Runtime runtime){
        LinearLayout body=FestivalUi.column(a);body.addView(FestivalUi.text(a,runtime==null?"Save your configured graphics. Applying a profile takes effect at the next launch.":"Save the currently running graphics. Pending next-launch changes are not included in that snapshot.",13,FestivalUi.MUTED));
        FestivalUi.action(a,body,"Save as named profile…",()->{
            EditText input=new EditText(a);input.setSingleLine(true);input.setHint("Profile name");
            AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Save graphics profile").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
            dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{
                File file=new File(folder(a),GraphicsProfileStore.name(input.getText().toString())+".toml");if(file.exists())throw new IOException("That name is already used.");
                GraphicsProfileStore.save(file,current(a,runtime));dialog.dismiss();Toast.makeText(a,"Graphics profile saved",Toast.LENGTH_SHORT).show();
            }catch(IOException error){input.setError(error.getMessage());}}));dialog.show();
        });
        if(runtime!=null)FestivalUi.action(a,body,"Mark these running graphics as working",()->{
            try{GraphicsProfileStore.save(lastGood(a),current(a,runtime));Toast.makeText(a,"Working graphics saved for recovery",Toast.LENGTH_SHORT).show();}catch(IOException error){SettingsHub.error(a,error);}
        });
        if(lastGood(a).isFile())FestivalUi.action(a,body,"Restore last working graphics",()->apply(a,runtime,lastGood(a)));
        File[] files=folder(a).listFiles(f->f.isFile()&&f.getName().endsWith(".toml")&&!java.nio.file.Files.isSymbolicLink(f.toPath()));
        if(files!=null){Arrays.sort(files,Comparator.comparing(File::getName));for(File file:Arrays.copyOf(files,Math.min(100,files.length)))FestivalUi.action(a,body,file.getName().replaceFirst("\\.toml$",""),()->apply(a,runtime,file));}
        FestivalUi.present(a,"Graphics profiles",body);
    }
    private static void apply(Activity a,MobileSettings.Runtime runtime,File file){
        try{Map<String,String> values=GraphicsProfileStore.load(file);new AlertDialog.Builder(a).setTitle("Apply graphics profile?").setMessage("Graphics change at the next launch. Your driver, layouts and saves are kept.\n\n"+GraphicsProfileStore.describe(values)).setNegativeButton("Cancel",null).setPositiveButton("Apply",(d,w)->SettingsHub.apply(a,runtime,values,true)).show();}
        catch(IOException error){SettingsHub.error(a,error);}
    }
    static void restoreForRecovery(Activity a)throws IOException{MobileConfig.putAll(a,GraphicsProfileStore.load(lastGood(a)));}
    private GraphicsProfilePage(){}
}
