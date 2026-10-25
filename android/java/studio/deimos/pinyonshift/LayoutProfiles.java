package studio.deimos.pinyonshift;

import android.app.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local named copies and templates; document import/export is intentionally absent. */
final class LayoutProfiles {
  static String name(String raw)throws IOException{
    String value=raw.trim();
    if(!value.matches("[A-Za-z0-9][A-Za-z0-9 _-]{0,39}"))throw new IOException("Use 1–40 letters, numbers, spaces or dashes.");
    return value;
  }
  private static File folder(Activity a){return new File(MobileConfig.base(a),"state/config/layouts");}
  static void show(Activity a){
    LinearLayout body=FestivalUi.column(a);
    FestivalUi.action(a,body,"Save current layout as…",()->{
      EditText field=new EditText(a);field.setSingleLine(true);
      AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Layout name").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
      dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
        try{
          File file=new File(folder(a),name(field.getText().toString())+".layout");
          if(file.exists())throw new IOException("That name already exists. Choose a different name.");
          TouchLayout layout=new TouchLayout();layout.load(a);
          Files.createDirectories(folder(a).toPath());
          Files.write(file.toPath(),layout.serialize().getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW);
          dialog.dismiss();Toast.makeText(a,"Layout saved",Toast.LENGTH_SHORT).show();
          if(a instanceof FestivalUi.PanelHost)((FestivalUi.PanelHost)a).backPanel();show(a);
        }catch(IOException error){field.setError(error.getMessage());}
      }));dialog.show();
    });
    for(boolean left:new boolean[]{false,true}){
      FestivalUi.action(a,body,(left?"Left":"Right")+"-handed driving template",()->apply(a,template(left,false)));
    }
    FestivalUi.action(a,body,"Compact driving template",()->apply(a,template(false,true)));
    FestivalUi.action(a,body,"Full controller template",()->apply(a,controllerTemplate()));
    File[] files=folder(a).listFiles(f->f.isFile()&&!Files.isSymbolicLink(f.toPath())&&f.getName().endsWith(".layout"));
    if(files!=null){
      Arrays.sort(files,Comparator.comparing(File::getName));
      for(File file:Arrays.copyOf(files,Math.min(files.length,100))){
        LinearLayout row=new LinearLayout(a);body.addView(row);
        Button use=FestivalUi.button(a,file.getName().replaceFirst("\\.layout$",""),false);
        row.addView(use,new LinearLayout.LayoutParams(0,-2,1));
        use.setOnClickListener(v->{try{
          if(file.length()>65536||Files.isSymbolicLink(file.toPath()))throw new IOException("Layout is unavailable or too large.");
          String text=new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
          if(!text.startsWith("# FH4Droid touch layout "))throw new IOException("This saved layout is invalid.");
          TouchLayout layout=new TouchLayout();layout.parse(text);apply(a,layout);
        }catch(IOException error){SettingsHub.error(a,error);}});
        Button remove=FestivalUi.button(a,"Delete",false);row.addView(remove);
        remove.setContentDescription("Delete saved layout "+file.getName());
        remove.setOnClickListener(v->new AlertDialog.Builder(a).setTitle("Delete saved layout?").setMessage(file.getName()+" will be removed. Your active layout is kept.")
            .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{try{Files.delete(file.toPath());body.removeView(row);}catch(IOException error){SettingsHub.error(a,error);}}).show());
      }
    }
    FestivalUi.present(a,"Saved layouts and templates",body);
  }
  static TouchLayout template(boolean left,boolean compact){
    TouchLayout layout=new TouchLayout();
    if(left)for(float[] point:layout.values)point[0]=1-point[0];
    if(compact)for(int i=0;i<TouchLayout.LABELS.length;i++)
      if(Arrays.asList("RS","UP","DOWN","DPAD_LEFT","DPAD_RIGHT","L3","R3").contains(TouchLayout.LABELS[i]))layout.hidden[i]=true;
    return layout;
  }
  static TouchLayout controllerTemplate(){
    TouchLayout layout=template(false,false);
    layout.steering="stick";
    layout.drivingLabels=false;
    return layout;
  }
  private static void apply(Activity a,TouchLayout layout){
    new AlertDialog.Builder(a).setTitle("Apply this layout?").setMessage("This replaces your active layout. Save a named copy first to keep your current edits.")
        .setNegativeButton("Cancel",null).setPositiveButton("Apply",(d,w)->{try{layout.save(a);SettingsHub.reload(a);Toast.makeText(a,"Layout applied",Toast.LENGTH_SHORT).show();}catch(IOException error){SettingsHub.error(a,error);}}).show();
  }
  private LayoutProfiles(){}
}
