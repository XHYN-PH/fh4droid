package studio.deimos.pinyonshift;

import android.app.*;
import android.content.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/**
 * Compact settings with one saved value source and explicit live/pending
 * state.
 */
final class SettingsHub {
  static final String[] PAGES = {
      "Graphics", "Display", "Performance", "Audio", "Touch", "Controller", "Gameplay"};
  private static final Set<String> ADVANCED =
      new HashSet<>(Arrays.asList("swap_post_effect_scene_only", "texture_mip_lod_bias",
          "fh1_untile_predicated_tiling", "vulkan_pipeline_creation_threads",
          "vulkan_pipeline_prewarm_background", "host_present_fps_limit", "host_present_sleep_spin",
          "vulkan_present_wait", "present_dither", "pad_gyro_steering", "pad_gyro_sensitivity",
          "present_cas_additional_sharpness", "present_fsr_sharpness_reduction"));
  private static final Map<Activity, Change> UNDO = new WeakHashMap<>();
  private static final List<MobileSettings.Option> OPTIONS =
      new ArrayList<>(Arrays.asList(MobileSettings.OPTIONS));
  static {
    OPTIONS.add(
        new MobileSettings.Option("Display", "pinyon_shift_fov_scale", "Camera field of view", "1",
            false, false, "Wider values show more of the scene and can add graphics work.", ".8",
            ".9", "1", "1.1", "1.2", "1.3"));
    OPTIONS.add(new MobileSettings.Option("Gameplay", "pinyon_shift_save_backups",
        "Automatic save backups", "true", true, false,
        "Keep the native port's rolling save backups. This does not restore "
            + "or delete your current "
            + "save.",
        "false", "true"));
  }
  private static final class Change {
    final Map<String, String> values;
    final boolean restart;
    Change(Map<String, String> values, boolean restart) {
      this.values = values;
      this.restart = restart;
    }
  }
  static void show(Activity a, MobileSettings.Runtime runtime, Runnable editor, Runnable drivers) {
    LinearLayout body = FestivalUi.column(a);
    body.addView(FestivalUi.text(a,
        "Choose a category. Hold a value button to pin it; tap a setting name for help.", 12,
        FestivalUi.MUTED));
    for (String page : PAGES)
      FestivalUi.action(a, body, page, () -> page(a, page, runtime, editor));
    FestivalUi.action(a, body, "Search all settings / pinned settings",
        () -> page(a, "All settings", runtime, editor));
    FestivalUi.action(a, body, "Graphics presets", () -> presets(a, runtime));
    FestivalUi.action(a, body, "Graphics driver", drivers);
    FestivalUi.action(a, body, "Graphics cache", () -> GraphicsCache.show(a));
    FestivalUi.present(a, "Settings", body);
  }
  static void page(Activity a, String page, MobileSettings.Runtime runtime, Runnable editor) {
    FestivalUi.present(a, page, buildPage(a, page, runtime, editor));
  }
  private static LinearLayout buildPage(
      Activity a, String page, MobileSettings.Runtime runtime, Runnable editor) {
    LinearLayout root = FestivalUi.column(a);
    HorizontalScrollView strip = new HorizontalScrollView(a);
    strip.setHorizontalScrollBarEnabled(false);
    LinearLayout tabs = new LinearLayout(a);
    for (String name : PAGES) {
      Button tab = FestivalUi.button(a, name, false);
      tab.setTextColor(name.equals(page) ? FestivalUi.ORANGE : FestivalUi.MUTED);
      tabs.addView(tab, new LinearLayout.LayoutParams(-2, -2));
      tab.setOnClickListener(v -> {
        if (a instanceof FestivalUi.PanelHost)
          ((FestivalUi.PanelHost) a).replacePanel(name, buildPage(a, name, runtime, editor));
        else
          page(a, name, runtime, editor);
      });
    }
    strip.addView(tabs);
    root.addView(strip);
    LinearLayout content = createPage(a, page, runtime, editor);
    root.addView(content);
    return root;
  }
  private static LinearLayout createPage(
      Activity a, String page, MobileSettings.Runtime runtime, Runnable editor) {
    LinearLayout body = FestivalUi.column(a);
    EditText search = new EditText(a);
    search.setSingleLine(true);
    search.setTextColor(FestivalUi.TEXT);
    search.setHintTextColor(FestivalUi.MUTED);
    search.setTextSize(13 * FestivalUi.scale(a));
    search.setHint("Search " + page.toLowerCase(Locale.ROOT));
    search.setMinHeight(FestivalUi.dp(a,44));
    body.addView(search, new LinearLayout.LayoutParams(-1, -2));
    boolean[] advanced = {
        a.getSharedPreferences("mobile", 0).getBoolean("advanced_settings", false)};
    boolean[] pinned = {false};
    LinearLayout flags = new LinearLayout(a);
    CheckBox expert = new CheckBox(a);
    expert.setText("Advanced");
    expert.setTextColor(FestivalUi.MUTED);
    expert.setChecked(advanced[0]);
    CheckBox pins = new CheckBox(a);
    pins.setText("Pinned only");
    pins.setTextColor(FestivalUi.MUTED);
    flags.addView(expert);
    flags.addView(pins);
    body.addView(flags);
    LinearLayout rows = FestivalUi.column(a);
    body.addView(rows);
    Runnable rebuild = () -> {
      rows.removeAllViews();
      String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
      for (MobileSettings.Option option : OPTIONS) {
        String category = option.page.equals("Controls")
            ? (option.key.equals("pinyon_shift_touch_controls") ? "Touch" : "Controller")
            : option.page;
        if (!page.equals("All settings") && !page.equals(category))
          continue;
        if (!advanced[0] && ADVANCED.contains(option.key) && query.isEmpty() && !pinned[0])
          continue;
        if (!query.isEmpty()
            && !(option.title + " " + option.help).toLowerCase(Locale.ROOT).contains(query))
          continue;
        if (pinned[0] && !isPinned(a, option.key))
          continue;
        optionRow(a, rows, option, runtime);
      }
      if (!pinned[0]) {
        for (String category : page.equals("All settings") ? PAGES : new String[] {page}) {
          LinearLayout extra = FestivalUi.column(a);
          extras(a, extra, category, runtime, editor);
          while (extra.getChildCount() > 0) {
            android.view.View child = extra.getChildAt(0);
            extra.removeViewAt(0);
            if (query.isEmpty() || matches(child, query))
              rows.addView(child);
          }
        }
      }
      if (rows.getChildCount() == 0)
        rows.addView(FestivalUi.text(a, "No matching settings.", 13, FestivalUi.MUTED));
      FestivalUi.action(a, rows, "Reset game settings in this section",
          ()
              -> new AlertDialog.Builder(a)
                     .setTitle("Reset " + page + "?")
                     .setMessage("Game settings in this section will return "
                         + "to their defaults. "
                         + "Phone options, menu size, saves, drivers "
                         + "and touch layouts are kept.")
                     .setNegativeButton("Cancel", null)
                     .setPositiveButton("Reset",
                         (d, w) -> {
                           Map<String, String> defaults = new LinkedHashMap<>();
                           for (MobileSettings.Option o : OPTIONS) {
                             String cat = o.page.equals("Controls")
                                 ? (o.key.equals("pinyon_shift_touch_controls") ? "Touch"
                                                                                : "Controller")
                                 : o.page;
                             if (page.equals(cat) || page.equals("All settings"))
                               defaults.put(o.key, literal(o, o.initial));
                           }
                           if (page.equals("Graphics") || page.equals("All settings")) {
                             defaults.put("fh1_render_scale_percent", "100");
                             defaults.put("fh1_msaa_single_sample", "true");
                             defaults.put("fh1_msaa_2x", "false");
                           }
                           if (page.equals("Display") || page.equals("All settings")) {
                             defaults.putAll(GraphicsProfiles.screenMode(0));
                           }
                           if (page.equals("Controller") || page.equals("All settings")) {
                             defaults.put("fh4droid_pad_inner", "0");
                             defaults.put("fh4droid_pad_outer", "1");
                             defaults.put("fh4droid_pad_curve", "1");
                             defaults.put("fh4droid_trigger_inner", "0");
                             defaults.put("fh4droid_trigger_outer", "1");
                           }
                           apply(a, runtime, defaults, true);
                           FestivalUi.refreshTree(body);
                         })
                     .show());
      FestivalUi.action(a, rows, "Undo last settings change", () -> {
        undo(a, runtime);
        FestivalUi.refreshTree(body);
      });
    };
    expert.setOnCheckedChangeListener((v, on) -> {
      advanced[0] = on;
      a.getSharedPreferences("mobile", 0).edit().putBoolean("advanced_settings", on).apply();
      UiPreferences.save(a);
      rebuild.run();
    });
    pins.setOnCheckedChangeListener((v, on) -> {
      pinned[0] = on;
      rebuild.run();
    });
    search.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        rebuild.run();
      }
      public void afterTextChanged(Editable e) {}
    });
    rebuild.run();
    return body;
  }
  private static boolean matches(android.view.View view, String query) {
    if (view instanceof TextView
        && ((TextView) view).getText().toString().toLowerCase(Locale.ROOT).contains(query))
      return true;
    if (view.getContentDescription() != null
        && view.getContentDescription().toString().toLowerCase(Locale.ROOT).contains(query))
      return true;
    if (view instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++)
        if (matches(group.getChildAt(i), query))
          return true;
    }
    return false;
  }
  private static boolean isPinned(Activity a, String key) {
    return a.getSharedPreferences("setting_pins", 0).getBoolean(key, false);
  }
  private static String literal(MobileSettings.Option o, String value) {
    return o.string ? MobileConfig.quote(value) : value;
  }
  private static String plain(String literal) {
    return literal.startsWith("\"") && literal.endsWith("\"")
        ? literal.substring(1, literal.length() - 1)
        : literal;
  }
  static String value(Activity a, String key, String fallback) {
    return MobileConfig.get(a, key, fallback);
  }
  private static String active(MobileSettings.Runtime runtime, String key) {
    try {
      return runtime == null ? null : runtime.value(key);
    } catch (RuntimeException e) {
      return null;
    }
  }
  private static void optionRow(
      Activity a, LinearLayout rows, MobileSettings.Option o, MobileSettings.Runtime runtime) {
    LinearLayout row = new LinearLayout(a);
    row.setGravity(Gravity.CENTER_VERTICAL);
    TextView name = FestivalUi.text(a, o.title, 14, FestivalUi.TEXT);
    name.setPadding(
        FestivalUi.dp(a, 8), FestivalUi.dp(a, 7), FestivalUi.dp(a, 8), FestivalUi.dp(a, 7));
    row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
    name.setOnClickListener(v
        -> FestivalUi.explain(
            a, o.title, o.help + (o.restart ? " Applies after restarting the game." : "")));
    Button choice = FestivalUi.button(a, "", false);
    row.addView(choice, new LinearLayout.LayoutParams(-2, -2));
    FestivalUi.onRefresh(choice, () -> {
      String saved = value(a, o.key, o.initial), live = active(runtime, o.key);
      boolean different = live != null && !SettingValues.same(saved, live);
      choice.setText(MobileSettings.label(o, saved) + (o.restart ? " · restart" : "")
          + (different ? " · active: " + MobileSettings.label(o, live) : ""));
      choice.setContentDescription(o.title + ": " + MobileSettings.label(o, saved)
          + (different ? ". Active: " + MobileSettings.label(o, live) : ""));
      name.setText((isPinned(a, o.key) ? "• " : "") + o.title);
    });
    choice.setOnLongClickListener(v -> {
      boolean pin = !isPinned(a, o.key);
      a.getSharedPreferences("setting_pins", 0).edit().putBoolean(o.key, pin).apply();
      UiPreferences.save(a);
      FestivalUi.refreshTree(row);
      Toast.makeText(a, pin ? "Setting pinned" : "Setting unpinned", Toast.LENGTH_SHORT).show();
      return true;
    });
    choice.setOnClickListener(v -> {
      String saved = value(a, o.key, o.initial);
      String[] labels = new String[o.values.length];
      int selected = -1;
      for (int i = 0; i < labels.length; i++) {
        labels[i] = MobileSettings.label(o, o.values[i]);
        if (SettingValues.same(saved, o.values[i]))
          selected = i;
      }
      if (o.key.equals("pad_rumble_strength") || o.key.equals("pinyon_shift_master_volume")) {
        LinearLayout slider = FestivalUi.column(a);
        FestivalUi.slider(
            a, slider, o.title, o.help, 0, 100, 1, integer(saved, 100), i -> i + "%", i -> {
              apply(a, runtime, Collections.singletonMap(o.key, Integer.toString(i)), o.restart);
              FestivalUi.refreshTree(row);
            });
        FestivalUi.present(a, o.title, slider);
      } else
        FestivalUi.choose(a, o.title, labels, selected, index -> {
          apply(
              a, runtime, Collections.singletonMap(o.key, literal(o, o.values[index])), o.restart);
          FestivalUi.refreshTree(row);
        });
    });
    rows.addView(row, new LinearLayout.LayoutParams(-1, -2));
  }
  static boolean apply(
      Activity a, MobileSettings.Runtime runtime, Map<String, String> values, boolean restart) {
    if (values.isEmpty())
      return true;
    Map<String, String> before = MobileConfig.read(a), old = new LinkedHashMap<>();
    for (String key : values.keySet()) {
      String fallback = GraphicsProfiles.preset(1).getOrDefault(key, "0");
      fallback = plain(fallback);
      if (key.equals("fh1_render_scale_percent"))
        fallback = "100";
      if (key.equals("pinyon_shift_hor_plus") || key.equals("present_allow_overscan_cutoff"))
        fallback = "false";
      if (key.equals("present_letterbox"))
        fallback = "true";
      if (key.equals("pinyon_shift_cheats") || key.equals("cheat_free_camera")
          || key.equals("cheat_show_collectibles") || key.equals("cheat_any_car_events"))
        fallback = "false";
      if (key.equals("cheat_time_scale") || key.equals("fh4droid_pad_outer")
          || key.equals("fh4droid_pad_curve") || key.equals("fh4droid_trigger_outer"))
        fallback = "1";
      if (key.equals("cheat_set_credits") || key.equals("cheat_time_of_day"))
        fallback = "-1";
      if (key.equals("android_gpu_turbo"))
        fallback = "false";
      if (key.equals("android_gpu_driver_no_preamble"))
        fallback = "true";
      if (key.equals("android_gpu_driver_env"))
        fallback = "";
      if (key.equals("cheat_set_profile_fields"))
        fallback = "";
      boolean quoted = values.get(key).startsWith("\"");
      for (MobileSettings.Option o : OPTIONS)
        if (o.key.equals(key))
          fallback = o.initial;
      String prior = before.getOrDefault(key, fallback);
      old.put(key, quoted ? MobileConfig.quote(prior) : prior);
    }
    try {
      MobileConfig.putAll(a, values);
      for (Map.Entry<String, String> e : values.entrySet()) {
        MobileConfig.recordChange(a, e.getKey(), e.getValue(), runtime != null && !restart);
        if (runtime != null && !restart)
          runtime.changed(e.getKey(), plain(e.getValue()));
      }
      UNDO.put(a, new Change(old, restart));
      if (restart)
        Toast.makeText(a, "Saved for the next game launch", Toast.LENGTH_SHORT).show();
      return true;
    } catch (IOException e) {
      Toast.makeText(a, "Could not save: " + e.getMessage(), Toast.LENGTH_LONG).show();
      return false;
    }
  }
  private static void undo(Activity a, MobileSettings.Runtime runtime) {
    Change c = UNDO.remove(a);
    if (c == null) {
      Toast.makeText(a, "No settings change to undo", Toast.LENGTH_SHORT).show();
      return;
    }
    if(apply(a, runtime, c.values, c.restart)){
      UNDO.remove(a);
      Toast.makeText(a,"Previous values restored",Toast.LENGTH_SHORT).show();
    }else UNDO.put(a,c);
  }
  private static int integer(String value, int fallback) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
  private static void extras(
      Activity a, LinearLayout rows, String page, MobileSettings.Runtime runtime, Runnable editor) {
    if (page.equals("Graphics")) {
      SeekBar resolution = FestivalUi.slider(a, rows, "Render resolution · next launch",
          "Lower values reduce GPU work. Restart avoids rebuilding game "
              + "render resources "
              + "mid-frame.",
          25, 100, 1, integer(value(a, "fh1_render_scale_percent", "100"), 100),
          i
          -> i + "% · " + ((1280 * i + 99) / 100) + " × " + ((720 * i + 99) / 100),
          i
          -> apply(a, runtime,
              Collections.singletonMap("fh1_render_scale_percent", Integer.toString(i)), true));
      FestivalUi.onRefresh(resolution,
          ()
              -> resolution.setProgress(
                  Math.max(
                      25, Math.min(100, integer(value(a, "fh1_render_scale_percent", "100"), 100)))
                  - 25));
      Button aa = FestivalUi.action(a, rows, "", () -> {});
      FestivalUi.onRefresh(aa,
          ()
              -> aa.setText("MSAA · next launch: "
                  + (value(a, "fh1_msaa_single_sample", "true").equals("true") ? "Off"
                          : value(a, "fh1_msaa_2x", "false").equals("true")    ? "2×"
                                                                               : "4×")));
      aa.setOnClickListener(v
          -> FestivalUi.choose(a, "MSAA", new String[] {"Off", "2×", "4×"},
              value(a, "fh1_msaa_single_sample", "true").equals("true") ? 0
                  : value(a, "fh1_msaa_2x", "false").equals("true")     ? 1
                                                                        : 2,
              index -> {
                Map<String, String> values = new LinkedHashMap<>();
                values.put("fh1_msaa_single_sample", index == 0 ? "true" : "false");
                values.put("fh1_msaa_2x", index == 1 ? "true" : "false");
                apply(a, runtime, values, true);
                FestivalUi.refreshTree(rows);
              }));
      FestivalUi.action(a, rows, "Graphics presets", () -> presets(a, runtime));
    }
    if (page.equals("Display")) {
      Button screen = FestivalUi.action(a, rows, "", () -> {});
      FestivalUi.onRefresh(screen,
          ()
              -> screen.setText("Screen: "
                  + GraphicsProfiles
                        .SCREEN_MODES[GraphicsProfiles.screenModeIndex(MobileConfig.read(a))]));
      screen.setOnClickListener(v
          -> FestivalUi.choose(a, "Screen layout · next launch", GraphicsProfiles.SCREEN_MODES,
              GraphicsProfiles.screenModeIndex(MobileConfig.read(a)), i -> {
                apply(a, runtime, GraphicsProfiles.screenMode(i), true);
                FestivalUi.refreshTree(rows);
              }));
      Button fsr = FestivalUi.action(a, rows, "", () -> {});
      FestivalUi.onRefresh(fsr,
          ()
              -> fsr.setText("FSR 1: "
                  + (value(a, "present_effect", "bilinear").equals("fsr")
                          ? value(a, "fh1_render_scale_percent", "100") + "% render resolution"
                          : "Off")));
      fsr.setOnClickListener(v
          -> FestivalUi.choose(a, "FSR 1 quality · next launch",
              new String[] {"Native · 100%", "Ultra quality · 77%", "Quality · 67%",
                  "Balanced · 59%", "Performance · 50%"},
              value(a, "present_effect", "bilinear").equals("fsr")
                  ? Arrays.asList("100", "77", "67", "59", "50")
                        .indexOf(value(a, "fh1_render_scale_percent", "100"))
                  : -1,
              i -> {
                Map<String, String> values = new LinkedHashMap<>();
                values.put("present_effect", MobileConfig.quote("fsr"));
                values.put(
                    "fh1_render_scale_percent", new String[] {"100", "77", "67", "59", "50"}[i]);
                values.put("present_fsr_sharpness_reduction", ".5");
                apply(a, runtime, values, true);
                FestivalUi.refreshTree(rows);
              }));
      FestivalUi.slider(a, rows, "Menu size", "Reopen this page to apply size changes.", 70, 105, 1,
          Math.round(FestivalUi.scale(a) * 100), i -> i + "%", i -> {
            a.getSharedPreferences("mobile", 0).edit().putFloat("ui_scale", i / 100f).apply();
            UiPreferences.save(a);
          });
      pref(a, rows, "Menu animations", "animations", true);
      pref(a, rows, "Show FPS", "show_fps", false);
      pref(a, rows, "Match display refresh", "match_refresh", true);
    }
    if (page.equals("Touch")) {
      TouchLayout layout = new TouchLayout();
      layout.load(a);
      Button style = FestivalUi.action(a, rows, "", () -> {});
      FestivalUi.onRefresh(style, () -> {
        TouchLayout current = new TouchLayout();
        current.load(a);
        style.setText("Layout: " + (current.drivingLabels ? "Mobile driving" : "Xbox buttons"));
      });
      style.setOnClickListener(v -> {
        TouchLayout current = new TouchLayout();
        current.load(a);
        FestivalUi.choose(a, "Touch layout", new String[] {"Xbox buttons", "Mobile driving"},
            current.drivingLabels ? 1 : 0, i -> {
              try {
                TouchLayout.selectStyle(a, i == 1);
                reload(a);
                FestivalUi.refreshTree(rows);
              } catch (IOException e) {
                error(a, e);
              }
            });
      });
      FestivalUi.action(a, rows, "Edit touch layout", editor);
      Button steering=FestivalUi.action(a,rows,"",()->{});
      FestivalUi.onRefresh(steering,()->{TouchLayout current=new TouchLayout();current.load(a);steering.setText("Steering: "+(current.steering.equals("arrows")?"Arrows":"Stick"));});
      steering.setOnClickListener(v->{TouchLayout current=new TouchLayout();current.load(a);
        FestivalUi.choose(a,"Mobile steering",new String[]{"Arrows","Stick"},current.steering.equals("arrows")?0:1,i->{
          try{current.steering=i==0?"arrows":"stick";current.save(a);reload(a);FestivalUi.refreshTree(rows);}
          catch(IOException error){error(a,error);}
        });});
      FestivalUi.toggle(a, rows, "Xbox button hints",
          "Small button names beneath mobile driving icons. "
              + "Action icons assume the game's default bindings.",
          layout.buttonHints, on -> {
            try {
              TouchLayout current = new TouchLayout();
              current.load(a);
              current.buttonHints = on;
              current.save(a);
              reload(a);
            } catch (IOException e) {
              error(a, e);
            }
          });
      FestivalUi.action(a, rows, "Saved layouts and templates", () -> LayoutProfiles.show(a));
      pref(a, rows, "Phone vibration", "phone_rumble", true);
      FestivalUi.slider(a, rows, "Phone vibration strength",
          "Intensity depends on the phone's vibration motor.", 0, 100, 1,
          a.getSharedPreferences("mobile", 0).getInt("phone_rumble_strength", 60),
          i -> i + "%", i -> {
            a.getSharedPreferences("mobile", 0).edit().putInt("phone_rumble_strength", i).apply();
            UiPreferences.save(a);
          });
      FestivalUi.action(a, rows, "Test phone vibration", () -> ControllerTools.testRumble(a));
      pref(a, rows, "Phone tilt steering", "phone_tilt", false);
      FestivalUi.slider(a, rows, "Tilt centre deadzone",
          "Ignore small movements around the calibrated centre.", 0, 15, 1,
          a.getSharedPreferences("mobile", 0).getInt("tilt_deadzone", 3), i -> i + "%", i -> {
            a.getSharedPreferences("mobile", 0).edit().putInt("tilt_deadzone", i).apply();
            UiPreferences.save(a);
          });
      FestivalUi.slider(a, rows, "Tilt steering range", "Tilt this far for full steering.", 15, 75,
          1, a.getSharedPreferences("mobile", 0).getInt("tilt_range", 35), i -> i + "°", i -> {
            a.getSharedPreferences("mobile", 0).edit().putInt("tilt_range", i).apply();
            UiPreferences.save(a);
          });
      FestivalUi.action(a, rows, "Calibrate tilt centre", () -> {
        if (a instanceof PinyonShiftActivity)
          ((PinyonShiftActivity) a).calibrateTilt();
        else
          FestivalUi.explain(a, "Tilt calibration",
              "Open this page while playing and hold the "
                  + "phone in your driving position.");
      });
    }
    if (page.equals("Controller") && a instanceof PinyonShiftActivity
        && a.getSharedPreferences("mobile", 0).getBoolean("advanced_settings", false))
      FestivalUi.action(a, rows, "Native controller remapping",
          () -> ((PinyonShiftActivity) a).openNativeSettings());
    if (page.equals("Controller"))
      FestivalUi.action(
          a, rows, "Test and calibrate controller", () -> ControllerTools.show(a, runtime));
    if (page.equals("Performance")) {
      if (a.getSharedPreferences("mobile", 0).getBoolean("advanced_settings", false))
        FestivalUi.action(a, rows, "Adreno options", () -> AdrenoSettings.show(a, runtime));
      FestivalUi.action(a, rows, "Graphics presets", () -> presets(a, runtime));
      pref(a, rows, "Sustained performance mode", "sustained_mode", false);
      if (a.getSharedPreferences("mobile", 0).getBoolean("advanced_settings", false))
        pref(a, rows, "Detailed GPU timings · next launch", "detailed_gpu", false);
      rows.addView(FestivalUi.text(a,
          "Sustained mode lowers the frame target "
              + "under severe heat, then restores it after "
              + "cooling. It keeps your saved preset.",
          12, FestivalUi.MUTED));
      FestivalUi.action(a, rows, "Graphics cache", () -> GraphicsCache.show(a));
      FestivalUi.action(
          a, rows, "Record a performance comparison", () -> ProblemReports.capture(a));
    }
    if (page.equals("Gameplay"))
      FestivalUi.action(a, rows, "Cheats · separate profile", () -> TrainerPage.show(a, runtime));
  }
  static void pref(Activity a, LinearLayout rows, String title, String key, boolean fallback) {
    FestivalUi.toggle(
        a, rows, title, "", a.getSharedPreferences("mobile", 0).getBoolean(key, fallback), on -> {
          a.getSharedPreferences("mobile", 0).edit().putBoolean(key, on).apply();
          UiPreferences.save(a);
          if (a instanceof PinyonShiftActivity)
            ((PinyonShiftActivity) a).refreshMobilePreferences();
        });
  }
  static void reload(Activity a) {
    if (a instanceof PinyonShiftActivity)
      ((PinyonShiftActivity) a).reloadControls();
  }
  static void error(Activity a, Exception e) {
    Toast.makeText(a, "Could not complete: " + e.getMessage(), Toast.LENGTH_LONG).show();
  }
  private static void presets(Activity a, MobileSettings.Runtime runtime) {
    FestivalUi.choose(a, "Graphics presets", GraphicsProfiles.NAMES, -1,
        i -> apply(a, runtime, GraphicsProfiles.preset(i), true));
  }
  private SettingsHub() {}
}
