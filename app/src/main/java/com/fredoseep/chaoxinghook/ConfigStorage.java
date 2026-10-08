package com.fredoseep.chaoxinghook;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** No module resources or Kotlin runtime required. */
public final class ConfigStorage {
    private ConfigStorage() {}
    public static File file(Context context) {
        if (context == null) return new File(ConfigManagerPath.LEGACY);
        // Keep the host's existing file and format; companion APK has its own file.
        if ("com.chaoxing.mobile".equals(context.getPackageName())) {
            File dir = context.getExternalFilesDir(null);
            if (dir == null) throw new IllegalStateException("宿主外部文件目录不可用，请稍后重试");
            return new File(dir, "chaoxing_loc.txt");
        }
        return new File(context.getFilesDir(), "chaoxing_loc.txt");
    }
    private static final class ConfigManagerPath {
        static final String LEGACY = "/storage/emulated/0/Android/data/com.chaoxing.mobile/files/chaoxing_loc.txt";
    }
    public static synchronized String read(Context context, String defaults) throws IOException {
        File target = file(context);
        AtomicFile atomic = new AtomicFile(target);
        // AtomicFile.openRead also recovers an interrupted write's backup.
        if (!target.exists() && !new File(target.getPath() + ".bak").exists()) {
            write(context, defaults);
            return defaults;
        }
        try (InputStream in = atomic.openRead()) { return readText(in); }
    }
    public static synchronized void write(Context context, String text) throws IOException {
        File target = file(context);
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
    }

    // ---------- 模块 App → 宿主配置的 root 桥 ----------
    // 宿主私有目录（Android/data/com.chaoxing.mobile）对其他应用是 FUSE 硬隔离，
    // 唯一共享通道是 root：读=su cat；写=内容经 stdin 交给 su cat >（truncate 原地覆盖，
    // 不经改名、不改属主——宿主设置页才能继续自己读写这份文件）。

    /** 模块 App 侧：root 读宿主配置；无 root / 文件缺失返回 null（由调用方回退私有副本）。 */
    public static String readHostByRoot() {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat '" + ConfigManagerPath.LEGACY + "'"});
            String text = readText(p.getInputStream());
            int code = p.waitFor();
            if (code == 0 && !text.trim().isEmpty()) return text;
        } catch (Throwable ignored) {
        } finally {
            if (p != null) p.destroy();
        }
        return null;
    }

    /** 模块 App 侧：root 覆写宿主配置（stdin 直灌，保持文件属主不变）。 */
    public static void writeHostByRoot(String text) throws IOException {
        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat > '" + ConfigManagerPath.LEGACY + "'"});
        try (OutputStream os = p.getOutputStream()) {
            os.write(text.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
        try {
            int code = p.waitFor();
            if (code != 0) throw new IOException("root 写入宿主配置失败: exit=" + code);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("root 写入被中断");
        } finally {
            p.destroy();
        }
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
