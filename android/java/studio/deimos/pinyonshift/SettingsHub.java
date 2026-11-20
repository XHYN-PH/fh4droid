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
  static final String[][] CATEGORY_CARDS = {
      {"graphics", "sliders", "Graphics", "Resolution, quality & FPS"},
      {"audio", "audio", "Audio", "Volume & music"},
      {"control-settings", "touch-steering", "Controls", "Touch, tilt & gamepad"},
      {"interface", "grid", "Interface", "Display & readability"},
      {"gameplay", "flag", "Gameplay", "Camera & preferences"},
      {"advanced", "chip", "Advanced", "Drivers & diagnostics"}
  };
  private static final String SEARCH = "All settings";
  private static final String RESOLUTION_HELP =
      "Lower resolution reduces GPU work and loses some detail. The upscaling filter controls "
          + "display smoothing. Applies next launch. Use left or right to adjust in 1% steps (25–100%).";
  private static final Set<String> ADVANCED =
      new HashSet<>(Arrays.asList("swap_post_effect_scene_only", "texture_mip_lod_bias",
          "fh1_untile_predicated_tiling", "vulkan_pipeline_creation_threads",
          "host_present_fps_limit", "host_present_sleep_spin",
          "vulkan_present_wait", "present_dither",
          "present_cas_additional_sharpness", "present_fsr_sharpness_reduction"));
  private static final Map<Activity, Change> UNDO = new WeakHashMap<>();
  private static final List<MobileSettings.Option> OPTIONS =
      new ArrayList<>(Arrays.asList(MobileSettings.OPTIONS));
  static {
    OPTIONS.add(
        new MobileSettings.Option("Display", "pinyon_shift_fov_scale", "Camera field of view", "1",
            false, false, "Wider values show more of the scene and can add graphics work.", ".8",
            ".9", "1", "1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.8", "1.9", "2"));
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
    LinearLayout categories = FestivalUi.column(a);
    for (int i = 0; i < CATEGORY_CARDS.length; i += 2) {
      String[] left = CATEGORY_CARDS[i], right = CATEGORY_CARDS[i + 1];
      FestivalUi.tileRow(a, categories,
          FestivalUi.tile(a, left[1], left[2], left[3], () -> page(a, left[2], runtime, editor)),
          FestivalUi.tile(a, right[1], right[2], right[3], () -> page(a, right[2], runtime, editor)));
    }
    FestivalUi.present(a, "Settings", categories);
  }
  static void page(Activity a, String section, MobileSettings.Runtime runtime, Runnable editor) {
    FestivalUi.present(a, pageTitle(section), buildPage(a, section, runtime, editor));
  }
  private static String pageTitle(String section) {
    switch (section) {
      case SEARCH: return "Search & favourites";
      case "Touch": return "Touch controls";
      case "Phone": return "Phone steering";
      default: return section;
    }
  }
  private static String category(MobileSettings.Option o){
    if(ADVANCED.contains(o.key)||o.page.equals("Performance"))return "Advanced";
    if(o.page.equals("Controls"))return "Controls";
    if(o.page.equals("Display"))return o.key.startsWith("fh1_hud")||o.key.equals("hud_hidden")?"Interface":"Graphics";
    return o.page;
  }
  private static boolean belongs(String page,MobileSettings.Option o){
    if(page.equals(SEARCH))return true;
    if(page.equals("Touch"))return o.key.equals("pinyon_shift_touch_controls");
    if(page.equals("Controller"))return o.page.equals("Controls")&&!o.key.equals("pinyon_shift_touch_controls")&&!ADVANCED.contains(o.key);
    return page.equals(category(o));
  }
  private static LinearLayout buildPage(Activity a,String section,MobileSettings.Runtime runtime,Runnable editor){
    LinearLayout root=FestivalUi.column(a);root.setTag(FestivalUi.FULL_PAGE,Boolean.TRUE);root.setClipChildren(true);
    LinearLayout content=new LinearLayout(a);content.setClipChildren(true);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
    ScrollView options = new ScrollView(a);
    options.setTag("settings-scroll");
    options.setFocusable(false);
    options.setClipChildren(true);
    content.addView(options, new LinearLayout.LayoutParams(0, -1, 2));
    FestivalUi.attachPageScroll(root, options);
    LinearLayout details=FestivalUi.column(a);details.setTag("details-panel");int inset=FestivalUi.dp(a,14);details.setPadding(inset,FestivalUi.dp(a,12),inset,FestivalUi.dp(a,10));details.setBackground(FestivalUi.surface(a,FestivalUi.GLASS,8));
    TextView heading=FestivalUi.text(a,"SETTING DETAILS",10,FestivalUi.RED);details.addView(heading);
    TextView detailTitle=FestivalUi.text(a,"Choose an option",16,FestivalUi.TEXT);detailTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);detailTitle.setPadding(0,FestivalUi.dp(a,8),0,FestivalUi.dp(a,8));details.addView(detailTitle);
    ScrollView helpScroll=new ScrollView(a);details.addView(helpScroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout helpBody=FestivalUi.column(a);helpScroll.addView(helpBody);
    TextView detailHelp=FestivalUi.text(a,"Select a setting to read its explanation.",12,FestivalUi.MUTED);helpBody.addView(detailHelp);
    TextView detailValue=FestivalUi.text(a,"",11,FestivalUi.TEXT);detailValue.setPadding(0,FestivalUi.dp(a,8),0,0);helpBody.addView(detailValue);
    Button more=FestivalUi.button(a,"More details",false);more.setTextSize(11);more.setMinHeight(0);more.setMinimumHeight(0);details.addView(more,new LinearLayout.LayoutParams(-1,FestivalUi.dp(a,32)));more.setOnClickListener(v->FestivalUi.explain(a,detailTitle.getText().toString(),detailHelp.getText()+"\n\n"+detailValue.getText()));
    LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,-1,FestivalUi.wideLayout(a)?1:1.25f);dp.leftMargin=FestivalUi.dp(a,8);content.addView(details,dp);
    java.util.function.Consumer<MobileSettings.Option> inspect = o -> {
      detailTitle.setText(o.title);
      String reason = SettingAvailability.reason(a, o.key);
      String timing = o.restart ? "Applies at the next launch." : "Applies during play.";
      detailHelp.setText(o.help + "\n\n" + (reason.isEmpty() ? timing : reason));
      String saved = value(a, o.key, o.initial), live = active(runtime, o.key);
      String actual = live == null ? null : MobileSettings.label(o, live);
      detailValue.setText(settingState(runtime, o.restart, actual, MobileSettings.label(o, saved),
          live != null && !SettingValues.same(live, saved)));
    };
    Runnable resolutionDetails = () -> {
      detailTitle.setText("Render resolution");
      detailHelp.setText(RESOLUTION_HELP);
      String saved = value(a, "fh1_render_scale_percent", "100");
      String live = active(runtime, "fh1_render_scale_percent");
      detailValue.setText(settingState(runtime, true, live == null ? null : SettingValues.plain(live) + "%",
          saved + "%", live != null && !SettingValues.same(live,saved)));
    };
    LinearLayout column;
    if (section.equals("Controls")) {
      column = FestivalUi.column(a);
      FestivalUi.settingsLink(a,column,"layout-edit","Edit touch layout","Move, resize or add on-screen buttons.",editor);
      FestivalUi.settingsLink(a,column,"touch-steering","Touch buttons and steering","Choose arrows or stick steering, text labels and shift buttons.",()->page(a,"Touch",runtime,editor));
      FestivalUi.settingsLink(a,column,"phone-motion","Phone tilt and vibration","Adjust motion steering independently of touch and controller settings.",()->page(a,"Phone",runtime,editor));
      FestivalUi.settingsLink(a,column,"controller","Physical controller","Test a connected gamepad and adjust rumble, gyro, stick and trigger response.",()->page(a,"Controller",runtime,editor));
      FestivalUi.settingsLink(a,column,"layout-library","Saved layouts and presets","Save your current layout or apply a template.",()->LayoutProfiles.show(a));
    } else {
      column = createPage(a, section, runtime, editor, inspect, resolutionDetails,
          rows->bindDetails(rows,detailTitle,detailHelp,detailValue));
    }
    options.addView(column);
    bindDetails(column, detailTitle, detailHelp, detailValue);
    if (section.equals("Graphics")) resolutionDetails.run();

    LinearLayout header = new LinearLayout(a);
    root.setTag(FestivalUi.HEADER_ACTIONS, header);
    if (!section.equals(SEARCH)) {
      Button search = headerButton(a, header, "Search");
      search.setOnClickListener(v -> page(a, SEARCH, runtime, editor));
    }
    Button reset = headerButton(a, header, "Reset / undo");
    reset.setOnClickListener(v -> {
      PopupMenu popup = new PopupMenu(a, reset);
      popup.getMenu().add(section.equals(SEARCH) ? "Reset all settings" : "Reset this section")
          .setOnMenuItemClickListener(i -> {
            resetSection(a, section, runtime, () -> FestivalUi.refreshTree(root));
            return true;
          });
      popup.getMenu().add("Undo last change").setOnMenuItemClickListener(i -> {
        undo(a, runtime);
        FestivalUi.refreshTree(root);
        return true;
      });
      popup.show();
    });
    if (a instanceof FestivalUi.PanelHost && !section.equals(SEARCH)) {
      List<String> inputPages = Arrays.asList("Touch", "Phone", "Controller");
      int inputIndex = inputPages.indexOf(section);
      if (inputIndex >= 0) {
        ControllerPrompts.tabs(root, direction -> {
          String next = inputPages.get(Math.floorMod(inputIndex + direction, inputPages.size()));
          ((FestivalUi.PanelHost)a).replacePanel(pageTitle(next),buildPage(a,next,runtime,editor));
        });
        return root;
      }
      for (int i = 0; i < CATEGORY_CARDS.length; i++) {
        if (!CATEGORY_CARDS[i][2].equals(section)) continue;
        int current = i;
        ControllerPrompts.tabs(root, direction -> {
          String next = CATEGORY_CARDS[Math.floorMod(current + direction, CATEGORY_CARDS.length)][2];
          ((FestivalUi.PanelHost) a).replacePanel(pageTitle(next), buildPage(a, next, runtime, editor));
        });
        break;
      }
    }
    return root;
  }
  private static Button headerButton(Activity a, LinearLayout header, String title) {
    Button button = FestivalUi.button(a, title, false);
    button.setTextSize(11);
    button.setMinHeight(0);
    button.setMinimumHeight(0);
    LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-2, FestivalUi.dp(a, 32));
    if (header.getChildCount() > 0) size.leftMargin = FestivalUi.dp(a, 8);
    header.addView(button, size);
    return button;
  }
  private static void bindDetails(View view,TextView title,TextView help,TextView value){
    if (view.isFocusable() && view.getOnFocusChangeListener() == null) {
      Runnable inspect = () -> {
        View v = view;
        Object labelled = v.getTag(FestivalUi.SETTING_TITLE);
        CharSequence caption = labelled instanceof String ? (String) labelled :
            v instanceof TextView ? ((TextView)v).getText() : v.getContentDescription();
        if (caption == null || caption.length() == 0) caption = v.getContentDescription();
        if (caption == null) return;
        title.setText(caption);
        Object description = v.getTag(FestivalUi.SETTING_HELP);
        help.setText(description instanceof String && !((String)description).isEmpty()
            ? (String)description : toolHelp(caption.toString()));
        value.setText("");
      };
      if (view instanceof SettingRow) ((SettingRow)view).inspection = inspect;
      view.setOnFocusChangeListener((v,on) -> { if(on)inspect.run(); });
    }
    if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)bindDetails(group.getChildAt(i),title,help,value);}
  }
  private static String settingState(MobileSettings.Runtime runtime, boolean restart, String live,
      String saved, boolean changed) {
    if (live == null) return (restart && runtime == null ? "Next launch   " : "Saved   ") + saved;
    if (changed) return "Running   " + live + "\n" + (restart ? "Next launch   " : "Saved   ") + saved;
    return "Running   " + live;
  }
  private static String toolHelp(String title){
    String[][] descriptions={
      {"Edit touch layout","Move and resize touch controls in the full-screen editor. Save to keep your changes."},
      {"Touch buttons","Choose arrows or a steering stick, adjust appearance and tune touch response."},
      {"Phone tilt","Use the phone sensors for steering. This is independent of the arrow layout; disable it for arrows-only steering."},
      {"Invert phone tilt","Reverse phone steering direction. Applies immediately to phone input and preserves controller axes."},
      {"Tilt steering range","Tilt this far from the calibrated centre for full steering. A larger range gives gentler response."},
      {"Tilt centre deadzone","Ignore small phone movements near the centre to reduce accidental steering."},
      {"Calibrate tilt","Hold the phone in your normal driving position while the game is running, then select this action."},
      {"Phone vibration","Control vibration on the phone; this does not change the controller's vibration setting."},
      {"Physical controller","Test inputs, set deadzones and adjust the response of a connected gamepad."},
      {"MSAA","Smooths geometry edges. Higher values cost graphics memory and GPU work. Applies next launch."},
      {"Screen","Choose original proportions, wider gameplay or stretch. Game menus can retain their original proportions. Applies next launch."},
      {"FSR 1","Choose an upscaling preset and corresponding render resolution. Applies next launch."},
      {"Graphics presets","Review a group of graphics changes before applying them."},
      {"My graphics profiles","Save or restore your own named sets of graphics options."},
      {"Graphics drivers","Choose and test the Vulkan driver used at the next launch. On Adreno 730, Automatic uses the system driver."},
      {"Saved layouts","Manage saved touch layouts and restore templates."},
      {"Menu size","Adjust the size of launcher controls. Reopen the page to apply the new size."},
      {"Menu animations","Animate focus and page transitions. Android's reduced-motion setting is respected."},
      {"Use my music player","Turn off game music at the next launch while keeping engine and effects."},
      {"Show FPS","Show the game's frame rate in a small overlay while playing."},
      {"Show latest frame time","Display the latest frame time alongside FPS to help spot inconsistent delivery."},
      {"Show RAM","Display memory use and Android thermal severity while playing."},
      {"CPU performance hints","Optional Android scheduling hints for the game thread. Compare a repeatable route before enabling them for regular play."},
      {"Record standard session logs","Save startup and runtime logs during normal Play. Use Play with diagnostics for per-frame captures."},
      {"Adreno options","Advanced Turnip settings. Change one at a time and compare the same route; a faster driver on another GPU may be slower on yours."},
      {"Graphics cache","Manage recognized graphics caches while the game is closed. Caches may need preparation again after removal."},
      {"Record a performance","Record a diagnostic run for a problem report. Capture adds overhead."}
    };for(String[] pair:descriptions)if(title.startsWith(pair[0]))return pair[1];return "Open "+title+" to view and adjust its options.";
  }
  private static void resetSection(Activity a,String page,MobileSettings.Runtime runtime,Runnable refresh){
    new AlertDialog.Builder(a).setTitle("Reset "+page+"?").setMessage("Restore game settings in this section. Phone options, layouts, drivers and saves are kept.").setNegativeButton("Cancel",null).setPositiveButton("Reset",(d,w)->{
      Map<String,String> defaults=new LinkedHashMap<>();for(MobileSettings.Option o:OPTIONS)if(belongs(page,o))defaults.put(o.key,literal(o,o.initial));
      if(page.equals("Graphics")||page.equals("All settings")){defaults.put("fh1_render_scale_percent","100");defaults.put("fh1_msaa_single_sample","true");defaults.put("fh1_msaa_2x","false");defaults.putAll(GraphicsProfiles.screenMode(0));}
      if(page.equals("Controls")||page.equals("Controller")||page.equals("All settings")){defaults.put("fh4droid_pad_inner","0");defaults.put("fh4droid_pad_outer","1");defaults.put("fh4droid_pad_curve","1");defaults.put("fh4droid_trigger_inner","0");defaults.put("fh4droid_trigger_outer","1");}
      apply(a,runtime,defaults,true);refresh.run();
    }).show();
  }
  private static LinearLayout createPage(
      Activity a, String page, MobileSettings.Runtime runtime, Runnable editor,
      java.util.function.Consumer<MobileSettings.Option> inspect, Runnable resolutionDetails,
      java.util.function.Consumer<View> bind) {
    LinearLayout body = FestivalUi.column(a);
    EditText search = new EditText(a);
    search.setSingleLine(true);
    search.setTextColor(FestivalUi.TEXT);
    search.setHintTextColor(FestivalUi.MUTED);
    search.setTextSize(13 * FestivalUi.scale(a));
    search.setHint("Search " + page.toLowerCase(Locale.ROOT));
    search.setMinHeight(FestivalUi.dp(a,44));
    if(page.equals("All settings"))body.addView(search, new LinearLayout.LayoutParams(-1, -2));
    if (page.equals("Graphics")) {
      buildGraphics(a, body, runtime, editor, inspect, resolutionDetails);
      return body;
    }
    boolean[] advanced = {
        page.equals("Advanced")||page.equals("All settings")&&a.getSharedPreferences("mobile",0).getBoolean("advanced_settings",false)};
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
    if(page.equals("All settings"))body.addView(flags);
    LinearLayout rows = FestivalUi.column(a);
    body.addView(rows);
    Runnable rebuild = () -> {
      rows.removeAllViews();
      String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
      for (MobileSettings.Option option : OPTIONS) {
        if(!belongs(page,option))continue;
        if (!advanced[0] && !pinned[0] && query.isEmpty() && ADVANCED.contains(option.key))
          continue;
        if (!query.isEmpty()
            && !(option.title + " " + option.help).toLowerCase(Locale.ROOT).contains(query))
          continue;
        if (pinned[0] && !isPinned(a, option.key))
          continue;
        optionRow(a, rows, option, runtime, inspect);
      }
      if (!pinned[0]) {
        for (String category : page.equals("All settings") ? new String[]{"Graphics","Audio","Touch","Phone","Controller","Interface","Gameplay","Advanced"} : new String[] {page}) {
          LinearLayout extra = FestivalUi.column(a);
          extras(a, extra, category, runtime, editor,resolutionDetails);
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
      bind.accept(rows);
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
  private static final Set<String> DISPLAY_SETTINGS = new LinkedHashSet<>(Arrays.asList(
      "pinyon_shift_fh1_render_fps_limit", "present_effect", "vsync"));
  private static final Set<String> RENDERER_SETTINGS = new HashSet<>(Arrays.asList(
      "vulkan_fh1_native_executor", "vulkan_dynamic_rendering", "vulkan_require_vertex_pipeline_stores_and_atomics"));

  private static void buildGraphics(Activity a, LinearLayout body, MobileSettings.Runtime runtime,
      Runnable editor, java.util.function.Consumer<MobileSettings.Option> inspect, Runnable resolutionDetails) {
    LinearLayout actions = FestivalUi.column(a);
    extras(a, actions, "Graphics", runtime, editor, resolutionDetails);
    heading(a, body, "Display");
    move(actions, body, "fh1_render_scale_percent");
    for (String key : DISPLAY_SETTINGS)
      for (MobileSettings.Option option : OPTIONS)
        if (option.key.equals(key)) optionRow(a,body,option,runtime,inspect);
    move(actions, body, "graphics-screen");
    move(actions, body, "graphics-fsr");
    heading(a, body, "Quality");
    move(actions, body, "graphics-msaa");
    for (MobileSettings.Option option : OPTIONS)
      if (belongs("Graphics",option) && !DISPLAY_SETTINGS.contains(option.key)
          && !RENDERER_SETTINGS.contains(option.key)) optionRow(a,body,option,runtime,inspect);
    heading(a, body, "Renderer");
    for (MobileSettings.Option option : OPTIONS)
      if (RENDERER_SETTINGS.contains(option.key)) optionRow(a,body,option,runtime,inspect);
    heading(a, body, "Presets");
    while (actions.getChildCount() > 0) {
      View child = actions.getChildAt(0);
      actions.removeViewAt(0);
      body.addView(child);
    }
  }
  private static void move(LinearLayout source, LinearLayout destination, String key) {
    View view = source.findViewWithTag(key);
    if (view == null) return;
    source.removeView(view);
    destination.addView(view);
  }
  private static void heading(Activity a, LinearLayout body, String title) {
    TextView heading = FestivalUi.text(a,title,11,FestivalUi.MUTED);
    heading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    heading.setPadding(FestivalUi.dp(a,10),FestivalUi.dp(a,7),0,FestivalUi.dp(a,6));
    heading.setAccessibilityHeading(true);
    body.addView(heading);
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
    if(o.string)return MobileConfig.quote(value);
    try{return new java.math.BigDecimal(value).stripTrailingZeros().toPlainString();}catch(NumberFormatException ignored){return value;}
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
  private static int choiceIndex(MobileSettings.Option option,String value){
    for(int i=0;i<option.values.length;i++)if(SettingValues.same(value,option.values[i]))return i;
    return -1;
  }
  private static void optionRow(
      Activity a, LinearLayout rows, MobileSettings.Option o, MobileSettings.Runtime runtime,
      java.util.function.Consumer<MobileSettings.Option> inspect) {
    SettingRow row=new SettingRow(a,o.title,ControlGlyphs.setting(o.title));row.setTag(o.key);
    row.setTag(FestivalUi.FOCUS_KEY, o.key);
    java.util.function.IntConsumer change=direction->{
      if (!SettingAvailability.reason(a,o.key).isEmpty()) return;
      int selected=choiceIndex(o,value(a,o.key,o.initial));
      int next=selected<0 ? (direction<0 ? o.values.length-1 : 0)
          : Math.max(0,Math.min(o.values.length-1,selected+direction));
      if(next!=selected){
        apply(a,runtime,Collections.singletonMap(o.key,literal(o,o.values[next])),o.restart,false);
        FestivalUi.refreshTree(row);
      }
      if(inspect!=null)inspect.accept(o);
    };
    FestivalUi.onRefresh(row,()->{
      String saved=value(a,o.key,o.initial),reason=SettingAvailability.reason(a,o.key);
      row.label.setText((isPinned(a,o.key)?"• ":"")+AppLanguage.text(a,o.title));
      row.value.setText(MobileSettings.label(o,saved));
      row.value.setTextColor(reason.isEmpty()?FestivalUi.TEXT:FestivalUi.MUTED);
      row.arrowsEnabled(reason.isEmpty());
      row.setContentDescription(o.title+", "+MobileSettings.label(o,saved)+(reason.isEmpty()?"":". "+reason));
      if (row.hasFocus() && inspect != null) inspect.accept(o);
    });
    row.setOnFocusChangeListener((v,on)->{if(on&&inspect!=null)inspect.accept(o);});
    row.adjust(change, () -> FestivalUi.explain(a,o.title,o.help+(o.restart?" Applies next launch.":"")));
    row.setOnLongClickListener(v->{boolean pin=!isPinned(a,o.key);a.getSharedPreferences("setting_pins",0).edit().putBoolean(o.key,pin).apply();UiPreferences.save(a);FestivalUi.refreshTree(row);return true;});
    row.setOnClickListener(v->{
      if(inspect!=null)inspect.accept(o);
      String reason = SettingAvailability.reason(a,o.key);
      if (!reason.isEmpty()) { FestivalUi.explain(a,o.title,reason); return; }
      String saved=value(a,o.key,o.initial);String[] labels=new String[o.values.length];for(int i=0;i<labels.length;i++)labels[i]=MobileSettings.label(o,o.values[i]);FestivalUi.choose(a,o.title,labels,choiceIndex(o,saved),index->{apply(a,runtime,Collections.singletonMap(o.key,literal(o,o.values[index])),o.restart);FestivalUi.refreshTree(row);if(inspect!=null)inspect.accept(o);});
    });
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=FestivalUi.dp(a,5);rows.addView(row,params);
  }
  private static final class SettingRow extends LinearLayout {
    final TextView label,value,left,right;
    Runnable inspection;
    SettingRow(Activity a,String title,String icon) {
      super(a);
      setGravity(Gravity.CENTER_VERTICAL);
      setFocusableInTouchMode(true);
      setClickable(true);
      setMinimumHeight(FestivalUi.dp(a,48));
      FestivalUi.glassBackground(a,this,false,8);
      setPadding(FestivalUi.dp(a,10),0,FestivalUi.dp(a,4),0);
      if (icon != null) {
        LayoutParams size = new LayoutParams(FestivalUi.dp(a,24), FestivalUi.dp(a,24));
        size.rightMargin = FestivalUi.dp(a,8);
        addView(FestivalUi.icon(a,icon,FestivalUi.TEXT),size);
      }
      label=FestivalUi.text(a,title,13,FestivalUi.TEXT);
      label.setMaxLines(2);
      label.setEllipsize(TextUtils.TruncateAt.END);
      addView(label,new LayoutParams(0,-2,1.6f));
      left = arrow(a,"‹","Decrease or previous value",title);
      value=FestivalUi.text(a,"",13,FestivalUi.TEXT);
      value.setMaxLines(2);
      value.setEllipsize(TextUtils.TruncateAt.END);
      value.setGravity(Gravity.CENTER);
      addView(value,new LayoutParams(0,-2,1));
      right = arrow(a,"›","Increase or next value",title);
    }
    private TextView arrow(Activity a,String glyph,String description,String title) {
      TextView arrow = FestivalUi.text(a,glyph,19,FestivalUi.TEXT);
      arrow.setGravity(Gravity.CENTER);
      arrow.setContentDescription(description+", "+title);
      arrow.setFocusable(false);
      arrow.setBackgroundResource(android.R.drawable.list_selector_background);
      addView(arrow,new LayoutParams(FestivalUi.dp(a,38),FestivalUi.dp(a,48)));
      return arrow;
    }
    void arrowsEnabled(boolean enabled) {
      left.setEnabled(enabled);
      right.setEnabled(enabled);
      left.setAlpha(enabled?1f:.4f);
      right.setAlpha(enabled?1f:.4f);
    }
    void adjust(java.util.function.IntConsumer step, Runnable help) {
      left.setOnClickListener(v -> { requestFocus(); step.accept(-1); });
      right.setOnClickListener(v -> { requestFocus(); step.accept(1); });
      setOnKeyListener((v,key,event) -> {
        if(key==KeyEvent.KEYCODE_BUTTON_Y){
          if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled())help.run();
          return true;
        }
        return ControllerPrompts.horizontalKey(event,step);
      });
      ControllerPrompts.horizontalStick(this,step);
    }
  }
  static boolean apply(
      Activity a, MobileSettings.Runtime runtime, Map<String, String> values, boolean restart) {
    return apply(a,runtime,values,restart,true);
  }
  private static boolean apply(Activity a, MobileSettings.Runtime runtime, Map<String,String> values,
      boolean restart, boolean announce) {
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
      boolean log=a instanceof PinyonShiftActivity ? ((PinyonShiftActivity)a).sessionLoggingEnabled()
          :a.getSharedPreferences("mobile",0).getBoolean("record_session_logs",false);
      for (Map.Entry<String, String> e : values.entrySet()) {
        if(log)MobileConfig.recordChange(a, e.getKey(), e.getValue(), runtime != null && !restart);
        if (runtime != null && !restart)
          runtime.changed(e.getKey(), plain(e.getValue()));
      }
      UNDO.put(a, new Change(old, restart));
      if (restart && announce)
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
  private static int msaaIndex(String single,String two) {
    return "true".equals(SettingValues.plain(single))?0:"true".equals(SettingValues.plain(two))?1:2;
  }
  private static String screenActive(MobileSettings.Runtime runtime) {
    if(runtime==null)return null;
    Map<String,String> values=new LinkedHashMap<>();
    for(String key:GraphicsProfiles.screenMode(0).keySet()){
      String setting=active(runtime,key);
      if(setting==null)return null;
      values.put(key,SettingValues.plain(setting));
    }
    return new String[]{"Original","Widescreen","Stretch"}[GraphicsProfiles.screenModeIndex(values)];
  }
  private static TouchLayout touchLayout(Activity a) {
    TouchLayout layout=new TouchLayout();
    layout.load(a);
    return layout;
  }
  private static void choiceRow(Activity a, LinearLayout rows, String key, String title, String icon,
      String help, String[] choices, java.util.function.IntSupplier selected,
      java.util.function.IntConsumer save, java.util.function.Supplier<String> live) {
    SettingRow row=new SettingRow(a,title,icon);
    row.setTag(key);
    row.setTag(FestivalUi.FOCUS_KEY,key);
    row.setTag(FestivalUi.SETTING_TITLE,title);
    row.setTag(FestivalUi.SETTING_HELP,help);
    LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);
    size.bottomMargin=FestivalUi.dp(a,5);
    rows.addView(row,size);
    java.util.function.IntConsumer apply=index->{
      if(index!=selected.getAsInt())save.accept(index);
      refreshOptions(row);
    };
    FestivalUi.onRefresh(row,()->{
      int index=selected.getAsInt();
      String value=index<0?(value(a,"present_effect","bilinear").equals("fsr")?"Custom":"Off"):choices[index];
      row.value.setText(value);
      String current=live.get();
      String status=current==null||current.equals(value)?"":"\n\nRunning  "+current+"\nNext launch  "+value;
      row.setTag(FestivalUi.SETTING_HELP,help+status);
      row.setContentDescription(title+", "+value+". Left or right to adjust.");
      if(row.hasFocus() && row.inspection!=null)row.inspection.run();
    });
    row.adjust(direction->{
      int index=selected.getAsInt();
      apply.accept(index<0 ? direction<0?choices.length-1:0 : Math.max(0,Math.min(choices.length-1,index+direction)));
    },()->FestivalUi.explain(a,title,(String)row.getTag(FestivalUi.SETTING_HELP)));
    row.setOnClickListener(v->FestivalUi.choose(a,title,choices,selected.getAsInt(),apply));
  }
  private static int integer(String value, int fallback) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
  private static void refreshOptions(View control){
    android.view.ViewParent parent=control.getParent();
    FestivalUi.refreshTree(parent instanceof View?(View)parent:control);
  }
  private static void extras(
      Activity a, LinearLayout rows, String page, MobileSettings.Runtime runtime, Runnable editor,
      Runnable resolutionDetails) {
    if (page.equals("Audio")) {
      FestivalUi.toggle(a, rows, "0.5.1-style audio · next launch",
          "Compare the original XAudio2 output and mixer with current audio. Applies after restarting the game.",
          () -> a.getSharedPreferences("mobile", 0).getBoolean("audio_compatibility", false),
          on -> {
            a.getSharedPreferences("mobile", 0).edit().putBoolean("audio_compatibility", on).apply();
            UiPreferences.save(a);
          });
    }
    if(page.equals("Audio")){
      Button music=FestivalUi.action(a,rows,"Use my music player",()->{});
      music.setOnClickListener(v->new AlertDialog.Builder(a).setTitle("Use external music?")
          .setMessage("Turn off the game radio and menu music at the next launch, keeping engine, tyres and effects. This app does not request exclusive Android audio focus. Start your music player normally.")
          .setNegativeButton("Cancel",null).setPositiveButton("Turn game music off",(d,w)->{
            if(apply(a,runtime,Collections.singletonMap("pinyon_shift_mix_music","0"),true))
              refreshOptions(music);
          }).show());
    }
    if (page.equals("Graphics")) {
      String key="fh1_render_scale_percent";
      String title="Render resolution";
      SettingRow resolution=new SettingRow(a,title,null);
      resolution.setTag(key);
      resolution.setTag(FestivalUi.FOCUS_KEY,key);
      LinearLayout.LayoutParams rowSize=new LinearLayout.LayoutParams(-1,-2);
      rowSize.bottomMargin=FestivalUi.dp(a,5);
      rows.addView(resolution,rowSize);
      java.util.function.IntSupplier read=()->Math.max(25,Math.min(100,integer(value(a,key,"100"),100)));
      Runnable update=()->{
        String label=read.getAsInt()+"%";
        resolution.value.setText(label);
        resolution.setContentDescription(title+", "+label+". Left or right to adjust.");
        if(resolutionDetails!=null&&resolution.hasFocus())resolutionDetails.run();
      };
      FestivalUi.onRefresh(resolution,update);
      resolution.setOnFocusChangeListener((v,on)->{if(on&&resolutionDetails!=null)resolutionDetails.run();});
      java.util.function.IntConsumer save = next -> {
        if (next != read.getAsInt())
          apply(a,runtime,Collections.singletonMap(key,Integer.toString(next)),true,false);
        update.run();
      };
      resolution.adjust(direction -> save.accept(Math.max(25,Math.min(100,read.getAsInt()+direction))),
          () -> FestivalUi.explain(a,title,RESOLUTION_HELP));
      resolution.setOnClickListener(v->{
        if(resolutionDetails!=null)resolutionDetails.run();
        LinearLayout slider=FestivalUi.column(a);
        slider.addView(FestivalUi.text(a,"Use left or right to adjust, or drag the slider. Applies next launch.",13,FestivalUi.MUTED));
        SeekBar bar=FestivalUi.slider(a,slider,title,RESOLUTION_HELP,25,100,1,read.getAsInt(),i->i+"%",save);
        slider.setTag(FestivalUi.DEFAULT_FOCUS,bar);
        FestivalUi.present(a,title,slider);
        bar.post(bar::requestFocus);
      });
      choiceRow(a,rows,"graphics-msaa","MSAA",null,
          "Smooths geometry edges. Higher values use more graphics memory and GPU time. Applies next launch.",
          new String[]{"Off","2×","4×"},()->msaaIndex(value(a,"fh1_msaa_single_sample","true"),
              value(a,"fh1_msaa_2x","false")),i->{
                Map<String,String> values=new LinkedHashMap<>();
                values.put("fh1_msaa_single_sample",Boolean.toString(i==0));
                values.put("fh1_msaa_2x",Boolean.toString(i==1));
                apply(a,runtime,values,true,false);
              }, () -> {
                String single=active(runtime,"fh1_msaa_single_sample"),two=active(runtime,"fh1_msaa_2x");
                return single==null||two==null?null:new String[]{"Off","2×","4×"}[msaaIndex(single,two)];
              });
      choiceRow(a,rows,"graphics-screen","Screen layout",null,
          "Original proportions keeps the game's aspect ratio. Widescreen extends gameplay and keeps original menus. Stretch fits the entire display. Applies next launch.",
          new String[]{"Original","Widescreen","Stretch"},()->GraphicsProfiles.screenModeIndex(MobileConfig.read(a)),
          i->apply(a,runtime,GraphicsProfiles.screenMode(i),true,false),()->screenActive(runtime));
      String[] scales={"100","77","67","59","50"};
      String[] fsrLabels={"Native · 100%","Ultra quality · 77%","Quality · 67%","Balanced · 59%","Performance · 50%"};
      choiceRow(a,rows,"graphics-fsr","FSR 1",null,
          "A preset changes the internal resolution and uses FSR 1 to scale the image for display. Applies next launch.",
          fsrLabels,()->value(a,"present_effect","bilinear").equals("fsr")
              ? Arrays.asList(scales).indexOf(value(a,key,"100")) : -1,i->{
                Map<String,String> values=new LinkedHashMap<>();
                values.put("present_effect",MobileConfig.quote("fsr"));
                values.put(key,scales[i]);
                values.put("present_fsr_sharpness_reduction",".5");
                apply(a,runtime,values,true,false);
              },()->null);
      FestivalUi.action(a, rows, "Graphics presets", () -> presets(a, runtime));
      FestivalUi.action(a, rows, "My graphics profiles", () -> GraphicsProfilePage.show(a,runtime));
    }
    if(page.equals("Interface")){
      FestivalUi.slider(a, rows, "Menu size", "Reopen this page to apply size changes.", 70, 105, 1,
          Math.round(FestivalUi.scale(a) * 100), i -> i + "%", i -> {
            a.getSharedPreferences("mobile", 0).edit().putFloat("ui_scale", i / 100f).apply();
            UiPreferences.save(a);
          });
      pref(a, rows, "Menu animations", "animations", true);
      pref(a, rows, "Show FPS", "show_fps", false);
      pref(a, rows, "Show latest frame time (with FPS)", "show_frame_time", false);
      pref(a, rows, "Show RAM and thermal severity (with FPS)", "show_memory_heat", false);
      pref(a, rows, "Match display refresh", "match_refresh", true);
    }
    if (page.equals("Touch")) {
      choiceRow(a,rows,"touch-layout-style","Layout","touch-label-mode",
          "Display driving action names or controller codes. Each style remembers its own positions.",
          new String[]{"Button codes","Driving labels"},()->touchLayout(a).drivingLabels?1:0,i->{
            try{TouchLayout.selectStyle(a,i==1);reload(a);}
            catch(IOException error){error(a,error);}
          },()->null);
      choiceRow(a,rows,"touch-steering-mode","Steering","steering-mode",
          "Choose steering arrows or a stick. Turn off phone tilt for steering with touch alone.",
          new String[]{"Arrows","Stick"},()->touchLayout(a).steering.equals("arrows")?0:1,i->{
            TouchLayout layout=touchLayout(a);
            layout.steering=i==0?"arrows":"stick";
            try{layout.save(a);reload(a);}
            catch(IOException error){error(a,error);}
          },()->null);
      FestivalUi.toggle(a, rows, "Button names",
          "Optional controller codes above the text action names. "
              + "Action names assume the game's default bindings.",
          ()->{TouchLayout current=new TouchLayout();current.load(a);return current.buttonHints;}, on -> {
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
      FestivalUi.toggle(a,rows,"Manual shifting controls","Show clutch, shift up and shift down together.",()->{TouchLayout current=new TouchLayout();current.load(a);return !current.hidden[3]||!current.hidden[4]||!current.hidden[6];},on->{try{TouchLayout current=new TouchLayout();current.load(a);for(int index:new int[]{3,4,6})current.hidden[index]=!on;current.save(a);reload(a);}catch(IOException error){error(a,error);}});

    }
    if(page.equals("Phone")){
      pref(a, rows, "Phone vibration", "phone_rumble", true);
      FestivalUi.slider(a, rows, "Phone vibration strength",
          "Intensity depends on the phone's vibration motor.", 0, 100, 1,
          a.getSharedPreferences("mobile", 0).getInt("phone_rumble_strength", 60),
          i -> i + "%", i -> {
            a.getSharedPreferences("mobile", 0).edit().putInt("phone_rumble_strength", i).apply();
            UiPreferences.save(a);
          });
      FestivalUi.controlAction(a, rows, "Test phone vibration", () -> ControllerTools.testRumble(a));
      pref(a, rows, "Phone tilt steering", "phone_tilt", false);
      pref(a, rows, "Invert phone tilt", "tilt_invert", false);
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
      FestivalUi.controlAction(a, rows, "Calibrate tilt centre", () -> {
        if (a instanceof PinyonShiftActivity)
          ((PinyonShiftActivity) a).calibrateTilt();
        else
          FestivalUi.explain(a, "Tilt calibration",
              "Open this page while playing and hold the "
                  + "phone in your driving position.");
      });
    }
    if (page.equals("Controller"))
      FestivalUi.controlAction(a, rows, "Native controller remapping", () -> {
        if(a instanceof PinyonShiftActivity)((PinyonShiftActivity)a).openNativeSettings();
        else FestivalUi.explain(a,"Controller remapping","Open this option from the in-game menu to change gamepad mappings.");
      });
    if (page.equals("Controller"))
      FestivalUi.controlAction(
          a, rows, "Test and calibrate controller", () -> ControllerTools.show(a, runtime));
    if (page.equals("Advanced")) {
      FestivalUi.action(a, rows, "Adreno options", () -> AdrenoSettings.show(a, runtime));
      if(runtime!=null){TextView aaActive=FestivalUi.text(a,"",12,FestivalUi.MUTED);rows.addView(aaActive);FestivalUi.onRefresh(aaActive,()->aaActive.setText("Running MSAA: "+(active(runtime,"fh1_msaa_single_sample")==null||active(runtime,"fh1_msaa_2x")==null?"unavailable":"true".equals(SettingValues.plain(active(runtime,"fh1_msaa_single_sample")))?"Off":"true".equals(SettingValues.plain(active(runtime,"fh1_msaa_2x")))?"2×":"4×")));}
      pref(a, rows, "Sustained performance mode", "sustained_mode", false);
      pref(a, rows, "Detailed GPU timings · next launch", "detailed_gpu", false);
      pref(a, rows, "CPU performance hints · experimental", "cpu_hints", false);
      pref(a, rows, "Record standard session logs · next launch", "record_session_logs", false);
      pref(a, rows, "Extended comparison capture · up to 64 MB · next launch", "extended_capture", false);
      rows.addView(FestivalUi.text(a,
          "Sustained mode lowers the frame target "
              + "under severe heat, then restores it after "
              + "cooling. It keeps your saved preset.",
          12, FestivalUi.MUTED));
      if(a instanceof PinyonShiftActivity)FestivalUi.action(a,rows,"Original game settings",()->((PinyonShiftActivity)a).openNativeSettings());
    }
    if (page.equals("Gameplay")) {
      if (!(a instanceof PinyonShiftActivity)) {
        FestivalUi.toggle(a, rows, "Save folder sync · next launch",
            "Share the normal save and achievements through the app's media folder. "
                + "Point your folder-sync app at Android/media/ph.xhyn.fh4droid/sync.",
            () -> a.getSharedPreferences("mobile", 0).getBoolean("save_folder_sync", false),
            on -> {
              a.getSharedPreferences("mobile", 0).edit().putBoolean("save_folder_sync", on).apply();
              UiPreferences.save(a);
            });
      }
    }
  }
  static void pref(Activity a, LinearLayout rows, String title, String key, boolean fallback) {
    FestivalUi.toggle(
        a, rows, title, "", ()->a.getSharedPreferences("mobile", 0).getBoolean(key, fallback), on -> {
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
        i -> {
          Map<String,String> values=GraphicsProfiles.preset(i);
          new AlertDialog.Builder(a).setTitle("Apply "+GraphicsProfiles.NAMES[i]+"?").setMessage("These graphics apply at the next launch.\n\n"+GraphicsProfileStore.describe(values)).setNegativeButton("Keep my settings",null).setPositiveButton("Apply",(d,w)->apply(a,runtime,values,true)).show();
        });
  }
  private SettingsHub() {}
}
