package com.fredoseep.chaoxinghook;

import android.util.Log;
import java.util.HashSet;
import java.util.Set;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/** WebView Hook 注册和事件分发；具体业务由各功能类实现。 */
final class WebViewHooks {
    private static final String TAG_WEBVIEW = "CX.WebView";
    private final Set<String> hookedWebViewClients = new HashSet<>();
    private final PostSignHook postSign = new PostSignHook();

    void install(LoadPackageParam lpparam) {
        try {
            Class<?> webViewClass = XposedHelpers.findClass("android.webkit.WebView", lpparam.classLoader);
            XposedBridge.hookAllMethods(webViewClass, "setWebViewClient", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object webViewObj = param.thisObject;

                    // ==========================================
                    // 【精确狙击】：强行接管 WebView 长按事件，注入极客专属下载菜单！
                    // ==========================================
                    if (webViewObj instanceof android.webkit.WebView) {
                        android.webkit.WebView view = (android.webkit.WebView) webViewObj;
                        postSign.attach(view);
                        ImageSaveHook.attach(view);
                    }
                    Object webViewClient = param.args[0];
                    if (webViewClient == null) return;
                    Class<?> clientClass = webViewClient.getClass();
                    if (!hookedWebViewClients.add(clientClass.getName())) return;

                    Class<?> targetClass = clientClass;
                    while (targetClass != null && !targetClass.getName().equals("java.lang.Object")) {
                        XposedBridge.hookAllMethods(targetClass, "shouldInterceptRequest", new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam innerParam) throws Throwable {
                                String url = null;
                                Object requestObj = null;

                                for (Object arg : innerParam.args) {
                                    if (arg instanceof String) { url = (String) arg; }
                                    else if (arg instanceof android.webkit.WebResourceRequest) {
                                        requestObj = arg;
                                        Object uriObj = XposedHelpers.callMethod(arg, "getUrl");
                                        if (uriObj != null) url = uriObj.toString();
                                    }
                                }
                                if (url == null) return;

                                ClassLoader loader = lpparam.classLoader;
                                if (ExamRiskHook.intercept(url, requestObj, innerParam, loader)) return;
                                if (VideoUnlockHook.intercept(url, requestObj, innerParam, loader)) return;
                                if (CopyRestrictionHook.intercept(url, requestObj, innerParam, loader)) return;
                                LocationSignHook.intercept(url, requestObj, innerParam, loader);
                            }
                        });
                        // ===== 页面加载完成注入：手势/位置签到自动完成 =====
                        XposedBridge.hookAllMethods(targetClass, "onPageFinished", new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam innerParam) throws Throwable {
                                try {
                                    if (innerParam.args.length < 2 || !(innerParam.args[0] instanceof android.webkit.WebView)) return;
                                    android.webkit.WebView wv = (android.webkit.WebView) innerParam.args[0];
                                    postSign.inject(wv);
                                    CopyRestrictionHook.onPageFinished(wv);
                                    AutoSignHook.onPageFinished(wv);
                                } catch (Throwable ignored) {}
                            }
                        });
                        targetClass = targetClass.getSuperclass();
                    }
                }
            });

            XC_MethodHook jsInterceptHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length > 0 && param.args[0] instanceof String) {
                        String jsCode = (String) param.args[0];

                        jsCode = ExamRiskHook.transformJavascript(jsCode);
                        param.args[0] = DeviceFlagHook.transformJavascript(jsCode);
                    }
                }
            };
            XposedBridge.hookAllMethods(webViewClass, "evaluateJavascript", jsInterceptHook);
            XposedBridge.hookAllMethods(webViewClass, "loadUrl", jsInterceptHook);

        } catch (Throwable t) {
            Log.e(TAG_WEBVIEW, "Chaoxing AdSkip Error (WebView Hook): ", t);
        }
    }
}
