package studio.deimos.pinyonshift;

import android.content.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Explicit handoff for UI preferences between launcher and :game processes. */
final class UiPreferences {
  private static final Set<String> BOOLEANS =
      new HashSet<>(Arrays.asList("animations", "show_fps", "show_frame_time", "show_memory_heat", "match_refresh", "phone_rumble",
          "phone_tilt", "press_names", "sustained_mode", "advanced_settings", "detailed_gpu"));
  static Map<String, Object> sanitized(Map<String, ?> values) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<String, ?> entry : values.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();
      if(key.equals("app_language")&&value instanceof String&&Arrays.asList("en","fil").contains(value))result.put(key,value);
      else if (BOOLEANS.contains(key) && value instanceof Boolean)
        result.put(key, value);
      else if (key.equals("ui_scale") && value instanceof Number) {
        float n = ((Number) value).floatValue();
        if (Float.isFinite(n))
          result.put(key, Math.max(.7f, Math.min(1.05f, n)));
      } else if ((key.equals("phone_rumble_strength") || key.equals("tilt_range")
                     || key.equals("tilt_deadzone"))
          && value instanceof Number) {
        if (!Double.isFinite(((Number) value).doubleValue()))
          continue;
        int n = ((Number) value).intValue();
        result.put(key,
            key.equals("tilt_range")
                ? Math.max(15, Math.min(75, n))
                : Math.max(0, Math.min(key.equals("tilt_deadzone") ? 15 : 100, n)));
      }
    }
    return result;
  }
  private static File file(Context c) {
    return new File(MobileConfig.base(c), "state/config/android-ui.json");
  }
  static void save(Context c) {
    Path stage = null;
    try {
      JSONObject root = new JSONObject();
      root.put(
          "preferences", new JSONObject(sanitized(c.getSharedPreferences("mobile", 0).getAll())));
      Map<String, Object> pins = new LinkedHashMap<>();
      for (Map.Entry<String, ?> entry :
          c.getSharedPreferences("setting_pins", 0).getAll().entrySet())
        if (entry.getKey().matches("[a-zA-Z0-9_]{1,80}") && entry.getValue() instanceof Boolean)
          pins.put(entry.getKey(), entry.getValue());
      root.put("pins", new JSONObject(pins));
      File target = file(c);
      target.getParentFile().mkdirs();
      stage = Files.createTempFile(target.getParentFile().toPath(), "android-ui-", ".part");
      try (FileOutputStream out = new FileOutputStream(stage.toFile())) {
        out.write(root.toString().getBytes(StandardCharsets.UTF_8));
        out.getFD().sync();
      }
      Files.move(stage, target.toPath(), StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception e) {
      android.util.Log.w("FH4Droid", "Could not save UI handoff", e);
    } finally {
      if (stage != null) try { Files.deleteIfExists(stage); } catch (IOException ignored) {}
    }
  }
  static void load(Context c) {
    File file = file(c);
    if (!file.isFile() || file.length() > 65536)
      return;
    try {
      JSONObject root =
          new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
      JSONObject raw = root.optJSONObject("preferences");
      Map<String, Object> values = new LinkedHashMap<>();
      if (raw != null)
        for (Iterator<String> it = raw.keys(); it.hasNext();) {
          String key = it.next();
          values.put(key, raw.opt(key));
        }
      SharedPreferences.Editor editor = c.getSharedPreferences("mobile", 0).edit();
      for (Map.Entry<String, Object> entry : sanitized(values).entrySet()) {
        Object value = entry.getValue();
        if (value instanceof Boolean)
          editor.putBoolean(entry.getKey(), (Boolean) value);
        else if (value instanceof Float)
          editor.putFloat(entry.getKey(), (Float) value);
        else if (value instanceof Integer)
          editor.putInt(entry.getKey(), (Integer) value);
      }
      editor.apply();
      JSONObject pins = root.optJSONObject("pins");
      if (pins != null) {
        SharedPreferences.Editor pinned = c.getSharedPreferences("setting_pins", 0).edit().clear();
        for (Iterator<String> it = pins.keys(); it.hasNext();) {
          String key = it.next();
          if (key.matches("[a-zA-Z0-9_]{1,80}") && pins.opt(key) instanceof Boolean)
            pinned.putBoolean(key, pins.optBoolean(key));
        }
        pinned.apply();
      }
    } catch (Exception e) {
      android.util.Log.w("FH4Droid", "Could not read UI handoff", e);
    }
  }
  private UiPreferences() {}
}
