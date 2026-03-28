package com.fredoseep.chaoxinghook;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
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

    // 定义一个内部类来存储配置信息
    private static class SignConfig {
        boolean modifyLocation = false;
        String longitude = "";
        String latitude = "";
        boolean modifyAddress = false;
        String address = "";
        boolean modifyName = false;
        String name = "";
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.chaoxing.mobile")) {
            return;
        }

        // ==================== 1. 启动页广告拦截 ====================
        try {
            Class<?> splashViewModelClass = XposedHelpers.findClass("com.chaoxing.mobile.activity.SplashViewModel", lpparam.classLoader);
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
            Class<?> homePageHeaderClass = XposedHelpers.findClass("com.chaoxing.mobile.study.home.mainpage.view.HomePageHeader", lpparam.classLoader);
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
            Class<?> categoryHolderClass = XposedHelpers.findClass("com.chaoxing.mobile.study.home.mainpage2.adapter.viewholder.MainRecordCategoryHolder", lpparam.classLoader);
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
            Class<?> adapterClass = XposedHelpers.findClass("com.chaoxing.mobile.study.home.mainpage2.adapter.MainPageRecordAdapter", lpparam.classLoader);
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
            Class<?> q1Class = XposedHelpers.findClass("com.chaoxing.mobile.chat.manager.q1", lpparam.classLoader);
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

        // ==================== 7. WebView 签到定位拦截 (自定义参数版) ====================
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

                                // 仅拦截签到请求
                                if (url != null && url.contains("stuSignajax")) {
                                    SignConfig config = getSignConfig();
                                    String newUrlString = url;
                                    boolean hasModified = false;

                                    // 1. 修改定位 (纬度和经度)
                                    if (config.modifyLocation && !config.latitude.isEmpty() && !config.longitude.isEmpty()) {
                                        if (newUrlString.contains("latitude=") && newUrlString.contains("longitude=")) {
                                            newUrlString = newUrlString.replaceAll("latitude=[^&]*", "latitude=" + config.latitude)
                                                    .replaceAll("longitude=[^&]*", "longitude=" + config.longitude);
                                            hasModified = true;
                                            XposedBridge.log("Chaoxing AdSkip: 已替换定位");
                                        }
                                    }

                                    // 2. 修改地址名 (自动 URL 编码)
                                    if (config.modifyAddress && !config.address.isEmpty()) {
                                        if (newUrlString.contains("address=")) {
                                            String encodedAddress = URLEncoder.encode(config.address, "UTF-8");
                                            newUrlString = newUrlString.replaceAll("address=[^&]*", "address=" + encodedAddress);
                                            hasModified = true;
                                            XposedBridge.log("Chaoxing AdSkip: 已替换地址名");
                                        }
                                    }

                                    // 3. 修改名字 (自动 URL 编码，可以直接填入注入代码)
                                    if (config.modifyName && !config.name.isEmpty()) {
                                        if (newUrlString.contains("name=")) {
                                            String encodedName = URLEncoder.encode(config.name, "UTF-8");
                                            newUrlString = newUrlString.replaceAll("name=[^&]*", "name=" + encodedName);
                                            hasModified = true;
                                            XposedBridge.log("Chaoxing AdSkip: 已替换名字");
                                        }
                                    }

                                    // 如果配置都是 false，或者没有成功替换任何参数，则直接放行原请求
                                    if (!hasModified) return;

                                    // 执行拦截转发
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
                                            XposedBridge.log("Chaoxing AdSkip: 签到请求代理转发成功！");
                                        }
                                    } catch (Exception e) {
                                        XposedBridge.log("Chaoxing AdSkip Error: 代理转发失败 -> " + e.getMessage());
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

    // 解析和自动生成配置文件的核心方法
    private SignConfig getSignConfig() {
        SignConfig config = new SignConfig();
        File file = new File("/storage/emulated/0/Android/data/com.chaoxing.mobile/files/chaoxing_loc.txt");

        // 如果文件不存在，自动创建并写入默认模板
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                FileWriter fw = new FileWriter(file);
                fw.write("是否开启定位修改: false\n");
                fw.write("经度: \n");
                fw.write("纬度: \n");
                fw.write("是否开启地址名修改: false\n");
                fw.write("地址名: \n");
                fw.write("是否开启名字修改: false\n");
                fw.write("名字: \n");
                fw.close();
                XposedBridge.log("Chaoxing AdSkip: 已自动创建默认配置文件");
            } catch (Exception e) {
                XposedBridge.log("Chaoxing AdSkip Error: 无法创建配置文件 -> " + e.getMessage());
            }
            return config;
        }

        // 逐行解析配置
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("是否开启定位修改:")) {
                    config.modifyLocation = parseBooleanValue(line);
                } else if (line.startsWith("经度:")) {
                    config.longitude = parseStringValue(line);
                } else if (line.startsWith("纬度:")) {
                    config.latitude = parseStringValue(line);
                } else if (line.startsWith("是否开启地址名修改:")) {
                    config.modifyAddress = parseBooleanValue(line);
                } else if (line.startsWith("地址名:")) {
                    config.address = parseStringValue(line);
                } else if (line.startsWith("是否开启名字修改:")) {
                    config.modifyName = parseBooleanValue(line);
                } else if (line.startsWith("名字:")) {
                    config.name = parseStringValue(line);
                }
            }
        } catch (Exception e) {
            XposedBridge.log("Chaoxing AdSkip Error: 读取配置文件失败 -> " + e.getMessage());
        }
        return config;
    }

    // 提取冒号后的布尔值
    private boolean parseBooleanValue(String line) {
        try {
            String val = line.substring(line.indexOf(":") + 1).trim();
            return "true".equalsIgnoreCase(val);
        } catch (Exception e) {
            return false;
        }
    }

    // 提取冒号后的字符串值
    private String parseStringValue(String line) {
        try {
            return line.substring(line.indexOf(":") + 1).trim();
        } catch (Exception e) {
            return "";
        }
    }
}