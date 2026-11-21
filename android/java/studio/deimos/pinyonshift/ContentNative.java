package studio.deimos.pinyonshift;

import java.io.File;
import java.io.IOException;
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
    private static native void nativeValidateConfig(byte[] text) throws IOException;

    static void validateConfig(String text) throws IOException {
        load();
        nativeValidateConfig(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
