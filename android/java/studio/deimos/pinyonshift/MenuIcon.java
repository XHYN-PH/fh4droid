package studio.deimos.pinyonshift;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.content.Context;

/** Lightweight line icons for the launcher and in-game menu. */
final class MenuIcon extends Drawable {
  private final String kind;
  private final int size;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  MenuIcon(Context context, String kind, int color) {
    this.kind = kind;
    size = Math.round(22 * context.getResources().getDisplayMetrics().density);
    paint.setColor(color); paint.setStrokeWidth(1.7f); paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
  }
  @Override public int getIntrinsicWidth() { return size; }
  @Override public int getIntrinsicHeight() { return size; }
  @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
  @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
  @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
  @Override public void draw(Canvas canvas) {
    Rect b=getBounds();canvas.save();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);
    if(kind.equals("menu")) {
      for(int y:new int[]{6,12,18})canvas.drawLine(4,y,20,y,paint);
    } else if(kind.equals("play")) {
      Path p=new Path();p.moveTo(7,3);p.lineTo(20,12);p.lineTo(7,21);p.close();paint.setStyle(Paint.Style.FILL);canvas.drawPath(p,paint);paint.setStyle(Paint.Style.STROKE);
    } else if(kind.equals("settings")) {
      for(int y:new int[]{6,12,18}) canvas.drawLine(3,y,21,y,paint);
      for(int[] p:new int[][]{{8,6},{16,12},{10,18}}){paint.setStyle(Paint.Style.FILL);canvas.drawCircle(p[0],p[1],2,paint);paint.setStyle(Paint.Style.STROKE);}
    } else if(kind.equals("files")) {
      Path p=new Path();p.moveTo(3,7);p.lineTo(3,19);p.lineTo(21,19);p.lineTo(21,9);p.lineTo(12,9);p.lineTo(10,7);p.close();canvas.drawPath(p,paint);
    } else if(kind.equals("controls")) {
      canvas.drawRoundRect(2,6,22,18,5,5,paint);canvas.drawLine(5,12,11,12,paint);canvas.drawLine(8,9,8,15,paint);canvas.drawCircle(16,10,1,paint);canvas.drawCircle(19,13,1,paint);
    } else if(kind.equals("drivers")) {
      canvas.drawRoundRect(6,6,18,18,2,2,paint);for(int i=8;i<=16;i+=4){canvas.drawLine(i,3,i,6,paint);canvas.drawLine(i,18,i,21,paint);canvas.drawLine(3,i,6,i,paint);canvas.drawLine(18,i,21,i,paint);}
    } else if(kind.equals("profile")) {
      canvas.drawCircle(12,8,3,paint);canvas.drawArc(5,12,19,22,180,180,false,paint);canvas.drawCircle(12,12,10,paint);
    } else if(kind.equals("more")) {
      paint.setStyle(Paint.Style.FILL);for(int x:new int[]{6,12,18})canvas.drawCircle(x,12,1.4f,paint);paint.setStyle(Paint.Style.STROKE);
    } else {
      OutlineIcon.draw(canvas,paint,12,12,10,kind,paint.getColor());
    }
    canvas.restore();
  }
  static String kind(String title) {
    switch(title) {
      case "Play": case "Resume": return "play";
      case "Settings": case "Graphics": return "settings";
      case "Game files": return "files";
      case "Touch controls": case "Controls": case "Controller": return ControlGlyphs.setting(title);
      case "Drivers": case "Graphics drivers": return "drivers";
      case "Profile": case "View profile": return "profile";
      case "More": return "more";
      default: return null;
    }
  }
}
