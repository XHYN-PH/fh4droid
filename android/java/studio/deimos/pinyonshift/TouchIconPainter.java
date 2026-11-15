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
        p.setColorFilter(null);
        p.setPathEffect(null);p.setStyle(Paint.Style.FILL);p.setColor(pressed?0xff4a142d:knob?0xffdce2eb:0xff14121a);p.setAlpha((int)((pressed?210:knob?170:165)*opacity*fade));
        c.drawRoundRect(x-w,y-h,x+w,y+h,corner,corner,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(selected?stroke*2:stroke);p.setColor(pressed||selected?0xffff0060:0xffebe7f0);p.setAlpha((int)((pressed||selected?255*iconOpacity:150*opacity)*fade));
        c.drawRoundRect(x-w+stroke/2,y-h+stroke/2,x+w-stroke/2,y+h-stroke/2,corner-stroke/2,corner-stroke/2,p);
        p.setStyle(Paint.Style.FILL);p.setAlpha((int)(255*iconOpacity*fade));p.setFilterBitmap(true);
        boolean stick=TouchIcons.NAMES[icon].equals("LS")||TouchIcons.NAMES[icon].equals("RS");
        boolean glyph=!(knob&&driving)&&!(!knob&&stick);
        if(atlas!=null){
            RectF target=new RectF(x-radius,y-radius,x+radius,y+radius);
            if(glyph)c.drawBitmap(atlas,TouchArtwork.source(icon,driving?0:2),target,p);
            if(!knob&&driving&&hints)c.drawBitmap(atlas,TouchArtwork.source(icon,1),target,p);
        }else if(glyph){
            p.setColor(0xfff5f7fa);p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(radius*.38f);
            String label=TouchIcons.NAMES[icon];c.drawText(label,x,y-(p.ascent()+p.descent())/2,p);
        }
        p.setStyle(Paint.Style.FILL);p.setAlpha(255);p.setFilterBitmap(false);p.setColorFilter(null);
    }
    private TouchIconPainter(){}
}
