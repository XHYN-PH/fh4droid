package studio.deimos.pinyonshift;

import android.content.Context;
import android.graphics.*;

/** The gameplay PNG is also the editor's artwork; positions use window pixels. */
final class TouchIconPainter {
    static void draw(Canvas canvas,Paint paint,int icon,float x,float y,float radius,float opacity,boolean pressed,boolean hidden){
        draw(canvas,paint,icon,x,y,radius,opacity,pressed,hidden,true,true);
    }
    static void draw(Canvas canvas,Paint paint,int icon,float x,float y,float radius,float opacity,boolean pressed,boolean hidden,boolean driving,boolean hints){
        // Retained fallback for old callers; the editor supplies its asset bitmap.
        draw(canvas,paint,null,icon,x,y,radius,opacity,.95f,pressed,hidden,driving,hints,false);
    }
    static void draw(Canvas c,Paint p,Bitmap atlas,int icon,float x,float y,float radius,float opacity,float iconOpacity,boolean pressed,boolean hidden,boolean driving,boolean hints,boolean selected){
        draw(c,p,atlas,icon,x,y,radius,opacity,iconOpacity,pressed,hidden,driving,hints,selected,false);
    }
    static void draw(Canvas c,Paint p,Bitmap atlas,int icon,float x,float y,float radius,float opacity,float iconOpacity,boolean pressed,boolean hidden,boolean driving,boolean hints,boolean selected,boolean knob){
        float w=TouchIcons.BOUNDS[icon][0]*radius,h=TouchIcons.BOUNDS[icon][1]*radius;
        float corner=TouchIcons.RECT[icon]?Math.min(w,h)*.25f:Math.min(w,h),stroke=Math.max(1.1f,radius*.022f),fade=hidden?.25f:1;
        boolean pedal=driving&&!knob&&(TouchIcons.NAMES[icon].equals("RT")||TouchIcons.NAMES[icon].equals("LT")||TouchIcons.NAMES[icon].equals("LB"));
        p.setColorFilter(null);
        p.setPathEffect(null);p.setStyle(Paint.Style.FILL);p.setColor(pressed?0xffff6c35:knob?0xffdce2eb:0xff141b25);p.setAlpha((int)((pressed?220:knob?170:190)*opacity*fade));
        if(!pedal)c.drawRoundRect(x-w,y-h,x+w,y+h,corner,corner,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(selected?stroke*2:stroke);p.setColor(pressed||selected?0xffff915a:0xffb8c9dc);p.setAlpha((int)((pressed||selected?255*iconOpacity:150*opacity)*fade));
        if(!pedal||selected)c.drawRoundRect(x-w+stroke/2,y-h+stroke/2,x+w-stroke/2,y+h-stroke/2,corner-stroke/2,corner-stroke/2,p);
        p.setStyle(Paint.Style.FILL);p.setAlpha((int)(255*iconOpacity*fade));p.setFilterBitmap(true);
        if(pedal&&pressed)p.setColorFilter(new PorterDuffColorFilter(0xffffbe7d,PorterDuff.Mode.MULTIPLY));
        boolean stick=TouchIcons.NAMES[icon].equals("LS")||TouchIcons.NAMES[icon].equals("RS");
        boolean glyph=!(knob&&driving)&&!(!knob&&stick);
        if(atlas!=null){
            RectF target=new RectF(x-radius,y-radius,x+radius,y+radius);
            if(glyph)c.drawBitmap(atlas,TouchArtwork.source(icon,driving?0:2),target,p);
            if(!knob&&driving&&hints)c.drawBitmap(atlas,TouchArtwork.source(icon,1),target,p);
        }else if(glyph){
            p.setColor(0xfff5f7fa);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,radius*.06f));
            float[] lines=driving?TouchIcons.LINES[icon]:TouchIcons.CLASSIC[icon];
            for(int i=0;i<lines.length;i+=4)c.drawLine(x+lines[i]*radius,y+lines[i+1]*radius,x+lines[i+2]*radius,y+lines[i+3]*radius,p);
        }
        p.setStyle(Paint.Style.FILL);p.setAlpha(255);p.setFilterBitmap(false);p.setColorFilter(null);
    }
    private TouchIconPainter(){}
}
