package studio.deimos.pinyonshift;

import android.animation.*;
import android.content.Context;
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;

/** Short, interruptible menu motion. Nothing runs once a transition has settled. */
final class MenuMotion {
  static boolean enabled(Context context) {
    return ValueAnimator.areAnimatorsEnabled()
        && context.getSharedPreferences("mobile", 0).getBoolean("animations", true);
  }

  static void page(View view, boolean back) {
    view.animate().cancel();
    view.setAlpha(1);
    view.setTranslationX(0);
    view.setTranslationY(0);
    if (!enabled(view.getContext())) return;
    view.setAlpha(0);
    view.setTranslationX((back ? -18 : 18) * view.getResources().getDisplayMetrics().density);
    view.animate()
        .alpha(1)
        .translationX(0)
        .setDuration(220)
        .setInterpolator(new DecelerateInterpolator(1.5f))
        .start();
  }

  static void dashboard(View cards, View topRow, View bottomRow) {
    if (!enabled(cards.getContext())) return;
    enter(cards, 0, 12, 0);
    enter(topRow, 9, 0, 30);
    enter(bottomRow, 9, 0, 60);
  }

  private static void enter(View view, int x, int y, long delay) {
    float density = view.getResources().getDisplayMetrics().density;
    view.setAlpha(0);
    view.setTranslationX(x * density);
    view.setTranslationY(y * density);
    view.animate()
        .alpha(1)
        .translationX(0)
        .translationY(0)
        .setStartDelay(delay)
        .setDuration(190)
        .setInterpolator(new DecelerateInterpolator(1.5f))
        .start();
  }

  static void drawerWindow(Window window) {
    Context context = window.getContext();
    // Let the dialog resolve its theme before applying the app's motion setting.
    window.getDecorView();
    window.setWindowAnimations(enabled(context) ? context.getResources().getIdentifier(
        "FH4DroidPauseDrawerMotion", "style", context.getPackageName()) : 0);
  }

  static void focus(View view, boolean focused) {
    view.animate().cancel();
    view.setScaleX(1);
    view.setScaleY(1);
    if (!focused || !enabled(view.getContext())) return;
    // Settle at the approved size: the steady-state screenshot stays identical.
    view.setScaleX(.985f);
    view.setScaleY(.985f);
    view.animate()
        .scaleX(1)
        .scaleY(1)
        .setDuration(160)
        .setInterpolator(new DecelerateInterpolator())
        .start();
  }

  static void press(View view) {
    if (!enabled(view.getContext())) {
      view.setStateListAnimator(null);
      view.setScaleX(1);
      view.setScaleY(1);
      return;
    }
    StateListAnimator states = new StateListAnimator();
    for (boolean pressed : new boolean[] {true, false}) {
      AnimatorSet set = new AnimatorSet();
      float size = pressed ? .975f : 1;
      set.playTogether(
          ObjectAnimator.ofFloat(view, "scaleX", size),
          ObjectAnimator.ofFloat(view, "scaleY", size));
      set.setDuration(pressed ? 80 : 150);
      set.setInterpolator(new DecelerateInterpolator());
      states.addState(pressed ? new int[] {android.R.attr.state_pressed} : new int[0], set);
    }
    view.setStateListAnimator(states);
  }

  static void stop(View view) {
    view.animate().cancel();
    view.setAlpha(1);
    view.setTranslationX(0);
    view.setTranslationY(0);
    view.setScaleX(1);
    view.setScaleY(1);
  }

  private MenuMotion() {}
}
