package com.fredoseep.chaoxinghook;

import java.util.Random;
import static com.fredoseep.chaoxinghook.HookConfigRepository.getSignConfig;

/** 设备标识回调替换及随机 flag 生成。 */
final class DeviceFlagHook {
    static String transformJavascript(String jsCode) {
        if (jsCode.contains("CLIENT_DEVICE_FLAG")) {
            SignConfig config = getSignConfig();
            if (config.randomizeDeviceFlag) {
                int start = jsCode.indexOf('{');
                int end = jsCode.lastIndexOf('}');
                if (start != -1 && end != -1 && start < end) {
                    String wipeMemory = "window.localStorage.clear(); window.sessionStorage.clear(); ";
                    String newJsCode = wipeMemory + jsCode.substring(0, start) + generateRandomDeviceFlag() + jsCode.substring(end + 1);
                    return newJsCode;
                }
            }
        }
        return jsCode;
    }

    private static String generateRandomDeviceFlag() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"flagInfo\":\"");
        for (int i = 0; i < 43; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        sb.append("=\"}");
        return sb.toString();
    }
}
