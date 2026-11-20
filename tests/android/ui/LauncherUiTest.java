package studio.deimos.pinyonshift;

import static org.junit.Assert.*;

import android.app.Activity;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;

/** Native Android rendering and interaction tests for the shipped launcher sources. */
@RunWith(RobolectricTestRunner.class)
@Config(
    manifest = Config.NONE,
    sdk = 33,
    qualifiers = "w960dp-h432dp-land-400dpi",
    assetDir = "../../android/art")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class LauncherUiTest {
  private File source(String path) {
    return new File(System.getProperty("launcher.source.root", "../.."), path);
  }

  private Activity activity;
  private final List<String> actions = new ArrayList<>();

  private RacingLauncherView create(boolean animations) {
    new File("renders-integrated").mkdirs();
    activity = Robolectric.buildActivity(Activity.class).setup().visible().get();
    activity
        .getSharedPreferences("mobile", 0)
        .edit()
        .clear()
        .putBoolean("animations", animations)
        .commit();
    activity.getWindow().setDecorFitsSystemWindows(false);
    activity
        .getWindow()
        .setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    activity.getWindow().getDecorView().setSystemUiVisibility(5894);
    Bitmap photo = BitmapFactory.decodeFile(source("android/art/menu-background.png").getPath());
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = 4;
    Bitmap logo = BitmapFactory.decodeFile(source("android/art/menu-logo.png").getPath(), options);
    assertNotNull(photo);
    assertNotNull(logo);
    RacingLauncherView root =
        new RacingLauncherView(
            activity,
            RacingLauncherView.Design.GRID,
            new RacingLauncherView.Model(),
            photo,
            logo,
            actions::add);
    activity.setContentView(root);
    arrange(root, 2400, 1080);
    return root;
  }

  private void arrange(View root, int w, int h) {
    ShadowLooper.idleMainLooper();
    root.measure(
        View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
    root.layout(0, 0, w, h);
  }

  private void settle() {
    ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
  }

  private void resize(RacingLauncherView root, int width, int height) {
    ViewGroup.LayoutParams frame = root.getLayoutParams();
    frame.width = width;
    frame.height = height;
    root.setLayoutParams(frame);
    arrange(root, width, height);
    settle();
    assertEquals(width, root.getWidth());
    assertEquals(height, root.getHeight());
  }

  private void key(View root, int code) {
    root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
    root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
  }

  private Bitmap screenshot(RacingLauncherView root, String name) throws Exception {
    arrange(root, 2400, 1080);
    return capture(root, name);
  }

  private Bitmap capture(RacingLauncherView root, String name) throws Exception {
    Bitmap image = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
    root.draw(new Canvas(image));
    new File("renders-integrated").mkdirs();
    try (OutputStream out = new FileOutputStream("renders-integrated/" + name + ".png")) {
      assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out));
    }
    return image;
  }

  @Test
  public void approvedHomeMatchesNativePreview() throws Exception {
    RacingLauncherView root = create(false);
    root.requestActionFocus("play");
    Bitmap image = screenshot(root, "B-home");
    assertEquals(
        "Approved B home pixels changed",
        "d2e01e0391192b6bc2bee7c3fd9c9a18cff47241ae70f7b21258b75670ad7fe2",
        pixelDigest(image));
    String baseline = System.getProperty("launcher.preview.baseline");
    if (baseline != null) {
      Bitmap approved = BitmapFactory.decodeFile(baseline);
      assertNotNull(approved);
      assertTrue("Home pixels differ from approved B preview", image.sameAs(approved));
    }
    root.requestActionFocus("settings");
    screenshot(root, "B-controller-focus");
    root.showPage("more");
    arrange(root, 2400, 1080);
    root.requestActionFocus("mods");
    Bitmap manage = screenshot(root, "B-manage");
    assertNull(root.findViewWithTag("launcher-footer"));
    assertEquals(
        "Approved management pixels changed",
        "79dcd01c93782e8033e41396273e4fabca9f0d21fa56bd1e9b6c9b3655bf727c",
        pixelDigest(manage));
    String manageBaseline = System.getProperty("launcher.preview.manage");
    if (manageBaseline != null)
      assertTrue(
          "Manage pixels differ from approved preview",
          manage.sameAs(BitmapFactory.decodeFile(manageBaseline)));
    root.showPage("settings");
    arrange(root, 2400, 1080);
    root.requestActionFocus("graphics");
    screenshot(root, "B-settings-categories");
  }

  @Test
  public void homeActionsMatchSettingsCardsAndUseSharedControls() {
    RacingLauncherView root = create(false);
    arrange(root, 2400, 1080);
    int width = root.findViewWithTag("settings").getWidth();
    int height = root.findViewWithTag("settings").getHeight();
    root.findViewWithTag("controls").performClick();
    assertEquals("control-settings", actions.get(actions.size() - 1));
    root.showPage("settings");
    arrange(root, 2400, 1080);
    View graphics = root.findViewWithTag("graphics");
    assertTrue(Math.abs(width - graphics.getWidth()) <= 1);
    assertEquals(height, graphics.getHeight());
  }

  @Test
  public void navigationActivationAndBackKeepTheirFocus() {
    RacingLauncherView root = create(false);
    root.requestActionFocus("play");
    key(root, KeyEvent.KEYCODE_BUTTON_A);
    assertEquals(Arrays.asList("play"), actions);
    key(root, KeyEvent.KEYCODE_DPAD_RIGHT);key(root, KeyEvent.KEYCODE_DPAD_RIGHT);key(root, KeyEvent.KEYCODE_DPAD_DOWN);
    assertEquals("more", root.focusedAction());
    key(root, KeyEvent.KEYCODE_DPAD_DOWN);
    assertEquals("github", root.focusedAction());
    key(root, KeyEvent.KEYCODE_DPAD_UP);
    assertEquals("more", root.focusedAction());
    key(root, KeyEvent.KEYCODE_BUTTON_A);
    arrange(root, 2400, 1080);
    assertEquals("more", root.page());
    key(root, KeyEvent.KEYCODE_DPAD_RIGHT);
    assertEquals("drivers", root.focusedAction());
    key(root, KeyEvent.KEYCODE_BUTTON_A);
    assertEquals("drivers", actions.get(actions.size() - 1));
    key(root, KeyEvent.KEYCODE_BUTTON_B);
    arrange(root, 2400, 1080);
    assertEquals("home", root.page());
    assertEquals("more", root.focusedAction());
    KeyEvent cancelled =
        new KeyEvent(
            1,
            2,
            KeyEvent.ACTION_UP,
            KeyEvent.KEYCODE_BUTTON_A,
            0,
            0,
            0,
            0,
            KeyEvent.FLAG_CANCELED);
    int count = actions.size();
    root.dispatchKeyEvent(cancelled);
    assertEquals(count, actions.size());
  }

  @Test
  public void fullPageWidgetsKeepNativeKeyboardBehaviorAndBackCallback() {
    RacingLauncherView root = create(false);
    LinearLayout body = FestivalUi.column(activity);
    body.setTag(FestivalUi.FULL_PAGE, true);
    SeekBar seek =
        FestivalUi.slider(activity, body, "Test value", "", 0, 100, 1, 50, i -> i + "%", i -> {});
    root.showContent("Settings", body, false);
    arrange(root, 2400, 1080);
    assertTrue(seek.requestFocus());
    assertEquals(seek, root.findFocus());
    int before = seek.getProgress();
    key(root, KeyEvent.KEYCODE_DPAD_RIGHT);
    assertTrue("Slider did not respond to Right", seek.getProgress() > before);
    key(root, KeyEvent.KEYCODE_BUTTON_B);
    assertEquals("back", actions.get(actions.size() - 1));
    root.clearContent(true);
    arrange(root, 2400, 1080);
    assertEquals("play", root.focusedAction());
  }

  @Test
  public void returningFromNestedPagesRestoresFocusAndScroll() {
    RacingLauncherView root = create(false);
    LinearLayout first = FestivalUi.column(activity);
    Button selected = null;
    for (int i = 0; i < 18; i++) {
      Button b = FestivalUi.action(activity, first, "Action " + i, () -> {});
      if (i == 12) selected = b;
    }
    root.showContent("Save backups", first, false);
    arrange(root, 2400, 1080);
    assertTrue(selected.requestFocus());
    ScrollView parent = (ScrollView) first.getParent().getParent();
    parent.scrollTo(0, 700);
    int y = parent.getScrollY();
    root.showContent("Choose backup", FestivalUi.column(activity), false);
    arrange(root, 2400, 1080);
    root.showContent("Save backups", first, true);
    arrange(root, 2400, 1080);
    assertEquals(selected, root.findFocus());
    assertEquals(y, ((ScrollView) first.getParent().getParent()).getScrollY());
  }

  @Test
  public void motionSettlesAndReducedMotionIsImmediate() {
    RacingLauncherView root = create(true);
    root.showPage("home");
    View dashboard = (View) root.findViewWithTag("play").getParent();
    View firstRow = (View) root.findViewWithTag("settings").getParent();
    View secondRow = (View) root.findViewWithTag("files").getParent();
    assertTrue(dashboard.getTranslationY() > 0);
    assertEquals(0, firstRow.getAlpha(), 0);
    assertTrue(firstRow.animate().getStartDelay() < secondRow.animate().getStartDelay());
    root.requestActionFocus("settings");
    View card = root.findViewWithTag("settings");
    assertTrue(card.getScaleX() < 1);
    settle();
    assertEquals(1, card.getScaleX(), .001);
    assertEquals(0, dashboard.getTranslationY(), .001);
    assertEquals(1, firstRow.getAlpha(), .001);
    assertEquals(1, secondRow.getAlpha(), .001);
    for (int i = 0; i < 4; i++) {
      root.showPage("more");
      root.showPage("home");
    }
    arrange(root, 2400, 1080);
    settle();
    assertTrue(root.findViewWithTag("play").isShown());
    activity.getSharedPreferences("mobile", 0).edit().putBoolean("animations", false).commit();
    root.showPage("home");
    assertEquals(0, ((View) root.findViewWithTag("play").getParent()).getTranslationY(), 0);
    assertEquals(1, ((View) root.findViewWithTag("files").getParent()).getAlpha(), 0);
    root.requestActionFocus("controls");
    assertEquals(1, root.findViewWithTag("controls").getScaleX(), 0);
    assertFalse(MenuMotion.enabled(activity));
    Window window = new android.app.Dialog(activity).getWindow();
    window.setWindowAnimations(android.R.style.Animation_Dialog);
    MenuMotion.drawerWindow(window);
    assertEquals(0, window.getAttributes().windowAnimations);
  }

  @Test
  @Config(qualifiers = "w640dp-h360dp-land-xxhdpi")
  public void compactRaceGridScrollsToEveryCard() throws Exception {
    RacingLauncherView root = create(false);
    arrange(root, 1920, 1080);
    root.requestActionFocus("more");
    settle();
    arrange(root, 1920, 1080);
    View more = root.findViewWithTag("more");
    Rect visible = new Rect();
    assertTrue(more.getGlobalVisibleRect(visible));
    assertTrue("Focused card is clipped", visible.width() >= more.getWidth() - 4);
    assertEquals("Focused card height is clipped", more.getHeight(), visible.height());
    capture(root, "B-compact");
  }

  @Test
  @Config(qualifiers = "w640dp-h336dp-land-xhdpi")
  public void shortLandscapeKeepsReleaseDashboardAndClearsSystemBars() throws Exception {
    org.robolectric.android.controller.ActivityController<LauncherActivity> controller =
        Robolectric.buildActivity(LauncherActivity.class);
    LauncherActivity launcher = controller.get();
    installLauncherFixture(launcher, "0.9.3", 913);
    File game = GameFiles.root(launcher);
    java.nio.file.Files.createDirectories(game.toPath());
    java.nio.file.Files.createFile(new File(game, "default.xex").toPath());
    MobileConfig.put(launcher, "fh1_render_scale_percent", "90");
    MobileConfig.put(launcher, "pinyon_shift_fh1_render_fps_limit", "60");
    launcher.getSharedPreferences("mobile", 0).edit().putBoolean("animations", false).commit();
    controller.setup().visible();
    ViewGroup content = launcher.findViewById(android.R.id.content);
    RacingLauncherView root = (RacingLauncherView) content.getChildAt(0);
    Insets top = Insets.of(0, 12, 0, 0);
    Insets bottom = Insets.of(0, 0, 0, 22);
    Insets cutout = Insets.of(58, 0, 4, 0);
    root.dispatchApplyWindowInsets(new WindowInsets.Builder()
        .setInsets(WindowInsets.Type.displayCutout(), cutout)
        .setInsetsIgnoringVisibility(WindowInsets.Type.displayCutout(), cutout)
        .setVisible(WindowInsets.Type.displayCutout(), true)
        .setInsetsIgnoringVisibility(WindowInsets.Type.statusBars(), top)
        .setInsetsIgnoringVisibility(WindowInsets.Type.navigationBars(), bottom)
        .build());
    float previousLabelSize = Float.POSITIVE_INFINITY;
    for (int height : new int[] {576, 492}) {
      resize(root, 1280, height);
      assertReleaseDashboard(root);
      assertFooterClear(root);
      int[] origin = new int[2];
      root.getLocationInWindow(origin);
      for (String id : new String[] {"play", "settings", "controls", "files", "more", "profile",
          "youtube", "telegram", "github", "installed-version", "launcher-heading", "launcher-summary"}) {
        View action = root.findViewWithTag(id);
        Rect visible = new Rect();
        assertTrue(id + " is hidden at " + height, action.getLocalVisibleRect(visible));
        assertEquals(id + " is clipped at " + height,
            new Rect(0, 0, action.getWidth(), action.getHeight()), visible);
        int[] location = new int[2];
        action.getLocationInWindow(location);
        int x = location[0] - origin[0], y = location[1] - origin[1];
        assertTrue(id + " overlaps a cutout or system bar at " + height,
            x >= cutout.left && x + action.getWidth() <= 1280 - cutout.right
            && y >= top.top && y + action.getHeight() <= height - bottom.bottom);
      }
      assertNotNull(findText(root, "Ready to drive"));
      assertNotNull(findText(root, "Game files ready"));
      TextView profile = (TextView) findText(root.findViewWithTag("profile"), "Local profile");
      assertEquals("The profile button should show its full label", 0, profile.getLayout().getEllipsisCount(0));
      assertTrue(((TextView) root.findViewWithTag("launcher-summary")).getText().toString().contains("60 FPS"));
      View settings = root.findViewWithTag("settings");
      float labelSize = ((TextView) findText(settings, "Settings")).getTextSize();
      assertTrue("Card labels should shrink as the window gets shorter", labelSize < previousLabelSize);
      assertTrue("Card labels should fit the compact type scale", labelSize <= 26);
      previousLabelSize = labelSize;
      View play = root.findViewWithTag("play");
      Rect playBounds = new Rect(), captionBounds = new Rect();
      assertTrue(play.getGlobalVisibleRect(playBounds));
      assertTrue(findText(play, "Start Forza Horizon").getGlobalVisibleRect(captionBounds));
      assertTrue("The Play caption should fit inside its card", playBounds.contains(captionBounds));
      assertTrue(root.requestActionFocus("play"));
      capture(root, "B-release-dashboard-1280x" + height);
    }
    assertTrue(root.requestActionFocus("controls"));
    key(root, KeyEvent.KEYCODE_DPAD_DOWN);
    assertEquals("more", root.focusedAction());
    launcher.getSharedPreferences("mobile", 0).edit().putFloat("ui_scale", 1.02f).commit();
    resize(root, 1052, 576);
    assertReleaseDashboard(root);
    assertFooterClear(root);
    assertEquals("more", root.focusedAction());
    key(root, KeyEvent.KEYCODE_DPAD_LEFT);
    assertEquals("files", root.focusedAction());

    resize(root, 1052, 320);
    assertFooterClear(root);
    ViewGroup menu = root.findViewWithTag("launcher-menu-viewport");
    ScrollView scroll = (ScrollView) menu.getChildAt(0);
    assertTrue("The short dashboard should need scrolling", scroll.getChildAt(0).getHeight() > scroll.getHeight());
    scroll.scrollTo(0, scroll.getChildAt(0).getHeight());
    assertTrue(scroll.getScrollY() > 0);
    Rect footer = new Rect();
    assertTrue(root.findViewWithTag("launcher-footer").getGlobalVisibleRect(footer));
    for (String id : new String[] {"play", "settings", "controls", "files", "more"}) {
      Rect card = new Rect();
      if (root.findViewWithTag(id).getGlobalVisibleRect(card))
        assertTrue("Scrolling should keep " + id + " above the footer", card.bottom < footer.top);
    }
    root.showPage("more");
    resize(root, 1052, 576);
    assertNull(root.findViewWithTag("launcher-footer"));
    assertNull(root.findViewWithTag("github"));
    root.showContent("Details", new LinearLayout(launcher), false);
    resize(root, 1052, 576);
    assertNull(root.findViewWithTag("launcher-footer"));
    root.clearContent(true);
    root.showPage("home");
    resize(root, 1052, 576);
    assertFooterClear(root);
  }

  private void assertReleaseDashboard(RacingLauncherView root) {
    Rect play = boundsInWindow(root.findViewWithTag("play"));
    Rect settings = boundsInWindow(root.findViewWithTag("settings"));
    Rect controls = boundsInWindow(root.findViewWithTag("controls"));
    Rect files = boundsInWindow(root.findViewWithTag("files"));
    Rect more = boundsInWindow(root.findViewWithTag("more"));
    assertTrue("Play should be on the left of both rows", play.right < settings.left);
    assertTrue("The right-hand cards should have a column gap", settings.right < controls.left);
    assertTrue("The right-hand cards should have a row gap", settings.bottom < files.top);
    assertEquals(settings.top, play.top);
    assertEquals(files.bottom, play.bottom);
    assertEquals(settings.left, files.left);
    assertEquals(controls.left, more.left);
    assertEquals(settings.top, controls.top);
    assertEquals(files.top, more.top);
    assertTrue(Math.abs(play.width() - settings.width()) <= 1);
  }

  private void assertFooterClear(RacingLauncherView root) {
    View menu = root.findViewWithTag("launcher-menu-viewport");
    Rect footer = boundsInWindow(root.findViewWithTag("launcher-footer"));
    assertTrue("The footer should have its own row below the menu", boundsInWindow(menu).bottom < footer.top);
    assertEquals(new Rect(0, 0, menu.getWidth(), menu.getHeight()), menu.getClipBounds());
  }

  private Rect boundsInWindow(View view) {
    int[] location = new int[2];
    view.getLocationInWindow(location);
    return new Rect(location[0], location[1], location[0] + view.getWidth(), location[1] + view.getHeight());
  }

  @Test
  public void actualLauncherRoutesSettingsAndReturnsToCategory() throws Exception {
    org.robolectric.android.controller.ActivityController<LauncherActivity> controller =
        Robolectric.buildActivity(LauncherActivity.class);
    LauncherActivity app = controller.get();
    installLauncherFixture(app, "0.8.2-polish-test3", 803);
    controller.setup().visible();
    app.getSharedPreferences("mobile", 0).edit().putBoolean("animations", false).commit();
    RacingLauncherView root =
        (RacingLauncherView)
            ((android.view.ViewGroup) app.findViewById(android.R.id.content)).getChildAt(0);
    arrange(root, 2400, 1080);
    root.requestActionFocus("settings");
    root.findViewWithTag("settings").performClick();
    arrange(root, 2400, 1080);
    assertEquals("settings", root.page());
    root.requestActionFocus("audio");
    root.findViewWithTag("audio").performClick();
    arrange(root, 2400, 1080);
    assertNotNull(findText(root, "Master volume"));
    app.onConfigurationChanged(
        new android.content.res.Configuration(app.getResources().getConfiguration()));
    root =
        (RacingLauncherView)
            ((android.view.ViewGroup) app.findViewById(android.R.id.content)).getChildAt(0);
    arrange(root, 2400, 1080);
    assertNotNull(findText(root, "Master volume"));
    screenshot(root, "B-audio-settings");
    app.onBackPressed();
    arrange(root, 2400, 1080);
    assertEquals("settings", root.page());
    assertEquals("audio", root.focusedAction());
    app.onBackPressed();
    arrange(root, 2400, 1080);
    assertEquals("home", root.page());
    assertEquals("settings", root.focusedAction());
    app.finish();
  }

  private void installLauncherFixture(LauncherActivity app, String version, long build)
      throws Exception {
    app.setTheme(android.R.style.Theme_Material_NoActionBar);
    File assets = File.createTempFile("menu-fixture-", ".zip");
    assets.deleteOnExit();
    try (java.util.zip.ZipOutputStream zip =
        new java.util.zip.ZipOutputStream(new FileOutputStream(assets))) {
      for (String name : new String[] {"menu-background.png", "menu-logo.png"}) {
        zip.putNextEntry(new java.util.zip.ZipEntry("assets/" + name));
        java.nio.file.Files.copy(source("android/art/" + name).toPath(), zip);
        zip.closeEntry();
      }
    }
    int cookie =
        org.robolectric.util.ReflectionHelpers.callInstanceMethod(
            app.getAssets(),
            "addAssetPath",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(
                String.class, assets.getCanonicalPath()));
    assertTrue(cookie > 0);
    android.content.pm.PackageInfo info =
        app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
    info.versionName = version;
    info.setLongVersionCode(build);
    org.robolectric.Shadows.shadowOf(app.getPackageManager()).installPackage(info);
  }

  @Test
  public void everyCategoryEmitsItsExistingAction() {
    RacingLauncherView root = create(false);
    String[][] groups = {
      {"settings", "graphics", "audio", "control-settings", "interface", "gameplay", "advanced"},
      {"controls", "touch-layout", "controller", "tilt"},
      {"files", "choose-game", "storage"},
      {"profile", "edit-profile", "achievements", "saves"},
      {"more", "mods", "drivers", "saves", "achievements", "reports", "about"}
    };
    for (String[] group : groups) {
      root.showPage(group[0]);
      arrange(root, 2400, 1080);
      for (int i = 1; i < group.length; i++) {
        View card = root.findViewWithTag(group[i]);
        assertNotNull(card);
        card.performClick();
        assertEquals(group[i], actions.get(actions.size() - 1));
      }
    }
  }

  @Test
  @Config(qualifiers = "w640dp-h360dp-land-xxhdpi")
  public void largeTextKeepsManagementActionsReachable() throws Exception {
    RacingLauncherView root = create(false);
    android.content.res.Configuration config =
        new android.content.res.Configuration(activity.getResources().getConfiguration());
    config.fontScale = 1.5f;
    activity
        .getResources()
        .updateConfiguration(config, activity.getResources().getDisplayMetrics());
    activity.getSharedPreferences("mobile", 0).edit().putFloat("ui_scale", 1.05f).commit();
    root.showPage("more");
    arrange(root, 1920, 1080);
    root.requestActionFocus("about");
    settle();
    arrange(root, 1920, 1080);
    Rect visible = new Rect();
    View about = root.findViewWithTag("about");
    assertTrue(about.getGlobalVisibleRect(visible));
    assertTrue(visible.height() >= about.getHeight() - 4);
    Rect header = new Rect();
    root.findViewWithTag("back").getGlobalVisibleRect(header);
    for (String id :
        new String[] {"mods", "drivers", "saves", "achievements", "reports", "about"}) {
      Rect card = new Rect();
      if (root.findViewWithTag(id).getGlobalVisibleRect(card))
        assertTrue("Scrolled cards overlap header", card.top >= header.bottom);
    }
    Bitmap image = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
    root.draw(new Canvas(image));
    try (OutputStream out = new FileOutputStream("renders-integrated/B-large-text.png")) {
      image.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
  }

  private String pixelDigest(Bitmap image) throws Exception {
    int[] pixels = new int[image.getWidth() * image.getHeight()];
    image.getPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
    java.nio.ByteBuffer bytes = java.nio.ByteBuffer.allocate(pixels.length * 4);
    bytes.asIntBuffer().put(pixels);
    byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes.array());
    StringBuilder hex = new StringBuilder();
    for (byte b : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
    return hex.toString();
  }

  private MotionEvent stick(float x, int source) {
    MotionEvent.PointerProperties property = new MotionEvent.PointerProperties();
    property.id = 0;
    MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
    coords.setAxisValue(MotionEvent.AXIS_X, x);
    return MotionEvent.obtain(
        0,
        android.os.SystemClock.uptimeMillis(),
        MotionEvent.ACTION_MOVE,
        1,
        new MotionEvent.PointerProperties[] {property},
        new MotionEvent.PointerCoords[] {coords},
        0,
        0,
        1,
        1,
        0,
        0,
        source,
        0);
  }

  @Test
  public void heldStickRepeatsAndReleaseOrLostFocusStopsIt() {
    RacingLauncherView root = create(false);
    root.requestActionFocus("play");
    MotionEvent mouse = stick(1, InputDevice.SOURCE_MOUSE);
    root.dispatchGenericMotionEvent(mouse);
    mouse.recycle();
    assertEquals("play", root.focusedAction());
    MotionEvent right = stick(1, InputDevice.SOURCE_JOYSTICK);
    root.dispatchGenericMotionEvent(right);
    right.recycle();
    assertEquals("settings", root.focusedAction());
    ShadowLooper.idleMainLooper(310, java.util.concurrent.TimeUnit.MILLISECONDS);
    assertEquals("controls", root.focusedAction());
    MotionEvent neutral = stick(0, InputDevice.SOURCE_JOYSTICK);
    root.dispatchGenericMotionEvent(neutral);
    neutral.recycle();
    settle();
    assertEquals("controls", root.focusedAction());
    right = stick(1, InputDevice.SOURCE_JOYSTICK);
    root.dispatchGenericMotionEvent(right);
    right.recycle();
    assertEquals("controls", root.focusedAction());
    root.onWindowFocusChanged(false);
    settle();
    assertEquals("controls", root.focusedAction());
  }

  private TextView findText(View root, String text) {
    if (root instanceof TextView && ((TextView) root).getText().toString().equals(text))
      return (TextView) root;
    if (root instanceof android.view.ViewGroup) {
      android.view.ViewGroup g = (android.view.ViewGroup) root;
      for (int i = 0; i < g.getChildCount(); i++) {
        TextView found = findText(g.getChildAt(i), text);
        if (found != null) return found;
      }
    }
    return null;
  }
}
