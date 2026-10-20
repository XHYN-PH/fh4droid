package studio.deimos.pinyonshift;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class TouchLayout {
    static final String[] LABELS={"RT","LT","A","B","X","Y","LB","RB","BACK","START","LS"};
    static final float[][] DEFAULTS={{.905f,.78f,.13f},{.745f,.84f,.10f},{.83f,.52f,.075f},{.93f,.43f,.075f},{.73f,.43f,.075f},
        {.83f,.34f,.075f},{.07f,.20f,.075f},{.93f,.20f,.075f},{.40f,.08f,.06f},{.60f,.08f,.06f},{.16f,.74f,.11f}};
    final float[][] values=new float[LABELS.length][3];final boolean[] hidden=new boolean[LABELS.length];float opacity=.55f;boolean autoHide=true;
    TouchLayout(){reset();}
    void reset(){for(int i=0;i<values.length;i++){System.arraycopy(DEFAULTS[i],0,values[i],0,3);hidden[i]=false;}opacity=.55f;autoHide=true;}
    void parse(String text){
        for(String row:text.split("\n")){String[] words=row.trim().split("\\s+");try{
            if(words.length==2&&words[0].equals("opacity")){float f=Float.parseFloat(words[1]);if(Float.isFinite(f))opacity=Math.max(.2f,Math.min(1,f));}
            else if(words.length==2&&words[0].equals("auto_hide"))autoHide=words[1].equals("1");
            else if(words.length==5){int i=Arrays.asList(LABELS).indexOf(words[0]);float x=Float.parseFloat(words[1]),y=Float.parseFloat(words[2]),r=Float.parseFloat(words[3]);
                if(i>=0&&Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(r)){values[i][0]=Math.max(0,Math.min(1,x));values[i][1]=Math.max(0,Math.min(1,y));values[i][2]=Math.max(.035f,Math.min(.24f,r));hidden[i]=words[4].equals("1");}}
        }catch(NumberFormatException ignored){}}
    }
    String serialize(){StringBuilder text=new StringBuilder("# FH4Droid touch layout v1\nopacity "+opacity+"\nauto_hide "+(autoHide?1:0)+"\n");
        for(int i=0;i<values.length;i++)text.append(String.format(Locale.ROOT,"%s %.6f %.6f %.6f %d%n",LABELS[i],values[i][0],values[i][1],values[i][2],hidden[i]?1:0));return text.toString();}
    static File file(android.content.Context c){return new File(MobileConfig.base(c),"state/config/touch-layout.txt");}
    void load(android.content.Context c){try{parse(new String(Files.readAllBytes(file(c).toPath()),StandardCharsets.UTF_8));}catch(IOException ignored){}}
    void save(android.content.Context c)throws IOException{File f=file(c);f.getParentFile().mkdirs();File temp=new File(f.getPath()+".part");
        try(FileOutputStream out=new FileOutputStream(temp)){out.write(serialize().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
        Files.move(temp.toPath(),f.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
}
