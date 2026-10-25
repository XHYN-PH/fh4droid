package studio.deimos.pinyonshift;

import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;

/** Uses the same generated paths as the native overlay; no font or game assets. */
final class TouchIconPainter {
    static void draw(Canvas canvas, Paint paint, int icon, float x, float y, float radius,
                     float opacity, boolean pressed, boolean hidden) {
        float w=TouchIcons.BOUNDS[icon][0]*radius, h=TouchIcons.BOUNDS[icon][1]*radius;
        float corner=TouchIcons.RECT[icon]?Math.min(w,h)*.25f:Math.min(w,h);
        float stroke=Math.max(2, radius*.065f);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xff0c0a0c);
        paint.setAlpha((int)(180*opacity*(hidden?.25f:1)));
        canvas.drawRoundRect(x-w,y-h,x+w,y+h,corner,corner,paint);
        paint.setColor(pressed?FestivalUi.ORANGE:FestivalUi.TEXT);
        paint.setAlpha((int)(255*opacity*(hidden?.3f:1)));
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);
        if(hidden)paint.setPathEffect(new DashPathEffect(new float[]{6,6},0));
        canvas.drawRoundRect(x-w+stroke/2,y-h+stroke/2,x+w-stroke/2,y+h-stroke/2,
            Math.max(0,corner-stroke/2),Math.max(0,corner-stroke/2),paint);
        paint.setPathEffect(null);paint.setStrokeWidth(stroke*1.25f);paint.setStrokeCap(Paint.Cap.BUTT);
        drawLines(canvas,paint,TouchIcons.LINES[icon],x,y,radius);
        paint.setStrokeWidth(stroke*.6f);
        drawLines(canvas,paint,TouchIcons.HINTS[icon],x,y,radius);
        paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);
    }
    private static void drawLines(Canvas canvas,Paint paint,float[] lines,float x,float y,float radius){
        for(int i=0;i<lines.length;i+=4)
            canvas.drawLine(x+lines[i]*radius,y+lines[i+1]*radius,
                x+lines[i+2]*radius,y+lines[i+3]*radius,paint);
    }
    private TouchIconPainter(){}
}
