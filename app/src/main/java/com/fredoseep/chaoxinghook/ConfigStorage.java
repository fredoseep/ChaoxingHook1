package com.fredoseep.chaoxinghook;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** No root, cross-package access, module resources or Kotlin runtime required. */
public final class ConfigStorage {
    private ConfigStorage() {}

    /**
     * 首选：公共 Download 下的统一配置（模块 App 与宿主共用同一份）。
     * 注意：不能选宿主的 Android/data 目录——Android 11+ 对跨应用 Android/data 是 FUSE 硬隔离，
     * 模块 App 即使持有「所有文件访问」也进不去（实测）；实际读写失败时由 read/write 回退各自私有目录。
     */
    private static File preferred(Context context) {
        try {
            File downloads = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS);
            if (downloads != null && downloads.isDirectory()) {
                return new File(downloads, "chaoxing_loc.txt");
            }
        } catch (Throwable ignored) {}
        return fallback(context);
    }

    /** 回退：各进程自己的私有目录（永远可用，不需要任何存储权限）。 */
    private static File fallback(Context context) {
        if (context == null) return new File(ConfigManagerPath.LEGACY);
        if ("com.chaoxing.mobile".equals(context.getPackageName())) {
            File dir = context.getExternalFilesDir(null);
            if (dir == null) throw new IllegalStateException("宿主外部文件目录不可用，请稍后重试");
            return new File(dir, "chaoxing_loc.txt");
        }
        return new File(context.getFilesDir(), "chaoxing_loc.txt");
    }

    /** 对外展示的配置路径（首选位置；实际读写失败时由 read/write 自动回退）。 */
    public static File file(Context context) { return preferred(context); }

    /** 最近一次实际读写的文件路径（诊断用） */
    public static volatile String lastUsedPath = "";

    private static final class ConfigManagerPath {
        static final String LEGACY = "/storage/emulated/0/Android/data/com.chaoxing.mobile/files/chaoxing_loc.txt";
    }

    public static synchronized String read(Context context, String defaults) throws IOException {
        // 首选路径的可访问性不能靠 canWrite() 预判（沙箱/FUSE 下有假阳性），实际打开失败即回退。
        // 注意 Android 上 EACCES 也抛 FileNotFoundException，走「缺失」分支时回退副本优先于播种默认值。
        File pref = preferred(context);
        try {
            return readAt(pref);
        } catch (FileNotFoundException missing) {
            File fb = fallback(context);
            if (!fb.equals(pref)) {
                try { return readAt(fb); } catch (FileNotFoundException ignored) {}
            }
            // 只有「真缺失」才在统一位置播种默认值；文件存在但跨 uid 不可读时直接走回退，
            // 否则播种的改名会被 FUSE 拒掉留下 .new 残留并把整个读操作拖崩
            if (!pref.exists()) {
                try { writeAt(pref, defaults); return defaults; }
                catch (IOException e) { if (fb.equals(pref)) throw e; }
            }
            writeAt(fb, defaults);
            return defaults;
        } catch (IOException e) {
            File fb = fallback(context);
            if (fb.equals(pref)) throw e;
            try { return readAt(fb); }
            catch (FileNotFoundException m2) { writeAt(fb, defaults); return defaults; }
        }
    }

    private static String readAt(File target) throws IOException {
        // AtomicFile.openRead also recovers an interrupted write's backup.
        try (InputStream in = new AtomicFile(target).openRead()) { lastUsedPath = target.getPath(); return readText(in); }
    }

    public static synchronized void write(Context context, String text) throws IOException {
        File pref = preferred(context);
        try {
            writeAt(pref, text);
        } catch (IOException e) {
            File fb = fallback(context);
            if (fb.equals(pref)) throw e;
            writeAt(fb, text);
        }
    }

    private static void writeAt(File target, String text) throws IOException {
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("无法创建配置目录");
        AtomicFile atomic = new AtomicFile(target);
        FileOutputStream out = null;
        try {
            out = atomic.startWrite();
            out.write(text.getBytes(StandardCharsets.UTF_8));
            atomic.finishWrite(out);
        } catch (IOException | RuntimeException e) {
            if (out != null) atomic.failWrite(out);
            throw e;
        }
        // FUSE 对跨 uid 覆盖改名会静默失败（AtomicFile 只打日志不抛异常），必须核实真正落盘
        if (new File(target.getPath() + ".new").exists())
            throw new IOException("写入未生效(.new 残留): " + target);
        byte[] expect = text.getBytes(StandardCharsets.UTF_8);
        byte[] onDisk = new byte[expect.length];
        int got = 0;
        try (InputStream in = new FileInputStream(target)) {
            while (got < onDisk.length) { int n = in.read(onDisk, got, onDisk.length - got); if (n < 0) break; got += n; }
        }
        if (got != expect.length || !java.util.Arrays.equals(onDisk, expect))
            throw new IOException("写入校验失败(回读不一致): " + target);
        lastUsedPath = target.getPath();
    }

    public static String readText(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1) {
            if (bytes.size() + n > 1024 * 1024) throw new IOException("配置文件超过 1MB");
            bytes.write(buffer, 0, n);
        }
        return bytes.toString("UTF-8");
    }
}
