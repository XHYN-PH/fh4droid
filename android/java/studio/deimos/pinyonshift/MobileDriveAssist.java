package studio.deimos.pinyonshift;

import android.app.Activity;
import android.content.*;
import android.hardware.*;
import android.os.*;
import android.view.Surface;
import java.util.function.*;

/**
 * Lifecycle-owned steering and opt-in thermal response; never changes saved
 * presets.
 */
final class MobileDriveAssist implements SensorEventListener {
  private final Activity activity;
  private final SensorManager sensors;
  private final PowerManager power;
  private final Consumer<Float> steering;
  private final IntConsumer frameTarget;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final PowerManager.OnThermalStatusChangedListener thermal = this::thermalChanged;
  private boolean resumed, registered, thermalRegistered, centred, limited;
  private int thermalStatus;
  private float angle, centre, filtered;
  private long coolSince;
  MobileDriveAssist(Activity a, Consumer<Float> steering, IntConsumer target) {
    activity = a;
    this.steering = steering;
    frameTarget = target;
    sensors = a.getSystemService(SensorManager.class);
    power = a.getSystemService(PowerManager.class);
  }
  void resume() {
    resumed = true;
    reload();
  }
  void pause() {
    resumed = false;
    coolSince = 0;
    handler.removeCallbacksAndMessages(null);
    if (sensors != null)
      sensors.unregisterListener(this);
    registered = false;
    if (power != null && thermalRegistered)
      power.removeThermalStatusListener(thermal);
    if(power!=null&&power.isSustainedPerformanceModeSupported())activity.getWindow().setSustainedPerformanceMode(false);
    thermalRegistered = false;
    centred = false;
    filtered = 0;
    steering.accept(0f);
  }
  void reload() {
    SharedPreferences prefs = activity.getSharedPreferences("mobile", 0);
    if (sensors != null) {
      sensors.unregisterListener(this);
      registered = false;
      if (resumed && prefs.getBoolean("phone_tilt", false)) {
        Sensor sensor = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY);
        if (sensor == null)
          sensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (sensor != null)
          registered = sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
      }
    }
    if (!registered) {
      centred = false;
      filtered = 0;
      steering.accept(0f);
    }
    boolean enabled = prefs.getBoolean("sustained_mode", false);
    if (power != null) {
      if (resumed && enabled && !thermalRegistered) {
        try {
          power.addThermalStatusListener(activity.getMainExecutor(), thermal);
          thermalRegistered = true;
        } catch (UnsupportedOperationException ignored) {
        }
      }
      if ((!resumed || !enabled) && thermalRegistered) {
        power.removeThermalStatusListener(thermal);
        thermalRegistered = false;
      }
      activity.getWindow().setSustainedPerformanceMode(
          resumed && enabled && power.isSustainedPerformanceModeSupported());
      thermalStatus = power.getCurrentThermalStatus();
    }
    if (!enabled) {
      limited = false;
      coolSince = 0;
      handler.removeCallbacksAndMessages(null);
    } else if(resumed) thermalChanged(thermalStatus);
    applyTarget();
  }
  private void thermalChanged(int status) {
    if (!resumed || !activity.getSharedPreferences("mobile", 0).getBoolean("sustained_mode", false))
      return;
    thermalStatus = status;
    if (status >= PowerManager.THERMAL_STATUS_SEVERE) {
      limited = true;
      coolSince = 0;
      handler.removeCallbacksAndMessages(null);
      applyTarget();
    } else if (status <= PowerManager.THERMAL_STATUS_LIGHT && limited) {
      if (coolSince == 0)
        coolSince = SystemClock.elapsedRealtime();
      handler.removeCallbacksAndMessages(null);
      handler.postDelayed(() -> {
        if (resumed && thermalStatus <= PowerManager.THERMAL_STATUS_LIGHT
            && SystemClock.elapsedRealtime() - coolSince >= 60000) {
          limited = false;
          applyTarget();
        }
      }, 60000);
    } else
      coolSince = 0;
  }
  void applyTarget() {
    int requested;
    try {
      requested =
          Integer.parseInt(MobileConfig.get(activity, "pinyon_shift_fh1_render_fps_limit", "30"));
    } catch (NumberFormatException e) {
      requested = 30;
    }
    frameTarget.accept(DrivingMath.sustainedTarget(requested,
        activity.getSharedPreferences("mobile", 0).getBoolean("sustained_mode", false),
        limited ? 3 : 0));
  }
  boolean tiltEnabled() {
    return resumed && registered;
  }
  boolean calibrate() {
    if (!registered || !centred)
      return false;
    centre = angle;
    filtered = 0;
    steering.accept(0f);
    return true;
  }
  @Override
  public void onSensorChanged(SensorEvent e) {
    if (!resumed || !registered || e.values.length < 3)
      return;
    float x = e.values[0], y = e.values[1];
    if (!Float.isFinite(x) || !Float.isFinite(y) || Math.hypot(x, y) < 1)
      return;
    int rotation =
        activity.getWindowManager().getDefaultDisplay().getRotation();
    angle = (float) Math.toDegrees(rotation == Surface.ROTATION_90 ? Math.atan2(y, -x)
            : rotation == Surface.ROTATION_270                     ? Math.atan2(-y, x)
            : rotation == Surface.ROTATION_180                     ? Math.atan2(x, -y)
                                                                   : Math.atan2(-x, y));
    if (!centred) {
      centre = angle;
      centred = true;
    }
    SharedPreferences prefs = activity.getSharedPreferences("mobile", 0);
    float target = DrivingMath.tilt(
        angle, centre, prefs.getInt("tilt_range", 35), prefs.getInt("tilt_deadzone", 3) / 100f);
    filtered += .22f * (target - filtered);
    steering.accept(filtered);
  }
  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
