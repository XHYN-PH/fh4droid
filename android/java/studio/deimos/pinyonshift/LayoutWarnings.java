package studio.deimos.pinyonshift;

import java.util.*;

/** Advisory geometry checks using the same safe-area clamping as the native overlay. */
final class LayoutWarnings {
    static List<String> check(TouchLayout layout,float width,float height,float left,float top,float right,float bottom,float minimumTap){
        List<String> warnings=new ArrayList<>();float w=Math.max(1,width-left-right),h=Math.max(1,height-top-bottom);
        float[] x=new float[TouchLayout.LABELS.length],y=x.clone(),rx=x.clone(),ry=x.clone();boolean[] shown=new boolean[x.length];
        for(int i=0;i<x.length;i++){
            shown[i]=!layout.hidden[i]&&layout.enabled(i)&&!(i==10&&layout.leftMode==2)&&!(i==13&&layout.rightMode==2);
            if(!shown[i])continue;
            float r=Math.min(layout.values[i][2]*height,Math.min(w*.49f/Math.max(1,TouchIcons.BOUNDS[layout.mapping[i]][0]),h*.49f/Math.max(1,TouchIcons.BOUNDS[layout.mapping[i]][1])));
            float ex=r*Math.max(1,TouchIcons.BOUNDS[layout.mapping[i]][0]),ey=r*Math.max(1,TouchIcons.BOUNDS[layout.mapping[i]][1]);
            x[i]=left+Math.max(ex,Math.min(w-ex,layout.values[i][0]*w));y[i]=top+Math.max(ey,Math.min(h-ey,layout.values[i][1]*h));
            rx[i]=r*(TouchIcons.RECT[layout.mapping[i]]?TouchIcons.BOUNDS[layout.mapping[i]][0]:1)*1.15f;ry[i]=r*(TouchIcons.RECT[layout.mapping[i]]?TouchIcons.BOUNDS[layout.mapping[i]][1]:1)*1.15f;
            if(Math.min(rx[i],ry[i])*2<minimumTap)warnings.add(TouchLayout.LABELS[i]+": small touch target");
        }
        for(int i=0;i<x.length;i++)if(shown[i])for(int j=i+1;j<x.length;j++)if(shown[j]){
            boolean overlap=Math.abs(x[i]-x[j])<rx[i]+rx[j]&&Math.abs(y[i]-y[j])<ry[i]+ry[j];
            if(overlap&&!TouchIcons.RECT[layout.mapping[i]]&&!TouchIcons.RECT[layout.mapping[j]])overlap=Math.hypot(x[i]-x[j],y[i]-y[j])<rx[i]+rx[j];
            if(overlap)warnings.add(TouchLayout.LABELS[i]+" and "+TouchLayout.LABELS[j]+": touch areas overlap");
        }
        return warnings;
    }
    private LayoutWarnings(){}
}
