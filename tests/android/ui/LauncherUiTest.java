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

  private void key(View root, int code) {
    root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
    root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
  }

  private Bitmap screenshot(RacingLauncherView root, String name) throws Exception {
    arrange(root, 2400, 1080);
    Bitmap image = Bitmap.createBitmap(2400, 1080, Bitmap.Config.ARGB_8888);
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
    assertEquals(
        "Approved management pixels changed",
        "0873a07ec285d44bd4158f00ec6ec1e23e240c4ef10c398214e0009f8e58e928",
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
    root.requestActionFocus("settings");
    View card = root.findViewWithTag("settings");
    assertTrue(card.getScaleX() < 1);
    settle();
    assertEquals(1, card.getScaleX(), .001);
    for (int i = 0; i < 4; i++) {
      root.showPage("more");
      root.showPage("home");
    }
    arrange(root, 2400, 1080);
    settle();
    assertTrue(root.findViewWithTag("play").isShown());
    activity.getSharedPreferences("mobile", 0).edit().putBoolean("animations", false).commit();
    root.requestActionFocus("controls");
    assertEquals(1, root.findViewWithTag("controls").getScaleX(), 0);
    assertFalse(MenuMotion.enabled(activity));
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
    Bitmap image = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
    root.draw(new Canvas(image));
    try (OutputStream out = new FileOutputStream("renders-integrated/B-compact.png")) {
      image.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
  }

  @Test
  public void actualLauncherRoutesSettingsAndReturnsToCategory() throws Exception {
    org.robolectric.android.controller.ActivityController<LauncherActivity> controller =
        Robolectric.buildActivity(LauncherActivity.class);
    LauncherActivity app = controller.get();
    app.setTheme(android.R.style.Theme_Material_NoActionBar);
    File assets = new File("menu-fixture.zip");
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
    info.versionName = "0.8.2-polish-test3";
    info.setLongVersionCode(803);
    org.robolectric.Shadows.shadowOf(app.getPackageManager()).installPackage(info);
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
