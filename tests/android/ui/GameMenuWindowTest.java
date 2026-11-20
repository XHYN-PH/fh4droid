package studio.deimos.pinyonshift;

import static org.junit.Assert.*;

import android.app.Dialog;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;
import org.libsdl.app.SDLActivity;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = {33, 35}, qualifiers = "w960dp-h432dp-land-400dpi",
    shadows = {GameMenuWindowTest.Bridge.class, GameMenuWindowTest.DrawerWindow.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class GameMenuWindowTest {
  @Implements(PinyonShiftActivity.class)
  public static class Bridge extends ShadowActivity {
    static PinyonShiftActivity activity;
    static final List<Boolean> capture = new ArrayList<>();

    @Implementation protected static void nativeHostMenu(boolean active) {
      if (active) {
        Dialog menu = ReflectionHelpers.getField(activity, "gameMenu");
        assertTrue("The window must be visible before it captures the game", menu.isShowing());
      }
      capture.add(active);
    }
  }

  @Implements(Dialog.class)
  public static class DrawerWindow extends ShadowDialog {
    static boolean fail;

    @Override @Implementation protected void show() {
      if (fail) throw new WindowManager.BadTokenException("Window no longer available");
      super.show();
    }
  }

  private PinyonShiftActivity game;
  private boolean previousBrokenLibraries;

  @Before public void setUp() {
    previousBrokenLibraries = SDLActivity.mBrokenLibraries;
    SDLActivity.mBrokenLibraries = true;
    game = Robolectric.buildActivity(PinyonShiftActivity.class).get();
    game.setTheme(android.R.style.Theme_Material_NoActionBar);
    game.getSharedPreferences("mobile", 0).edit().clear().putBoolean("animations", false).commit();
    ReflectionHelpers.setField(game, "nativeReady", true);
    ReflectionHelpers.setField(game, "bridgeLoaded", true);
    ReflectionHelpers.setField(game, "gameResumed", true);
    Bridge.activity = game;
    Bridge.capture.clear();
    DrawerWindow.fail = false;
  }

  @After public void restoreSdlState() {
    SDLActivity.mBrokenLibraries = previousBrokenLibraries;
  }

  private Dialog open() {
    ReflectionHelpers.callInstanceMethod(game, "showGameMenu");
    Dialog menu = ReflectionHelpers.getField(game, "gameMenu");
    assertNotNull(menu);
    assertTrue(menu.isShowing());
    return menu;
  }

  @Test public void resumeBackAndCancelReleaseInput() {
    Dialog menu = open();
    Button resume = menu.getWindow().getDecorView().findViewWithTag("Resume");
    assertTrue(resume.performClick());
    ShadowLooper.idleMainLooper();
    assertFalse(menu.isShowing());

    menu = open();
    game.backPanel();
    ShadowLooper.idleMainLooper();
    assertFalse(menu.isShowing());

    menu = open();
    menu.cancel();
    ShadowLooper.idleMainLooper();
    assertFalse(menu.isShowing());
    assertEquals(Arrays.asList(true, false, true, false, true, false), Bridge.capture);
  }

  @Test public void goingBackFromAnInnerPageKeepsInputCaptured() {
    Dialog menu = open();
    game.showPanel("Nested settings", new LinearLayout(game));
    game.backPanel();
    ShadowLooper.idleMainLooper();
    assertTrue(menu.isShowing());
    assertEquals(Collections.singletonList(true), Bridge.capture);
    game.resumeDriving();
    ShadowLooper.idleMainLooper();
    assertEquals(Arrays.asList(true, false), Bridge.capture);
  }

  @Test public void failedWindowAttachDoesNotCaptureInputOrBlockRetry() {
    DrawerWindow.fail = true;
    ReflectionHelpers.callInstanceMethod(game, "showGameMenu");
    assertFalse(Bridge.capture.contains(true));
    assertNull(ReflectionHelpers.getField(game, "gameMenu"));
    DrawerWindow.fail = false;
    Dialog menu = open();
    menu.dismiss();
    ShadowLooper.idleMainLooper();
    assertEquals(Arrays.asList(false, true, false), Bridge.capture);
  }
}
