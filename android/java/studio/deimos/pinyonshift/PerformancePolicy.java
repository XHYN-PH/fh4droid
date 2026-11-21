package studio.deimos.pinyonshift;

/** Deterministic Android resource policy. It never rewrites a saved preset. */
final class PerformancePolicy {
    static int texturePoolMb(long totalBytes, boolean lowRam) {
        long gib = 1024L * 1024 * 1024;
        if (lowRam || totalBytes > 0 && totalBytes <= 4 * gib) return 64;
        if (totalBytes > 0 && totalBytes <= 6 * gib) return 128;
        return 256; // Preserve the existing allocation when RAM is unknown.
    }
    static int shaderWorkers(long totalBytes, boolean lowRam, int cores) {
        return lowRam || totalBytes > 0 && totalBytes <= 4L * 1024 * 1024 * 1024
            || cores <= 4 ? 1 : 2;
    }
    static int configured(String text, int automatic, int min, int max) {
        if (text == null) return automatic;
        try { int n = Integer.parseInt(text); return n < 0 ? automatic : Math.max(min, Math.min(max, n)); }
        catch (NumberFormatException error) { return automatic; }
    }
    static float presentationTarget(String game, String host) {
        float g = rate(game), h = rate(host);
        return g == 0 ? h : h == 0 ? g : Math.min(g, h);
    }
    static float seamlessCadence(float fps, float current, float[] alternatives) {
        if (!(fps>0) || !Float.isFinite(fps)) return 0;
        if (evenCadence(fps,current)) return current;
        float match = Float.POSITIVE_INFINITY;
        for (float refresh : alternatives) if (evenCadence(fps,refresh)) match=Math.min(match,refresh);
        return Float.isFinite(match)?match:0;
    }
    static boolean evenCadence(float fps,float refresh) {
        if (!Float.isFinite(fps) || fps<=0 || !Float.isFinite(refresh) || refresh<=0) return false;
        float multiple = refresh / fps;
        int repeat = Math.round(multiple);
        return repeat >= 1 && Math.abs(multiple-repeat) <= repeat*.0015f;
    }
    static boolean preferDisplayMode(float fps, float refresh, float chosen, boolean batterySaver) {
        return refresh > chosen && (!batterySaver || refresh <= 60.1f) && evenCadence(fps, refresh);
    }
    private static float rate(String text) {
        if (text == null) return 0;
        try { float n = Float.parseFloat(text); return Float.isFinite(n) ? Math.max(0, Math.min(240, n)) : 0; }
        catch (NumberFormatException error) { return 0; }
    }
    static boolean trim(int level) {
        // RUNNING_LOW/CRITICAL or actual background pressure. UI_HIDDEN
        // alone only means the window disappeared, not that memory is low.
        return level >= 10 && level < 20 || level >= 40;
    }
    private PerformancePolicy() {}
}
