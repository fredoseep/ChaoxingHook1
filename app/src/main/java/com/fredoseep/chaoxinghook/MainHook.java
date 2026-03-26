package com.fredoseep.chaoxinghook;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MainHook implements IXposedHookLoadPackage {

    private static final Set<String> hookedWebViewClients = new HashSet<>();

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.chaoxing.mobile")) {
            return;
        }

        // ==================== 1. 启动页广告拦截 ====================
        try {
            Class<?> splashViewModelClass = XposedHelpers.findClass(
                    "com.chaoxing.mobile.activity.SplashViewModel",
                    lpparam.classLoader
            );
            XposedBridge.hookAllMethods(splashViewModelClass, "a", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(null);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("Chaoxing AdSkip Error: " + t.getMessage());
        }

        // ==================== 2. 首页 Banner 拦截 ====================
        try {
            Class<?> homePageHeaderClass = XposedHelpers.findClass(
                    "com.chaoxing.mobile.study.home.mainpage.view.HomePageHeader",
                    lpparam.classLoader
            );
            XposedBridge.hookAllMethods(homePageHeaderClass, "g", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length > 0) param.args[0] = null;
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("Chaoxing AdSkip Error (HomePageHeader): " + t.getMessage());
        }

        // ==================== 3. 隐藏首页推荐栏 ====================
        try {
            Class<?> categoryHolderClass = XposedHelpers.findClass(
                    "com.chaoxing.mobile.study.home.mainpage2.adapter.viewholder.MainRecordCategoryHolder",
                    lpparam.classLoader
            );
            XposedBridge.hookAllMethods(categoryHolderClass, "o", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object viewHolder = param.thisObject;
                    android.widget.TextView tvLeft = (android.widget.TextView) XposedHelpers.getObjectField(viewHolder, "c");

                    if (tvLeft != null && tvLeft.getText() != null) {
                        String title = tvLeft.getText().toString();
                        if (title.contains("推荐") || title.contains("Recommend")) {
                            android.view.View itemView = (android.view.View) XposedHelpers.getObjectField(viewHolder, "itemView");
                            if (itemView != null) {
                                itemView.setVisibility(android.view.View.GONE);
                                android.view.ViewGroup.LayoutParams layoutParams = itemView.getLayoutParams();
                                layoutParams.height = 0;
                                layoutParams.width = 0;
                                itemView.setLayoutParams(layoutParams);
                            }
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("Chaoxing AdSkip Error (CategoryHolder): " + t.getMessage());
        }

        // ==================== 4. 清空第一页帖子 ====================
        try {
            Class<?> adapterClass = XposedHelpers.findClass(
                    "com.chaoxing.mobile.study.home.mainpage2.adapter.MainPageRecordAdapter",
                    lpparam.classLoader
            );
            XposedBridge.hookAllMethods(adapterClass, "getItemCount", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        java.util.List<?> listA = (java.util.List<?>) XposedHelpers.getObjectField(param.thisObject, "a");
                        if (listA != null) param.setResult(listA.size());
                    } catch (NoSuchFieldError e) {
                        java.util.List<?> listA = (java.util.List<?>) XposedHelpers.getObjectField(param.thisObject, "f82688a");
                        if (listA != null) param.setResult(listA.size());
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("Chaoxing AdSkip Error (Adapter): " + t.getMessage());
        }

        // ==================== 5. 最近使用记录数量提升 ====================
        try {
            Class<?> dbQueryClass = XposedHelpers.findClass("lo.b0", lpparam.classLoader);
            XposedBridge.hookAllMethods(dbQueryClass, "W", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length == 3 && param.args[2] instanceof Integer) {
                        if ((Integer) param.args[2] == 3) param.args[2] = 15;
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("Chaoxing AdSkip Error (Record Limit): " + t.getMessage());
        }

        // ==================== 6. 掐断特定对话类型 ====================
        try {
            Class<?> q1Class = XposedHelpers.findClass(
                    "com.chaoxing.mobile.chat.manager.q1",
                    lpparam.classLoader
            );
            XposedBridge.hookAllMethods(q1Class, "c1", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object result = param.getResult();
                    if (result instanceof java.util.List) {
                        java.util.List<?> list = (java.util.List<?>) result;
                        for (int i = list.size() - 1; i >= 0; i--) {
                            Object info = list.get(i);
                            if (info != null && info.getClass().getSimpleName().equals("ConversationInfo")) {
                                if ((Integer) XposedHelpers.callMethod(info, "getType") == 20) {
                                    list.remove(i);
                                }
                            }
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("Chaoxing AdSkip Error (q1.c1): " + t.getMessage());
        }

        // ==================== 7. WebView 签到定位拦截 (精简生产版) ====================
        try {
            Class<?> webViewClass = XposedHelpers.findClass("android.webkit.WebView", lpparam.classLoader);

            XposedBridge.hookAllMethods(webViewClass, "setWebViewClient", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
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
                                    if (arg instanceof String) {
                                        url = (String) arg;
                                    } else if (arg != null && arg.getClass().getName().endsWith("WebResourceRequest")) {
                                        requestObj = arg;
                                        Object uriObj = XposedHelpers.callMethod(arg, "getUrl");
                                        if (uriObj != null) url = uriObj.toString();
                                    }
                                }

                                if (url != null && url.contains("stuSignajax") && url.contains("latitude=") && url.contains("longitude=")) {
                                    String[] loc = getTargetLocation();
                                    if (loc != null && loc.length >= 2) {
                                        String lat = loc[0].trim();
                                        String lng = loc[1].trim();

                                        if ("-1".equals(lat) || "-1.0".equals(lat)) return;

                                        String newUrlString = url.replaceAll("latitude=[^&]*", "latitude=" + lat)
                                                .replaceAll("longitude=[^&]*", "longitude=" + lng);

                                        try {
                                            URL newUrl = new URL(newUrlString);
                                            HttpURLConnection conn = (HttpURLConnection) newUrl.openConnection();

                                            if (requestObj != null) {
                                                conn.setRequestMethod((String) XposedHelpers.callMethod(requestObj, "getMethod"));
                                                @SuppressWarnings("unchecked")
                                                Map<String, String> headers = (Map<String, String>) XposedHelpers.callMethod(requestObj, "getRequestHeaders");
                                                if (headers != null) {
                                                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                                                        conn.setRequestProperty(entry.getKey(), entry.getValue());
                                                    }
                                                }
                                            } else {
                                                conn.setRequestMethod("GET");
                                            }

                                            String cookie = android.webkit.CookieManager.getInstance().getCookie(newUrlString);
                                            if (cookie != null) conn.setRequestProperty("Cookie", cookie);

                                            String contentType = conn.getContentType();
                                            String mimeType = (contentType != null) ? contentType.split(";")[0].trim() : "application/json";
                                            String encoding = conn.getContentEncoding() != null ? conn.getContentEncoding() : "utf-8";

                                            Class<?> responseClass = XposedHelpers.findClassIfExists("android.webkit.WebResourceResponse", lpparam.classLoader);
                                            if (responseClass != null) {
                                                innerParam.setResult(XposedHelpers.newInstance(responseClass, mimeType, encoding, conn.getInputStream()));
                                                XposedBridge.log("Chaoxing AdSkip: 签到位置已成功修改为 -> " + lat + "," + lng);
                                            }
                                        } catch (Exception e) {
                                            XposedBridge.log("Chaoxing AdSkip Error: 代理转发失败 -> " + e.getMessage());
                                        }
                                    }
                                }
                            }
                        });
                        targetClass = targetClass.getSuperclass();
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("Chaoxing AdSkip Error (WebView): " + t.getMessage());
        }
    }

    private String[] getTargetLocation() {
        try {
            File file = new File("/storage/emulated/0/Android/data/com.chaoxing.mobile/files/chaoxing_loc.txt");
            if (!file.exists()) return null;
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line = br.readLine();
                if (line != null && line.contains(",")) return line.split(",");
            }
        } catch (Exception ignored) {}
        return null;
    }
}