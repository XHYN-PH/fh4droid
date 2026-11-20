package studio.deimos.pinyonshift;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import java.io.File;
import java.io.FileDescriptor;

/** Cache only regular files with a usable device, inode and change timestamp. */
final class FileIdentity {
  static String stamp(File file) {
    try { return stamp(Os.stat(file.getPath())); }
    catch (ErrnoException | SecurityException error) { return null; }
  }

  static String stamp(FileDescriptor fd) {
    try { return stamp(Os.fstat(fd)); }
    catch (ErrnoException | SecurityException error) { return null; }
  }

  private static String stamp(StructStat s) {
    if (!OsConstants.S_ISREG(s.st_mode) || s.st_ino <= 0 || s.st_size <= 0
        || s.st_ctim == null || s.st_mtim == null || s.st_ctim.tv_sec <= 0) return null;
    return s.st_dev + ":" + s.st_ino + ":" + s.st_size + ":" + s.st_ctim.tv_sec + ":"
        + s.st_ctim.tv_nsec + ":" + s.st_mtim.tv_sec + ":" + s.st_mtim.tv_nsec;
  }

  private FileIdentity() {}
}
