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
        "Move the sticks and triggers to inspect raw input. "
            + "Calibration affects physical SDL "
            + "controllers; touch controls stay separate.",
        12, FestivalUi.MUTED));
    Preview preview = new Preview(a);
    body.addView(preview, new LinearLayout.LayoutParams(-1, FestivalUi.dp(a, 130)));
    String[] keys = {"fh4droid_pad_inner", "fh4droid_pad_outer", "fh4droid_pad_curve",
        "fh4droid_trigger_inner", "fh4droid_trigger_outer"};
    String[] names = {"Stick inner deadzone", "Stick outer range", "Stick response curve",
        "Trigger inner deadzone", "Trigger outer range"};
    int[] min = {0, 60, 50, 0, 60}, max = {30, 100, 200, 30, 100}, defaults = {0, 100, 100, 0, 100};
    for (int i = 0; i < keys.length; i++) {
      final int index = i;
      int value;
      try {
        value = Math.round(
            Float.parseFloat(MobileConfig.get(a, keys[i], Float.toString(defaults[i] / 100f)))
            * 100);
      } catch (NumberFormatException e) {
        value = defaults[i];
      }
      FestivalUi.slider(a, body, names[i],
          "Inner ignores small motion; outer controls where full input is "
              + "reached. A higher curve "
              + "makes the centre gentler.",
          min[i], max[i], 1, value,
          n
          -> n + "%",
          n
          -> SettingsHub.apply(
              a, runtime, Collections.singletonMap(keys[index], Float.toString(n / 100f)), false));
    }
    FestivalUi.present(a, "Controller calibration", body);
    preview.post(preview::requestFocus);
  }
  private static final class Preview extends View {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    float x, y, rx, ry, lt, rt;
    String device = "Connect a controller";
    Preview(Context c) {
      super(c);
      setFocusable(true);
      setFocusableInTouchMode(true);
      setContentDescription("Controller sticks and trigger input preview");
    }
    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
      if ((e.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
        x = e.getAxisValue(MotionEvent.AXIS_X);
        y = e.getAxisValue(MotionEvent.AXIS_Y);
        rx = e.getAxisValue(MotionEvent.AXIS_Z);
        ry = e.getAxisValue(MotionEvent.AXIS_RZ);
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
    @Override
    protected void onDraw(Canvas c) {
      p.setColor(FestivalUi.TEXT);
      p.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
      c.drawText(device, 8, p.getTextSize() + 4, p);
      float radius = getHeight() * .24f, cy = getHeight() * .58f;
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(2);
      c.drawCircle(getWidth() * .25f, cy, radius, p);
      c.drawCircle(getWidth() * .65f, cy, radius, p);
      p.setStyle(Paint.Style.FILL);
      p.setColor(FestivalUi.ORANGE);
      c.drawCircle(getWidth() * .25f + x * radius, cy + y * radius, 5, p);
      c.drawCircle(getWidth() * .65f + rx * radius, cy + ry * radius, 5, p);
      c.drawRect(getWidth() * .88f, getHeight() * (.85f - .55f * lt), getWidth() * .91f,
          getHeight() * .85f, p);
      c.drawRect(getWidth() * .94f, getHeight() * (.85f - .55f * rt), getWidth() * .97f,
          getHeight() * .85f, p);
    }
  }
  private ControllerTools() {}
}
