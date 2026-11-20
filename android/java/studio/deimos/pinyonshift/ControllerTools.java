package studio.deimos.pinyonshift;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

final class ControllerTools {
  static void testRumble(Activity a) {
    Vibrator vibrator = PlatformCompat.vibrator(a);
    if (vibrator == null || !vibrator.hasVibrator()) {
      FestivalUi.explain(a, "Phone vibration", "This device does not report a vibration motor.");
      return;
    }
    int percent = a.getSharedPreferences("mobile", 0).getInt("phone_rumble_strength", 60);
    if (percent <= 0)
      return;
    vibrator.vibrate(VibrationEffect.createOneShot(120,
        vibrator.hasAmplitudeControl() ? Math.max(1, Math.min(255, Math.round(percent * 2.55f)))
                                       : VibrationEffect.DEFAULT_AMPLITUDE));
  }
  static void show(Activity a, MobileSettings.Runtime runtime) {
    LinearLayout body = FestivalUi.column(a);
    body.addView(FestivalUi.text(a,
        "Focus the tester and move your sticks or triggers. White marks show raw input. "
            + "Pink marks show how the saved calibration shapes it.",
        12, FestivalUi.MUTED));
    Preview preview = new Preview(a);
    body.addView(preview, new LinearLayout.LayoutParams(-1, FestivalUi.dp(a, 130)));
    String[] keys = {"fh4droid_pad_inner", "fh4droid_pad_outer", "fh4droid_pad_curve",
        "fh4droid_trigger_inner", "fh4droid_trigger_outer"};
    String[] names = {"Stick inner deadzone", "Stick outer range", "Stick response curve",
        "Trigger inner deadzone", "Trigger outer range"};
    String[] help = {"Ignore small stick movements near centre.",
        "Set where the stick reaches full input.", "Higher values make the stick gentler near centre.",
        "Ignore small trigger presses.", "Set where the trigger reaches full input."};
    int[] min = {0, 60, 50, 0, 60}, max = {30, 100, 200, 30, 100}, defaults = {0, 100, 100, 0, 100};
    Map<String,String> settings = MobileConfig.read(a);
    for (int i = 0; i < keys.length; i++) {
      final int index = i;
      int value;
      try {
        value = Math.round(
            Float.parseFloat(settings.getOrDefault(keys[i], Float.toString(defaults[i] / 100f)))
            * 100);
      } catch (NumberFormatException e) {
        value = defaults[i];
      }
      preview.calibration[i] = Math.max(min[i],Math.min(max[i],value))/100f;
      FestivalUi.slider(a, body, names[i],help[i],
          min[i], max[i], 1, value,
          n
          -> n + "%",
          n -> {
            float next = n / 100f;
            if (SettingsHub.apply(a,runtime,Collections.singletonMap(keys[index],Float.toString(next)),false)) {
              preview.calibration[index] = next;
              preview.invalidate();
            }
          });
    }
    FestivalUi.present(a, "Controller calibration", body);
    preview.post(preview::requestFocus);
  }
  static float shaped(float raw,float inner,float outer,float curve) {
    float unit=Math.max(0,Math.min(1,(Math.abs(raw)-inner)/Math.max(.05f,outer-inner)));
    return Math.copySign((float)Math.pow(unit,curve),raw);
  }
  private static final class Preview extends View {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final float[] calibration = {0,1,1,0,1};
    float x, y, rx, ry, lt, rt;
    String device = "Connect a controller";
    Preview(Activity a) {
      super(a);
      FestivalUi.controlFocus(a,this);
      setContentDescription("Controller sticks and trigger input preview");
    }
    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
      if (e.isFromSource(InputDevice.SOURCE_JOYSTICK) && e.getActionMasked()==MotionEvent.ACTION_MOVE) {
        x = e.getAxisValue(MotionEvent.AXIS_X);
        y = e.getAxisValue(MotionEvent.AXIS_Y);
        rx = axis(e,MotionEvent.AXIS_Z,MotionEvent.AXIS_RX);
        ry = axis(e,MotionEvent.AXIS_RZ,MotionEvent.AXIS_RY);
        lt = Math.max(
            e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE));
        rt = Math.max(
            e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS));
        device = e.getDevice() == null ? "Controller" : e.getDevice().getName();
        invalidate();
        return true;
      }
      return super.onGenericMotionEvent(e);
    }
    private static float axis(MotionEvent event,int standard,int fallback) {
      InputDevice device=event.getDevice();
      return event.getAxisValue(device!=null&&device.getMotionRange(standard,event.getSource())==null
          ? fallback : standard);
    }
    @Override
    protected void onDraw(Canvas c) {
      p.setColor(FestivalUi.TEXT);
      p.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
      CharSequence label=android.text.TextUtils.ellipsize(device,new android.text.TextPaint(p),
          Math.max(1,getWidth()-20),android.text.TextUtils.TruncateAt.END);
      c.drawText(label.toString(), 8, p.getTextSize() + 4, p);
      float radius = Math.min(getHeight()*.23f,getWidth()*.15f), cy = getHeight() * .58f;
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(2);
      c.drawCircle(getWidth() * .25f, cy, radius, p);
      c.drawCircle(getWidth() * .65f, cy, radius, p);
      c.drawCircle(getWidth()*.25f+x*radius,cy+y*radius,5,p);
      c.drawCircle(getWidth()*.65f+rx*radius,cy+ry*radius,5,p);
      trigger(c,.86f,lt,.018f);
      trigger(c,.94f,rt,.018f);
      p.setStyle(Paint.Style.FILL);
      p.setColor(FestivalUi.ORANGE);
      c.drawCircle(getWidth()*.25f+shape(x)*radius,cy+shape(y)*radius,4,p);
      c.drawCircle(getWidth()*.65f+shape(rx)*radius,cy+shape(ry)*radius,4,p);
      trigger(c,.86f,shaped(lt,calibration[3],calibration[4],1),.01f);
      trigger(c,.94f,shaped(rt,calibration[3],calibration[4],1),.01f);
    }
    private float shape(float value) {
      return shaped(value,calibration[0],calibration[1],calibration[2]);
    }
    private void trigger(Canvas c,float center,float level,float halfWidth) {
      float floor=getHeight()*.85f;
      c.drawRect(getWidth()*(center-halfWidth),floor-getHeight()*.55f*Math.max(0,Math.min(1,level)),
          getWidth()*(center+halfWidth),floor,p);
    }
  }
  private ControllerTools() {}
}
