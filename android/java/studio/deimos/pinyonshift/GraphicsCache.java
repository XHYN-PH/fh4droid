package studio.deimos.pinyonshift;

import android.app.*;
import android.widget.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Cache-only management; never traverses links or the user/save tree. */
final class GraphicsCache {
  static File root(android.content.Context c) {
    return new File(MobileConfig.base(c), "state/cache");
  }
  static long size(File file) throws IOException {
    if (!file.exists())
      return 0;
    final long[] bytes = {0};
    final int[] count = {0};
    Files.walkFileTree(
        file.toPath(), EnumSet.noneOf(FileVisitOption.class), 12, new SimpleFileVisitor<Path>() {
          public FileVisitResult visitFile(Path p, BasicFileAttributes a) throws IOException {
            if (++count[0] > 100000)
              throw new IOException("Cache contains too many files");
            if (a.isRegularFile())
              bytes[0] += a.size();
            return FileVisitResult.CONTINUE;
          }
        });
    return bytes[0];
  }
  static boolean graphicsDirectory(File cache, File candidate) {
    if (!candidate.toPath().toAbsolutePath().normalize().getParent().equals(
            cache.toPath().toAbsolutePath().normalize()))
      return false;
    String name = candidate.getName();
    return name.equals("shaders") || name.matches("raster-v[0-9]+-(?:[2-9][0-9]|100)");
  }
  static void clear(File cache, File selected) throws IOException {
    Path root = cache.toPath().toAbsolutePath().normalize(),
         path = selected.toPath().toAbsolutePath().normalize();
    if (!graphicsDirectory(cache, selected) || !path.startsWith(root) || path.equals(root)
        || Files.isSymbolicLink(root) || Files.isSymbolicLink(path))
      throw new IOException("Invalid cache selection");
    Path parent = path.getParent();
    while (parent != null && parent.startsWith(root)) {
      if (Files.isSymbolicLink(parent))
        throw new IOException("Linked cache directory");
      parent = parent.getParent();
    }
    if (!Files.exists(path))
      return;
    Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
      public FileVisitResult visitFile(Path p, BasicFileAttributes a) throws IOException {
        Files.delete(p);
        return FileVisitResult.CONTINUE;
      }
      public FileVisitResult postVisitDirectory(Path p, IOException error) throws IOException {
        if (error != null)
          throw error;
        Files.delete(p);
        return FileVisitResult.CONTINUE;
      }
    });
  }
  static void show(Activity a) {
    LinearLayout body = FestivalUi.column(a);
    body.addView(FestivalUi.text(a,
        "Cached graphics reduce repeated preparation. Clearing a cache makes "
            + "the next launch "
            + "prepare it again. Quit the game before clearing.",
        12, FestivalUi.MUTED));
    TextView status = FestivalUi.text(a, "Checking graphics caches…", 13, FestivalUi.MUTED);
    body.addView(status);
    FestivalUi.present(a, "Graphics cache", body);
    new Thread(() -> {
      try {
        File cache = root(a);
        File[] children = cache.listFiles();
        List<File> found = new ArrayList<>();
        List<Long> sizes = new ArrayList<>();
        if (children != null)
          for (File file : children)
            if (graphicsDirectory(cache, file) && !Files.isSymbolicLink(file.toPath())
                && file.isDirectory()) {
              found.add(file);
              sizes.add(size(file));
            }
        a.runOnUiThread(() -> {
          if (a.isFinishing() || a.isDestroyed())
            return;
          status.setText(found.isEmpty() ? "No graphics cache has been created yet."
                                         : "Select only the cache you want to rebuild.");
          for (int i = 0; i < found.size(); i++) {
            File selected = found.get(i);
            long bytes = sizes.get(i);
            FestivalUi.action(a, body,
                selected.getName() + " · "
                    + String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0),
                () -> {
                  if (GameFiles.running(a)) {
                    FestivalUi.explain(a, "Game is running",
                        "Quit to the launcher before clearing "
                            + "a graphics cache.");
                    return;
                  }
                  new AlertDialog.Builder(a)
                      .setTitle("Rebuild this cache?")
                      .setMessage("Only " + selected.getName()
                          + (" will be cleared. Saves, game files and "
                              + "settings are kept."))
                      .setNegativeButton("Cancel", null)
                      .setPositiveButton("Clear cache",
                          (d, w)
                              -> new Thread(
                                  ()
                                      -> {
                                    try {
                                      if (GameFiles.running(a))
                                        throw new IOException("Quit the game before "
                                            + "clearing a graphics cache");
                                      clear(cache, selected);
                                      a.runOnUiThread(
                                          ()
                                              -> status.setText("Cache cleared. It will "
                                                  + "be "
                                                  + "prepared on the next "
                                                  + "launch."));
                                    } catch (IOException e) {
                                      a.runOnUiThread(() -> SettingsHub.error(a, e));
                                    }
                                  },
                                  "cache-clear")
                                     .start())
                      .show();
                });
          }
        });
      } catch (IOException e) {
        a.runOnUiThread(() -> status.setText("Could not inspect caches: " + e.getMessage()));
      }
    }, "cache-inspect").start();
  }
  private GraphicsCache() {}
}
