package studio.deimos.pinyonshift;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;

/** Content tools do not load the game or install custom Vulkan hooks. */
final class ContentNative {
    private static boolean loaded;
    private static synchronized void load() throws IOException {
        if (loaded) return;
        try {
            System.loadLibrary("c++_shared");
            System.loadLibrary("rexruntime");
            System.loadLibrary("fh4droid_android");
            loaded = true;
        } catch (LinkageError error) { throw new IOException("Content tools could not load", error); }
    }
    private static native byte[] nativeManifest(String path) throws IOException;
    private static native void nativeExtractDlc(String source,String destination,String id) throws IOException;
    private static native long nativeExtractForzaProfile(String source,String destination) throws IOException;
    private static native byte[] nativeReadConfig(byte[] text) throws IOException;
    private static native byte[] nativeUpdateConfig(byte[] text, byte[] changes) throws IOException;
    private static native byte[] nativeRestoreConfig(byte[] text, byte[] before, String key) throws IOException;

    static Map<String,String> readConfig(byte[] text) throws IOException {
        load();
        Map<String,String> settings = new LinkedHashMap<>();
        try {
            JSONObject values = new JSONObject(new String(nativeReadConfig(text), StandardCharsets.UTF_8));
            for (Iterator<String> keys = values.keys(); keys.hasNext();) {
                String key = keys.next();
                settings.put(key, values.getString(key));
            }
        } catch (org.json.JSONException error) {
            throw new IOException("Could not read the settings", error);
        }
        return settings;
    }
    static String updateConfig(String text, String changes) throws IOException {
        load();
        return new String(nativeUpdateConfig(text.getBytes(StandardCharsets.UTF_8),
            changes.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }
    static String restoreConfig(String text, String before, String key) throws IOException {
        load();
        return new String(nativeRestoreConfig(text.getBytes(StandardCharsets.UTF_8),
            before.getBytes(StandardCharsets.UTF_8), key), StandardCharsets.UTF_8);
    }
    static JSONObject manifest(File file) throws IOException {
        load();
        try { return new JSONObject(new String(nativeManifest(file.getAbsolutePath()),java.nio.charset.StandardCharsets.UTF_8)); }
        catch (org.json.JSONException error) { throw new IOException("Invalid mod manifest", error); }
    }
    static void extractDlc(File source,File destination,String id) throws IOException {
        load();nativeExtractDlc(source.getAbsolutePath(),destination.getAbsolutePath(),id);
    }

    static long extractForzaProfile(File source, File destination) throws IOException {
        load();
        return nativeExtractForzaProfile(source.getAbsolutePath(), destination.getAbsolutePath());
    }
    private ContentNative() {}
}
