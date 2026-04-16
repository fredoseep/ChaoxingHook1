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

    // ==========================================================
    // 混淆字典配置区 (宿主更新时，只需要修改这里的字符串即可)
    // ==========================================================
    public static class ObfuscationMap {
        // 1. 启动页广告拦截
        public static final String CLASS_SPLASH_VIEW_MODEL = "com.chaoxing.mobile.activity.SplashViewModel";
        public static final String METHOD_SPLASH_A = "a";

        // 2. 首页 Banner 拦截
        public static final String CLASS_HOME_PAGE_HEADER = "com.chaoxing.mobile.study.home.mainpage.view.HomePageHeader";
        public static final String METHOD_HOME_HEADER_G = "g";

        // 3. 隐藏首页推荐栏
        public static final String CLASS_CATEGORY_HOLDER = "com.chaoxing.mobile.study.home.mainpage2.adapter.viewholder.MainRecordCategoryHolder";
        public static final String METHOD_CATEGORY_HOLDER_O = "o";
        public static final String FIELD_CATEGORY_HOLDER_TV_LEFT = "c"; // 推荐栏标题 TextView 的字段名

        // 4. 清空第一页帖子
        public static final String CLASS_MAIN_PAGE_RECORD_ADAPTER = "com.chaoxing.mobile.study.home.mainpage2.adapter.MainPageRecordAdapter";
        public static final String METHOD_ADAPTER_GET_ITEM_COUNT = "getItemCount";
        public static final String FIELD_ADAPTER_LIST_A = "a";         // 列表数据的字段名 1
        public static final String FIELD_ADAPTER_LIST_F82688A = "f82688a"; // 列表数据的字段名 2 (备用)

        // 5. 最近使用记录数量提升
        public static final String CLASS_DB_QUERY = "lo.b0";
        public static final String METHOD_DB_QUERY_W = "W";

        // 6. 掐断特定对话类型
        public static final String CLASS_CHAT_MANAGER_Q1 = "com.chaoxing.mobile.chat.manager.q1";
        public static final String METHOD_CHAT_MANAGER_C1 = "c1";

        // 9. 消息防撤回 (环信 SDK)
        public static final String CLASS_EM_CMD_MESSAGE_BODY = "com.hyphenate.chat.EMCmdMessageBody";
        public static final String METHOD_EM_CMD_ACTION = "action";
    }

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
            Class<?> splashViewModelClass = XposedHelpers.findClass(ObfuscationMap.CLASS_SPLASH_VIEW_MODEL, lpparam.classLoader);
            XposedBridge.hookAllMethods(splashViewModelClass, ObfuscationMap.METHOD_SPLASH_A, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(null);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("Chaoxing AdSkip Error (Splash): " + t.getMessage());
        }

        // ==================== 2. 首页 Banner 拦截 ====================
        try {
            Class<?> homePageHeaderClass = XposedHelpers.findClass(ObfuscationMap.CLASS_HOME_PAGE_HEADER, lpparam.classLoader);
            XposedBridge.hookAllMethods(homePageHeaderClass, ObfuscationMap.METHOD_HOME_HEADER_G, new XC_MethodHook() {
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
            Class<?> categoryHolderClass = XposedHelpers.findClass(ObfuscationMap.CLASS_CATEGORY_HOLDER, lpparam.classLoader);
            XposedBridge.hookAllMethods(categoryHolderClass, ObfuscationMap.METHOD_CATEGORY_HOLDER_O, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object viewHolder = param.thisObject;
                    android.widget.TextView tvLeft = (android.widget.TextView) XposedHelpers.getObjectField(viewHolder, ObfuscationMap.FIELD_CATEGORY_HOLDER_TV_LEFT);

                    if (tvLeft != null && tvLeft.getText() != null) {
                        String title = tvLeft.getText().toString();
                        if (title.contains("推荐") || title.contains("Recommend")) {
                            android.view.View itemView = (android.view.View) XposedHelpers.getObjectField(viewHolder, "itemView"); // itemView是RecyclerView.ViewHolder自带的，无需混淆
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
            Class<?> adapterClass = XposedHelpers.findClass(ObfuscationMap.CLASS_MAIN_PAGE_RECORD_ADAPTER, lpparam.classLoader);
            XposedBridge.hookAllMethods(adapterClass, ObfuscationMap.METHOD_ADAPTER_GET_ITEM_COUNT, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        java.util.List<?> listA = (java.util.List<?>) XposedHelpers.getObjectField(param.thisObject, ObfuscationMap.FIELD_ADAPTER_LIST_A);
                        if (listA != null) param.setResult(listA.size());
                    } catch (NoSuchFieldError e) {
                        java.util.List<?> listA = (java.util.List<?>) XposedHelpers.getObjectField(param.thisObject, ObfuscationMap.FIELD_ADAPTER_LIST_F82688A);
                        if (listA != null) param.setResult(listA.size());
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("Chaoxing AdSkip Error (Adapter): " + t.getMessage());
        }

        // ==================== 5. 最近使用记录数量提升 ====================
        try {
            Class<?> dbQueryClass = XposedHelpers.findClass(ObfuscationMap.CLASS_DB_QUERY, lpparam.classLoader);
            XposedBridge.hookAllMethods(dbQueryClass, ObfuscationMap.METHOD_DB_QUERY_W, new XC_MethodHook() {
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
            Class<?> q1Class = XposedHelpers.findClass(ObfuscationMap.CLASS_CHAT_MANAGER_Q1, lpparam.classLoader);
            XposedBridge.hookAllMethods(q1Class, ObfuscationMap.METHOD_CHAT_MANAGER_C1, new XC_MethodHook() {
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

        // ==================== [新增] 9. 消息防撤回核心拦截网 ====================
        try {
            Class<?> cmdMsgBodyClass = XposedHelpers.findClass(ObfuscationMap.CLASS_EM_CMD_MESSAGE_BODY, lpparam.classLoader);
            XposedBridge.hookAllMethods(cmdMsgBodyClass, ObfuscationMap.METHOD_EM_CMD_ACTION, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object result = param.getResult();
                    if (result != null && "REVOKE_FLAG".equals(result.toString())) {
                        // 篡改返回结果，使客户端无法识别撤回指令
                        param.setResult("BLOCK_REVOKE_FLAG");
                        XposedBridge.log("Chaoxing AdSkip: 【防撤回触发】成功拦截并篡改了一条撤回指令！消息已保住。");
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("Chaoxing AdSkip Error (Anti-Revoke): " + t.getMessage());
        }

        // ==================== 7 & 8. WebView 核心请求拦截 (考试日志 + 签到定位) ====================
        try {
            // 注意：android.webkit 包属于安卓系统层，不受 App 混淆影响，无需提取配置
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

                                if (url == null) return;

                                // ==================== 8. 考试风控拦截网 (监控日志 + 切屏计数) ====================
                                if (url.startsWith("https://mooc1-api.chaoxing.com/keeper/api/receiveExamLogs") ||
                                        url.contains("/exam-ans/exam/phone/exit-count")) {

                                    XposedBridge.log("Chaoxing AdSkip: 成功触发考试风控拦截网！拦截到目标 -> " + url);
                                    try {
                                        Class<?> responseClass = XposedHelpers.findClassIfExists("android.webkit.WebResourceResponse", lpparam.classLoader);
                                        if (responseClass != null) {
                                            // 伪造一个通用的成功 JSON 响应体，骗过前端，防止网页报错
                                            String fakeResponse = "{\"status\":1,\"result\":true,\"msg\":\"success\",\"data\":null}";
                                            java.io.InputStream fakeStream = new java.io.ByteArrayInputStream(fakeResponse.getBytes("UTF-8"));

                                            // 核心截杀：直接塞回伪造的流，真实的请求将被彻底掐断在本地
                                            innerParam.setResult(XposedHelpers.newInstance(responseClass, "application/json", "utf-8", fakeStream));
                                            XposedBridge.log("Chaoxing AdSkip: 考试风控请求已扔进黑洞，并返回了伪造 Success！");
                                        }
                                    } catch (Exception e) {
                                        XposedBridge.log("Chaoxing AdSkip Error: 拦截考试风控异常 -> " + e.getMessage());
                                    }
                                    return;
                                }

                                // ==================== 7. 仅拦截签到请求 ====================
                                if (url.contains("stuSignajax")) {
                                    SignConfig config = getSignConfig();
                                    String newUrlString = url;
                                    boolean hasModified = false;

                                    if (config.modifyLocation && !config.latitude.isEmpty() && !config.longitude.isEmpty()) {
                                        if (newUrlString.contains("latitude=") && newUrlString.contains("longitude=")) {
                                            newUrlString = newUrlString.replaceAll("latitude=[^&]*", "latitude=" + config.latitude)
                                                    .replaceAll("longitude=[^&]*", "longitude=" + config.longitude);
                                            hasModified = true;
                                            XposedBridge.log("Chaoxing AdSkip: 已替换定位");
                                        }
                                    }

                                    if (config.modifyAddress && !config.address.isEmpty()) {
                                        if (newUrlString.contains("address=")) {
                                            String encodedAddress = URLEncoder.encode(config.address, "UTF-8");
                                            newUrlString = newUrlString.replaceAll("address=[^&]*", "address=" + encodedAddress);
                                            hasModified = true;
                                            XposedBridge.log("Chaoxing AdSkip: 已替换地址名");
                                        }
                                    }

                                    if (config.modifyName && !config.name.isEmpty()) {
                                        if (newUrlString.contains("name=")) {
                                            String encodedName = URLEncoder.encode(config.name, "UTF-8");
                                            newUrlString = newUrlString.replaceAll("name=[^&]*", "name=" + encodedName);
                                            hasModified = true;
                                            XposedBridge.log("Chaoxing AdSkip: 已替换名字");
                                        }
                                    }

                                    if (!hasModified) return;

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

    private SignConfig getSignConfig() {
        SignConfig config = new SignConfig();
        File file = new File("/storage/emulated/0/Android/data/com.chaoxing.mobile/files/chaoxing_loc.txt");

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

    private boolean parseBooleanValue(String line) {
        try {
            String val = line.substring(line.indexOf(":") + 1).trim();
            return "true".equalsIgnoreCase(val);
        } catch (Exception e) {
            return false;
        }
    }

    private String parseStringValue(String line) {
        try {
            return line.substring(line.indexOf(":") + 1).trim();
        } catch (Exception e) {
            return "";
        }
    }
}