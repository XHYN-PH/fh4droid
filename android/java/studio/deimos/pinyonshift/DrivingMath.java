package studio.deimos.pinyonshift;

/**
 * Deterministic steering/thermal decisions; independent of Android services.
 */
final class DrivingMath {
  static float axis(float raw, float inner, float outer, float power) {
    if (!Float.isFinite(raw))
      return 0;
    if (!Float.isFinite(inner))
      inner = 0;
    if (!Float.isFinite(outer))
      outer = 1;
    if (!Float.isFinite(power))
      power = 1;
    inner = Math.max(0, Math.min(.4f, inner));
    outer = Math.max(inner + .05f, Math.min(1, outer));
    float normalized = Math.max(0, Math.min(1, (Math.abs(raw) - inner) / (outer - inner)));
    return Math.copySign((float) Math.pow(normalized, Math.max(.5f, Math.min(2, power))), raw);
  }
  static float tilt(float angle, float centre, float range) {
    return tilt(angle, centre, range, .025f);
  }
  static float tilt(float angle, float centre, float range, float deadzone) {
    if (!Float.isFinite(angle) || !Float.isFinite(centre))
      return 0;
    if (!Float.isFinite(range))
      range = 35;
    float difference = (float)(((double)angle - centre) % 360.0);
    if (difference > 180) difference -= 360;
    if (difference < -180) difference += 360;
    return axis(difference / Math.max(15, Math.min(75, range)), deadzone, 1, 1);
  }
  static int sustainedTarget(int requested, boolean enabled, int thermal) {
    int normal = Math.max(0, Math.min(240, requested));
    return enabled && thermal >= 3 ? normal==0?30:Math.min(30, normal) : normal;
  }
  private DrivingMath() {}
}
