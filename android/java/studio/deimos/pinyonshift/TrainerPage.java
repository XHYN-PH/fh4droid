package studio.deimos.pinyonshift;

import android.app.*;
import android.widget.*;
import java.util.*;

/**
 * Only established trainer flags are exposed; activating it requires a new
 * process.
 */
final class TrainerPage {
  static void show(Activity a, MobileSettings.Runtime runtime) {
    FestivalUi.present(a, "Cheats · modded profile", build(a, runtime));
  }
  private static LinearLayout build(Activity a, MobileSettings.Runtime runtime) {
    LinearLayout body = FestivalUi.column(a);
    body.addView(FestivalUi.text(a,
        "Cheats play a separate modded copy of your profile. Enabling or "
            + "disabling this mode "
            + "requires restarting the game. Local achievements remain "
            + "associated with that profile.",
        12, FestivalUi.MUTED));
    boolean enabled = MobileConfig.get(a, "pinyon_shift_cheats", "false").equals("true");
    FestivalUi.toggle(a, body, "Use cheat profile on next launch",
        "Your normal racing save is kept separate.", enabled, on -> {
          if (SettingsHub.apply(a, runtime,
                  Collections.singletonMap("pinyon_shift_cheats", Boolean.toString(on)), true)
              && a instanceof FestivalUi.PanelHost)
            ((FestivalUi.PanelHost) a).replacePanel("Cheats · modded profile", build(a, runtime));
        });
    boolean live = runtime != null && "true".equals(runtime.value("pinyon_shift_cheats"));
    if (!enabled && !live) {
      body.addView(FestivalUi.text(
          a, "Enable the cheat profile to configure trainer options.", 13, FestivalUi.MUTED));
      return body;
    }
    final boolean pending = !live;
    FestivalUi.action(a, body, "Set credits", () -> {
      EditText input = new EditText(a);
      input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
      input.setHint("0–999999999");
      new AlertDialog.Builder(a)
          .setTitle("Set modded profile credits")
          .setView(input)
          .setNegativeButton("Cancel", null)
          .setPositiveButton("Set",
              (d, w) -> {
                try {
                  long n = Long.parseLong(input.getText().toString());
                  if (n < 0 || n > 999999999)
                    throw new NumberFormatException();
                  SettingsHub.apply(a, runtime,
                      Collections.singletonMap("cheat_set_credits", Long.toString(n)), pending);
                } catch (NumberFormatException e) {
                  FestivalUi.explain(
                      a, "Invalid amount", "Enter a whole number from 0 to 999999999.");
                }
              })
          .show();
    });
    FestivalUi.action(a, body, "Wristband level · next launch",
        ()
            -> FestivalUi.choose(a, "Wristband level",
                new String[] {"Level 1", "Level 2", "Level 3", "Level 4", "Level 5", "Level 6",
                    "Level 7", "Level 8"},
                -1,
                i
                -> SettingsHub.apply(a, runtime,
                    Collections.singletonMap("cheat_set_profile_fields",
                        MobileConfig.quote("Main/WristbandLevel=" + (i + 1))),
                    true)));
    FestivalUi.slider(a, body, "Game speed", "Changes gameplay speed, not graphics performance.",
        25, 200, 5, speed(a),
        i
        -> i + "%",
        i
        -> SettingsHub.apply(a, runtime,
            Collections.singletonMap("cheat_time_scale", Float.toString(i / 100f)), pending));
    FestivalUi.action(a, body, "Time of day",
        ()
            -> FestivalUi.choose(a, "Time of day",
                new String[] {"Normal cycle", "Dawn · 06:00", "Noon · 12:00", "Sunset · 18:00",
                    "Night · 22:00"},
                -1,
                i
                -> SettingsHub.apply(a, runtime,
                    Collections.singletonMap(
                        "cheat_time_of_day", new String[] {"-1", "6", "12", "18", "22"}[i]),
                    pending)));
    FestivalUi.action(a, body, "Day length",
        ()
            -> FestivalUi.choose(a, "Day length",
                new String[] {"Game default", "12 minutes", "24 minutes", "48 minutes", "2 hours"},
                -1,
                i
                -> SettingsHub.apply(a, runtime,
                    Collections.singletonMap(
                        "cheat_day_length_minutes", new String[] {"0", "12", "24", "48", "120"}[i]),
                    pending)));
    for (String[] option : new String[][] {{"cheat_free_camera", "Free camera"},
             {"cheat_show_collectibles", "Show collectibles on map"},
             {"cheat_any_car_events", "Any car in any event"}}) {
      String key = option[0];
      FestivalUi.toggle(a, body, option[1], "Event restrictions change when the next event loads.",
          MobileConfig.get(a, key, "false").equals("true"),
          on
          -> SettingsHub.apply(
              a, runtime, Collections.singletonMap(key, Boolean.toString(on)), pending));
    }
    return body;
  }
  private static int speed(Activity a) {
    try {
      float value = Float.parseFloat(MobileConfig.get(a, "cheat_time_scale", "1"));
      return Float.isFinite(value) ? Math.max(25, Math.min(200, Math.round(value * 100))) : 100;
    } catch (NumberFormatException e) {
      return 100;
    }
  }
  private TrainerPage() {}
}
