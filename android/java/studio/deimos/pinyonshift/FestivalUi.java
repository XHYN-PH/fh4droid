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
  static final int BG = 0xff120c0d, PANEL = 0xf21b1214, TEXT = 0xfffff5ef, MUTED = 0xffc5b8b3,
                   RED = 0xfff1393e, ORANGE = 0xffff883b;
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
    v.setText(s);
    v.setTextSize(size * scale(a));
    v.setTextColor(color);
    v.setTypeface(Typeface.create("sans-serif-condensed", 0));
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
    b.setText(title);
    b.setAllCaps(false);
    b.setTextSize(14 * scale(a));
    b.setTextColor(primary ? BG : TEXT);
    b.setTypeface(Typeface.create("sans-serif-condensed", 1));
    b.setGravity(Gravity.CENTER);
    b.setIncludeFontPadding(false);
    GradientDrawable d = primary
        ? new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {RED, ORANGE})
        : surface(a, 0xd52b2023, 11);
    d.setCornerRadius(dp(a, 11));
    if (!primary)
      d.setStroke(dp(a, 1), 0xff624247);
    b.setBackgroundTintList(null);
    GradientDrawable focused=surface(a,primary?0xffffaf70:0xff50302a,11);
    focused.setStroke(dp(a,2),primary?TEXT:ORANGE);
    StateListDrawable states=new StateListDrawable();
    states.addState(new int[]{android.R.attr.state_focused},focused);
    states.addState(new int[]{android.R.attr.state_selected},focused);
    states.addState(new int[]{},d);
    b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),states,surface(a,Color.WHITE,11)));
    b.setFocusable(true);
    b.setMinimumHeight(dp(a, 44));
    b.setMinHeight(dp(a, 44));
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
    int p = dp(a, 8);
    c.setPadding(p, p, p, p);
    c.setBackground(surface(a, 0xdb292024, 13));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(a, 6);
    parent.addView(c, lp);
    TextView t = text(a, title, 15, TEXT);
    t.setTypeface(Typeface.create("sans-serif-condensed", 1));
    c.addView(t);
    if (help != null && !help.isEmpty()) {
      TextView h = text(a, help, 12, MUTED);
      h.setPadding(0, dp(a, 4), 0, dp(a, 6));
      c.addView(h);
    }
    return c;
  }
  static AlertDialog dialog(Activity a, String title, View body) {
    final AlertDialog[] ref = new AlertDialog[1];
    View panel = panel(a, title, body, () -> ref[0].dismiss());
    AlertDialog d = new AlertDialog.Builder(a).setView(panel).create();
    ref[0] = d;
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
  static void toggle(Activity a, LinearLayout parent, String title, String help, boolean enabled,
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
    s.setMinHeight(dp(a, 44));
    s.setContentDescription(title);
    s.setThumbTintList(
        new ColorStateList(new int[][] {new int[] {android.R.attr.state_checked}, new int[0]},
            new int[] {ORANGE, MUTED}));
    s.setOnCheckedChangeListener((v, on) -> { changed.accept(on); });
    c.addView(s, new LinearLayout.LayoutParams(-2, -2));
    parent.addView(c, new LinearLayout.LayoutParams(-1, -2));
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
    LinearLayout p = column(a);
    int pad = dp(a, 10);
    p.setPadding(pad, pad, pad, pad);
    p.setBackground(surface(a, PANEL, 18));
    LinearLayout head = new LinearLayout(a);
    head.setGravity(Gravity.CENTER_VERTICAL);
    Button b = button(a, "‹", false);
    b.setContentDescription("Back");
    head.addView(b, new LinearLayout.LayoutParams(dp(a, 44), -2));
    b.setOnClickListener(v -> back.run());
    TextView t = text(a, title, 20, TEXT);
    t.setTypeface(Typeface.create("sans-serif-condensed", 1));
    t.setPadding(dp(a, 12), 0, 0, 0);
    head.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    t.setSingleLine(true);
    t.setEllipsize(android.text.TextUtils.TruncateAt.END);
    if (a instanceof PinyonShiftActivity) {
      Button resume = button(a, "Resume", true);
      head.addView(resume, new LinearLayout.LayoutParams(-2, -2));
      resume.setOnClickListener(v -> ((PinyonShiftActivity) a).resumeDriving());
    }
    p.addView(head);
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
    p.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
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
          .setTitle(title)
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
    private final Bitmap art, logo;
    Artwork(Activity a) {
      super(a);
      art = asset(a, "menu-background.png");
      logo = asset(a, "menu-logo.png");
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
      paint.setShader(new LinearGradient(
          0, 0, w, 0, new int[] {0xe6140c0e, 0x77140c0e, 0x17140c0e}, null, Shader.TileMode.CLAMP));
      c.drawRect(0, 0, w, h, paint);
      paint.setShader(new LinearGradient(0, 0, 0, h, new int[] {0x44120c0d, 0x00120c0d, 0xf5120c0d},
          new float[] {0, .35f, 1}, Shader.TileMode.CLAMP));
      c.drawRect(0, 0, w, h, paint);
      paint.setShader(null);
      if (logo != null) {
        float lw = Math.min(115 * d, w * .18f), lh = lw * logo.getHeight() / logo.getWidth(),
              right = w - 22 * d, top = 12 * d;
        c.drawBitmap(logo, null, new RectF(right - lw, top, right, top + lh), paint);
      }
      paint.setShader(new LinearGradient(
          0, h - 3 * d, w, h, new int[] {RED, ORANGE}, null, Shader.TileMode.CLAMP));
      c.drawRect(0, h - 3 * d, w, h, paint);
      paint.setShader(null);
    }
  }
  private FestivalUi() {}
}
