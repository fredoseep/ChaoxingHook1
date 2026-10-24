package com.fredoseep.chaoxinghook;

import android.app.AndroidAppHelper;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;

/** 宿主配置读取、3 秒缓存及读取重入保护。 */
final class HookConfigRepository {
    private static final String TAG_CONFIG = "CX.Config";
    static final String FAKE_UPLOAD_FILE_PATH = "/storage/emulated/0/Download/fake_exam_image.png";
    private static final ThreadLocal<Boolean> READING_CONFIG = ThreadLocal.withInitial(() -> false);
    private static SignConfig cachedConfig;
    private static long lastReadTime;

    static SignConfig getSignConfig() {
        // 核心修复：防止底层死循环读取
        if (Boolean.TRUE.equals(READING_CONFIG.get())) {
            return cachedConfig != null ? cachedConfig : new SignConfig();
        }

        if (cachedConfig != null && (System.currentTimeMillis() - lastReadTime < 3000)) return cachedConfig;

        READING_CONFIG.set(true);
        try {
            SignConfig config = new SignConfig();
            File file = ConfigStorage.file(AndroidAppHelper.currentApplication());

            if (!file.exists() && !new File(file.getPath() + ".bak").exists()) {
                try {
                    file.getParentFile().mkdirs();
                    FileWriter fw = new FileWriter(file);
                    fw.write("是否开启定位修改: false\n经度: \n纬度: \n是否开启地址名修改: false\n地址名: \n是否开启名字修改: false\n名字: \n是否开启随机指纹: true\n是否开启经纬度爆破: false\n是否开启手势自动签到: false\n是否开启签到码自动签到: false\n是否开启考试风控拦截: true\n是否开启复制限制解除: true\n是否开启考试截图替换: false\n截图替换路径: " + FAKE_UPLOAD_FILE_PATH + "\n");
                    fw.close();
                } catch (Exception e) {}
                cachedConfig = config;
                lastReadTime = System.currentTimeMillis();
                return config;
            }

            try (BufferedReader br = new BufferedReader(new java.io.InputStreamReader(
                    new android.util.AtomicFile(file).openRead(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("是否开启定位修改:")) config.modifyLocation = parseBooleanValue(line);
                    else if (line.startsWith("经度:")) config.longitude = parseStringValue(line);
                    else if (line.startsWith("纬度:")) config.latitude = parseStringValue(line);
                    else if (line.startsWith("是否开启地址名修改:")) config.modifyAddress = parseBooleanValue(line);
                    else if (line.startsWith("地址名:")) config.address = parseStringValue(line);
                    else if (line.startsWith("是否开启名字修改:")) config.modifyName = parseBooleanValue(line);
                    else if (line.startsWith("名字:")) config.name = parseStringValue(line);
                    else if (line.startsWith("是否开启随机指纹:")) config.randomizeDeviceFlag = parseBooleanValue(line);
                    else if (line.startsWith("是否开启经纬度爆破:")) config.autoCalculateLocation = parseBooleanValue(line);
                    else if (line.startsWith("是否开启手势自动签到:")) config.autoGestureSign = parseBooleanValue(line);
                    else if (line.startsWith("是否开启签到码自动签到:")) config.autoCodeSign = parseBooleanValue(line);
                    else if (line.startsWith("是否开启考试风控拦截:")) config.bypassExamCheat = parseBooleanValue(line);
                    else if (line.startsWith("是否开启复制限制解除:")) config.enableCopyRestriction = parseBooleanValue(line);
                    else if (line.startsWith("是否开启考试截图替换:")) config.replaceExamScreenshot = parseBooleanValue(line);
                    else if (line.startsWith("截图替换路径:")) config.fakeImagePath = parseStringValue(line);
                }
            } catch (Exception e) {}

            cachedConfig = config;
            lastReadTime = System.currentTimeMillis();
            return config;
        } catch (Exception e) {
            Log.e(TAG_CONFIG, "config-read FAILED: ", e);
            return cachedConfig != null ? cachedConfig : new SignConfig();
        } finally {
            READING_CONFIG.set(false);
        }
    }

    static boolean parseBooleanValue(String line) {
        try { return "true".equalsIgnoreCase(line.substring(line.indexOf(":") + 1).trim()); } catch (Exception e) { return false; }
    }

    static String parseStringValue(String line) {
        try { return line.substring(line.indexOf(":") + 1).trim(); } catch (Exception e) { return ""; }
    }
}
