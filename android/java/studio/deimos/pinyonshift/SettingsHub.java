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
      "Graphics", "Audio", "Controls", "Interface", "Gameplay", "Advanced"};
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
  static void show(Activity a,MobileSettings.Runtime runtime,Runnable editor,Runnable drivers){
    FestivalUi.present(a,"Settings",buildPage(a,"Graphics",runtime,editor));
  }
  static void page(Activity a,String page,MobileSettings.Runtime runtime,Runnable editor){FestivalUi.present(a,"Settings",buildPage(a,page,runtime,editor));}
  private static String category(MobileSettings.Option o){
    if(ADVANCED.contains(o.key)||o.page.equals("Performance"))return "Advanced";
    if(o.page.equals("Controls"))return "Controls";
    if(o.page.equals("Display"))return o.key.startsWith("fh1_hud")||o.key.equals("hud_hidden")?"Interface":"Graphics";
    return o.page;
  }
  private static boolean belongs(String page,MobileSettings.Option o){
    if(page.equals("All settings"))return true;
    if(page.equals("Touch"))return o.key.equals("pinyon_shift_touch_controls");
    if(page.equals("Controller"))return o.page.equals("Controls")&&!o.key.equals("pinyon_shift_touch_controls")&&!ADVANCED.contains(o.key);
    return page.equals(category(o));
  }
  private static LinearLayout buildPage(Activity a,String first,MobileSettings.Runtime runtime,Runnable editor){
    LinearLayout root=FestivalUi.column(a);root.setTag(FestivalUi.FULL_PAGE,Boolean.TRUE);root.setClipChildren(true);
    LinearLayout tabs=new LinearLayout(a);root.addView(tabs,new LinearLayout.LayoutParams(-1,FestivalUi.dp(a,38)));
    View gap=new View(a);root.addView(gap,new LinearLayout.LayoutParams(1,FestivalUi.dp(a,8)));
    LinearLayout content=new LinearLayout(a);content.setClipChildren(true);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
    FrameLayout pane=new FrameLayout(a);content.addView(pane,new LinearLayout.LayoutParams(0,-1,2));
    LinearLayout details=FestivalUi.column(a);details.setTag("details-panel");int inset=FestivalUi.dp(a,14);details.setPadding(inset,FestivalUi.dp(a,12),inset,FestivalUi.dp(a,10));details.setBackground(FestivalUi.surface(a,FestivalUi.GLASS,8));
    TextView heading=FestivalUi.text(a,"SETTING DETAILS",10,FestivalUi.RED);details.addView(heading);
    TextView detailTitle=FestivalUi.text(a,"Choose an option",16,FestivalUi.TEXT);detailTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);detailTitle.setPadding(0,FestivalUi.dp(a,8),0,FestivalUi.dp(a,8));details.addView(detailTitle);
    ScrollView helpScroll=new ScrollView(a);details.addView(helpScroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout helpBody=FestivalUi.column(a);helpScroll.addView(helpBody);
    TextView detailHelp=FestivalUi.text(a,"Select a setting to read its explanation.",12,FestivalUi.MUTED);helpBody.addView(detailHelp);
    TextView detailValue=FestivalUi.text(a,"",11,FestivalUi.TEXT);detailValue.setPadding(0,FestivalUi.dp(a,8),0,0);helpBody.addView(detailValue);
    Button more=FestivalUi.button(a,"Y  More details",false);more.setTextSize(11);more.setMinHeight(0);more.setMinimumHeight(0);details.addView(more,new LinearLayout.LayoutParams(-1,FestivalUi.dp(a,32)));more.setOnClickListener(v->FestivalUi.explain(a,detailTitle.getText().toString(),detailHelp.getText()+"\n\n"+detailValue.getText()));
    LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,-1,1);dp.leftMargin=FestivalUi.dp(a,8);content.addView(details,dp);
    java.util.function.Consumer<MobileSettings.Option> inspect=o->{detailTitle.setText(o.title);detailHelp.setText(o.help+(o.restart?"\n\nApplies next launch.":""));String saved=value(a,o.key,o.initial),live=active(runtime,o.key);detailValue.setText(live==null?"Saved: "+MobileSettings.label(o,saved):"Running: "+MobileSettings.label(o,live)+"\nSaved: "+MobileSettings.label(o,saved));};
    Runnable resolutionDetails=()->{detailTitle.setText("Render resolution");detailHelp.setText("Lower values reduce GPU work. Applies next launch. Left / right changes by 1% (25–100%).");String saved=value(a,"fh1_render_scale_percent","100"),live=active(runtime,"fh1_render_scale_percent");detailValue.setText(live==null?"Next launch: "+saved+"%":"Running: "+SettingValues.plain(live)+"%\nNext launch: "+saved+"%");};
    Map<String,ScrollView> pages=new HashMap<>();Map<String,Button> buttons=new LinkedHashMap<>();String[] selected={first};
    java.util.function.Consumer<String> select=section->{
      selected[0]=section;pane.removeAllViews();ScrollView view=pages.get(section);
      if(view==null){view=new ScrollView(a);view.setTag("settings-scroll");view.setClipChildren(true);view.setFillViewport(false);LinearLayout column=FestivalUi.column(a);view.addView(column);
        if(section.equals("Controls")){
          FestivalUi.action(a,column,"Edit touch layout",editor);
          FestivalUi.action(a,column,"Touch buttons and steering",()->page(a,"Touch",runtime,editor));
          FestivalUi.action(a,column,"Phone tilt and vibration",()->page(a,"Phone",runtime,editor));
          FestivalUi.action(a,column,"Physical controller",()->page(a,"Controller",runtime,editor));
          FestivalUi.action(a,column,"Saved layouts and presets",()->LayoutProfiles.show(a));
        }else column.addView(createPage(a,section,runtime,editor,inspect,resolutionDetails));
        bindDetails(column,detailTitle,detailHelp,detailValue);pages.put(section,view);
      }
      pane.addView(view,new FrameLayout.LayoutParams(-1,-1));detailTitle.setText("Choose an option");detailHelp.setText("Focus or select an option to see what it does and when it applies.");detailValue.setText("");if(section.equals("Graphics"))resolutionDetails.run();
      for(Map.Entry<String,Button> entry:buttons.entrySet()){boolean on=entry.getKey().equals(section)||entry.getKey().equals("Controls")&&Arrays.asList("Touch","Phone","Controller").contains(section);entry.getValue().setSelected(on);}
      FestivalUi.refreshTree(view);FestivalUi.slideIn(view);root.setTag(FestivalUi.DEFAULT_FOCUS,buttons.get(Arrays.asList("Touch","Phone","Controller").contains(section)?"Controls":section));
    };
    for(String section:PAGES){Button button=FestivalUi.button(a,section,false);button.setCompoundDrawables(null,null,null,null);button.setTextSize(12);button.setPadding(0,0,0,0);button.setMinHeight(0);button.setMinimumHeight(0);LinearLayout.LayoutParams tab=new LinearLayout.LayoutParams(0,-1,1);tab.rightMargin=FestivalUi.dp(a,6);tabs.addView(button,tab);button.setOnClickListener(v->select.accept(section));buttons.put(section,button);}
    LinearLayout header=new LinearLayout(a);root.setTag(FestivalUi.HEADER_ACTIONS,header);Button search=FestivalUi.button(a,"Search / favourites",false);header.addView(search,new LinearLayout.LayoutParams(-2,FestivalUi.dp(a,32)));search.setTextSize(11);search.setMinHeight(0);search.setMinimumHeight(0);search.setOnClickListener(v->select.accept("All settings"));Button reset=FestivalUi.button(a,"Reset / undo",false);LinearLayout.LayoutParams resetParams=new LinearLayout.LayoutParams(-2,FestivalUi.dp(a,32));resetParams.leftMargin=FestivalUi.dp(a,8);header.addView(reset,resetParams);reset.setTextSize(11);reset.setMinHeight(0);reset.setMinimumHeight(0);reset.setOnClickListener(v->{PopupMenu popup=new PopupMenu(a,reset);popup.getMenu().add("Reset this section").setOnMenuItemClickListener(i->{resetSection(a,selected[0],runtime,()->FestivalUi.refreshTree(root));return true;});popup.getMenu().add("Undo last change").setOnMenuItemClickListener(i->{undo(a,runtime);FestivalUi.refreshTree(root);return true;});popup.show();});
    select.accept(first);return root;
  }
  private static void bindDetails(View view,TextView title,TextView help,TextView value){
    if(view.isFocusable()&&view.getOnFocusChangeListener()==null)view.setOnFocusChangeListener((v,on)->{if(on){CharSequence caption=v instanceof TextView?((TextView)v).getText():v.getContentDescription();if(caption==null||caption.length()==0)caption=v.getContentDescription();title.setText(caption);Object desc=v.getTag(0x7f0a7f08);help.setText(desc instanceof String?(String)desc:toolHelp(title.getText().toString()));value.setText("");}});
    if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)bindDetails(group.getChildAt(i),title,help,value);}
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
      java.util.function.Consumer<MobileSettings.Option> inspect, Runnable resolutionDetails) {
    LinearLayout body = FestivalUi.column(a);
    EditText search = new EditText(a);
    search.setSingleLine(true);
    search.setTextColor(FestivalUi.TEXT);
    search.setHintTextColor(FestivalUi.MUTED);
    search.setTextSize(13 * FestivalUi.scale(a));
    search.setHint("Search " + page.toLowerCase(Locale.ROOT));
    search.setMinHeight(FestivalUi.dp(a,44));
    if(page.equals("All settings"))body.addView(search, new LinearLayout.LayoutParams(-1, -2));
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
      LinearLayout graphicsExtras=FestivalUi.column(a);
      boolean graphics=page.equals("Graphics")&&!pinned[0]&&query.isEmpty();
      if(graphics){extras(a,graphicsExtras,"Graphics",runtime,editor,resolutionDetails);View resolution=graphicsExtras.getChildAt(0);graphicsExtras.removeView(resolution);rows.addView(resolution);}
      List<MobileSettings.Option> ordered=new ArrayList<>(OPTIONS);
      if(graphics){String[] keys={"pinyon_shift_fh1_render_fps_limit","vulkan_texture_max_size","present_effect"};for(int n=keys.length-1;n>=0;n--){final String key=keys[n];for(int i=0;i<ordered.size();i++)if(ordered.get(i).key.equals(key)){ordered.add(0,ordered.remove(i));break;}}}
      for (MobileSettings.Option option : ordered) {
        if(!belongs(page,option))continue;
        if (!advanced[0] && ADVANCED.contains(option.key))
          continue;
        if (!query.isEmpty()
            && !(option.title + " " + option.help).toLowerCase(Locale.ROOT).contains(query))
          continue;
        if (pinned[0] && !isPinned(a, option.key))
          continue;
        optionRow(a, rows, option, runtime, inspect);
      }
      if (!pinned[0]) {
        for (String category : page.equals("All settings") ? new String[]{"Graphics","Touch","Phone","Controller","Interface","Gameplay","Advanced"} : new String[] {page}) {
          if(category.equals("Graphics")&&graphics){while(graphicsExtras.getChildCount()>0){View child=graphicsExtras.getChildAt(0);graphicsExtras.removeViewAt(0);rows.addView(child);}continue;}
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
  private static void optionRow(
      Activity a, LinearLayout rows, MobileSettings.Option o, MobileSettings.Runtime runtime,
      java.util.function.Consumer<MobileSettings.Option> inspect) {
    SettingRow row=new SettingRow(a,o.title);row.setTag(o.key);
    java.util.function.IntConsumer change=direction->{String saved=value(a,o.key,o.initial);int index=0;for(int i=0;i<o.values.length;i++)if(SettingValues.same(saved,o.values[i]))index=i;int next=Math.max(0,Math.min(o.values.length-1,index+direction));if(next!=index){apply(a,runtime,Collections.singletonMap(o.key,literal(o,o.values[next])),o.restart);FestivalUi.refreshTree(row);}if(inspect!=null)inspect.accept(o);};
    FestivalUi.onRefresh(row,()->{String saved=value(a,o.key,o.initial),reason=SettingAvailability.reason(a,o.key);row.label.setText((isPinned(a,o.key)?"• ":"")+o.title);row.value.setText("‹  "+MobileSettings.label(o,saved)+"  ›");row.setEnabled(reason.isEmpty());row.setContentDescription(o.title+": "+MobileSettings.label(o,saved)+(reason.isEmpty()?"":". "+reason));});
    row.setOnFocusChangeListener((v,on)->{if(on&&inspect!=null)inspect.accept(o);});
    row.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_BUTTON_Y){if(event.getAction()==KeyEvent.ACTION_UP)FestivalUi.explain(a,o.title,o.help+(o.restart?" Applies next launch.":""));return true;}if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT)return false;if(event.getAction()==KeyEvent.ACTION_DOWN)change.accept(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1);return true;});
    row.setOnLongClickListener(v->{boolean pin=!isPinned(a,o.key);a.getSharedPreferences("setting_pins",0).edit().putBoolean(o.key,pin).apply();UiPreferences.save(a);FestivalUi.refreshTree(row);return true;});
    row.setOnClickListener(v->{if(inspect!=null)inspect.accept(o);String saved=value(a,o.key,o.initial);String[] labels=new String[o.values.length];int selected=-1;for(int i=0;i<labels.length;i++){labels[i]=MobileSettings.label(o,o.values[i]);if(SettingValues.same(saved,o.values[i]))selected=i;}FestivalUi.choose(a,o.title,labels,selected,index->{apply(a,runtime,Collections.singletonMap(o.key,literal(o,o.values[index])),o.restart);FestivalUi.refreshTree(row);if(inspect!=null)inspect.accept(o);});});
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=FestivalUi.dp(a,5);rows.addView(row,params);
  }
  private static final class SettingRow extends LinearLayout {
    final TextView label,value;
    SettingRow(Activity a,String title){super(a);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setFocusable(true);setFocusableInTouchMode(true);setClickable(true);setMinimumHeight(FestivalUi.dp(a,44));setPadding(FestivalUi.dp(a,12),FestivalUi.dp(a,6),FestivalUi.dp(a,12),FestivalUi.dp(a,6));FestivalUi.glassBackground(a,this,false,8);label=FestivalUi.text(a,title,13,FestivalUi.TEXT);label.setMaxLines(2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);addView(label,new LayoutParams(0,-2,1));value=FestivalUi.text(a,"",13,FestivalUi.TEXT);value.setMaxLines(2);value.setGravity(Gravity.RIGHT);value.setPadding(FestivalUi.dp(a,8),0,0,0);addView(value,new LayoutParams(-2,-2));}
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
  private static String screenActive(MobileSettings.Runtime runtime){if(runtime==null)return "";Map<String,String> values=new LinkedHashMap<>();for(String key:GraphicsProfiles.screenMode(0).keySet()){String v=active(runtime,key);if(v==null)return " · running mode unavailable";values.put(key,SettingValues.plain(v));}return "\nRunning: "+GraphicsProfiles.SCREEN_MODES[GraphicsProfiles.screenModeIndex(values)];}
  private static int integer(String value, int fallback) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
  private static void extras(
      Activity a, LinearLayout rows, String page, MobileSettings.Runtime runtime, Runnable editor,
      Runnable resolutionDetails) {
    if(page.equals("Audio"))FestivalUi.action(a,rows,"Use my music player",()->new AlertDialog.Builder(a).setTitle("Use external music?").setMessage("Turn off the game radio and menu music at the next launch, keeping engine, tyres and effects. This app does not request exclusive Android audio focus. Start your music player normally.").setNegativeButton("Cancel",null).setPositiveButton("Turn game music off",(d,w)->apply(a,runtime,Collections.singletonMap("pinyon_shift_mix_music","0"),true)).show());
    if (page.equals("Graphics")) {
      SettingRow resolution=new SettingRow(a,"Render resolution");resolution.setTag("fh1_render_scale_percent");rows.addView(resolution,new LinearLayout.LayoutParams(-1,FestivalUi.dp(a,44)));
      Runnable update=()->{resolution.value.setText("‹  "+value(a,"fh1_render_scale_percent","100")+"%  ›");if(resolutionDetails!=null&&resolution.hasFocus())resolutionDetails.run();};FestivalUi.onRefresh(resolution,update);
      resolution.setOnFocusChangeListener((v,on)->{if(on&&resolutionDetails!=null)resolutionDetails.run();});
      resolution.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_BUTTON_Y){if(event.getAction()==KeyEvent.ACTION_UP)FestivalUi.explain(a,"Render resolution","Lower values reduce GPU work. Applies next launch. Left / right changes by 1% (25–100%).");return true;}if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT)return false;if(event.getAction()==KeyEvent.ACTION_DOWN){int n=Math.max(25,Math.min(100,integer(value(a,"fh1_render_scale_percent","100"),100)+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1)));apply(a,runtime,Collections.singletonMap("fh1_render_scale_percent",Integer.toString(n)),true);update.run();}return true;});
      resolution.setOnClickListener(v->{if(resolutionDetails!=null)resolutionDetails.run();LinearLayout slider=FestivalUi.column(a);FestivalUi.slider(a,slider,"Render resolution","Lower values reduce GPU work. Applies next launch.",25,100,1,integer(value(a,"fh1_render_scale_percent","100"),100),i->i+"%",i->{apply(a,runtime,Collections.singletonMap("fh1_render_scale_percent",Integer.toString(i)),true);update.run();});FestivalUi.present(a,"Render resolution",slider);});
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
      if(runtime!=null){TextView aaActive=FestivalUi.text(a,"",12,FestivalUi.MUTED);rows.addView(aaActive);FestivalUi.onRefresh(aaActive,()->aaActive.setText("Running MSAA: "+(active(runtime,"fh1_msaa_single_sample")==null||active(runtime,"fh1_msaa_2x")==null?"unavailable":"true".equals(SettingValues.plain(active(runtime,"fh1_msaa_single_sample")))?"Off":"true".equals(SettingValues.plain(active(runtime,"fh1_msaa_2x")))?"2×":"4×")));}
      FestivalUi.action(a, rows, "Graphics presets", () -> presets(a, runtime));
      FestivalUi.action(a, rows, "My graphics profiles", () -> GraphicsProfilePage.show(a,runtime));
      FestivalUi.action(a,rows,"Graphics drivers",()->{if(a instanceof LauncherActivity)((LauncherActivity)a).openDrivers();else FestivalUi.explain(a,"Graphics drivers","Quit to the launcher to select a driver.");});
    }
    if (page.equals("Graphics")) {
      Button screen = FestivalUi.action(a, rows, "", () -> {});
      FestivalUi.onRefresh(screen,
          ()
              -> screen.setText("Screen · next launch: "+GraphicsProfiles.SCREEN_MODES[GraphicsProfiles.screenModeIndex(MobileConfig.read(a))]+screenActive(runtime)));
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
      TouchLayout layout = new TouchLayout();
      layout.load(a);
      Button style = FestivalUi.action(a, rows, "", () -> {});
      FestivalUi.onRefresh(style, () -> {
        TouchLayout current = new TouchLayout();
        current.load(a);
        style.setText("Layout: " + (current.drivingLabels ? "Driving action names" : "Controller button codes"));
      });
      style.setOnClickListener(v -> {
        TouchLayout current = new TouchLayout();
        current.load(a);
        FestivalUi.choose(a, "Touch layout", new String[] {"Controller button codes", "Driving action names"},
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
      FestivalUi.action(a, rows, "Saved layouts and templates", () -> LayoutProfiles.show(a));
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
      FestivalUi.action(a, rows, "Test phone vibration", () -> ControllerTools.testRumble(a));
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
    if (page.equals("Advanced")) {
      FestivalUi.action(a, rows, "Adreno options", () -> AdrenoSettings.show(a, runtime));
      if(runtime!=null){TextView aaActive=FestivalUi.text(a,"",12,FestivalUi.MUTED);rows.addView(aaActive);FestivalUi.onRefresh(aaActive,()->aaActive.setText("Running MSAA: "+(active(runtime,"fh1_msaa_single_sample")==null||active(runtime,"fh1_msaa_2x")==null?"unavailable":"true".equals(SettingValues.plain(active(runtime,"fh1_msaa_single_sample")))?"Off":"true".equals(SettingValues.plain(active(runtime,"fh1_msaa_2x")))?"2×":"4×")));}
      FestivalUi.action(a, rows, "Graphics presets", () -> presets(a, runtime));
      FestivalUi.action(a, rows, "My graphics profiles", () -> GraphicsProfilePage.show(a,runtime));
      pref(a, rows, "Sustained performance mode", "sustained_mode", false);
      pref(a, rows, "Detailed GPU timings · next launch", "detailed_gpu", false);
      pref(a, rows, "Extended comparison capture · up to 64 MB · next launch", "extended_capture", false);
      rows.addView(FestivalUi.text(a,
          "Sustained mode lowers the frame target "
              + "under severe heat, then restores it after "
              + "cooling. It keeps your saved preset.",
          12, FestivalUi.MUTED));
      if(a instanceof PinyonShiftActivity)FestivalUi.action(a,rows,"Original game settings",()->((PinyonShiftActivity)a).openNativeSettings());
      FestivalUi.action(a, rows, "Graphics cache", () -> GraphicsCache.show(a));
      FestivalUi.action(
          a, rows, "Record a performance comparison", () -> ProblemReports.capture(a));
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
      FestivalUi.action(a, rows, "Cheats · separate profile", () -> TrainerPage.show(a, runtime));
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
