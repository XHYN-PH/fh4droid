package studio.deimos.pinyonshift;

import android.app.Activity;
import android.content.Context;
import android.system.Os;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.JSONObject;

/**
 * Opt-in controls for the driver that actually passed preflight, not its ZIP
 * filename.
 */
final class AdrenoSettings {
  private static final Set<String> OWNED =
      new HashSet<>(Arrays.asList("sysmem", "gmem", "nolrz", "noubwc"));
  static Map<String, String> environment(String literal) {
    Map<String, String> pairs = new LinkedHashMap<>();
    for (String pair : SettingValues.plain(literal).split(";")) {
      int equals = pair.indexOf('=');
      if (equals > 0)
        pairs.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
    }
    return pairs;
  }
  static String flags(String literal) {
    return environment(literal).getOrDefault("TU_DEBUG", "");
  }
  static boolean has(String flags, String token) {
    return Arrays.asList(flags.split(",")).contains(token);
  }
  static String configure(String literal, int mode, boolean noLrz, boolean noUbwc) {
    Map<String, String> pairs = environment(literal);
    Set<String> flags = new LinkedHashSet<>();
    for (String flag : pairs.getOrDefault("TU_DEBUG", "").split(","))
      if (!flag.trim().isEmpty() && !OWNED.contains(flag.trim()))
        flags.add(flag.trim());
    if (mode == 1)
      flags.add("sysmem");
    if (mode == 2)
      flags.add("gmem");
    if (noLrz)
      flags.add("nolrz");
    if (noUbwc)
      flags.add("noubwc");
    if (flags.isEmpty())
      pairs.remove("TU_DEBUG");
    else
      pairs.put("TU_DEBUG", String.join(",", flags));
    List<String> output = new ArrayList<>();
    for (Map.Entry<String, String> e : pairs.entrySet())
      output.add(e.getKey() + "=" + e.getValue());
    return String.join(";", output);
  }
  static boolean isAdreno(int vendor, String gpu) {
    // Turnip also uses FDxxx device names. Use Vulkan's Qualcomm vendor ID.
    return vendor == 0x5143;
  }
  static boolean isTurnip(int vendor, String gpu, int driverId) {
    return isAdreno(vendor, gpu) && driverId == 18; // VK_DRIVER_ID_MESA_TURNIP
  }
  private static File reportFile(Context c) {
    return new File(MobileConfig.base(c), "state/config/android-driver-test.json");
  }
  static void record(Context c, String requested, String report) throws IOException {
    try {
      JSONObject data = new JSONObject(report);
      data.put("requested_driver", requested == null ? "" : requested);
      data.put("tested_ms", System.currentTimeMillis());
      File target = reportFile(c);
      target.getParentFile().mkdirs();
      File stage = new File(target.getPath() + ".part");
      try (FileOutputStream out = new FileOutputStream(stage)) {
        out.write(data.toString().getBytes(StandardCharsets.UTF_8));
        out.getFD().sync();
      }
      Files.move(stage.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } catch (org.json.JSONException e) {
      throw new IOException("Invalid driver test result", e);
    }
  }
  static JSONObject report(Context c) {
    File file = reportFile(c);
    try {
      if (!file.isFile() || file.length() > 65536)
        return new JSONObject();
      JSONObject data =
          new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
      return MobileConfig.get(c, "android_gpu_driver", "auto")
                  .equals(data.optString("requested_driver"))
              && data.optBoolean("ok")
          ? data
          : new JSONObject();
    } catch (Exception e) {
      return new JSONObject();
    }
  }
  static String reportDetails(Context c) {
    try {
      File file = reportFile(c);
      return file.isFile() && file.length() <= 65536
          ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
          : "No completed driver test recorded";
    } catch (IOException e) {
      return "Driver test report unavailable";
    }
  }
  /**
   * Same two driver variables as the runtime loader, before preflight opens
   * the custom ICD.
   */
  static void prepareProbe(Context c, String selected) throws Exception {
    if (selected == null || selected.isEmpty())
      return;
    Map<String, String> pairs = environment(MobileConfig.get(c, "android_gpu_driver_env", ""));
    for (String key : new String[] {"TU_DEBUG", "IR3_SHADER_DEBUG"}) {
      String value = pairs.get(key);
      if (value != null && value.length() <= 4096)
        Os.setenv(key, value, true);
    }
    if (!pairs.containsKey("IR3_SHADER_DEBUG") && Os.getenv("IR3_SHADER_DEBUG") == null
        && MobileConfig.get(c, "android_gpu_driver_no_preamble", "true").equals("true"))
      Os.setenv("IR3_SHADER_DEBUG", "nopreamble", false);
  }
  private static void save(
      Activity a, MobileSettings.Runtime runtime, int mode, boolean noLrz, boolean noUbwc) {
    String configured =
        configure(MobileConfig.get(a, "android_gpu_driver_env", ""), mode, noLrz, noUbwc);
    SettingsHub.apply(a, runtime,
        Collections.singletonMap("android_gpu_driver_env", MobileConfig.quote(configured)), true);
  }
  static void show(Activity a, MobileSettings.Runtime runtime) {
    LinearLayout body = FestivalUi.column(a);
    JSONObject report = report(a);
    boolean adreno = isAdreno(report.optInt("vendor_id"), report.optString("gpu"));
    boolean turnip =
        isTurnip(report.optInt("vendor_id"), report.optString("gpu"), report.optInt("driver_id"))
        && !report.optString("effective_driver").isEmpty();
    body.addView(FestivalUi.text(a,
        report.optString("gpu", "Run the Vulkan test for your selected driver first."), 14,
        FestivalUi.TEXT));
    body.addView(FestivalUi.text(a,GpuCompatibility.guidance(report.optInt("vendor_id"),report.optString("gpu")),13,FestivalUi.MUTED));
    if (adreno) {
      body.addView(FestivalUi.text(a,
          report.optString("driver_name", "Driver") + ". Changes apply on the next game launch.",
          12, FestivalUi.MUTED));
      FestivalUi.toggle(a, body, "Prefer peak GPU speed",
          "Adreno clock request. Uses more power and can heat the phone "
              + "sooner. Thermal limits still apply. Default off.",
          MobileConfig.get(a, "android_gpu_turbo", "false").equals("true"),
          on
          -> SettingsHub.apply(a, runtime,
              Collections.singletonMap("android_gpu_turbo", Boolean.toString(on)), true));
    }
    if (turnip) {
      Button rendering = FestivalUi.action(a, body, "", () -> {});
      String[] modes = {
          "Automatic · recommended", "System memory · experimental", "Tiled memory · experimental"};
      FestivalUi.onRefresh(rendering, () -> {
        String f = flags(MobileConfig.get(a, "android_gpu_driver_env", ""));
        rendering.setText("Turnip rendering: "
            + modes[has(f, "sysmem") ? 1
                    : has(f, "gmem") ? 2
                                     : 0]);
      });
      rendering.setOnClickListener(v -> {
        String f = flags(MobileConfig.get(a, "android_gpu_driver_env", ""));
        FestivalUi.choose(a, "Turnip rendering · next launch", modes,
            has(f, "sysmem")     ? 1
                : has(f, "gmem") ? 2
                                 : 0,
            i -> {
              save(a, runtime, i, has(f, "nolrz"), has(f, "noubwc"));
              FestivalUi.refreshTree(body);
            });
      });
      body.addView(FestivalUi.text(a,
          "Automatic lets Turnip choose. Forced modes can be slower or "
              + "render differently; compare the same route before keeping a change.",
          12, FestivalUi.MUTED));
      FestivalUi.toggle(a, body, "Skip shader preambles",
          "The renderer's existing Turnip option. Default on; changing it "
              + "recompiles affected driver shaders. An explicit IR3 override "
              + "takes precedence.",
          MobileConfig.get(a, "android_gpu_driver_no_preamble", "true").equals("true"),
          on
          -> SettingsHub.apply(a, runtime,
              Collections.singletonMap("android_gpu_driver_no_preamble", Boolean.toString(on)),
              true));
      for (String[] option :
          new String[][] {{"nolrz", "Depth workaround",
                              "Disable Turnip's low-resolution depth rejection for an "
                                  + "artifact comparison. Can reduce performance."},
              {"noubwc", "Framebuffer compression workaround",
                  "Disable Turnip's framebuffer compression for an artifact "
                      + "comparison. Can increase memory bandwidth and power use."}}) {
        String token = option[0];
        FestivalUi.toggle(a, body, option[1], option[2],
            has(flags(MobileConfig.get(a, "android_gpu_driver_env", "")), token), on -> {
              String f = flags(MobileConfig.get(a, "android_gpu_driver_env", ""));
              save(a, runtime,
                  has(f, "sysmem")     ? 1
                      : has(f, "gmem") ? 2
                                       : 0,
                  token.equals("nolrz") ? on : has(f, "nolrz"),
                  token.equals("noubwc") ? on : has(f, "noubwc"));
            });
      }
    } else
      body.addView(FestivalUi.text(a,
          adreno ? "Turnip options require an imported or bundled Mesa "
                  + "Turnip driver that passed the Vulkan test."
                 : "Adreno controls are available after the selected driver "
                  + "reports a Qualcomm Adreno GPU. Other GPUs keep their own "
                  + "driver settings.",
          12, FestivalUi.MUTED));
    if (adreno)
      FestivalUi.action(a, body, "Restore Adreno defaults",
          ()
              -> new android.app.AlertDialog.Builder(a)
                     .setTitle("Restore Adreno defaults?")
                     .setMessage("Use automatic Turnip rendering, normal depth and "
                         + "compression, default preambles and no peak-speed "
                         + "request. Your graphics preset and driver are kept.")
                     .setNegativeButton("Cancel", null)
                     .setPositiveButton("Restore",
                         (d, w) -> {
                           Map<String, String> defaults = new LinkedHashMap<>();
                           defaults.put("android_gpu_turbo", "false");
                           defaults.put("android_gpu_driver_no_preamble", "true");
                           defaults.put("android_gpu_driver_env",
                               MobileConfig.quote(
                                   configure(MobileConfig.get(a, "android_gpu_driver_env", ""), 0,
                                       false, false)));
                           if (SettingsHub.apply(a, runtime, defaults, true)) {
                             if (a instanceof FestivalUi.PanelHost)
                               ((FestivalUi.PanelHost) a).backPanel();
                           }
                         })
                     .show());
    FestivalUi.present(a, "Adreno · Advanced", body);
  }
  private AdrenoSettings() {}
}
