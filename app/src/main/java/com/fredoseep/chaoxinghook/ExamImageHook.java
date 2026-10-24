package com.fredoseep.chaoxinghook;

import android.util.Log;
import java.io.File;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import static com.fredoseep.chaoxinghook.HookConfigRepository.getSignConfig;
import static com.fredoseep.chaoxinghook.HookConfigRepository.FAKE_UPLOAD_FILE_PATH;

/** 考试截图文件替换和旧版截图协议 Hook。 */
final class ExamImageHook {
    private static final String TAG_EXAM_IMAGE = "CX.ExamImage";
    static void installFileReplaceHook(LoadPackageParam lpparam) {
        try {
            XC_MethodHook fileReadHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length == 0 || param.args[0] == null) return;

                    String path = "";
                    if (param.args[0] instanceof java.io.File) {
                        path = ((java.io.File) param.args[0]).getAbsolutePath();
                    } else if (param.args[0] instanceof String) {
                        path = (String) param.args[0];
                    }
                    if (path.isEmpty()) return;

                    // 核心修复：前置过滤。如果不是目标文件，绝对不调用 getSignConfig()，从根源斩断递归
                    if (!path.contains("/Android/data/com.chaoxing.mobile/cache/image/") ||
                            (!path.endsWith(".png") && !path.endsWith(".jpg"))) {
                        return;
                    }

                    SignConfig config = getSignConfig();
                    if (!config.replaceExamScreenshot) return;

                    String imagePath = config.fakeImagePath.isEmpty() ? FAKE_UPLOAD_FILE_PATH : config.fakeImagePath;
                    File fakeFile = new File(imagePath);
                    if (fakeFile.exists()) {
                        if (param.args[0] instanceof java.io.File) {
                            param.args[0] = fakeFile;
                        } else {
                            param.args[0] = imagePath;
                        }
                        Log.i(TAG_EXAM_IMAGE, "Chaoxing [绝杀]: 抓到监考截图上传！已成功替换为自定义图片 -> 拦截原图: " + path);
                    } else {
                        Log.e(TAG_EXAM_IMAGE, "Chaoxing [警告]: 找不到自定义伪装图片，未执行替换！请检查路径: " + imagePath);
                    }
                }
            };

            XposedBridge.hookAllConstructors(java.io.FileInputStream.class, fileReadHook);

        } catch (Throwable t) {
            Log.e(TAG_EXAM_IMAGE, "Chaoxing Error (File Replace Hook): ", t);
        }
    }

    static void installExamSnapshotHook(LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.chaoxing.mobile.webapp.jsprotocal.common.f1",
                    lpparam.classLoader,
                    "q0",
                    File.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Log.i(TAG_EXAM_IMAGE, "Chaoxing [终极斩杀]: 成功拦截 f1.q0，ExamKeeper 截图上传已被物理蒸发！");
                            Object f1Instance = param.thisObject;
                            String fakeSuccessJson = (String) XposedHelpers.callMethod(f1Instance, "f0", 1, null);
                            XposedHelpers.callMethod(f1Instance, "r", "CLIENT_SNAPSHOT", fakeSuccessJson);
                            param.setResult(null);
                        }
                    }
            );
        } catch (Throwable t) {
            // 6.7.8 起 f1.q0 已不存在：上传拦截由 URL 层（pan-yz.chaoxing.com/upload）承担
        }
    }
}
