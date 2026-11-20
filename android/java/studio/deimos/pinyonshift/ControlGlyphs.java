package studio.deimos.pinyonshift;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/** Outlines for the individual actions and options under Controls. */
final class ControlGlyphs {
  static String setting(String title) {
    switch (title) {
      case "Controls": return "touch-steering";
      case "Touch controls": return "touch-screen";
      case "Controller": return "controller";
      case "Layout": return "touch-label-mode";
      case "Steering": return "steering-mode";
      case "Button names": return "button-captions";
      case "Manual shifting controls": return "manual-gears";
      case "Phone vibration": return "phone-rumble";
      case "Phone vibration strength": return "rumble-level";
      case "Test phone vibration": return "rumble-test";
      case "Phone tilt steering": return "phone-tilt";
      case "Invert phone tilt": return "tilt-invert";
      case "Tilt centre deadzone": return "tilt-deadzone";
      case "Tilt steering range": return "tilt-range";
      case "Calibrate tilt centre": return "tilt-center";
      case "Game rumble strength": return "controller-rumble";
      case "Controller tilt steering": return "controller-gyro";
      case "Controller tilt sensitivity": return "gyro-sensitivity";
      case "Native controller remapping": return "controller-remap";
      case "Test and calibrate controller": return "controller-test";
      case "Stick inner deadzone": return "stick-inner";
      case "Stick outer range": return "stick-outer";
      case "Stick response curve": return "stick-curve";
      case "Trigger inner deadzone": return "trigger-inner";
      case "Trigger outer range": return "trigger-outer";
      case "Control opacity": return "opacity";
      case "Size": return "resize";
      case "Horizontal position": return "position-x";
      case "Vertical position": return "position-y";
      case "Show this control": return "eye";
      case "Fade timer": return "timer";
      case "Arrow return to centre": return "arrow-return";
      case "Maximum arrow steering": return "steering-limit";
      case "Sliding pedals": return "pedal";
      case "Hide with controller": return "hide-control";
      case "Touch vibration": return "touch-feedback";
      case "Driving action names": return "button-captions";
      case "Button codes": return "touch-label-mode";
      case "Button background": return "backing";
      case "Text label opacity": return "text-opacity";
      case "High contrast": return "contrast";
      case "Global fade timer": return "global-timer";
      default: return null;
    }
  }

