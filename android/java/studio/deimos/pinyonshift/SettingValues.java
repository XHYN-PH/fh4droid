package studio.deimos.pinyonshift;

/**
 * Compare TOML literals with runtime cvar formatting without false pending
 * labels.
 */
final class SettingValues {
  static String plain(String value) {
    String s = value == null ? "" : value.trim();
    return s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")
        ? s.substring(1, s.length() - 1)
        : s;
  }
  static boolean same(String saved, String active) {
    String a = plain(saved), b = plain(active);
    if (a.equals(b))
      return true;
    try {
      double x = Double.parseDouble(a), y = Double.parseDouble(b);
      return Double.isFinite(x) && Double.isFinite(y)
          && Math.abs(x - y) <= 1e-6 * Math.max(1, Math.max(Math.abs(x), Math.abs(y)));
    } catch (NumberFormatException e) {
      return false;
    }
  }
  private SettingValues() {}
}
