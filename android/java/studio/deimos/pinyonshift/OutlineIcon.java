package studio.deimos.pinyonshift;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small drawn icons, independent of emoji or the game's font coverage. */
final class OutlineIcon extends View {
  final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  final String kind;
  int color;
  OutlineIcon(Context c, String kind, int color) {
    super(c);
    this.kind = kind;
    this.color = color;
    setContentDescription(kind);
  }
  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    draw(canvas, paint, getWidth() / 2f, getHeight() / 2f, Math.min(getWidth(), getHeight()) * .32f,
        kind, color);
  }
  static void draw(Canvas c, Paint p, float x, float y, float r, String kind, int color) {
    p.setColor(color);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(Math.max(1.5f, r * .1f));
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeJoin(Paint.Join.ROUND);
    if (kind.equals("trash")) {
      c.drawLine(x - r * .7f, y - r * .55f, x + r * .7f, y - r * .55f, p);
      c.drawRoundRect(x - r * .5f, y - r * .5f, x + r * .5f, y + r * .7f, r * .1f, r * .1f, p);
      c.drawLine(x - r * .2f, y - r * .8f, x + r * .2f, y - r * .8f, p);
      c.drawLine(x - r * .17f, y - r * .2f, x - r * .17f, y + r * .4f, p);
      c.drawLine(x + r * .17f, y - r * .2f, x + r * .17f, y + r * .4f, p);
    } else if (kind.equals("camera")) {
      c.drawRoundRect(x - r * .85f, y - r * .5f, x + r * .85f, y + r * .55f, r * .15f, r * .15f, p);
      c.drawCircle(x, y, r * .3f, p);
      c.drawLine(x - r * .45f, y - r * .75f, x + r * .1f, y - r * .75f, p);
    } else if (kind.equals("left") || kind.equals("right") || kind.equals("up")
        || kind.equals("down")) {
      c.save();
      c.rotate(kind.equals("left")  ? 180
              : kind.equals("up")   ? 270
              : kind.equals("down") ? 90
                                    : 0,
          x, y);
      c.drawLine(x - r * .7f, y, x + r * .7f, y, p);
      c.drawLine(x + r * .2f, y - r * .5f, x + r * .7f, y, p);
      c.drawLine(x + r * .2f, y + r * .5f, x + r * .7f, y, p);
      c.restore();
    } else if (kind.equals("pause")) {
      c.drawLine(x - r * .3f, y - r * .6f, x - r * .3f, y + r * .6f, p);
      c.drawLine(x + r * .3f, y - r * .6f, x + r * .3f, y + r * .6f, p);
    } else if (kind.equals("rewind")) {
      for (float o : new float[] {-.25f, .45f}) {
        Path path = new Path();
        path.moveTo(x + (o + .25f) * r, y - r * .5f);
        path.lineTo(x + (o - .3f) * r, y);
        path.lineTo(x + (o + .25f) * r, y + r * .5f);
        path.close();
        c.drawPath(path, p);
      }
    } else if (kind.equals("map")) {
      Path path = new Path();
      path.moveTo(x - r * .8f, y - r * .65f);
      path.lineTo(x - r * .25f, y - r * .85f);
      path.lineTo(x + r * .25f, y - r * .6f);
      path.lineTo(x + r * .8f, y - r * .8f);
      path.lineTo(x + r * .8f, y + r * .65f);
      path.lineTo(x + r * .25f, y + r * .85f);
      path.lineTo(x - r * .25f, y + r * .6f);
      path.lineTo(x - r * .8f, y + r * .8f);
      path.close();
      c.drawPath(path, p);
    } else if (kind.equals("file")) {
      Path path = new Path();
      path.moveTo(x - r * .55f, y - r * .8f);
      path.lineTo(x + r * .2f, y - r * .8f);
      path.lineTo(x + r * .55f, y - r * .45f);
      path.lineTo(x + r * .55f, y + r * .8f);
      path.lineTo(x - r * .55f, y + r * .8f);
      path.close();
      c.drawPath(path, p);
      c.drawLine(x + r * .2f, y - r * .8f, x + r * .2f, y - r * .45f, p);
      c.drawLine(x + r * .2f, y - r * .45f, x + r * .55f, y - r * .45f, p);
      c.drawLine(x - r * .25f, y, x + r * .25f, y, p);
      c.drawLine(x - r * .25f, y + r * .35f, x + r * .25f, y + r * .35f, p);
    } else if (kind.equals("folder")) {
      Path path = new Path();
      path.moveTo(x - r * .85f, y - r * .35f);
      path.lineTo(x - r * .85f, y - r * .6f);
      path.lineTo(x - r * .25f, y - r * .6f);
      path.lineTo(x, y - r * .35f);
      c.drawPath(path, p);
      c.drawRoundRect(x - r * .85f, y - r * .35f, x + r * .85f, y + r * .6f, r * .12f, r * .12f, p);
    } else if (kind.equals("download") || kind.equals("upload")) {
      if (kind.equals("upload")) {
        c.drawLine(x, y + r * .3f, x, y - r * .7f, p);
        c.drawLine(x - r * .4f, y - r * .3f, x, y - r * .7f, p);
        c.drawLine(x + r * .4f, y - r * .3f, x, y - r * .7f, p);
      } else {
        c.drawLine(x, y - r * .7f, x, y + r * .3f, p);
        c.drawLine(x - r * .4f, y - r * .1f, x, y + r * .3f, p);
        c.drawLine(x + r * .4f, y - r * .1f, x, y + r * .3f, p);
      }
      Path tray = new Path();
      tray.moveTo(x - r * .7f, y + r * .2f);
      tray.lineTo(x - r * .7f, y + r * .7f);
      tray.lineTo(x + r * .7f, y + r * .7f);
      tray.lineTo(x + r * .7f, y + r * .2f);
      c.drawPath(tray, p);
    } else if (kind.equals("chip")) {
      c.drawRoundRect(x - r * .5f, y - r * .5f, x + r * .5f, y + r * .5f, r * .1f, r * .1f, p);
      c.drawRect(x - r * .2f, y - r * .2f, x + r * .2f, y + r * .2f, p);
      for (float o : new float[] {-.25f, .25f}) {
        c.drawLine(x + o * r, y - r * .5f, x + o * r, y - r * .8f, p);
        c.drawLine(x + o * r, y + r * .5f, x + o * r, y + r * .8f, p);
        c.drawLine(x - r * .5f, y + o * r, x - r * .8f, y + o * r, p);
        c.drawLine(x + r * .5f, y + o * r, x + r * .8f, y + o * r, p);
      }
    } else if (kind.equals("flask")) {
      Path path = new Path();
      path.moveTo(x - r * .25f, y - r * .8f);
      path.lineTo(x - r * .25f, y - r * .2f);
      path.lineTo(x - r * .75f, y + r * .6f);
      path.lineTo(x - r * .6f, y + r * .75f);
      path.lineTo(x + r * .6f, y + r * .75f);
      path.lineTo(x + r * .75f, y + r * .6f);
      path.lineTo(x + r * .25f, y - r * .2f);
      path.lineTo(x + r * .25f, y - r * .8f);
      c.drawPath(path, p);
      c.drawLine(x - r * .4f, y - r * .8f, x + r * .4f, y - r * .8f, p);
      c.drawLine(x - r * .5f, y + r * .2f, x + r * .5f, y + r * .2f, p);
    } else if (kind.equals("wrench")) {
      c.drawArc(x - r * .1f, y - r * .8f, x + r * .8f, y + r * .1f, 350, 290, false, p);
      c.drawLine(x + r * .03f, y - r * .03f, x - r * .7f, y + r * .7f, p);
    } else if (kind.equals("layers")) {
      Path top = new Path();
      top.moveTo(x, y - r * .85f);
      top.lineTo(x + r * .8f, y - r * .4f);
      top.lineTo(x, y + r * .05f);
      top.lineTo(x - r * .8f, y - r * .4f);
      top.close();
      c.drawPath(top, p);
      for (float o : new float[] {0f, .4f}) {
        Path edge = new Path();
        edge.moveTo(x - r * .8f, y + r * (.0f + o));
        edge.lineTo(x, y + r * (.45f + o));
        edge.lineTo(x + r * .8f, y + r * (.0f + o));
        c.drawPath(edge, p);
      }
    } else if (kind.equals("save")) {
      c.drawRoundRect(x - r * .75f, y - r * .75f, x + r * .75f, y + r * .75f, r * .12f, r * .12f, p);
      c.drawRect(x - r * .4f, y - r * .75f, x + r * .35f, y - r * .3f, p);
      c.drawRect(x - r * .45f, y + r * .1f, x + r * .45f, y + r * .75f, p);
    } else if (kind.equals("user")) {
      c.drawCircle(x, y, r * .9f, p);
      c.drawCircle(x, y - r * .2f, r * .25f, p);
      c.drawArc(x - r * .5f, y + r * .15f, x + r * .5f, y + r * .95f, 180, 180, false, p);
    } else if (kind.equals("info")) {
      c.drawCircle(x, y, r * .9f, p);
      c.drawLine(x, y - r * .05f, x, y + r * .45f, p);
      c.drawPoint(x, y - r * .4f, p);
    } else if (kind.equals("chevron")) {
      c.drawLine(x - r * .25f, y - r * .5f, x + r * .25f, y, p);
      c.drawLine(x + r * .25f, y, x - r * .25f, y + r * .5f, p);
    } else {
      c.drawArc(x - r * .45f, y - r * .8f, x + r * .45f, y + r * .3f, 0, 180, false, p);
      c.drawLine(x - r * .45f, y - r * .25f, x - r * .45f, y - r * .8f, p);
      c.drawLine(x + r * .45f, y - r * .25f, x + r * .45f, y - r * .8f, p);
      c.drawLine(x - r * .45f, y - r * .8f, x + r * .45f, y - r * .8f, p);
      c.drawArc(x - r * .8f, y - r * .65f, x - r * .15f, y + r * .1f, 60, 230, false, p);
      c.drawArc(x + r * .15f, y - r * .65f, x + r * .8f, y + r * .1f, 250, 230, false, p);
      c.drawLine(x, y + r * .1f, x, y + r * .65f, p);
      c.drawLine(x - r * .4f, y + r * .7f, x + r * .4f, y + r * .7f, p);
    }
    p.setStyle(Paint.Style.FILL);
  }
}
