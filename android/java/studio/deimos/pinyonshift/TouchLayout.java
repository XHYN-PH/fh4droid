package studio.deimos.pinyonshift;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Shared versioned layout; old steering and D-pad positions migrate separately. */
final class TouchLayout {
    static final String[] LABELS=TouchIcons.NAMES;
    static final float[][] DEFAULTS={{.905f,.78f,.13f},{.745f,.84f,.10f},{.83f,.52f,.075f},{.93f,.43f,.075f},{.73f,.43f,.075f},
        {.83f,.34f,.075f},{.07f,.20f,.075f},{.93f,.20f,.075f},{.40f,.08f,.06f},{.60f,.08f,.06f},{.16f,.74f,.11f},
        {.075f,.80f,.10f},{.215f,.80f,.10f},{.535f,.80f,.085f},{.30f,.33f,.05f},{.30f,.55f,.05f},
        {.24f,.44f,.05f},{.36f,.44f,.05f},{.10f,.43f,.05f},{.535f,.53f,.05f}};
    final float[][] values=new float[LABELS.length][3];
    final boolean[] hidden=new boolean[LABELS.length];
    final float[] timeout=new float[LABELS.length];
    float opacity,idleSeconds,iconOpacity;
    boolean autoHide,analogPedals,highContrast,haptics,drivingLabels,buttonHints;
    String steering;
    int steeringResponseMs,leftMode,rightMode;
    TouchLayout(){reset();}
    void reset(){
        for(int i=0;i<values.length;i++){System.arraycopy(DEFAULTS[i],0,values[i],0,3);hidden[i]=false;timeout[i]=-1;}
        opacity=.55f;iconOpacity=.95f;idleSeconds=20;autoHide=true;steering="arrows";steeringResponseMs=0;
        analogPedals=false;highContrast=false;haptics=false;drivingLabels=true;buttonHints=true;leftMode=rightMode=0;
    }
    void modernDefaults(){modernDefaults(16f/9f);}
    void modernDefaults(Context context){
        android.util.DisplayMetrics display=context.getResources().getDisplayMetrics();
        modernDefaults(display.widthPixels/(float)Math.max(1,display.heightPixels));
    }
    void modernDefaults(float aspect){
        reset();
        if(!Float.isFinite(aspect)||aspect<1)aspect=16f/9f;
        float[][] modern={{.94f,.80f,.14f},{.795f,.83f,.115f},{.94f,.525f,.077f},{.755f,.47f,.065f},{.665f,.49f,.065f},{.82f,.54f,.064f},{.69f,.82f,.075f},{.846f,.12f,.057f},{.75f,.12f,.057f},{.943f,.12f,.057f},{.165f,.79f,.137f},{.10f,.83f,.10f},{.23f,.83f,.10f}};
        for(int i=0;i<modern.length;i++){
            System.arraycopy(modern[i],0,values[i],0,3);
            // Keep each thumb cluster the same distance from its screen edge,
            // measured against height, including on 4:3 tablets and wide phones.
            float edgeScale=(16f/9f)/aspect,x=values[i][0];
            values[i][0]=Math.max(0,Math.min(1,x<.5f?x*edgeScale:1-(1-x)*edgeScale));
        }
        for(int i=0;i<LABELS.length;i++)hidden[i]=Arrays.asList("B","X","LB","RS","UP","DOWN","DPAD_LEFT","DPAD_RIGHT","L3","R3").contains(LABELS[i]);
    }
    void drivingDefaults(){reset();}
    boolean enabled(int i){String name=LABELS[i];return name.equals("LS")?steering.equals("stick"):
        !(name.equals("LEFT")||name.equals("RIGHT"))||steering.equals("arrows");}
    static String displayLabel(int i,boolean driving){return driving?TouchIcons.TITLES[i]:LABELS[i].replace("DPAD_","D-pad ");}
    private static float bounded(String text,float min,float max){float v=Float.parseFloat(text);if(!Float.isFinite(v))throw new NumberFormatException();return Math.max(min,Math.min(max,v));}
    void parse(String text){
        boolean extended=false;
        for(String row:text.split("\n")){String name=row.trim().split("\\s+",2)[0];if(name.equals("steering_buttons")||name.equals("STEER-L")||name.equals("STEER-R")){extended=true;break;}}
        for(String row:text.split("\n")){
            String[] words=row.trim().split("\\s+");
            try{
                if(words.length==2){switch(words[0]){
                    case "opacity":opacity=bounded(words[1],.2f,1);break;
                    case "icon_opacity":iconOpacity=bounded(words[1],.3f,1);break;
                    case "auto_hide":autoHide=words[1].equals("1");break;
                    case "steering":if(words[1].equals("arrows")||words[1].equals("stick"))steering=words[1];break;
                    case "steering_buttons":if(words[1].equals("0")||words[1].equals("1"))steering=words[1].equals("1")?"arrows":"stick";break;
                    case "steering_response_ms":steeringResponseMs=Math.max(0,Math.min(500,Integer.parseInt(words[1])));break;
                    case "analog_pedals":analogPedals=words[1].equals("1");break;
                    case "high_contrast":highContrast=words[1].equals("1");break;
                    case "haptics":haptics=words[1].equals("1");break;
                    case "driving_labels":drivingLabels=words[1].equals("1");break;
                    case "button_hints":buttonHints=words[1].equals("1");break;
                    case "idle_seconds":idleSeconds=bounded(words[1],0,120);break;
                    case "left_mode":leftMode=(int)bounded(words[1],0,2);break;
                    case "right_mode":rightMode=(int)bounded(words[1],0,2);break;
                }}else if(words.length==3&&words[0].equals("timeout")){
                    String name=words[1];
                    if(extended&&name.equals("LEFT"))name="DPAD_LEFT";
                    else if(extended&&name.equals("RIGHT"))name="DPAD_RIGHT";
                    else if(name.equals("STEER-L"))name="LEFT";
                    else if(name.equals("STEER-R"))name="RIGHT";
                    int i=Arrays.asList(LABELS).indexOf(name);if(i>=0)timeout[i]=bounded(words[2],-1,120);
                }else if(words.length==5){
                    String name=extended&&words[0].equals("LEFT")?"DPAD_LEFT":extended&&words[0].equals("RIGHT")?"DPAD_RIGHT":words[0].equals("STEER-L")?"LEFT":words[0].equals("STEER-R")?"RIGHT":words[0];
                    int i=Arrays.asList(LABELS).indexOf(name);
                    if(i>=0){float x=bounded(words[1],0,1),y=bounded(words[2],0,1),r=bounded(words[3],.035f,.24f);
                        values[i][0]=x;values[i][1]=y;values[i][2]=r;hidden[i]=words[4].equals("1");}
                }
            }catch(NumberFormatException ignored){}
        }
    }
    String serialize(){
        StringBuilder text=new StringBuilder("# FH4Droid touch layout v4\nopacity "+opacity+"\nauto_hide "+(autoHide?1:0)+"\nsteering "+steering+"\n");
        text.append("steering_response_ms ").append(steeringResponseMs).append("\nanalog_pedals ").append(analogPedals?1:0).append("\nhigh_contrast ").append(highContrast?1:0).append("\nhaptics ").append(haptics?1:0).append('\n');
        text.append("icon_opacity ").append(iconOpacity).append('\n');
        text.append("driving_labels ").append(drivingLabels?1:0).append("\nbutton_hints ").append(buttonHints?1:0).append("\nidle_seconds ").append(idleSeconds).append("\nleft_mode ").append(leftMode).append("\nright_mode ").append(rightMode).append('\n');
        for(int i=0;i<values.length;i++){text.append(String.format(Locale.ROOT,"%s %.6f %.6f %.6f %d%n",LABELS[i],values[i][0],values[i][1],values[i][2],hidden[i]?1:0));text.append("timeout ").append(LABELS[i]).append(' ').append(timeout[i]).append('\n');}
        return text.toString();
    }
    static File file(Context c){return new File(MobileConfig.base(c),"state/config/touch-layout.txt");}
    static File bank(Context c,boolean driving){return new File(file(c).getParentFile(),driving?"touch-layout-driving.txt":"touch-layout-xbox.txt");}
    void load(Context c){if(!file(c).exists())modernDefaults(c);else read(file(c));}
    private void read(File f){try{if(f.isFile()&&!Files.isSymbolicLink(f.toPath())&&f.length()<=65536)parse(new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));}catch(IOException ignored){}}
    static void write(File f,String text)throws IOException{
        Files.createDirectories(f.getParentFile().toPath());Path temp=Files.createTempFile(f.getParentFile().toPath(),"layout-",".part");
        try{try(FileOutputStream out=new FileOutputStream(temp.toFile())){out.write(text.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
            Files.move(temp,f.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
    void save(Context c)throws IOException{String text=serialize();write(bank(c,drivingLabels),text);write(file(c),text);}
    static void selectStyle(Context c,boolean driving)throws IOException{
        TouchLayout current=new TouchLayout();current.load(c);current.save(c);
        TouchLayout selected=new TouchLayout();selected.parse(current.serialize());selected.read(bank(c,driving));selected.drivingLabels=driving;selected.save(c);
    }
}