  static boolean draw(Canvas canvas, Paint paint, float x, float y, float radius, String kind) {
    canvas.save();
    canvas.translate(x, y);
    canvas.scale(radius / 10, radius / 10);
    paint.setStrokeWidth(1.55f);
    boolean drawn = true;
    switch (kind) {
      case "layout-edit":
        line(canvas, paint, -9, -2, -9, -8, -3, -8);
        line(canvas, paint, 3, 8, 9, 8, 9, 2);
        line(canvas, paint, -2, 6, 6, -2, 8, 0, 0, 8, -4, 9, -2, 6);
        break;
      case "touch-steering":
        canvas.drawCircle(0, 0, 8, paint);
        canvas.drawCircle(0, 0, 2, paint);
        line(canvas, paint, -8, -3, -2, -1);
        line(canvas, paint, 2, -1, 8, -3);
        line(canvas, paint, 0, 2, 0, 8);
        break;
      case "phone-motion":
        canvas.save();
        canvas.rotate(-24);
        canvas.drawRoundRect(-5, -8, 5, 8, 2, 2, paint);
        canvas.drawPoint(0, 5, paint);
        canvas.restore();
        canvas.drawArc(-11, -11, 11, 11, 205, 60, false, paint);
        canvas.drawArc(-11, -11, 11, 11, 25, 60, false, paint);
        break;
      case "layout-library":
        canvas.drawRoundRect(-5, -4, 9, 8, 2, 2, paint);
        line(canvas, paint, -9, 4, -9, -8, 5, -8);
        line(canvas, paint, -2, 0, 6, 0);
        line(canvas, paint, -2, 4, 3, 4);
        break;
      case "controller":
        pad(canvas, paint, -5, 6);
        line(canvas, paint, -6, 0, -2, 0);
        line(canvas, paint, -4, -2, -4, 2);
        canvas.drawPoint(4, -1, paint);
        canvas.drawPoint(6, 1, paint);
        break;
      case "touch-screen":
        line(canvas, paint, -3, 7, -8, 7, -8, -7, 8, -7, 8, -2);
        line(canvas, paint, -1, 4, -1, -2, 1, -2, 1, 2, 6, 3, 7, 5, 5, 9, 1, 9, -3, 4);
        break;
      case "touch-label-mode":
        canvas.drawRoundRect(-9, -5, -1, 5, 2, 2, paint);
        canvas.drawRoundRect(1, -5, 9, 5, 2, 2, paint);
        line(canvas, paint, -7, 2, -5, -2, -3, 2);
        line(canvas, paint, 3, -1, 7, -1);
        line(canvas, paint, 3, 2, 6, 2);
        break;
      case "steering-mode":
        line(canvas, paint, -4, -4, -8, 0, -4, 4);
        line(canvas, paint, 4, -4, 8, 0, 4, 4);
        canvas.drawCircle(0, 0, 1.25f, paint);
        break;
      case "button-captions":
        line(canvas, paint, -8, -6, 8, -6, 8, 4, 1, 4, -4, 8, -4, 4, -8, 4, -8, -6);
        line(canvas, paint, -5, -2, 5, -2);
        line(canvas, paint, -5, 1, 1, 1);
        break;
      case "manual-gears":
        line(canvas, paint, -6, -7, -6, 7);
        line(canvas, paint, 0, -7, 0, 7);
        line(canvas, paint, 6, -7, 6, 7);
        line(canvas, paint, -6, 0, 6, 0);
        break;
      case "phone-rumble":
        canvas.drawRoundRect(-4, -8, 4, 8, 2, 2, paint);
        line(canvas, paint, -8, -4, -10, 0, -8, 4);
        line(canvas, paint, 8, -4, 10, 0, 8, 4);
        break;
      case "rumble-level":
        line(canvas, paint, -8, 5, -8, 1);
        line(canvas, paint, -3, 5, -3, -2);
        line(canvas, paint, 2, 5, 2, -5);
        line(canvas, paint, 7, 5, 7, -8);
        break;
      case "rumble-test":
        line(canvas, paint, -9, 1, -5, 1, -3, -6, 1, 6, 4, -2, 9, -2);
        break;
      case "phone-tilt":
        canvas.save();
        canvas.rotate(-27);
        canvas.drawRoundRect(-7, -5, 7, 4, 2, 2, paint);
        canvas.drawPoint(4, 0, paint);
        canvas.restore();
        line(canvas, paint, -9, 8, 9, 8);
        break;
      case "tilt-invert":
        line(canvas, paint, -8, -4, 8, -4, 5, -7);
        line(canvas, paint, 8, 4, -8, 4, -5, 7);
        break;
      case "tilt-deadzone":
        line(canvas, paint, -4, -5, -8, -5, -8, 5, -4, 5);
        line(canvas, paint, 4, -5, 8, -5, 8, 5, 4, 5);
        canvas.drawCircle(0, 0, 1.8f, paint);
        break;
      case "tilt-range":
        line(canvas, paint, -7, 6, 7, 6, 3, -7);
        canvas.drawArc(-4, -5, 14, 13, 182, 69, false, paint);
        break;
      case "tilt-center":
        canvas.drawCircle(0, 0, 6, paint);
        canvas.drawPoint(0, 0, paint);
        for (int side : new int[]{-1, 1}) {
          line(canvas, paint, side * 5, 0, side * 9, 0);
          line(canvas, paint, 0, side * 5, 0, side * 9);
        }
        break;
      case "controller-rumble":
        pad(canvas, paint, -2, 8);
        line(canvas, paint, -7, -5, -5, -8, -3, -5);
        line(canvas, paint, 3, -5, 5, -8, 7, -5);
        break;
      case "controller-gyro":
        canvas.drawCircle(0, 0, 8, paint);
        canvas.save();
        canvas.rotate(-38);
        canvas.drawOval(-8, -3, 8, 3, paint);
        canvas.restore();
        canvas.drawPoint(0, 0, paint);
        break;
      case "gyro-sensitivity":
        canvas.drawArc(-9, -5, 9, 12, 180, 180, false, paint);
        line(canvas, paint, -9, 4, 9, 4);
        line(canvas, paint, 0, 4, 5, -2);
        break;
      case "controller-remap":
        canvas.drawRoundRect(-9, 1, -3, 7, 1, 1, paint);
        canvas.drawRoundRect(3, 1, 9, 7, 1, 1, paint);
        line(canvas, paint, -6, -2, -6, -6, 7, -6, 4, -9);
        line(canvas, paint, 7, -6, 4, -3);
        break;
      case "controller-test":
        pad(canvas, paint, -5, 6);
        line(canvas, paint, -5, 0, -2, 0, 0, -3, 2, 2, 5, 2);
        break;
      case "stick-inner":
        canvas.drawCircle(0, 0, 8, paint);
        canvas.drawCircle(0, 0, 3, paint);
        break;
      case "stick-outer":
        canvas.drawCircle(0, 0, 6, paint);
        canvas.drawPoint(-9, 0, paint);
        canvas.drawPoint(9, 0, paint);
        canvas.drawPoint(0, -9, paint);
        canvas.drawPoint(0, 9, paint);
        break;
      case "stick-curve":
        line(canvas, paint, -8, -7, -8, 6, 8, 6);
        Path curve = new Path();
        curve.moveTo(-5, 3);
        curve.cubicTo(0, 3, 2, 0, 7, -7);
        canvas.drawPath(curve, paint);
        break;
      case "trigger-inner":
      case "trigger-outer":
        line(canvas, paint, -7, -5, 7, -5, 6, 3, -5, 7, -7, -5);
        float mark = kind.equals("trigger-inner") ? -1 : 2;
        line(canvas, paint, -3, mark, 4, mark);
        break;
      case "flag":
        line(canvas,paint,-7,9,-7,-8,8,-8,8,3,-7,3);
        line(canvas,paint,0,-8,0,3);
        line(canvas,paint,-7,-2,8,-2);
        break;
      case "sliders":
        for(int speed:new int[]{-6,0,6}) {
          line(canvas,paint,-9,speed,9,speed);
          canvas.drawCircle(speed/2f,speed,1.8f,paint);
        }
        break;
      case "audio":
        line(canvas,paint,-8,-3,-4,-3,0,-7,0,7,-4,3,-8,3,-8,-3);
        canvas.drawArc(-4,-7,9,7,-68,136,false,paint);
        break;
      case "pulse":
        line(canvas,paint,-9,1,-4,1,-2,-6,2,6,4,0,9,0);
        break;
      case "play":
        line(canvas,paint,-4,-8,8,0,-4,8,-4,-8);
        break;
      case "opacity":
        line(canvas,paint,0,-8,6,0,6,4,3,8,-3,8,-6,4,-6,0,0,-8);
        line(canvas,paint,-4,3,4,3);
        break;
      case "resize":
        line(canvas,paint,-8,1,-8,-8,1,-8);
        line(canvas,paint,8,-1,8,8,-1,8);
        line(canvas,paint,-6,6,6,-6,0,-6);
        line(canvas,paint,6,-6,6,0);
        break;
      case "position-x":
      case "position-y":
        if(kind.equals("position-y"))canvas.rotate(90);
        line(canvas,paint,-8,0,8,0);
        line(canvas,paint,-4,-4,-8,0,-4,4);
        line(canvas,paint,4,-4,8,0,4,4);
        break;
      case "eye":
        canvas.drawOval(-9,-5,9,5,paint);
        canvas.drawCircle(0,0,2.5f,paint);
        break;
      case "timer":
      case "global-timer":
        canvas.drawCircle(0,1,7,paint);
        line(canvas,paint,0,-4,0,1,4,3);
        if(kind.equals("global-timer"))canvas.drawArc(-10,-9,10,11,182,92,false,paint);
        else line(canvas,paint,-2,-9,2,-9);
        break;
      case "arrow-return":
        line(canvas,paint,8,-6,8,3,1,6,-8,6,-4,2);
        line(canvas,paint,-8,6,-4,9);
        break;
      case "steering-limit":
        canvas.drawArc(-8,-8,8,8,185,170,false,paint);
        line(canvas,paint,-9,-1,-9,5);
        line(canvas,paint,9,-1,9,5);
        line(canvas,paint,0,5,5,-1);
        break;
      case "pedal":
        canvas.save();
        canvas.rotate(18);
        canvas.drawRoundRect(-4,-8,4,8,2,2,paint);
        line(canvas,paint,-2,-2,2,-2);
        line(canvas,paint,-2,3,2,3);
        canvas.restore();
        break;
      case "hide-control":
        pad(canvas,paint,-5,6);
        line(canvas,paint,-8,-8,8,8);
        break;
      case "touch-feedback":
        canvas.drawCircle(1,2,2,paint);
        canvas.drawArc(-6,-6,8,9,210,200,false,paint);
        canvas.drawArc(-10,-10,11,11,210,160,false,paint);
        break;
      case "backing":
        canvas.drawRoundRect(-7,-7,7,7,2,2,paint);
        line(canvas,paint,-4,4,4,4);
        break;
      case "text-opacity":
        line(canvas,paint,-7,-6,1,-6);
        line(canvas,paint,-3,-6,-3,5);
        canvas.drawCircle(5,3,3,paint);
        break;
      case "contrast":
        canvas.drawCircle(0,0,8,paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawArc(-8,-8,8,8,-90,180,true,paint);
        paint.setStyle(Paint.Style.STROKE);
        break;
      default:
        drawn = false;
    }
    canvas.restore();
    return drawn;
  }

  private static void line(Canvas canvas, Paint paint, float... points) {
    Path path = new Path();
    path.moveTo(points[0], points[1]);
    for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
    canvas.drawPath(path, paint);
  }

  private static void pad(Canvas canvas, Paint paint, float top, float bottom) {
    canvas.drawRoundRect(-9, top, 9, bottom, 4, 4, paint);
  }

  private ControlGlyphs() {}
}
