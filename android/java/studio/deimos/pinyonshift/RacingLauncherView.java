package studio.deimos.pinyonshift;

import android.content.Context;
import android.graphics.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

/** The approved race-grid launcher. Preview and app render this same Android view. */
public final class RacingLauncherView extends FrameLayout {
  public interface Actions {
    void open(String action);
  }

  public enum Design {
    FESTIVAL,
    GRID
  }

  public static final class Model {
    public boolean ready = true, running = false, busy = false;
    public String notice = "";
    public String graphics = "Balanced · 30 FPS";
    public String version = "v0.8.2-polish-test3";
  }

  private static final int WHITE = 0xfff6f4f1, MUTED = 0xffaeb8c5, PINK = 0xffff326e;
  private final Model model;
  private final Design design;
  private final Actions actions;
  private final Bitmap photo, logo;
  private int viewportWidth, viewportHeight;
  private float layoutScale = 1f;
  private boolean compact;
  private Insets contentInsets = Insets.NONE;
  private LinearLayout content;
  private final Map<String, View> focus = new LinkedHashMap<>();
  private String page = "home", lastHome = "play", lastContentFocus = "play";
  private int heldDirection;
  private final android.hardware.input.InputManager.InputDeviceListener devices =
      new android.hardware.input.InputManager.InputDeviceListener() {
        public void onInputDeviceAdded(int id) {}

        public void onInputDeviceChanged(int id) {}

        public void onInputDeviceRemoved(int id) {
          stopStick();
        }
      };

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    android.hardware.input.InputManager input =
        (android.hardware.input.InputManager) getContext().getSystemService(Context.INPUT_SERVICE);
    if (input != null)
      input.registerInputDeviceListener(
          devices, new android.os.Handler(android.os.Looper.getMainLooper()));
  }

  private final Runnable stickRepeat =
      new Runnable() {
        public void run() {
          if (heldDirection == 0 || !isAttachedToWindow()) {
            stopStick();
            return;
          }
          moveStick(heldDirection);
          postDelayed(this, 140);
        }
      };

  private void stopStick() {
    heldDirection = 0;
    removeCallbacks(stickRepeat);
  }

  private void moveStick(int direction) {
    if (externalBody != null) {
      View current = findFocus();
      View target = current == null ? null : current.focusSearch(direction);
      if (target != null) target.requestFocus();
    } else move(direction);
  }

  @Override
  public void onWindowFocusChanged(boolean focused) {
    super.onWindowFocusChanged(focused);
    if (!focused) stopStick();
  }

  private final Map<String, String> pageFocus = new HashMap<>();
  private final Map<String, Integer> pageScroll = new HashMap<>();
  private ScrollView pageScroller;
  private HorizontalScrollView tileScroller;
  private View externalBody;
  private String externalTitle = "", signature = "";
  private boolean reversing;
  private Runnable restoreAfterResize;

  private float uiScale() {
    Object raw = getContext().getSharedPreferences("mobile", 0).getAll().get("ui_scale");
    float n = raw instanceof Number ? ((Number) raw).floatValue() : .82f;
    return Float.isFinite(n) ? Math.max(.7f, Math.min(1.05f, n)) / .82f : 1f;
  }

  private int columns() {
    return viewportWidth < dp(580) || (viewportWidth < dp(780) && largeText()) ? 2 : 3;
  }

  private boolean largeText() {
    return textScale() > 1.2f;
  }

  private float textScale() {
    return getResources().getConfiguration().fontScale * uiScale();
  }

  private boolean home() {
    return externalBody == null && page.equals("home");
  }

  public void update() {
    String next =
        model.ready
            + "|"
            + model.running
            + "|"
            + model.busy
            + "|"
            + model.graphics
            + "|"
            + model.version
            + "|"
            + model.notice;
    if (!next.equals(signature)) {
      remember();
      render(!isLaidOut());
    }
  }

  private void remember() {
    if (externalBody != null) {
      FestivalUi.remember(externalBody);
      return;
    }
    String selected = focusedAction();
    if (!selected.isEmpty()) pageFocus.put(page, selected);
    if (pageScroller != null) pageScroll.put(page, pageScroller.getScrollY());
  }

  public void showContent(String title, View body, boolean back) {
    remember();
    externalTitle = title;
    externalBody = body;
    reversing = back;
    render();
  }

  public void clearContent(boolean back) {
    if (externalBody == null) return;
    remember();
    externalBody = null;
    reversing = back;
    render();
  }

  public boolean navigateBack() {
    if (externalBody != null) return false;
    if (!page.equals("home")) {
      showPage("home", true);
      return true;
    }
    return false;
  }

  private View firstFocusable(View root) {
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) {
        View found = firstFocusable(group.getChildAt(i));
        if (found != null) return found;
      }
    }
    return root.isFocusable() && root.isEnabled() && root.getVisibility() == VISIBLE ? root : null;
  }

  public void focusContent() {
    View view = externalBody == null ? null : firstFocusable(externalBody);
    if (view != null) view.requestFocus();
  }

  @Override
  protected void onDetachedFromWindow() {
    android.hardware.input.InputManager input =
        (android.hardware.input.InputManager) getContext().getSystemService(Context.INPUT_SERVICE);
    if (input != null) input.unregisterInputDeviceListener(devices);
    super.onDetachedFromWindow();
    stopStick();
    MenuMotion.stop(this);
  }

  public RacingLauncherView(
      Context context, Design design, Model model, Bitmap photo, Bitmap logo, Actions actions) {
    super(context);
    this.design = design;
    this.model = model;
    this.photo = photo;
    this.logo = logo;
    this.actions = actions;
    android.content.res.Configuration configuration = getResources().getConfiguration();
    viewportWidth = dp(configuration.screenWidthDp);
    viewportHeight = dp(configuration.screenHeightDp);
    setFocusableInTouchMode(true);
    setClipChildren(false);
    render();
  }

  void setContentInsets(Insets insets) {
    if (contentInsets.equals(insets)) return;
    contentInsets = insets;
    padContent();
  }

  @Override
  protected void onMeasure(int widthSpec, int heightSpec) {
    int width = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED ? viewportWidth
        : Math.max(1, MeasureSpec.getSize(widthSpec) - contentInsets.left - contentInsets.right);
    int height = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED ? viewportHeight
        : Math.max(1, MeasureSpec.getSize(heightSpec) - contentInsets.top - contentInsets.bottom);
    boolean changed = width != viewportWidth || (height != viewportHeight && externalBody == null);
    viewportWidth = width;
    viewportHeight = height;
    if (changed) {
      remember();
      render(false);
    }
    super.onMeasure(widthSpec, heightSpec);
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    Runnable restore = restoreAfterResize;
    restoreAfterResize = null;
    if (restore != null) restore.run();
  }

  private float dashboardScale() {
    if (!home() || design != Design.GRID) return 1f;
    float widthFit = viewportWidth / (float) screenDp(640);
    float heightFit = viewportHeight / (float) screenDp(348);
    return Math.max(.72f, Math.min(1f, Math.min(widthFit, heightFit)));
  }

  private int sidePadding() {
    return dp(viewportWidth < screenDp(460) ? 12 : compact ? 24 : 30);
  }

  private int contentWidth() {
    return Math.max(1, viewportWidth - 2 * sidePadding());
  }

  private void padContent() {
    int side = sidePadding();
    int top = compact ? 8 : 18;
    int bottom = compact ? 4 : 10;
    content.setPadding(side + contentInsets.left, dp(top) + contentInsets.top,
        side + contentInsets.right, dp(bottom) + contentInsets.bottom);
  }

  private int cardHeight(int value) {
    return Math.round(value * Math.max(1f, textScale()));
  }

  private int screenDp(float value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private int dp(float value) {
    return screenDp(value * layoutScale);
  }

  private TextView text(String value, float size, int color, boolean strong) {
    TextView t = new TextView(getContext());
    t.setText(AppLanguage.text(getContext(), value));
    t.setTextColor(color);
    float compactTypeScale = compact && home() && design == Design.GRID ? .90f : 1f;
    t.setTextSize(size * uiScale() * layoutScale * compactTypeScale);
    t.setIncludeFontPadding(false);
    t.setTypeface(Typeface.create(strong ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
    t.setMaxLines(1);
    t.setEllipsize(TextUtils.TruncateAt.END);
    t.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    return t;
  }

  private LinearLayout column() {
    LinearLayout result = new LinearLayout(getContext());
    result.setOrientation(LinearLayout.VERTICAL);
    result.setClipChildren(false);
    return result;
  }

  private LinearLayout row() {
    LinearLayout result = new LinearLayout(getContext());
    result.setGravity(Gravity.CENTER_VERTICAL);
    result.setClipChildren(false);
    return result;
  }

  private LinearLayout.LayoutParams lp(int width, int height) {
    return new LinearLayout.LayoutParams(
        width < 0 ? width : dp(width), height < 0 ? height : dp(height));
  }

  private void gap(LinearLayout parent, int size) {
    parent.addView(new View(getContext()), lp(1, size));
  }

  private void render() {
    render(true);
  }

  private void render(boolean animate) {
    stopStick();
    MenuMotion.stop(this);
    restoreAfterResize = null;
    removeAllViews();
    focus.clear();
    tileScroller = null;
    signature =
        model.ready
            + "|"
            + model.running
            + "|"
            + model.busy
            + "|"
            + model.graphics
            + "|"
            + model.version
            + "|"
            + model.notice;
    compact = viewportHeight < screenDp(400);
    layoutScale = dashboardScale();
    addView(new Backdrop(getContext()), new FrameLayout.LayoutParams(-1, -1));
    content = column();
    padContent();
    addView(content, new FrameLayout.LayoutParams(-1, -1));
    content.addView(header(), lp(-1, compact ? 44 : 48));
    gap(content, compact ? 8 : 16);
    LinearLayout slot = column();
    slot.setTag("launcher-menu-viewport");
    slot.setClipChildren(true);
    // Clip menu drawing and animations to the space above the footer.
    slot.addOnLayoutChangeListener((view, l, t, r, b, oldL, oldT, oldR, oldB) ->
        view.setClipBounds(new Rect(0, 0, r - l, b - t)));
    content.addView(slot, new LinearLayout.LayoutParams(-1, 0, 1));
    ScrollView scroll = new ScrollView(getContext());
    pageScroller = scroll;
    scroll.setFillViewport(true);
    scroll.setSmoothScrollingEnabled(true);
    scroll.setVerticalScrollBarEnabled(false);
    scroll.setFocusable(false);
    LinearLayout body = column();
    body.setGravity(Gravity.CENTER_VERTICAL);
    scroll.addView(body);
    slot.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (home()) {
      gap(content, 12);
      LinearLayout footer = footer();
      footer.setTag("launcher-footer");
      content.addView(footer, lp(-1, -2));
    }
    if (externalBody != null) {
      LinearLayout headingRow=row();TextView heading=text(externalTitle,compact?23:25,WHITE,true);heading.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));headingRow.addView(heading,new LinearLayout.LayoutParams(0,-2,1));Object header=externalBody.getTag(FestivalUi.HEADER_ACTIONS);if(header instanceof View){View actions=(View)header;if(actions.getParent() instanceof ViewGroup)((ViewGroup)actions.getParent()).removeView(actions);headingRow.addView(actions,new LinearLayout.LayoutParams(-2,-2));}body.addView(headingRow,lp(-1,-2));
      gap(body,compact?8:12);
      if (externalBody.getParent() instanceof ViewGroup)
        ((ViewGroup) externalBody.getParent()).removeView(externalBody);
      boolean full = Boolean.TRUE.equals(externalBody.getTag(FestivalUi.FULL_PAGE));
      if (full) {
        scroll.removeView(body);
        slot.removeView(scroll);
        slot.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        body.setGravity(Gravity.TOP);
        body.addView(externalBody, new LinearLayout.LayoutParams(-1, 0, 1));
      } else {
        body.setGravity(Gravity.TOP);
        body.addView(externalBody, lp(-1, -2));
        FestivalUi.attachPageScroll(externalBody, scroll);
      }
    } else if (page.equals("home")) {
      if (design == Design.FESTIVAL) festival(body);
      else {
        // Measure the actual header and footer before sharing the space left for the cards.
        content.measure(MeasureSpec.makeMeasureSpec(viewportWidth + contentInsets.left
            + contentInsets.right, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(viewportHeight + contentInsets.top
                + contentInsets.bottom, MeasureSpec.EXACTLY));
        grid(body, scroll.getMeasuredHeight(), animate || !isLaidOut());
      }
    } else secondary(body);
    wireFocus();
    final View expected = externalBody;
    final String expectedPage = page;
    Runnable restore = () -> {
      if (pageScroller != scroll || externalBody != expected || !page.equals(expectedPage)) return;
      if (externalBody != null) FestivalUi.restorePage(externalBody);
      else {
        scroll.scrollTo(0, pageScroll.getOrDefault(page, 0));
        requestActionFocus(pageFocus.getOrDefault(page, page.equals("home") ? lastHome : firstCard()));
      }
    };
    if (animate) {
      post(restore);
      if (!home() || design != Design.GRID) MenuMotion.page(slot, reversing);
    } else restoreAfterResize = restore;
    reversing = false;
  }

  private LinearLayout header() {
    LinearLayout head = row(), brand = row();
    boolean isHome = home();
    FocusCard action = isHome ? card("profile", "user", "Local profile", "", false, false)
        : card("back", "arrow", "Back", "", false, false);
    int actionMargin = isHome ? dp(compact ? 12 : 18) : 0;
    boolean largeLabel = textScale() * layoutScale > 1.2f;
    int preferredAction = isHome ? (largeLabel ? 210 : 143) : (largeLabel ? 130 : 92);
    int actionWidth = Math.min(Math.max(dp(preferredAction), preferredWidth(action)), contentWidth() * 45 / 100);
    ImageView mark = new ImageView(getContext());
    mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
    mark.setImageBitmap(logo);
    mark.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    brand.addView(mark, lp(58, 32));
    TextView title = text("FH4DROID", compact ? 22 : 24, WHITE, true);
    title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
    title.setLetterSpacing(.035f);
    title.setMaxWidth(Math.max(0, contentWidth() - actionWidth - actionMargin - dp(68)));
    LinearLayout.LayoutParams label = lp(-2, -2);
    label.leftMargin = dp(10);
    brand.addView(title, label);
    head.addView(brand, lp(-2, -2));
    head.addView(new View(getContext()), new LinearLayout.LayoutParams(0, 1, 1));
    if (!isHome) {
      head.addView(action, new LinearLayout.LayoutParams(actionWidth, dp(40)));
      action.setOnClickListener(v -> back());
    } else {
      LinearLayout state = row();
      View dot = new View(getContext());
      android.graphics.drawable.GradientDrawable green =
          new android.graphics.drawable.GradientDrawable();
      green.setShape(1);
      green.setColor(model.ready ? 0xff75d8a3 : 0xfff4bf6a);
      dot.setBackground(green);
      state.addView(dot, lp(6, 6));
      TextView ready =
          text(
              model.busy
                  ? "Working…"
                  : model.running
                      ? "Game running"
                      : model.ready ? "Game files ready" : "Setup needed",
              11,
              MUTED,
              false);
      LinearLayout.LayoutParams stat = lp(-2, -2);
      stat.leftMargin = dp(7);
      state.addView(ready, stat);
      if (preferredWidth(brand) + preferredWidth(state) + actionMargin + actionWidth <= contentWidth())
        head.addView(state, lp(-2, -2));
      LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(actionWidth, dp(40));
      pp.leftMargin = actionMargin;
      head.addView(action, pp);
      action.setOnClickListener(v -> open("profile"));
    }
    return head;
  }

  private void title(LinearLayout body, String heading, String sub, int size) {
    TextView title = text(heading, size, WHITE, true);
    title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
    title.setTag("launcher-heading");
    body.addView(title, lp(-1, -2));
    title.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    title.setAccessibilityHeading(true);
    TextView summary = text(compact && home() ? sub.replace("\n", " · ") : sub, 12, MUTED, false);
    summary.setTag("launcher-summary");
    if (home() && !compact) summary.setMaxLines(2);
    LinearLayout.LayoutParams sl = lp(-1, -2);
    sl.topMargin = dp(6);
    body.addView(summary, sl);
  }

  private void festival(LinearLayout body) {
    LinearLayout panel = column();
    int width = Math.round(getResources().getConfiguration().screenWidthDp * .57f);
    body.addView(panel, lp(Math.min(560, width), -2));
    title(
        panel,
        model.ready ? "Ready to drive." : "Let’s set up your game.",
        model.graphics,
        compact ? 22 : 28);
    gap(panel, compact ? 10 : 12);
    FocusCard play =
        card(
            "play",
            "play",
            model.running ? "Resume" : model.ready ? "Play" : "Set up game",
            model.running ? "Return to your session" : "Start Forza Horizon",
            true,
            false);
    panel.addView(play, lp(-1, cardHeight(compact ? 70 : 82)));
    play.setOnClickListener(v -> activate("play"));
    gap(panel, 8);
    LinearLayout first = row(), second = row();
    panel.addView(first, lp(-1, cardHeight(compact ? 49 : 58)));
    gap(panel, 8);
    panel.addView(second, lp(-1, cardHeight(compact ? 49 : 58)));
    addPair(
        first,
        "settings",
        "sliders",
        "Settings",
        "Graphics & sound",
        "controls",
        "controller",
        "Controls",
        "Touch & controller",
        10);
    addPair(
        second,
        "files",
        "folder",
        "Game files",
        "Installation & storage",
        "more",
        "grid",
        "More",
        "Mods, saves & tools",
        10);
  }

  private void addPair(
      LinearLayout row,
      String a,
      String ai,
      String at,
      String as,
      String b,
      String bi,
      String bt,
      String bs,
      int spacing) {
    FocusCard left = card(a, ai, at, as, false, false), right = card(b, bi, bt, bs, false, false);
    LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, -1, 1),
        r = new LinearLayout.LayoutParams(0, -1, 1);
    r.leftMargin = dp(spacing);
    row.addView(left, l);
    row.addView(right, r);
    left.setOnClickListener(v -> open(a));
    right.setOnClickListener(v -> open(b));
  }

  private int menuCardHeight(){return cardHeight(compact ? 74 : 84);}

  private int categoryColumns() {
    return page.equals("settings") && (viewportWidth < screenDp(540) || largeText()) ? 2 : columns();
  }

  private void grid(LinearLayout body, int availableHeight, boolean animate) {
    title(body, model.running ? "Game running" : model.ready ? "Ready to drive" : "Game files needed",
        model.graphics, compact ? 23 : 29);
    if (!model.notice.isEmpty()) {
      TextView notice = text(model.notice, 11, 0xfff4bf6a, false);
      notice.setMaxLines(2);
      body.addView(notice, lp(-1, -2));
    }
    gap(body, compact ? 10 : 16);
    body.measure(MeasureSpec.makeMeasureSpec(contentWidth(), MeasureSpec.EXACTLY),
        MeasureSpec.UNSPECIFIED);
    int spacing = compact ? 12 : 10;
    int cardGap = dp(spacing);
    int cardWidth = Math.max(1, (contentWidth() - 2 * cardGap) / 3);
    int availableForCards = availableHeight - body.getMeasuredHeight();
    String playTitle = model.running ? "Resume" : model.ready ? "Play" : "Set up game";
    String playCaption = model.running ? "Return to your session"
        : model.ready ? "Start Forza Horizon" : "Choose your game files";
    FocusCard play = card("play", "play", playTitle, playCaption, true, true);
    LinearLayout first = row(), second = row();
    addPair(first, "settings", "sliders", "Settings", "Graphics & sound", "controls", "controller",
        "Controls", "Touch & controller", spacing);
    addPair(second, "files", "folder", "Game files", "Installation & storage", "more", "grid",
        "More", "Mods, saves & tools", spacing);
    FocusCard[] cards = {(FocusCard) first.getChildAt(0), (FocusCard) first.getChildAt(1),
        (FocusCard) second.getChildAt(0), (FocusCard) second.getChildAt(1)};
    // Play spans both rows. Size the shared rows to the available space and their text.
    int minimumRow = (play.minimumHeight(cardWidth) - cardGap + 1) / 2;
    for (FocusCard card : cards) minimumRow = Math.max(minimumRow, card.minimumHeight(cardWidth));
    int rowHeight = Math.max(minimumRow, Math.min(dp(menuCardHeight()), (availableForCards - cardGap) / 2));
    int areaHeight = 2 * rowHeight + cardGap;

    LinearLayout area = row();
    body.addView(area, new LinearLayout.LayoutParams(-1, areaHeight));
    area.addView(play, new LinearLayout.LayoutParams(0, -1, 1));
    play.setOnClickListener(v -> activate("play"));
    play.fitHeight(cardWidth, areaHeight);
    LinearLayout options = column();
    LinearLayout.LayoutParams optionColumns = new LinearLayout.LayoutParams(cardGap, -1, 2);
    optionColumns.leftMargin = cardGap;
    area.addView(options, optionColumns);
    options.addView(first, new LinearLayout.LayoutParams(-1, rowHeight));
    gap(options, spacing);
    options.addView(second, new LinearLayout.LayoutParams(-1, rowHeight));
    for (FocusCard card : cards) card.fitHeight(cardWidth, rowHeight);
    if (animate) MenuMotion.dashboard(area, first, second);
  }

  private void secondary(LinearLayout body) {
    String heading, description;
    String[][] entries;
    switch (page) {
      case "settings":
        heading = "Settings";
        description = "Tune graphics, sound and controls.";
        entries = SettingsHub.CATEGORY_CARDS;
        break;
      case "controls":
        heading = "Choose how you drive.";
        description = "Touch and controller options, in one place.";
        entries =
            new String[][] {
              {"touch-layout", "layout-edit", "Touch layout", "Edit buttons & steering"},
              {"touch-settings", "touch-steering", "Touch buttons", "Racing labels & input"},
              {"controller", "controller", "Controller", "Mappings & calibration"},
              {"tilt", "phone-motion", "Phone steering", "Tilt & vibration"},
              {"saved-layouts", "layout-library", "Saved layouts", "Switch or apply presets"}
            };
        break;
      case "files":
        heading = "Your game files.";
        description = "Use your own supported game folder or disc image.";
        entries =
            new String[][] {
              {"choose-game", "folder", "Installation", "Choose or check game files"},
              {"storage", "save", "Storage", "Manage installed data"}
            };
        break;
      case "profile":
        heading = "Your local profile.";
        description = "Profile information stays on your device.";
        entries =
            new String[][] {
              {"edit-profile", "user", "Edit profile", "Name & gamer picture"},
              {"achievements", "trophy", "Achievements", "Your local progress"},
              {"saves", "save", "Save backups", "Import, export & restore"}
            };
        break;
      default:
        heading = "Manage your game.";
        description = "Mods, drivers, backups and support.";
        entries =
            new String[][] {
              {"mods", "grid", "Mods & DLC", "Packages & shader lists"},
              {"drivers", "chip", "Graphics drivers", "Choose & test a driver"},
              {"saves", "save", "Save backups", "Import, export & restore"},
              {"achievements", "trophy", "Achievements", "View local progress"},
              {"reports", "pulse", "Problem reports", "Logs & diagnostics"},
              {"about", "info", "About", "Version, links & credits"}
            };
    }
    title(body, heading, description, compact ? 25 : 30);
    gap(body, compact ? 14 : 20);
    int columns = categoryColumns();
    for (int index = 0; index < entries.length; index += columns) {
      LinearLayout line = row();
      body.addView(line, lp(-1, menuCardHeight()));
      for (int c = 0; c < columns; c++) {
        if (index + c >= entries.length) {
          line.addView(new View(getContext()), new LinearLayout.LayoutParams(0, 1, 1));
          continue;
        }
        String[] item = entries[index + c];
        FocusCard tile = card(item[0], item[1], item[2], item[3], false, false);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
        if (c > 0) p.leftMargin = dp(10);
        line.addView(tile, p);
        tile.setOnClickListener(v -> actions.open(item[0]));
      }
      if (index + columns < entries.length) gap(body, 10);
    }
  }

  private LinearLayout footer() {
    LinearLayout footer = row(), hints = row();
    int linkHeight = 32;
    hints.addView(new Icon(getContext(), "dpad", MUTED), lp(17, 17));
    hints.addView(text(" Move", 10, MUTED, false));
    key(hints, "A", 0xff89d9a6, "Select");
    key(hints, "B", 0xfff4a4a6, "Back");
    footer.addView(hints, lp(-2, -2));
    LinearLayout social = row();
    for (String link : new String[] {"YouTube", "Telegram", "GitHub"}) {
      TextView view = text(link, 10.5f, MUTED, false);
      view.setGravity(Gravity.CENTER);
      view.setPadding(dp(8), 0, dp(8), 0);
      view.setMinimumHeight(dp(linkHeight));
      view.setFocusable(true);
      view.setFocusableInTouchMode(true);
      FestivalUi.touchFocus(view);
      view.setClickable(true);
      android.graphics.drawable.StateListDrawable background =
          new android.graphics.drawable.StateListDrawable();
      android.graphics.drawable.GradientDrawable focused =
          new android.graphics.drawable.GradientDrawable();
      focused.setColor(0x22ffffff);
      focused.setCornerRadius(dp(4));
      focused.setStroke(dp(1), PINK);
      background.addState(new int[] {android.R.attr.state_focused}, focused);
      background.addState(
          new int[] {}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
      view.setBackground(background);
      register(link.toLowerCase(Locale.ROOT), view);
      view.setOnClickListener(v -> actions.open(link.toLowerCase(Locale.ROOT)));
      view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
      social.addView(view, lp(-2, linkHeight));
    }
    TextView version = text(model.version, 10.5f, MUTED, false);
    version.setTag("installed-version");
    version.setGravity(Gravity.RIGHT);
    int width = contentWidth();
    int socialWidth = preferredWidth(social);
    if (preferredWidth(hints) + preferredWidth(version) + socialWidth > width) {
      LinearLayout both = column();
      footer.addView(version, new LinearLayout.LayoutParams(0, -2, 1));
      both.addView(footer, lp(-1, linkHeight));
      social.setGravity(Gravity.CENTER);
      if (socialWidth > width) {
        for (int i = 0; i < social.getChildCount(); i++)
          social.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(0, dp(linkHeight), 1));
      }
      both.addView(social, lp(-1, linkHeight));
      return both;
    }
    footer.addView(new View(getContext()), new LinearLayout.LayoutParams(0, 1, 1));
    footer.addView(social, lp(-2, -2));
    footer.addView(new View(getContext()), new LinearLayout.LayoutParams(0, 1, 1));
    footer.addView(version, lp(-2, -2));
    footer.setMinimumHeight(dp(compact ? linkHeight : 36));
    return footer;
  }

  private int preferredWidth(View view) {
    view.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
    return view.getMeasuredWidth();
  }

  private void key(LinearLayout row, String value, int color, String caption) {
    TextView key = text(value, 10, color, true);
    key.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams p = lp(19, 20);
    p.leftMargin = dp(12);
    row.addView(key, p);
    row.addView(text(" " + caption, 10, MUTED, false));
  }

  private FocusCard card(
      String id, String icon, String title, String subtitle, boolean primary, boolean vertical) {
    FocusCard view = new FocusCard(getContext(), icon, title, subtitle, primary, vertical);
    register(id, view);
    return view;
  }

  private String firstCard() {
    for (String id : focus.keySet())
      if (!Arrays.asList("back", "youtube", "telegram", "github").contains(id)) return id;
    return "back";
  }

  private void register(String id, View view) {
    view.setId(View.generateViewId());
    view.setNextFocusUpId(view.getId());
    view.setNextFocusDownId(view.getId());
    view.setNextFocusLeftId(view.getId());
    view.setNextFocusRightId(view.getId());
    view.setTag(id);
    view.setFocusable(true);
    view.setFocusableInTouchMode(true);
    view.setOnFocusChangeListener(
        (focused, on) -> {
          focused.invalidate();
          MenuMotion.focus(focused, on);
          if (on && !Arrays.asList("profile", "back", "youtube", "telegram", "github").contains(id))
            lastContentFocus = id;
          if (on && tileScroller != null) {
            android.graphics.Rect rect = new android.graphics.Rect();
            focused.getDrawingRect(rect);
            focused.requestRectangleOnScreen(rect, false);
          }
        });
    MenuMotion.press(view);
    focus.put(id, view);
  }

  public boolean requestActionFocus(String id) {
    View view = focus.get(id);
    return view != null && view.requestFocus();
  }

  public String focusedAction() {
    View f = findFocus();
    Object tag = f == null ? null : f.getTag();
    return tag instanceof String && focus.containsKey(tag) ? (String) tag : "";
  }

  public String navigationFocus() {
    String current = focusedAction();
    return current.isEmpty()
        ? pageFocus.getOrDefault(page, page.equals("home") ? lastHome : firstCard())
        : current;
  }

  public String homeFocus() {
    return home() ? navigationFocus() : pageFocus.getOrDefault("home", lastHome);
  }

  public void restoreHomeFocus(String id) {
    if (Arrays.asList(
            "play",
            "settings",
            "controls",
            "files",
            "more",
            "profile",
            "youtube",
            "telegram",
            "github")
        .contains(id)) {
      lastHome = id;
      pageFocus.put("home", id);
    }
  }

  public String page() {
    return page;
  }

  public void restoreNavigation(String name, String selected) {
    if (!Arrays.asList("home", "settings", "controls", "files", "profile", "more").contains(name))
      name = "home";
    if (selected != null && !selected.isEmpty()) pageFocus.put(name, selected);
    showPage(name);
  }

  public void showPage(String name) {
    showPage(name, false);
  }

  private void showPage(String name, boolean back) {
    remember();
    externalBody = null;
    page = name;
    reversing = back;
    render();
  }

  private void open(String action) {
    lastHome = action;
    if (action.equals("controls")) actions.open("control-settings");
    else if (action.equals("profile")) actions.open("edit-profile");
    else showPage(action);
  }

  private void activate(String action) {
    if (action.equals("play")) {
      if (model.busy) return;
      actions.open(model.ready ? model.running ? "resume" : "play" : "choose-game");
    } else open(action);
  }

  private void back() {
    if (!navigateBack()) actions.open("back");
  }

  private void edge(String from, int direction, String to) {
    View a = focus.get(from), b = focus.get(to);
    if (a == null || b == null) return;
    switch (direction) {
      case View.FOCUS_UP:
        a.setNextFocusUpId(b.getId());
        break;
      case View.FOCUS_DOWN:
        a.setNextFocusDownId(b.getId());
        break;
      case View.FOCUS_LEFT:
        a.setNextFocusLeftId(b.getId());
        break;
      case View.FOCUS_RIGHT:
        a.setNextFocusRightId(b.getId());
        break;
    }
  }

  private void wireFocus() {
    if (home()) {
      edge("profile", View.FOCUS_DOWN, "play");
      edge("play", View.FOCUS_UP, "profile");
      if (design == Design.FESTIVAL) {
        edge("play", View.FOCUS_DOWN, "settings");
        edge("settings", View.FOCUS_UP, "play");
        edge("controls", View.FOCUS_UP, "play");
        edge("settings", View.FOCUS_RIGHT, "controls");
        edge("controls", View.FOCUS_LEFT, "settings");
        edge("settings", View.FOCUS_DOWN, "files");
        edge("controls", View.FOCUS_DOWN, "more");
        edge("files", View.FOCUS_UP, "settings");
        edge("more", View.FOCUS_UP, "controls");
        edge("files", View.FOCUS_RIGHT, "more");
        edge("more", View.FOCUS_LEFT, "files");
        edge("files", View.FOCUS_DOWN, "youtube");
        edge("more", View.FOCUS_DOWN, "github");
      } else {
        edge("play",View.FOCUS_RIGHT,"settings");edge("play",View.FOCUS_DOWN,"youtube");
        edge("settings",View.FOCUS_UP,"profile");edge("controls",View.FOCUS_UP,"profile");
        edge("settings",View.FOCUS_LEFT,"play");edge("files",View.FOCUS_LEFT,"play");
        edge("settings",View.FOCUS_RIGHT,"controls");edge("controls",View.FOCUS_LEFT,"settings");edge("controls",View.FOCUS_RIGHT,"controls");
        edge("settings",View.FOCUS_DOWN,"files");edge("files",View.FOCUS_UP,"settings");edge("controls",View.FOCUS_DOWN,"more");edge("more",View.FOCUS_UP,"controls");
        edge("files",View.FOCUS_RIGHT,"more");edge("more",View.FOCUS_LEFT,"files");edge("more",View.FOCUS_RIGHT,"more");
        edge("files",View.FOCUS_DOWN,"youtube");edge("more",View.FOCUS_DOWN,"github");
      }
      edge("youtube", View.FOCUS_UP, "files");
      edge("telegram", View.FOCUS_UP, "more");
      edge("github", View.FOCUS_UP, "more");
      edge("youtube", View.FOCUS_RIGHT, "telegram");
      edge("telegram", View.FOCUS_LEFT, "youtube");
      edge("telegram", View.FOCUS_RIGHT, "github");
      edge("github", View.FOCUS_LEFT, "telegram");
    } else if (externalBody != null) {
      View first = firstFocusable(externalBody);
      if (first != null) {
        if (first.getId() == NO_ID) first.setId(View.generateViewId());
        focus.get("back").setNextFocusDownId(first.getId());
      }
    } else {
      int columns = categoryColumns();
      List<String> cards = new ArrayList<>(focus.keySet());
      cards.remove("back");
      for (int i = 0; i < cards.size(); i++) {
        String current = cards.get(i);
        edge(current, View.FOCUS_UP, i < columns ? "back" : cards.get(i - columns));
        if (i + columns < cards.size()) edge(current, View.FOCUS_DOWN, cards.get(i + columns));
        if (i % columns > 0) edge(current, View.FOCUS_LEFT, cards.get(i - 1));
        if (i % columns < columns - 1 && i + 1 < cards.size())
          edge(current, View.FOCUS_RIGHT, cards.get(i + 1));
      }
      if (!cards.isEmpty()) edge("back", View.FOCUS_DOWN, cards.get(0));
    }
  }

  private boolean move(int direction) {
    View current = findFocus();
    if (current != null
        && direction == FOCUS_UP
        && Arrays.asList("youtube", "telegram", "github").contains(String.valueOf(current.getTag()))
        && requestActionFocus(lastContentFocus)) return true;
    if (current == null) return requestActionFocus(page.equals("home") ? "play" : "back");
    int next;
    switch (direction) {
      case FOCUS_UP:
        next = current.getNextFocusUpId();
        break;
      case FOCUS_DOWN:
        next = current.getNextFocusDownId();
        break;
      case FOCUS_LEFT:
        next = current.getNextFocusLeftId();
        break;
      default:
        next = current.getNextFocusRightId();
    }
    View target = next == NO_ID ? current.focusSearch(direction) : findViewById(next);
    return target != null && target.requestFocus();
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    int key = event.getKeyCode();
    int direction;
    switch (key) {
      case KeyEvent.KEYCODE_DPAD_UP:
        direction = FOCUS_UP;
        break;
      case KeyEvent.KEYCODE_DPAD_DOWN:
        direction = FOCUS_DOWN;
        break;
      case KeyEvent.KEYCODE_DPAD_LEFT:
        direction = FOCUS_LEFT;
        break;
      case KeyEvent.KEYCODE_DPAD_RIGHT:
        direction = FOCUS_RIGHT;
        break;
      default:
        direction = 0;
    }
    if (direction != 0) {
      if (externalBody != null) return super.dispatchKeyEvent(event);
      if (event.getAction() == KeyEvent.ACTION_DOWN) move(direction);
      return true;
    }
    if ((key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_DPAD_CENTER)
        && externalBody != null) return super.dispatchKeyEvent(event);
    if (key == KeyEvent.KEYCODE_BUTTON_A
        || key == KeyEvent.KEYCODE_ENTER
        || key == KeyEvent.KEYCODE_DPAD_CENTER) {
      if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
        View view = findFocus();
        if (view != null) view.performClick();
      }
      return true;
    }
    if (key == KeyEvent.KEYCODE_BUTTON_B
        || key == KeyEvent.KEYCODE_ESCAPE
        || key == KeyEvent.KEYCODE_BACK) {
      if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) back();
      return true;
    }
    return super.dispatchKeyEvent(event);
  }

  @Override
  public boolean dispatchGenericMotionEvent(MotionEvent event) {
    if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK)
      return super.dispatchGenericMotionEvent(event);
    if (externalBody != null && super.dispatchGenericMotionEvent(event)) {
      stopStick();
      return true;
    }
    if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
      stopStick();
      return true;
    }
    float x = event.getAxisValue(MotionEvent.AXIS_HAT_X),
        y = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
    if (Math.abs(x) < .55f && Math.abs(y) < .55f) {
      x = event.getAxisValue(MotionEvent.AXIS_X);
      y = event.getAxisValue(MotionEvent.AXIS_Y);
    }
    if (event.getActionMasked() != MotionEvent.ACTION_MOVE)
      return super.dispatchGenericMotionEvent(event);
    if (Math.max(Math.abs(x), Math.abs(y)) <= .55f) {
      stopStick();
      return true;
    }
    int direction =
        Math.abs(x) > Math.abs(y)
            ? x < 0 ? FOCUS_LEFT : FOCUS_RIGHT
            : y < 0 ? FOCUS_UP : FOCUS_DOWN;
    if (direction != heldDirection) {
      stopStick();
      heldDirection = direction;
      moveStick(direction);
      postDelayed(stickRepeat, 300);
    }
    return true;
  }

  private final class Backdrop extends View {
    private final Paint paint = new Paint(3);

    Backdrop(Context context) {
      super(context);
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas c) {
      float w = getWidth(), h = getHeight();
      if (photo != null) {
        float scale = Math.max(w / photo.getWidth(), h / photo.getHeight());
        float width = photo.getWidth() * scale, height = photo.getHeight() * scale;
        c.drawBitmap(
            photo,
            null,
            new RectF((w - width) / 2, (h - height) / 2, (w + width) / 2, (h + height) / 2),
            paint);
      } else c.drawColor(0xff0b1016);
      paint.setShader(
          new LinearGradient(
              0,
              0,
              w * .88f,
              0,
              new int[] {0xf3090c13, 0xc50b0f17, 0x1a090b11},
              new float[] {0, .48f, 1},
              Shader.TileMode.CLAMP));
      c.drawRect(0, 0, w, h, paint);
      paint.setShader(
          new LinearGradient(
              0,
              0,
              0,
              h,
              new int[] {0x82090c12, 0x00090c12, 0xf0090c12},
              new float[] {0, .45f, 1},
              Shader.TileMode.CLAMP));
      c.drawRect(0, 0, w, h, paint);
      paint.setShader(null);
      if (!home()) {
        paint.setColor(0xb80b1018);
        c.drawRect(0, 0, w, h, paint);
      }
      paint.setColor(PINK);
      c.drawRect(dp(30), h - dp(3), dp(98), h, paint);
    }
  }

  private final class FocusCard extends LinearLayout {
    private final Paint paint = new Paint(3);
    private final boolean primary;
    private final RectF rect = new RectF();

    FocusCard(
        Context context,
        String icon,
        String title,
        String subtitle,
        boolean primary,
        boolean vertical) {
      super(context);
      this.primary = primary;
      setWillNotDraw(false);
      setOrientation(vertical ? VERTICAL : HORIZONTAL);
      setGravity(vertical ? Gravity.LEFT | Gravity.CENTER_VERTICAL : Gravity.CENTER_VERTICAL);
      int verticalPadding = subtitle.isEmpty() ? 6 : compact ? 7 : 10;
      setPadding(
          dp(vertical ? 17 : 16),
          dp(verticalPadding),
          dp(14),
          dp(verticalPadding));
      setClickable(true);
      setFocusable(true);
      setFocusableInTouchMode(true);
      FestivalUi.touchFocus(this);
      setContentDescription(
          AppLanguage.text(getContext(), title)
              + (subtitle.isEmpty() ? "" : ". " + AppLanguage.text(getContext(), subtitle)));
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
      setAccessibilityDelegate(
          new AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(
                View host, android.view.accessibility.AccessibilityNodeInfo node) {
              super.onInitializeAccessibilityNodeInfo(host, node);
              node.setClassName("android.widget.Button");
            }
          });
      int foreground = primary ? 0xff11141b : WHITE;
      Icon symbol = new Icon(context, icon, foreground);
      int iconSize = subtitle.isEmpty() ? 18 : primary ? 26 : compact ? 20 : 22;
      addView(symbol, lp(iconSize, iconSize));
      LinearLayout words = column();
      if (vertical) {
        LinearLayout.LayoutParams wp = lp(-1, -2);
        wp.topMargin = dp(12);
        addView(words, wp);
      } else {
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, -2, 1);
        wp.leftMargin = dp(primary ? 16 : 12);
        addView(words, wp);
      }
      TextView label =
          text(
              title,
              primary
                  ? vertical ? 26 : compact ? 26 : 29
                  : subtitle.isEmpty() ? 13 : compact ? 14 : 16,
              foreground,
              true);
      if (largeText() && !subtitle.isEmpty()) label.setMaxLines(2);
      words.addView(label, lp(-1, -2));
      if (!subtitle.isEmpty()) {
        TextView details =
            text(
                subtitle,
                primary ? 12 : compact ? 10.5f : 11.5f,
                primary ? 0xff62656b : MUTED,
                false);
        if (largeText()) details.setMaxLines(2);
        LinearLayout.LayoutParams sp = lp(-1, -2);
        sp.topMargin = dp(compact ? 3 : 5);
        words.addView(details, sp);
      }
      setOnFocusChangeListener((v, focused) -> invalidate());
      setOnTouchListener(
          (v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) requestFocus();
            return false;
          });
    }

    private int contentHeight(int width) {
      measure(MeasureSpec.makeMeasureSpec(Math.max(1, width), MeasureSpec.EXACTLY),
          MeasureSpec.UNSPECIFIED);
      return getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
    }

    private int minimumHeight(int width) {
      return Math.max(dp(44), contentHeight(width) + 2 * dp(3));
    }

    private void fitHeight(int width, int height) {
      int padding = Math.max(dp(3), (height - contentHeight(width)) / 2);
      if (padding < getPaddingTop()) setPadding(getPaddingLeft(), padding, getPaddingRight(), padding);
    }

    @Override
    protected void onDraw(Canvas c) {
      float inset = dp(2);
      rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(primary ? WHITE : isFocused() ? 0xf0272530 : 0xe8171d27);
      c.drawRoundRect(rect, dp(10), dp(10), paint);
      if (isFocused()) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(PINK);
        c.drawRoundRect(rect, dp(10), dp(10), paint);
        paint.setStyle(Paint.Style.FILL);
      }
      if (primary) {
        paint.setColor(PINK);
        float edge = Math.min(dp(22), getHeight() * .25f);
        c.drawRoundRect(new RectF(dp(2), edge, dp(5), getHeight() - edge), dp(2), dp(2), paint);
      }
      super.onDraw(c);
    }
  }

  private final class Icon extends View {
    private final Paint p = new Paint(3);
    private final String name;
    private final int color;

    Icon(Context context, String name, int color) {
      super(context);
      this.name = name;
      this.color = color;
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private void line(Canvas c, float... values) {
      Path path = new Path();
      path.moveTo(values[0], values[1]);
      for (int i = 2; i < values.length; i += 2) path.lineTo(values[i], values[i + 1]);
      c.drawPath(path, p);
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      Canvas c = canvas;
      c.save();
      float size = Math.min(getWidth(), getHeight());
      c.translate((getWidth() - size) / 2, (getHeight() - size) / 2);
      c.scale(size / 24, size / 24);
      p.setColor(color);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(1.8f);
      p.setStrokeCap(Paint.Cap.ROUND);
      p.setStrokeJoin(Paint.Join.ROUND);
      switch (name) {
        case "play":
          line(c, 8, 4, 20, 12, 8, 20, 8, 4);
          break;
        case "sliders":
          line(c, 3, 6, 10, 6);
          line(c, 14, 6, 21, 6);
          c.drawCircle(12, 6, 2, p);
          line(c, 3, 12, 5, 12);
          line(c, 9, 12, 21, 12);
          c.drawCircle(7, 12, 2, p);
          line(c, 3, 18, 14, 18);
          line(c, 18, 18, 21, 18);
          c.drawCircle(16, 18, 2, p);
          break;
        case "controller":
          c.drawRoundRect(new RectF(2, 6, 22, 19), 5, 5, p);
          line(c, 6, 12, 12, 12);
          line(c, 9, 9, 9, 15);
          c.drawCircle(16, 11, .7f, p);
          c.drawCircle(19, 14, .7f, p);
          break;
        case "folder":
          line(c, 3, 6, 10, 6, 12, 8, 21, 8, 21, 20, 3, 20, 3, 6);
          break;
        case "grid":
          for (int x : new int[] {4, 14})
            for (int y : new int[] {4, 14}) c.drawRoundRect(new RectF(x, y, x + 6, y + 6), 1, 1, p);
          break;
        case "user":
          c.drawCircle(12, 8, 3, p);
          Path arc = new Path();
          arc.addArc(new RectF(4, 13, 20, 28), 180, 180);
          c.drawPath(arc, p);
          break;
        case "arrow":
          line(c, 13, 5, 6, 12, 13, 19);
          line(c, 6, 12, 21, 12);
          break;
        case "chip":
          c.drawRoundRect(new RectF(6, 6, 18, 18), 2, 2, p);
          c.drawRect(9, 9, 15, 15, p);
          for (int n : new int[] {8, 12, 16}) {
            line(c, n, 3, n, 6);
            line(c, n, 18, n, 21);
            line(c, 3, n, 6, n);
            line(c, 18, n, 21, n);
          }
          break;
        case "save":
          line(c, 4, 3, 17, 3, 21, 7, 21, 21, 3, 21, 3, 3, 4, 3);
          c.drawRect(7, 3, 16, 9, p);
          line(c, 7, 21, 7, 14, 17, 14, 17, 21);
          break;
        case "trophy":
          line(c, 7, 4, 17, 4, 17, 9, 15, 14, 9, 14, 7, 9, 7, 4);
          line(c, 7, 6, 3, 6, 3, 11, 8, 12);
          line(c, 17, 6, 21, 6, 21, 11, 16, 12);
          line(c, 12, 14, 12, 20);
          line(c, 8, 21, 16, 21);
          break;
        case "pulse":
          line(c, 2, 13, 7, 13, 10, 5, 14, 20, 17, 11, 22, 11);
          break;
        case "info":
          c.drawCircle(12, 12, 9, p);
          line(c, 12, 11, 12, 17);
          c.drawPoint(12, 7, p);
          break;
        case "audio":
          line(c, 3, 9, 8, 9, 13, 5, 13, 20, 8, 16, 3, 16, 3, 9);
          c.drawArc(new RectF(12, 7, 22, 18), -65, 130, false, p);
          break;
        case "flag":
          line(c, 5, 21, 5, 3, 19, 3, 19, 14, 5, 14);
          line(c, 5, 8, 19, 8);
          line(c, 12, 3, 12, 14);
          break;
        case "check":
          line(c, 4, 12, 10, 18, 21, 6);
          break;
        case "touch":
          c.drawCircle(12, 7, 4, p);
          line(c, 9, 21, 5, 14, 8, 12, 11, 16, 11, 7, 14, 7, 14, 12, 19, 14, 18, 21, 9, 21);
          break;
        case "tilt":
          c.save();
          c.rotate(-16, 12, 12);
          c.drawRoundRect(new RectF(6, 3, 18, 21), 2, 2, p);
          line(c, 10, 18, 14, 18);
          c.restore();
          break;
        case "dpad":
          line(
              c, 9, 3, 15, 3, 15, 9, 21, 9, 21, 15, 15, 15, 15, 21, 9, 21, 9, 15, 3, 15, 3, 9, 9, 9,
              9, 3);
          break;
        default:
          ControlGlyphs.draw(c,p,12,12,10,name);
          break;
      }
      c.restore();
    }
  }
}
