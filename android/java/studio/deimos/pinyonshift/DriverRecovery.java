package studio.deimos.pinyonshift;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Local launch state shared by the launcher and the isolated game process. */
final class DriverRecovery {
    private static File file(Context context, String name) {
        return new File(MobileConfig.base(context), "state/config/" + name + ".json");
    }

    private static JSONObject read(Context context, String name) {
        try { return new JSONObject(ContentFiles.text(file(context, name), 16384)); }
        catch (Exception ignored) { return new JSONObject(); }
    }

    private static void write(Context context, String name, JSONObject state) throws Exception {
        ContentFiles.atomic(file(context, name), state.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String identity(Context context, String driver) {
        if (driver.isEmpty()) return Build.FINGERPRINT;
        if (!driver.matches("[A-Za-z0-9_-]{1,100}")) return null;
        try {
            File folder = new File(DriverStore.root(context), driver);
            return FileIdentity.stamp(new File(folder, DriverStore.library(folder)));
        } catch (Exception ignored) { return null; }
    }

    private static boolean startupCrash(Context context, JSONObject attempt) {
        long start = attempt.optLong("started_ms");
        if (start == 0 || attempt.optBoolean("played") || GameSession.interrupted(context) < start)
            return false;
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        if (manager == null) return false;
        try {
            for (ApplicationExitInfo exit : manager.getHistoricalProcessExitReasons(null, 0, 12)) {
                if (exit.getTimestamp() < start || !(context.getPackageName() + ":game").equals(exit.getProcessName()))
                    continue;
                return exit.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE
                    || exit.getReason() == ApplicationExitInfo.REASON_CRASH
                    || exit.getReason() == ApplicationExitInfo.REASON_SIGNALED && exit.getStatus() == 11;
            }
        } catch (RuntimeException ignored) {}
        return false;
    }

    static String fallback(Context context) {
        JSONObject attempt = read(context, "driver-attempt");
        if (!attempt.optBoolean("automatic") || attempt.optInt("streak") < 2
                || attempt.optBoolean("used_retry") || !startupCrash(context, attempt)) return null;
        return lastPlayable(context, attempt.optString("driver"));
    }

    static String lastPlayable(Context context, String failedDriver) {
        JSONObject good = read(context, "driver-last-playable");
        if (!good.has("driver")) return null;
        String driver = good.optString("driver");
        String stamp = identity(context, driver);
        if (driver.equals(failedDriver) || stamp == null
                || !stamp.equals(good.optString("identity"))) return null;
        return driver;
    }

    static void consumeRetry(Context context) {
        try { write(context, "driver-attempt", read(context, "driver-attempt").put("used_retry", true)); }
        catch (Exception error) { android.util.Log.w("FH4Droid", "Driver retry state unavailable", error); }
    }

    static void launched(Context context, String driver, boolean automatic, boolean retry) {
        JSONObject prior = read(context, "driver-attempt");
        boolean continuing = automatic && prior.optBoolean("automatic") && startupCrash(context, prior);
        try {
            JSONObject state = new JSONObject().put("driver", driver).put("automatic", automatic)
                .put("started_ms", System.currentTimeMillis())
                .put("used_retry", retry || continuing && prior.optBoolean("used_retry"))
                .put("streak", continuing ? Math.min(100, prior.optInt("streak") + 1) : 1);
            write(context, "driver-attempt", state);
        } catch (Exception error) { android.util.Log.w("FH4Droid", "Driver launch state unavailable", error); }
    }

    static void recordGameplay(PinyonShiftActivity activity) {
        String driver = activity.getIntent().getStringExtra("verified_driver");
        JSONObject state = read(activity, "driver-attempt");
        if (driver == null || !driver.equals(state.optString("driver", "auto"))) return;
        try {
            write(activity, "driver-attempt", state.put("played", true));
            String stamp = identity(activity, driver);
            if (stamp != null && activity.getIntent().getStringExtra("driver_flag_trial") == null) {
                write(activity, "driver-last-playable", new JSONObject().put("driver", driver).put("identity", stamp));
            }
        } catch (Exception error) { android.util.Log.w("FH4Droid", "Driver recovery state unavailable", error); }
    }
}
