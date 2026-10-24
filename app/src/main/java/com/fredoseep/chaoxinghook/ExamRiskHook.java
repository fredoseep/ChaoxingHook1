package com.fredoseep.chaoxinghook;

import android.util.Log;
import java.io.ByteArrayInputStream;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import static com.fredoseep.chaoxinghook.HookConfigRepository.getSignConfig;

/** 进程退出、考试上报请求及 H5 生命周期处理。 */
final class ExamRiskHook {
    private static final String TAG_EXAM_RISK = "CX.ExamRisk";
    static void installProcessExitHook() {
        XC_MethodHook exitHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!getSignConfig().bypassExamCheat) return;

                int status = 0;
                if (param.args.length > 0 && param.args[0] instanceof Integer) {
                    status = (Integer) param.args[0];
                }
                param.setResult(null);
                Log.d(TAG_EXAM_RISK, "process-exit blocked: " + param.method.getDeclaringClass().getName()
                        + "." + param.method.getName() + "(" + status + ")");
            }
        };

        XposedHelpers.findAndHookMethod(System.class, "exit", int.class, exitHook);
        XposedHelpers.findAndHookMethod(Runtime.class, "exit", int.class, exitHook);
    }

    static boolean intercept(String url, Object requestObj, XC_MethodHook.MethodHookParam innerParam, ClassLoader loader) throws Throwable {
        if (getSignConfig().bypassExamCheat && (url.startsWith("https://mooc1-api.chaoxing.com/keeper/api/receiveExamLogs") || url.contains("/exam-ans/exam/phone/exit-count")||url.contains("https://data-xxt.aichoxing.com/analysis/ac_event")||url.contains("pan-yz.chaoxing.com/upload"))) {
            try {
                Class<?> responseClass = XposedHelpers.findClassIfExists("android.webkit.WebResourceResponse", loader);
                if (responseClass != null) {
                    String fakeResponse = "{\"status\":1,\"result\":true,\"msg\":\"success\",\"data\":null}";
                    innerParam.setResult(XposedHelpers.newInstance(
                            responseClass,
                            "application/json",
                            "utf-8",
                            new ByteArrayInputStream(fakeResponse.getBytes("UTF-8"))
                    ));
                }
            } catch (Exception e) {}
            return true;
        }
        return false;
    }

    static String transformJavascript(String jsCode) {
        if (jsCode.contains("CLIENT_WEB_LIFECYCLE") && getSignConfig().bypassExamCheat) {
            // 正则需与 cxanalysis 验证版完全一致：\\\\? = 可选反斜杠（兼容 JSON 转义），
            // 漏一层转义会变成"字面问号"导致永远匹配不上
            String replaced = jsCode.replaceAll("\\\\?\"status\\\\?\"\\s*:\\s*\\d+", "\"status\":11")
                    .replaceAll("'status'\\s*:\\s*\\d+", "'status':11");
            if (!replaced.equals(jsCode)) {
                jsCode = replaced;
                Log.d(TAG_EXAM_RISK, "Chaoxing [防切屏]: CLIENT_WEB_LIFECYCLE status 已伪装为前台(11)");
            }
        }
        return jsCode;
    }
}
