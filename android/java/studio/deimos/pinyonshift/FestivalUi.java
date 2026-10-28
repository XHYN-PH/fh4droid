package studio.deimos.pinyonshift;

import android.animation.*;
import android.app.*;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.io.InputStream;
import java.util.function.*;

/**
 * Shared landscape menu components. Settings pages keep their own scroll
 * position.
 */
final class FestivalUi {
  // RED and ORANGE are the two ends of the accent gradient (magenta to hot pink). The names are
  // kept because many pages already reference them.
  static final int BG = 0xff0a0a10, PANEL = 0xf0101015, TEXT = 0xfff7f4f8, MUTED = 0xffb9b3c2,
                   RED = 0xffd9145a, ORANGE = 0xffff2e7e;
  static final int GLASS = 0xd9101116, GLASS_SELECTED = 0xe61d0c17, GLASS_FOCUS = 0xf0301424,
                   STROKE = 0x2effffff;
  static final int FULL_PAGE = 0x7f0a7f04;
  private static final int SCROLL_STATE = 0x7f0a7f01;
  private static final int REFRESH = 0x7f0a7f02;
  private static final int PAGE_SCROLL = 0x7f0a7f03;
  private static final class PageState {
    int y;
    boolean restoring;
    View focus;
  }
  interface PanelHost {
    void showPanel(String title, View body);
    void backPanel();
    default void replacePanel(String title, View body) {
      showPanel(title, body);
    }
  }
  static int dp(Activity a, float n) {
    return Math.round(n * a.getResources().getDisplayMetrics().density);
  }
  static float scale(Context c) {
    Object raw = c.getSharedPreferences("mobile", 0).getAll().get("ui_scale");
    float value = raw instanceof Number ? ((Number) raw).floatValue() : .82f;
    return Float.isFinite(value) ? Math.max(.7f, Math.min(1.05f, value)) : .82f;
  }
  static boolean animations(Context c) {
    return ValueAnimator.areAnimatorsEnabled()
        && c.getSharedPreferences("mobile", 0).getBoolean("animations", true);
  }
  static TextView text(Activity a, String s, int size, int color) {
    TextView v = new TextView(a);
    v.setText(AppLanguage.text(a,s));
    v.setTextSize(Math.max(size>=18?16:13,size * scale(a)));
    v.setTextColor(color);
    v.setTypeface(Typeface.create("sans-serif", 0));
    return v;
  }
  static LinearLayout column(Activity a) {
    LinearLayout v = new LinearLayout(a);
    v.setOrientation(1);
    return v;
  }
  static GradientDrawable surface(Activity a, int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(a, radius));
    return d;
  }
  static GradientDrawable outlined(Activity a, int fill, int stroke, float strokeDp, int radius) {
    GradientDrawable d = surface(a, fill, radius);
    d.setStroke(Math.max(1, dp(a, strokeDp)), stroke);
    return d;
  }
  /** Frosted card background with a pressed ripple and a bright focus ring. */
  static void glassBackground(Activity a, View v, boolean selected, int radius) {
    GradientDrawable base = outlined(a, selected ? GLASS_SELECTED : GLASS,
        selected ? ORANGE : STROKE, selected ? 1.5f : 1, radius);
    GradientDrawable focus = outlined(a, GLASS_FOCUS, ORANGE, 2, radius);
    StateListDrawable states = new StateListDrawable();
    states.addState(new int[] {android.R.attr.state_focused}, focus);
    states.addState(new int[] {android.R.attr.state_selected}, focus);
    states.addState(new int[] {}, base);
    v.setBackground(new RippleDrawable(
        ColorStateList.valueOf(0x33ffffff), states, surface(a, Color.WHITE, radius)));
  }
  private static void buttonMotion(Button b) {
    b.animate().cancel();
    b.setScaleX(1);
    b.setScaleY(1);
    if (!animations(b.getContext())) {
      b.setStateListAnimator(null);
      return;
    }
    StateListAnimator states = new StateListAnimator();
    for (boolean pressed : new boolean[] {true, false}) {
      AnimatorSet set = new AnimatorSet();
      float s = pressed ? .975f : 1;
      set.playTogether(
          ObjectAnimator.ofFloat(b, "scaleX", s), ObjectAnimator.ofFloat(b, "scaleY", s));
      set.setDuration(pressed ? 90 : 160);
      set.setInterpolator(new DecelerateInterpolator());
      states.addState(pressed ? new int[] {android.R.attr.state_pressed} : new int[0], set);
    }
    b.setStateListAnimator(states);
  }
  static Button button(Activity a, String title, boolean primary) {
    Button b = new Button(a);
    b.setText(AppLanguage.text(a,title));
    b.setAllCaps(false);
    b.setTextSize(Math.max(15,16 * scale(a)));
    b.setTextColor(TEXT);
    b.setTypeface(Typeface.create("sans-serif", 1));
    b.setGravity(Gravity.CENTER);
    b.setIncludeFontPadding(false);
    GradientDrawable d;
    if (primary) {
      d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {RED, ORANGE});
      d.setCornerRadius(dp(a, 12));
    } else {
      d = outlined(a, GLASS, STROKE, 1, 12);
    }

    b.setBackgroundTintList(null);
    GradientDrawable focused = outlined(a, primary ? ORANGE : GLASS_FOCUS, primary ? TEXT : ORANGE, 2, 12);
    StateListDrawable states=new StateListDrawable();
    states.addState(new int[]{android.R.attr.state_focused},focused);
    states.addState(new int[]{android.R.attr.state_selected},focused);
    states.addState(new int[]{},d);
    b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),states,surface(a,Color.WHITE,12)));
    b.setFocusable(true);
    b.setMinimumHeight(dp(a, 48));
    b.setMinHeight(dp(a, 48));
    b.setMinWidth(0);
    b.setPadding(dp(a, 12), dp(a, 5), dp(a, 12), dp(a, 5));
    b.setElevation(0);
    buttonMotion(b);
    b.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
      public void onViewAttachedToWindow(View v) {
        buttonMotion(b);
      }
      public void onViewDetachedFromWindow(View v) {}
    });
    return b;
  }
  static Button action(Activity a, LinearLayout parent, String title, Runnable action) {
    Button b = button(a, title, false);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.topMargin = dp(a, 5);
    parent.addView(b, p);
    b.setOnClickListener(v -> action.run());
    return b;
  }
  static LinearLayout card(Activity a, LinearLayout parent, String title, String help) {
    LinearLayout c = column(a);
    int p = dp(a, 10);
    c.setPadding(p, p, p, p);
    c.setBackground(outlined(a, GLASS, STROKE, 1, 14));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(a, 6);
    parent.addView(c, lp);
    TextView t = text(a, title, 15, TEXT);
    t.setTypeface(Typeface.create("sans-serif", 1));
    c.addView(t);
    if (help != null && !help.isEmpty()) {
      TextView h = text(a, help, 12, MUTED);
      h.setPadding(0, dp(a, 4), 0, dp(a, 6));
      c.addView(h);
    }
    return c;
  }
  // ---- Menu components: icon + title + subtitle on frosted cards ----------------------------

  static OutlineIcon icon(Activity a, String kind, int color) {
    OutlineIcon i = new OutlineIcon(a, kind, color);
    i.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    return i;
  }
  private static float titleSize(Activity a) {
    return Math.max(15f, 17f * scale(a));
  }
  private static float subtitleSize(Activity a) {
    return Math.max(13f, 14f * scale(a));
  }
  private static LinearLayout.LayoutParams stacked(Activity a) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(a, 8);
    return lp;
  }
  /** Width of the page content in dp. Wide screens keep it to the left so the artwork shows. */
  static int contentWidthDp(Activity a) {
    int w = a.getResources().getConfiguration().screenWidthDp;
    return wideLayout(a) ? Math.round(w * .62f) - 36 : w - 36;
  }
  static boolean wideLayout(Activity a) {
    android.content.res.Configuration c = a.getResources().getConfiguration();
    return c.screenWidthDp >= 650 && c.fontScale < 1.5f;
  }
  /** Screen readers should announce these custom cards as buttons or radio buttons. */
  private static void role(View v, String className, BooleanSupplier checked) {
    v.setAccessibilityDelegate(new View.AccessibilityDelegate() {
      @Override
      public void onInitializeAccessibilityNodeInfo(
          View host, android.view.accessibility.AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(host, info);
        info.setClassName(className);
        if (checked != null) {
          info.setCheckable(true);
          info.setChecked(checked.getAsBoolean());
        }
      }
    });
  }
  static final class RadioDot extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean on;
    RadioDot(Context c, boolean on) {
      super(c);
      this.on = on;
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    boolean on() {
      return on;
    }
    void set(boolean value) {
      on = value;
      invalidate();
    }
    @Override
    protected void onDraw(Canvas c) {
      float d = getResources().getDisplayMetrics().density;
      float cx = getWidth() / 2f, cy = getHeight() / 2f;
      float r = Math.min(getWidth(), getHeight()) / 2f - 2 * d;
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(2 * d);
      paint.setColor(on ? ORANGE : 0x88ffffff);
      c.drawCircle(cx, cy, r, paint);
      if (on) {
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, r * .5f, paint);
      }
    }
  }
  static final class Bar extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int percent;
    Bar(Context c, int percent) {
      super(c);
      this.percent = Math.max(0, Math.min(100, percent));
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override
    protected void onDraw(Canvas c) {
      float w = getWidth(), h = getHeight();
      paint.setShader(null);
      paint.setColor(0x33ffffff);
      c.drawRoundRect(new RectF(0, 0, w, h), h / 2, h / 2, paint);
      if (percent > 0) {
        paint.setShader(
            new LinearGradient(0, 0, w, 0, RED, ORANGE, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(0, 0, Math.max(h, w * percent / 100f), h), h / 2, h / 2, paint);
        paint.setShader(null);
      }
    }
  }
  private static LinearLayout row(Activity a, String icon, String title, String sub,
      boolean selected, View trailing, int radius, int iconDp, int padDp) {
    LinearLayout r = new LinearLayout(a);
    r.setGravity(Gravity.CENTER_VERTICAL);
    r.setPadding(dp(a, padDp + 4), dp(a, padDp), dp(a, padDp + 4), dp(a, padDp));
    r.setMinimumHeight(dp(a, 60));
    r.setFocusable(true);
    r.setClickable(true);
    glassBackground(a, r, selected, radius);
    r.addView(icon(a, icon, TEXT), new LinearLayout.LayoutParams(dp(a, iconDp), dp(a, iconDp)));
    LinearLayout words = column(a);
    words.setPadding(dp(a, 10), 0, dp(a, 8), 0);
    TextView t = text(a, title, 16, TEXT);
    t.setTextSize(titleSize(a));
    t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    words.addView(t);
    if (sub != null && !sub.isEmpty()) {
      TextView s = text(a, sub, 13, MUTED);
      s.setTextSize(subtitleSize(a));
      s.setPadding(0, dp(a, 2), 0, 0);
      words.addView(s);
    }
    r.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
    if (trailing != null) {
      int size = trailing instanceof RadioDot ? 22 : 24;
      r.addView(trailing, new LinearLayout.LayoutParams(dp(a, size), dp(a, size)));
    }
    r.setContentDescription(AppLanguage.text(a, title)
        + (sub == null || sub.isEmpty() ? "" : ". " + AppLanguage.text(a, sub)));
    return r;
  }
  /** A tappable card with an icon, a title, a subtitle and a chevron. */
  static LinearLayout listRow(Activity a, LinearLayout parent, String icon, String title,
      String sub, Runnable action) {
    LinearLayout r = row(a, icon, title, sub, false, icon(a, "chevron", MUTED), 14, 40, 10);
    parent.addView(r, stacked(a));
    r.setOnClickListener(v -> action.run());
    role(r, "android.widget.Button", null);
    return r;
  }
  /** A card for one choice in a list where exactly one entry is selected. */
  static LinearLayout radioRow(Activity a, LinearLayout parent, String icon, String title,
      String sub, boolean selected, Runnable action) {
    RadioDot dot = new RadioDot(a, selected);
    LinearLayout r = row(a, icon, title, sub, selected, dot, 14, 40, 10);
    parent.addView(r, stacked(a));
    r.setOnClickListener(v -> action.run());
    role(r, "android.widget.RadioButton", dot::on);
    return r;
  }
  /** A compact action card with an icon beside a title and subtitle. Use with tileRow. */
  static LinearLayout tile(Activity a, String icon, String title, String sub, Runnable action) {
    LinearLayout t = new LinearLayout(a);
    t.setGravity(Gravity.CENTER_VERTICAL);
    t.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10));
    t.setMinimumHeight(dp(a, 64));
    t.setFocusable(true);
    t.setClickable(true);
    glassBackground(a, t, false, 14);
    t.addView(icon(a, icon, TEXT), new LinearLayout.LayoutParams(dp(a, 32), dp(a, 32)));
    LinearLayout words = column(a);
    words.setPadding(dp(a, 8), 0, 0, 0);
    TextView x = text(a, title, 15, TEXT);
    x.setTextSize(Math.max(14f, 15f * scale(a)));
    x.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    words.addView(x);
    TextView y = text(a, sub, 13, MUTED);
    words.addView(y);
    t.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
    t.setContentDescription(AppLanguage.text(a, title) + ". " + AppLanguage.text(a, sub));
    t.setOnClickListener(v -> action.run());
    role(t, "android.widget.Button", null);
    return t;
  }
  /** Lays tiles side by side, or stacked on a narrow screen. */
  static void tileRow(Activity a, LinearLayout parent, View... tiles) {
    boolean narrow = contentWidthDp(a) < 480;
    LinearLayout row = new LinearLayout(a);
    row.setOrientation(narrow ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
    for (int i = 0; i < tiles.length; i++) {
      LinearLayout.LayoutParams lp =
          narrow ? new LinearLayout.LayoutParams(-1, -2) : new LinearLayout.LayoutParams(0, -1, 1);
      if (i > 0) {
        if (narrow)
          lp.topMargin = dp(a, 8);
        else
          lp.leftMargin = dp(a, 10);
      }
      row.addView(tiles[i], lp);
    }
    parent.addView(row, stacked(a));
  }
  /** A card with a heading and a set of radio chips, for example "Save type". */
  static LinearLayout choiceGroup(Activity a, LinearLayout parent, String title, String help,
      String[] icons, String[] titles, String[] subs, int selected, IntConsumer changed) {
    LinearLayout c = card(a, parent, title, help);
    ((LinearLayout.LayoutParams) c.getLayoutParams()).topMargin = dp(a, 8);
    boolean narrow = contentWidthDp(a) < 480;
    LinearLayout group = new LinearLayout(a);
    group.setOrientation(narrow ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
    RadioDot[] dots = new RadioDot[titles.length];
    LinearLayout[] chips = new LinearLayout[titles.length];
    for (int i = 0; i < titles.length; i++) {
      final int index = i;
      dots[i] = new RadioDot(a, i == selected);
      chips[i] = row(a, icons[i], titles[i], subs[i], i == selected, dots[i], 12, 28, 8);
      chips[i].setOnClickListener(v -> {
        for (int k = 0; k < chips.length; k++) {
          dots[k].set(k == index);
          glassBackground(a, chips[k], k == index, 12);
        }
        changed.accept(index);
      });
      role(chips[i], "android.widget.RadioButton", dots[i]::on);
      LinearLayout.LayoutParams lp =
          narrow ? new LinearLayout.LayoutParams(-1, -2) : new LinearLayout.LayoutParams(0, -1, 1);
      if (i > 0) {
        if (narrow)
          lp.topMargin = dp(a, 8);
        else
          lp.leftMargin = dp(a, 8);
      }
      group.addView(chips[i], lp);
    }
    c.addView(group, new LinearLayout.LayoutParams(-1, -2));
    return c;
  }
  /** Card with a progress bar and a detail line. A null action makes it read-only. */
  static LinearLayout statusCard(Activity a, LinearLayout parent, String icon, String title,
      String sub, int percent, String detail, Runnable action) {
    LinearLayout c = new LinearLayout(a);
    c.setPadding(dp(a, 18), dp(a, 12), dp(a, 18), dp(a, 12));
    glassBackground(a, c, false, 14);
    c.addView(icon(a, icon, TEXT), new LinearLayout.LayoutParams(dp(a, 40), dp(a, 40)));
    LinearLayout words = column(a);
    words.setPadding(dp(a, 10), 0, 0, 0);
    TextView t = text(a, title, 16, TEXT);
    t.setTextSize(titleSize(a));
    t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    words.addView(t);
    TextView s = text(a, sub, 13, MUTED);
    s.setTextSize(subtitleSize(a));
    s.setPadding(0, dp(a, 2), 0, 0);
    words.addView(s);
    LinearLayout barRow = new LinearLayout(a);
    barRow.setGravity(Gravity.CENTER_VERTICAL);
    barRow.setPadding(0, dp(a, 8), 0, dp(a, 6));
    barRow.addView(new Bar(a, percent), new LinearLayout.LayoutParams(0, dp(a, 6), 1));
    TextView pct = text(a, percent + "%", 13, TEXT);
    pct.setPadding(dp(a, 10), 0, 0, 0);
    barRow.addView(pct);
    words.addView(barRow, new LinearLayout.LayoutParams(-1, -2));
    words.addView(text(a, detail, 13, MUTED));
    c.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
    c.setContentDescription(AppLanguage.text(a, title) + ". " + AppLanguage.text(a, sub) + ". "
        + percent + "%. " + AppLanguage.text(a, detail));
    parent.addView(c, stacked(a));
    if (action != null) {
      c.setFocusable(true);
      c.setClickable(true);
      c.setOnClickListener(v -> action.run());
      role(c, "android.widget.Button", null);
    }
    return c;
  }
  /** Icon over a label: the launcher's quick links. */
  static LinearLayout shortcut(Activity a, String icon, String label, Runnable action) {
    LinearLayout t = column(a);
    t.setGravity(Gravity.CENTER_HORIZONTAL);
    t.setPadding(dp(a, 4), dp(a, 10), dp(a, 4), dp(a, 8));
    t.setMinimumHeight(dp(a, 66));
    t.setFocusable(true);
    t.setClickable(true);
    glassBackground(a, t, false, 14);
    t.addView(icon(a, icon, TEXT), new LinearLayout.LayoutParams(dp(a, 30), dp(a, 30)));
    TextView l = text(a, label, 13, TEXT);
    l.setGravity(Gravity.CENTER);
    l.setSingleLine(true);
    l.setEllipsize(android.text.TextUtils.TruncateAt.END);
    t.addView(l, new LinearLayout.LayoutParams(-2, -2));
    t.setContentDescription(AppLanguage.text(a, label));
    t.setOnClickListener(v -> action.run());
    role(t, "android.widget.Button", null);
    return t;
  }

  // ---- Page header: logo, wordmark, divider, title and a Back pill -----------------------

  private static Bitmap headerLogoBitmap;
  private static synchronized Bitmap headerLogo(Context c) {
    if (headerLogoBitmap == null) {
      try (InputStream in = c.getAssets().open("menu-logo.png")) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 2;
        headerLogoBitmap = BitmapFactory.decodeStream(in, null, o);
      } catch (Exception e) {
        headerLogoBitmap = null;
      }
    }
    return headerLogoBitmap;
  }
  static View brand(Activity a, boolean wordmark) {
    LinearLayout row = new LinearLayout(a);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setContentDescription("FH4Droid");
    ImageView logo = new ImageView(a);
    logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
    Bitmap bitmap = headerLogo(a);
    if (bitmap != null)
      logo.setImageBitmap(bitmap);
    row.addView(logo, new LinearLayout.LayoutParams(dp(a, 52), dp(a, 28)));
    if (wordmark) {
      LinearLayout words = column(a);
      words.setPadding(dp(a, 8), 0, 0, 0);
      TextView name = new TextView(a);
      name.setText("FH4DROID");
      name.setTextSize(19);
      name.setTextColor(TEXT);
      name.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
      name.setLetterSpacing(.04f);
      name.setIncludeFontPadding(false);
      words.addView(name);
      TextView tagline = new TextView(a);
      tagline.setText("YOUR NEXT DRIVE AWAITS");
      tagline.setTextSize(8);
      tagline.setTextColor(MUTED);
      tagline.setLetterSpacing(.14f);
      tagline.setIncludeFontPadding(false);
      tagline.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      words.addView(tagline);
      row.addView(words);
    }
    return row;
  }
  private static View backPill(Activity a, Runnable back) {
    LinearLayout pill = new LinearLayout(a);
    pill.setGravity(Gravity.CENTER);
    pill.setPadding(dp(a, 14), 0, dp(a, 18), 0);
    pill.setFocusable(true);
    pill.setClickable(true);
    glassBackground(a, pill, false, 12);
    pill.addView(icon(a, "left", TEXT), new LinearLayout.LayoutParams(dp(a, 24), dp(a, 24)));
    TextView label = text(a, "Back", 14, TEXT);
    label.setTextSize(Math.max(14f, 15f * scale(a)));
    label.setPadding(dp(a, 6), 0, 0, 0);
    pill.addView(label);
    pill.setContentDescription(AppLanguage.text(a, "Back"));
    pill.setOnClickListener(v -> back.run());
    role(pill, "android.widget.Button", null);
    return pill;
  }
  private static View pageHeader(Activity a, String title, Runnable back) {
    LinearLayout head = new LinearLayout(a);
    head.setGravity(Gravity.CENTER_VERTICAL);
    head.setPadding(0, 0, 0, dp(a, 10));
    head.addView(brand(a, a.getResources().getConfiguration().screenWidthDp >= 480),
        new LinearLayout.LayoutParams(-2, -2));
    View divider = new View(a);
    divider.setBackgroundColor(0x40ffffff);
    LinearLayout.LayoutParams dv = new LinearLayout.LayoutParams(dp(a, 1), dp(a, 28));
    dv.setMargins(dp(a, 14), 0, dp(a, 14), 0);
    head.addView(divider, dv);
    TextView t = text(a, title, 18, TEXT);
    t.setText(t.getText().toString().toUpperCase(java.util.Locale.ROOT));
    t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    t.setLetterSpacing(.06f);
    t.setSingleLine(true);
    t.setEllipsize(android.text.TextUtils.TruncateAt.END);
    if (android.os.Build.VERSION.SDK_INT >= 28)
      t.setAccessibilityHeading(true);
    head.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    head.addView(backPill(a, back), new LinearLayout.LayoutParams(-2, dp(a, 42)));
    return head;
  }

  static AlertDialog dialog(Activity a, String title, View body) {
    final AlertDialog[] ref = new AlertDialog[1];
    View panel = panel(a, title, body, () -> ref[0].dismiss());
    AlertDialog d = new AlertDialog.Builder(a).setView(panel).create();
    ref[0] = d;
    d.setOnKeyListener((dialog,key,event)->ControllerPrompts.handle(a,d.getWindow(),key,event,d::dismiss));
    d.setOnShowListener(x -> {
      Window w = d.getWindow();
      if (w != null) {
        w.setBackgroundDrawableResource(android.R.color.transparent);
        w.setDimAmount(.6f);
        w.setLayout(
            Math.min(dp(a, 640), (int) (a.getResources().getDisplayMetrics().widthPixels * .94f)),
            (int) (a.getResources().getDisplayMetrics().heightPixels * .9f));
      }
      enter(panel);
    });
    return d;
  }
  static void present(Activity a, String title, View body) {
    if (a instanceof PanelHost)
      ((PanelHost) a).showPanel(title, body);
    else
      dialog(a, title, body).show();
  }
  static void choose(Activity a, String title, String[] labels, int selected, IntConsumer changed) {
    LinearLayout body = column(a);
    final AlertDialog[] d = new AlertDialog[1];
    boolean panel = a instanceof PanelHost;
    for (int i = 0; i < labels.length; i++) {
      final int index = i;
      action(a, body, (i == selected ? "✓  " : "") + labels[i], () -> {
        changed.accept(index);
        if (panel)
          ((PanelHost) a).backPanel();
        else
          d[0].dismiss();
      });
    }
    if (panel)
      ((PanelHost) a).showPanel(title, body);
    else {
      d[0] = dialog(a, title, body);
      d[0].show();
    }
  }
  static Switch toggle(Activity a, LinearLayout parent, String title, String help, boolean enabled,
      Consumer<Boolean> changed) {
    LinearLayout c = new LinearLayout(a);
    c.setGravity(Gravity.CENTER_VERTICAL);
    c.setPadding(dp(a, 8), dp(a, 2), dp(a, 8), dp(a, 2));
    TextView label = text(a, title, 14, TEXT);
    label.setPadding(0, dp(a, 5), dp(a, 8), dp(a, 5));
    c.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    if (help != null && !help.isEmpty()) {
      label.setContentDescription(title + ". " + help);
      label.setOnClickListener(v -> explain(a, title, help));
    }
    Switch s = new Switch(a);
    s.setTextColor(TEXT);
    s.setChecked(enabled);
    s.setMinHeight(dp(a, 48));
    s.setContentDescription(title);
    s.setThumbTintList(
        new ColorStateList(new int[][] {new int[] {android.R.attr.state_checked}, new int[0]},
            new int[] {ORANGE, MUTED}));
    s.setTrackTintList(
        new ColorStateList(new int[][] {new int[] {android.R.attr.state_checked}, new int[0]},
            new int[] {0x88d9145a, 0x44ffffff}));
    s.setOnCheckedChangeListener((v, on) -> { changed.accept(on); });
    c.addView(s, new LinearLayout.LayoutParams(-2, -2));
    parent.addView(c, new LinearLayout.LayoutParams(-1, -2));
    return s;
  }
  static void toggle(Activity a, LinearLayout parent, String title, String help,
      java.util.function.BooleanSupplier read, Consumer<Boolean> changed) {
    Switch toggle=toggle(a,parent,title,help,read.getAsBoolean(),changed);
    CompoundButton.OnCheckedChangeListener listener=(v,on)->{changed.accept(on);refreshTree(toggle);};
    onRefresh(toggle,()->{toggle.setOnCheckedChangeListener(null);toggle.setChecked(read.getAsBoolean());toggle.setOnCheckedChangeListener(listener);});
  }
  static SeekBar slider(Activity a, LinearLayout parent, String title, String help, int min,
      int max, int step, int initial, IntFunction<String> label, IntConsumer changed) {
    LinearLayout c = card(a, parent, title, "");
    c.setOnLongClickListener(v -> {
      explain(a, title, help);
      return true;
    });
    initial = Math.max(min, Math.min(max, initial));
    TextView value = text(a, label.apply(initial), 13, ORANGE);
    c.addView(value);
    SeekBar s = new SeekBar(a);
    s.setMax((max - min) / step);
    s.setProgress(Math.max(0, Math.min(s.getMax(), (initial - min) / step)));
    s.setContentDescription(title);
    s.setProgressTintList(ColorStateList.valueOf(RED));
    s.setThumbTintList(ColorStateList.valueOf(TEXT));
    c.addView(s, new LinearLayout.LayoutParams(-1, dp(a, 36)));
    s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      private boolean tracking;
      public void onStartTrackingTouch(SeekBar b) {
        tracking = true;
      }
      public void onProgressChanged(SeekBar b, int p, boolean user) {
        value.setText(label.apply(min + p * step));
        if (user && !tracking)
          changed.accept(min + p * step);
      }
      public void onStopTrackingTouch(SeekBar b) {
        tracking = false;
        changed.accept(min + b.getProgress() * step);
      }
    });
    return s;
  }
  static View panel(Activity a, String title, View body, Runnable back) {
    remember(body);
    if (body.getParent() instanceof ViewGroup)
      ((ViewGroup) body.getParent()).removeView(body);
    // The launcher and setup screens are full pages with the brand header. The in-game menu keeps
    // its compact header inside a drawer.
    boolean page = a instanceof LauncherActivity || a instanceof SetupActivity;
    LinearLayout p = column(a);
    if (page) {
      p.addView(pageHeader(a, title, back));
    } else {
      int pad = dp(a, 10);
      p.setPadding(pad, pad, pad, pad);
      p.setBackground(outlined(a, PANEL, STROKE, 1, 18));
      LinearLayout head = new LinearLayout(a);
      head.setGravity(Gravity.CENTER_VERTICAL);
      Button b = button(a, "‹", false);
      b.setContentDescription("Back");
      head.addView(b, new LinearLayout.LayoutParams(dp(a, 44), -2));
      b.setOnClickListener(v -> back.run());
      TextView t = text(a, title, 20, TEXT);
      t.setTypeface(Typeface.create("sans-serif", 1));
      t.setPadding(dp(a, 12), 0, 0, 0);
      head.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
      t.setSingleLine(true);
      t.setEllipsize(android.text.TextUtils.TruncateAt.END);
      if (a instanceof PinyonShiftActivity && !title.equals("Paused")) {
        Button resume = button(a, "Resume", true);
        head.addView(resume, new LinearLayout.LayoutParams(-2, -2));
        resume.setOnClickListener(v -> ((PinyonShiftActivity) a).resumeDriving());
      }
      p.addView(head);
    }
    if(Boolean.TRUE.equals(body.getTag(FULL_PAGE))){
      if(body.getParent() instanceof ViewGroup)((ViewGroup)body.getParent()).removeView(body);
      p.addView(body,new LinearLayout.LayoutParams(-1,0,1));refreshTree(body);return p;
    }
    PageState state = body.getTag(SCROLL_STATE) instanceof PageState
        ? (PageState) body.getTag(SCROLL_STATE)
        : new PageState();
    body.setTag(SCROLL_STATE, state);
    state.restoring = true;
    ScrollView scroll = new ScrollView(a);
    body.setTag(PAGE_SCROLL, scroll);
    scroll.setClipToPadding(false);
    scroll.setPadding(0, dp(a, 6), 0, dp(a, 6));
    scroll.addView(body);
    // On wide screens keep the content in the left column so the artwork stays visible.
    int width = page && wideLayout(a)
        ? Math.round(a.getResources().getDisplayMetrics().widthPixels * .62f)
        : -1;
    p.addView(scroll, new LinearLayout.LayoutParams(width, 0, 1));
    scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> {
      if (!state.restoring)
        state.y = y;
    });
    refreshTree(body);
    scroll.post(() -> {
      if (body.getTag(PAGE_SCROLL) != scroll)
        return;
      if (state.focus != null && state.focus.isAttachedToWindow())
        state.focus.requestFocus();
      scroll.scrollTo(0, state.y);
      state.restoring = false;
    });
    return p;
  }
  static void explain(Activity a, String title, String help) {
    if (help != null && !help.isEmpty())
      new AlertDialog.Builder(a)
          .setTitle(AppLanguage.text(a,title))
          .setMessage(help)
          .setPositiveButton("Got it", null)
          .show();
  }
  static void onRefresh(View view, Runnable refresh) {
    view.setTag(REFRESH, refresh);
    refresh.run();
  }
  static void refreshTree(View view) {
    if (view.getTag(REFRESH) instanceof Runnable)
      ((Runnable) view.getTag(REFRESH)).run();
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) refreshTree(group.getChildAt(i));
    }
  }
  static void remember(View body) {
    if (!(body.getTag(SCROLL_STATE) instanceof PageState))
      return;
    PageState state = (PageState) body.getTag(SCROLL_STATE);
    if (body.getParent() instanceof ScrollView && !state.restoring) {
      state.y = ((ScrollView) body.getParent()).getScrollY();
      state.focus = body.findFocus();
      state.restoring = true;
    }
  }
  static void rememberTree(View view) {
    remember(view);
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) rememberTree(group.getChildAt(i));
    }
  }
  private static void motion(View v, boolean horizontal) {
    v.animate().cancel();
    v.setAlpha(1);
    v.setTranslationX(0);
    v.setTranslationY(0);
    if (!animations(v.getContext()))
      return;
    float distance = 12 * v.getResources().getDisplayMetrics().density;
    v.setAlpha(0);
    if (horizontal)
      v.setTranslationX(distance);
    else
      v.setTranslationY(distance);
    v.animate()
        .alpha(1)
        .translationX(0)
        .translationY(0)
        .setDuration(180)
        .setInterpolator(new DecelerateInterpolator())
        .start();
  }
  static void enter(View v) {
    motion(v, false);
  }
  static void slideIn(View v) {
    motion(v, true);
  }
  static void immersive(Activity a) {
    a.getWindow().setStatusBarColor(BG);
    a.getWindow().setNavigationBarColor(BG);
    a.getWindow().getDecorView().setSystemUiVisibility(5894);
  }
  static final class Artwork extends View {
    private final Paint paint = new Paint(3);
    private final Bitmap art;
    Artwork(Activity a) {
      super(a);
      art = asset(a, "menu-background.png");
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    private Bitmap asset(Activity a, String n) {
      try (InputStream in = a.getAssets().open(n)) {
        return BitmapFactory.decodeStream(in);
      } catch (Exception e) {
        return null;
      }
    }
    protected void onDraw(Canvas c) {
      float w = getWidth(), h = getHeight(), d = getResources().getDisplayMetrics().density;
      c.drawColor(BG);
      if (art != null) {
        float s = Math.max(w / art.getWidth(), h / art.getHeight());
        float bw = art.getWidth() * s, bh = art.getHeight() * s;
        c.drawBitmap(
            art, null, new RectF((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2), paint);
      }
      // Dusk grade: deep purple sky, a magenta/orange glow at the horizon and a dark road.
      paint.setShader(new LinearGradient(0, 0, 0, h,
          new int[] {0xcc1b0b3d, 0x70a3175f, 0x66ff7a3d, 0xe00a0710},
          new float[] {0, .45f, .62f, 1}, Shader.TileMode.CLAMP));
      c.drawRect(0, 0, w, h, paint);
      // Keep the left side calm so text and cards stay readable over the picture.
      paint.setShader(new LinearGradient(
          0, 0, w, 0, new int[] {0xe60a0810, 0x880a0810, 0x100a0810}, null, Shader.TileMode.CLAMP));
      c.drawRect(0, 0, w, h, paint);
      paint.setShader(null);
      paint.setShader(new LinearGradient(
          0, h - 3 * d, w, h, new int[] {RED, ORANGE}, null, Shader.TileMode.CLAMP));
      c.drawRect(0, h - 3 * d, w, h, paint);
      paint.setShader(null);
    }
  }
  private FestivalUi() {}
}
