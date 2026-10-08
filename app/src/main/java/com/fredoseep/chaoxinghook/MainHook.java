package com.fredoseep.chaoxinghook;

import android.app.AndroidAppHelper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.matchers.MethodsMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.ClassDataList;

public class MainHook implements IXposedHookLoadPackage {

    // 诊断日志（写学习通私有目录，logcat 不可靠时用）：/data/user/0/com.chaoxing.mobile/files/chaoxinghook_debug.log
    private static final String DEBUG_LOG_FILE = "/data/user/0/com.chaoxing.mobile/files/chaoxinghook_debug.log";

    private static void debugLog(String msg) {
        try { android.util.Log.i("ChaoxingHookDiag", msg); } catch (Throwable ignored) {}
        try {
            java.io.FileWriter fw = new java.io.FileWriter(DEBUG_LOG_FILE, true);
            fw.write(msg + "\n");
            fw.close();
        } catch (Throwable ignored) {}
    }

    private static final java.util.concurrent.atomic.AtomicBoolean applicationHooksStarted =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private static final Set<String> hookedWebViewClients = new HashSet<>();
    private static final String FAKE_UPLOAD_FILE_PATH = "/storage/emulated/0/Download/fake_exam_image.png";
    private static final java.util.WeakHashMap<android.view.View, Boolean> injectedLongPressRows = new java.util.WeakHashMap<>();

    // 核心修复：添加防重入标志，防止读取配置文件时触发无限递归死循环
    private static final ThreadLocal<Boolean> READING_CONFIG = ThreadLocal.withInitial(() -> false);

    // ==================== DexKit 反混淆定位辅助 ====================
    // 学习通使用 R8/梆梆加固混淆，类名/方法名在版本更新后可能变化。
    // 策略：优先用 DexKit 按"类名(可空)+方法签名"结构匹配（不依赖方法名），
    // 找不到再回退到旧的硬编码类名，最大化 hook 在版本更新后的存活率。

    /** 按 类名(可空)+方法签名 查找类（默认限 com.chaoxing.mobile 包）；返回 null 表示未找到（调用方自行回退） */
    private static Class<?> findClassByMethods(DexKitBridge bridge, ClassLoader loader,
                                               String tag, String className, String returnType, String... paramTypes) {
        return findClassByMethodsImpl(bridge, loader, tag, className, false, returnType, paramTypes);
    }

    /** 全包搜索版：顶级混淆包（如 y6e）不在 com.chaoxing.mobile 下，必须放开包名限制 */
    private static Class<?> findClassByMethodsEverywhere(DexKitBridge bridge, ClassLoader loader,
                                                         String tag, String className, String returnType, String... paramTypes) {
        return findClassByMethodsImpl(bridge, loader, tag, className, true, returnType, paramTypes);
    }

    private static Class<?> findClassByMethodsImpl(DexKitBridge bridge, ClassLoader loader,
                                                   String tag, String className, boolean searchEverywhere, String returnType, String... paramTypes) {
        try {
            MethodMatcher mm = MethodMatcher.create();
            if (returnType != null) mm = mm.returnType(returnType);
            if (paramTypes.length > 0) mm = mm.paramTypes(paramTypes);
            ClassMatcher cm = ClassMatcher.create()
                    .methods(MethodsMatcher.create().methods(List.of(mm)));
            if (className != null) cm = cm.className(className);
            FindClass fc = FindClass.create();
            if (!searchEverywhere) fc = fc.searchPackages("com.chaoxing.mobile");
            fc = fc.matcher(cm);
            ClassDataList list = bridge.findClass(fc);
            ClassData cd = list.singleOrThrow(() -> new IllegalStateException(tag + " multiple matches"));
            Class<?> clazz = cd.getInstance(loader);
            XposedBridge.log("Chaoxing DexKit[" + tag + "]: " + clazz.getName());
            return clazz;
        } catch (Throwable t) {
            debugLog("DexKit[" + tag + "] 结构匹配失败: " + t.getMessage());
        }
        if (className != null) {
            try {
                Class<?> clazz = XposedHelpers.findClassIfExists(className, loader);
                if (clazz != null) {
                    XposedBridge.log("Chaoxing DexKit[" + tag + "]: fallback class " + clazz.getName());
                    return clazz;
                }
            } catch (Throwable ignored) {}
            debugLog("DexKit[" + tag + "] 回退类不存在: " + className);
        }
        return null;
    }

    /** 反射：按 返回类型(可空)+参数类型 找方法（参数类型用原始类型名，混淆不影响）
     *  注意：同签名可能有多个方法（如 zo.b0 的 I/W），需全部 hook */
    private static Method findMethodBySignature(Class<?> clazz, String returnType, String... paramTypes) {
        List<Method> all = findMethodsBySignature(clazz, returnType, paramTypes);
        return all.isEmpty() ? null : all.get(0);
    }

    private static List<Method> findMethodsBySignature(Class<?> clazz, String returnType, String... paramTypes) {
        List<Method> result = new ArrayList<>();
        if (clazz == null) return result;
        try {
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.isBridge() || m.isSynthetic()) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != paramTypes.length) continue;
                boolean ok = true;
                for (int i = 0; i < pts.length; i++) {
                    if (!pts[i].getName().equals(paramTypes[i])) { ok = false; break; }
                }
                if (!ok) continue;
                if (returnType != null && !m.getReturnType().getName().equals(returnType)) continue;
                result.add(m);
            }
        } catch (Throwable ignored) {}
        return result;
    }

    /** 安全 hook：try-catch 包裹，失败静默并记日志 */
    private static void hookMethodSafe(Method method, XC_MethodHook callback, String tag) {
        if (method == null) {
            debugLog("Hook[" + tag + "]: 方法未找到（类名或签名已变化）");
            return;
        }
        try {
            XposedBridge.hookMethod(method, callback);
            XposedBridge.log("Chaoxing DexKit[" + tag + "]: hooked " + method.toGenericString());
        } catch (Throwable t) {
            XposedBridge.log("Chaoxing DexKit[" + tag + "]: hook failed " + t);
        }
    }

    static class LocationPoint {
        double lat;
        double lon;
        double distance;
        LocationPoint(double lat, double lon, double distance) {
            this.lat = lat; this.lon = lon; this.distance = distance;
        }
    }
    private static final List<LocationPoint> historyPoints = new ArrayList<>();
    private static double[] calculatedTarget = null;

    public static class ObfuscationMap {
        // 以下均为"当前版本已知名"，仅作 DexKit 结构匹配失败时的回退；方法名不做依赖
        public static final String CLASS_SPLASH_VIEW_MODEL = "com.chaoxing.mobile.activity.SplashViewModel";
        public static final String METHOD_SPLASH_A = "a";
        public static final String CLASS_HOME_PAGE_HEADER = "com.chaoxing.mobile.study.home.mainpage.view.HomePageHeader";
        public static final String METHOD_HOME_HEADER_G = "g";
        public static final String CLASS_CATEGORY_HOLDER = "com.chaoxing.mobile.study.home.mainpage2.adapter.viewholder.MainRecordCategoryHolder";
        public static final String METHOD_CATEGORY_HOLDER_O = "o";
        public static final String FIELD_CATEGORY_HOLDER_TV_LEFT = "c";
        public static final String CLASS_MAIN_PAGE_RECORD_ADAPTER = "com.chaoxing.mobile.study.home.mainpage2.adapter.MainPageRecordAdapter";
        public static final String METHOD_ADAPTER_GET_ITEM_COUNT = "getItemCount";
        public static final String FIELD_ADAPTER_LIST_A = "a";
        public static final String FIELD_ADAPTER_LIST_F82688A = "f82688a";
        public static final String CLASS_DB_QUERY = "y6e"; // 7.0.1（6.7.8 为 zo.b0，顶级混淆包，勿加包前缀）
        public static final String METHOD_DB_QUERY_W = "W";
        public static final String CLASS_CHAT_MANAGER = "com.chaoxing.mobile.chat.manager.b"; // 7.0.1（6.7.8 为 chat.manager.q1）
        public static final String CLASS_EM_CMD_MESSAGE_BODY = "com.hyphenate.chat.EMCmdMessageBody";
        public static final String METHOD_EM_CMD_ACTION = "action";
    }

    private static class SignConfig {
        boolean modifyLocation = false;
        String longitude = "";
        String latitude = "";
        boolean modifyAddress = false;
        String address = "";
        boolean modifyName = false;
        String name = "";
        boolean randomizeDeviceFlag = false;
        boolean autoCalculateLocation = false;
        boolean autoGestureSign = false;
        boolean autoCodeSign = false;
        boolean bypassExamCheat = true;
        boolean enableCopyRestriction = true;
        boolean replaceExamScreenshot = false;
        String fakeImagePath = "";
    }

    private static SignConfig cachedConfig = null;
    private static long lastReadTime = 0;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.chaoxing.mobile")) return;
        debugLog("=== handleLoadPackage enter: " + System.currentTimeMillis());
        try {
            System.loadLibrary("dexkit");
            debugLog("loadLibrary dexkit OK");
        } catch (Throwable t) {
            debugLog("loadLibrary dexkit FAILED: " + t);
        }

        try {
            installProcessExitHook();
            debugLog("installProcessExitHook OK");
        } catch (Throwable t) {
            debugLog("installProcessExitHook FAILED: " + t);
        }

        try {
            installApplicationReadyHook();
        } catch (Throwable t) {
            debugLog("Application.attach observer FAILED: " + t);
        }
        try {
            installWebViewHooks(lpparam);
            debugLog("installWebViewHooks OK");
        } catch (Throwable t) {
            debugLog("installWebViewHooks FAILED: " + t);
        }
        try {
            installFileReplaceHook(lpparam);
            debugLog("installFileReplaceHook OK");
        } catch (Throwable t) {
            debugLog("installFileReplaceHook FAILED: " + t);
        }
        try {
            installExamSnapshotHook(lpparam);
            debugLog("installExamSnapshotHook OK");
        } catch (Throwable t) {
            debugLog("installExamSnapshotHook FAILED: " + t);
        }
        debugLog("=== handleLoadPackage done; business hooks deferred");
    }

    /** Register before attach; if attach already finished, initialize from its real Context. */
    private void installApplicationReadyHook() {
        XposedHelpers.findAndHookMethod(android.app.Application.class, "attach",
                android.content.Context.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable()) return;
                        if (param.args.length > 0 && param.args[0] instanceof android.content.Context) {
                            initializeApplicationHooks((android.content.Context) param.args[0]);
                        }
                    }
                });
        debugLog("Application.attach observer registered");
        android.app.Application current = AndroidAppHelper.currentApplication();
        if (current != null && current.getBaseContext() != null) {
            initializeApplicationHooks(current.getBaseContext());
        }
    }

    private void initializeApplicationHooks(android.content.Context context) {
        if (!"com.chaoxing.mobile".equals(context.getPackageName())) return;
        ClassLoader loader = context.getClassLoader();
        if (loader == null) {
            debugLog("Application.attach ready: ClassLoader unavailable");
            return;
        }
        if (!applicationHooksStarted.compareAndSet(false, true)) return;
        debugLog("[INIT-FIX-1] Application.attach ready: context=" + context.getClass().getName()
                + " loader=" + loader);
        try (DexKitBridge bridge = DexKitBridge.create(loader, true)) {
            debugLog("DexKitBridge.create OK (application loader)");
            installCoreHooks(bridge, loader);
            debugLog("installCoreHooks completed; inspect individual Hook logs");
        } catch (Throwable t) {
            debugLog("application DexKit/core initialization FAILED: " + t);
        }
        try {
            installLongPressModuleEntry(loader);
            debugLog("installLongPressModuleEntry OK (application loader)");
        } catch (Throwable t) {
            debugLog("installLongPressModuleEntry FAILED: " + t);
        }
        try {
            ClipboardPopKiller.hook(loader);
        } catch (Throwable t) {
            debugLog("ClipboardPopKiller FAILED: " + t);
        }
    }

    /** 核心 hook 区：全部走 DexKit 结构匹配 + 硬编码名回退 */
    /**
     * Block process termination triggered by the target app's risk checks.
     * Both common Java exit paths are controlled by the existing exam-risk switch.
     */
    private void installProcessExitHook() {
        XC_MethodHook exitHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!getSignConfig().bypassExamCheat) return;

                int status = 0;
                if (param.args.length > 0 && param.args[0] instanceof Integer) {
                    status = (Integer) param.args[0];
                }
                param.setResult(null);
                XposedBridge.log("Chaoxing [exam-risk]: blocked process exit, status=" + status);
                debugLog("process-exit blocked: " + param.method.getDeclaringClass().getName()
                        + "." + param.method.getName() + "(" + status + ")");
            }
        };

        XposedHelpers.findAndHookMethod(System.class, "exit", int.class, exitHook);
        XposedHelpers.findAndHookMethod(Runtime.class, "exit", int.class, exitHook);
    }

    private void installCoreHooks(DexKitBridge bridge, ClassLoader loader) {

        // 1. SplashViewModel.a(Activity) -> Ad：拦截开屏广告数据（返回 null 使广告不展示）
        try {
            Class<?> clazz = findClassByMethods(bridge, loader, "splash",
                    ObfuscationMap.CLASS_SPLASH_VIEW_MODEL, null, "android.app.Activity");
            Method m = findMethodBySignature(clazz, null, "android.app.Activity");
            hookMethodSafe(m, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) { param.setResult(null); }
            }, "splash.a");
        } catch (Throwable t) { debugLog("core-group-1 FAILED: " + t); }

        // 2. HomePageHeader.g(List) -> V：清空首页头部广告数据
        try {
            Class<?> clazz = findClassByMethods(bridge, loader, "home-header",
                    ObfuscationMap.CLASS_HOME_PAGE_HEADER, "void", "java.util.List");
            Method m = findMethodBySignature(clazz, "void", "java.util.List");
            hookMethodSafe(m, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length > 0) param.args[0] = null;
                }
            }, "home-header.g");
        } catch (Throwable t) { debugLog("core-group-2 FAILED: " + t); }

        // 3. MainRecordCategoryHolder.o(ResourceLog) -> V：隐藏"推荐"分类卡片
        try {
            Class<?> clazz = findClassByMethods(bridge, loader, "category-holder",
                    ObfuscationMap.CLASS_CATEGORY_HOLDER, "void", "com.chaoxing.mobile.resource.ui.ResourceLog");
            Method m = findMethodBySignature(clazz, "void", "com.chaoxing.mobile.resource.ui.ResourceLog");
            hookMethodSafe(m, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object viewHolder = param.thisObject;
                        android.widget.TextView tvLeft = findTextViewField(viewHolder, ObfuscationMap.FIELD_CATEGORY_HOLDER_TV_LEFT);
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
                    } catch (Throwable ignored) {}
                }
            }, "category-holder.o");
        } catch (Throwable t) { debugLog("core-group-3 FAILED: " + t); }

        // 4. MainPageRecordAdapter.getItemCount()：主页记录列表只显示真正的记录（去掉推荐位）
        try {
            Class<?> clazz = findClassByMethods(bridge, loader, "record-adapter",
                    ObfuscationMap.CLASS_MAIN_PAGE_RECORD_ADAPTER, "int", (String[]) new String[0]);
            Method m = findMethodBySignature(clazz, "int", new String[0]);
            hookMethodSafe(m, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        java.util.List<?> listA = findListField(param.thisObject, new String[]{
                                ObfuscationMap.FIELD_ADAPTER_LIST_A, ObfuscationMap.FIELD_ADAPTER_LIST_F82688A});
                        if (listA != null) param.setResult(listA.size());
                    } catch (Throwable ignored) {}
                }
            }, "record-adapter.getItemCount");
        } catch (Throwable t) { debugLog("core-group-4 FAILED: " + t); }

        // 5. y6e.W/I(Context, int, int) -> LiveData：数据库查询分页大小 3 -> 15
        //    注意：该类在顶级混淆包（7.0.1=y6e，6.7.8=zo.b0），必须用全包搜索的结构匹配
        try {
            Class<?> clazz = findClassByMethodsEverywhere(bridge, loader, "db-query",
                    null, "androidx.lifecycle.LiveData", "android.content.Context", "int", "int");
            if (clazz == null) {
                // 类名也变化时回退旧名
                clazz = findClassByMethods(bridge, loader, "db-query",
                        ObfuscationMap.CLASS_DB_QUERY, "androidx.lifecycle.LiveData", "android.content.Context", "int", "int");
            }
            // 注意：zo.b0 的 I/W 方法签名相同 ((Context,int,int)->LiveData)，必须 hook 全部匹配方法
            List<Method> methods = findMethodsBySignature(clazz, "androidx.lifecycle.LiveData",
                    "android.content.Context", "int", "int");
            debugLog("db-query: clazz=" + (clazz != null ? clazz.getName() : "null")
                    + " matchedMethods=" + methods.size());
            for (Method m : methods) {
                hookMethodSafe(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (param.args.length == 3 && param.args[2] instanceof Integer) {
                            int pageSize = (Integer) param.args[2];
                            if (pageSize == 3) {
                                param.args[2] = 15;
                                debugLog("db-query." + param.method.getName() + ": pageSize 3 -> 15");
                            }
                        }
                    }
                }, "db-query." + m.getName());
            }
        } catch (Throwable t) { debugLog("core-group-5 FAILED: " + t); }

        // 6. 聊天列表过滤：移除 type==20 的官方推送会话
        //    7.0.1：chat.manager.b 的 N2(List,boolean)->void 与 r0(List,boolean)->int
        //    （6.7.8 为 q1.c1()，已消失）；两方法首参均为主界面会话列表，前置过滤即可
        try {
            Class<?> clazz = findClassByMethods(bridge, loader, "chat-filter",
                    ObfuscationMap.CLASS_CHAT_MANAGER, "void", "java.util.List", "boolean");
            if (clazz == null) {
                try { clazz = XposedHelpers.findClassIfExists(ObfuscationMap.CLASS_CHAT_MANAGER, loader); } catch (Throwable ignored) {}
            }
            if (clazz != null) {
                XC_MethodHook filterHook = new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (param.args.length > 0 && (param.args[0] instanceof java.util.List)) {
                                int removed = 0;
                                java.util.List<?> list = (java.util.List<?>) param.args[0];
                                for (int i = list.size() - 1; i >= 0; i--) {
                                    Object info = list.get(i);
                                    if (info != null
                                            && "com.chaoxing.mobile.chat.ConversationInfo".equals(info.getClass().getName())
                                            && (Integer) XposedHelpers.callMethod(info, "getType") == 20) {
                                        list.remove(i);
                                        removed++;
                                    }
                                }
                                if (removed > 0) debugLog("chat-filter: 移除 " + removed + " 个 type==20 会话");
                            }
                        } catch (Throwable ignored) {}
                    }
                };
                // 同签名方法可能不止一个，全部 hook（过滤器按元素类型自判，误挂无副作用）
                for (Method m : findMethodsBySignature(clazz, "void", "java.util.List", "boolean")) {
                    hookMethodSafe(m, filterHook, "chat-filter.voidListBool");
                }
                for (Method m : findMethodsBySignature(clazz, "int", "java.util.List", "boolean")) {
                    hookMethodSafe(m, filterHook, "chat-filter.intListBool");
                }
            } else {
                debugLog("Hook[chat-filter]: manager 类未找到");
            }
        } catch (Throwable t) { debugLog("core-group-6 FAILED: " + t); }

        // 7. EMCmdMessageBody.action()：环信 SDK 库类（第三方库不混淆，保持硬编码）
        try {
            XposedBridge.hookAllMethods(XposedHelpers.findClass(ObfuscationMap.CLASS_EM_CMD_MESSAGE_BODY, loader), ObfuscationMap.METHOD_EM_CMD_ACTION, new XC_MethodHook() { @Override protected void afterHookedMethod(MethodHookParam param) { Object result = param.getResult(); if (result != null && "REVOKE_FLAG".equals(result.toString())) { param.setResult("BLOCK_REVOKE_FLAG"); } } });
        } catch (Throwable t) {}
    }

    /** 从 ViewHolder 中找 TextView 字段：优先指定名，字段名混淆后改找任意 TextView 类型字段 */
    private static android.widget.TextView findTextViewField(Object obj, String preferredName) {
        try { return (android.widget.TextView) XposedHelpers.getObjectField(obj, preferredName); }
        catch (Throwable t) {
            try {
                for (java.lang.reflect.Field f : obj.getClass().getDeclaredFields()) {
                    if (f.getType() == android.widget.TextView.class) {
                        f.setAccessible(true);
                        return (android.widget.TextView) f.get(obj);
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /** 从 RecyclerView.Adapter 中找 List 字段：依次尝试指定名，再找任意 List 类型字段 */
    private static java.util.List<?> findListField(Object obj, String[] preferredNames) {
        for (String name : preferredNames) {
            try {
                Object v = XposedHelpers.getObjectField(obj, name);
                if (v instanceof java.util.List) return (java.util.List<?>) v;
            } catch (Throwable ignored) {}
        }
        try {
            for (java.lang.reflect.Field f : obj.getClass().getDeclaredFields()) {
                if (java.util.List.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v instanceof java.util.List) return (java.util.List<?>) v;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }


    /** WebView 层 hook：图片长按下载、H5 上报参数篡改、防切屏、复制限制解除、定位签到、网课解锁等 */
    private void installWebViewHooks(LoadPackageParam lpparam) {
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
                        final android.webkit.WebView webView = (android.webkit.WebView) webViewObj;
                        attachPostSignBridge(webView);

                        // 覆盖可能存在的原有长按逻辑
                        webView.setOnLongClickListener(new android.view.View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(android.view.View v) {
                                // 探针：获取手指按压位置的内容类型
                                final android.webkit.WebView.HitTestResult result = webView.getHitTestResult();
                                if (result != null) {
                                    int type = result.getType();

                                    // 如果长按的是 纯图片 或 带有链接的图片
                                    if (type == android.webkit.WebView.HitTestResult.IMAGE_TYPE ||
                                            type == android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {

                                        final String imageUrl = result.getExtra();
                                        if (imageUrl != null && imageUrl.startsWith("http")) {

                                            // 呼叫 Android 原生系统弹窗
                                            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(webView.getContext());
                                            builder.setTitle("chaoxingHook: ");
                                            builder.setMessage("锁定目标图片，是否执行下载？\n\n" + imageUrl);
                                            builder.setPositiveButton("立即下载", new android.content.DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(android.content.DialogInterface dialog, int which) {
                                                    // 用户点击下载，开启后台线程拉取文件
                                                    new Thread(() -> {
                                                        try {
                                                            File dir = new File("/storage/emulated/0/Download/ChaoxingExam");
                                                            if (!dir.exists()) dir.mkdirs();

                                                            String fileName = "IMG_" + System.currentTimeMillis() + ".png";
                                                            String[] parts = imageUrl.split("/");
                                                            if (parts.length > 5) {
                                                                fileName = parts[parts.length - 2] + "_" + parts[parts.length - 1];
                                                                if (fileName.contains("?")) fileName = fileName.substring(0, fileName.indexOf("?"));
                                                            }

                                                            File saveFile = new File(dir, fileName);

                                                            URL targetUrl = new URL(imageUrl);
                                                            HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();
                                                            conn.setRequestMethod("GET");
                                                            InputStream is = conn.getInputStream();
                                                            java.io.FileOutputStream fos = new java.io.FileOutputStream(saveFile);
                                                            byte[] buf = new byte[4096];
                                                            int len;
                                                            while ((len = is.read(buf)) != -1) fos.write(buf, 0, len);
                                                            fos.close();
                                                            is.close();

                                                            // 可选：在这里可以用 Looper 抛出一个 Toast 提示下载完成
                                                            XposedBridge.log("Chaoxing [精准打击]: 图片已成功降维至本地 -> " + saveFile.getAbsolutePath());
                                                        } catch (Exception e) {
                                                            XposedBridge.log("Chaoxing Error: " + e.getMessage());
                                                        }
                                                    }).start();
                                                }
                                            });
                                            builder.setNegativeButton("取消", null);
                                            builder.show(); // 弹出对话框

                                            return true; // 返回 true 代表我们消费了这个长按动作，拦截原本的事件传递
                                        }
                                    }
                                }
                                return false; // 如果长按的不是图片，原样放行，不影响网页滑动或系统功能
                            }
                        });
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

                                if (getSignConfig().bypassExamCheat && (url.startsWith("https://mooc1-api.chaoxing.com/keeper/api/receiveExamLogs") || url.contains("/exam-ans/exam/phone/exit-count")||url.contains("https://data-xxt.aichoxing.com/analysis/ac_event")||url.contains("pan-yz.chaoxing.com/upload"))) {
                                    try {
                                        Class<?> responseClass = XposedHelpers.findClassIfExists("android.webkit.WebResourceResponse", lpparam.classLoader);
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
                                    return;
                                }
                                // ==========================================
                                // 【终极网课解锁】：拦截课程源头，暴力篡改视频限制参数 (兼容实体转义)
                                // ==========================================
                                // 扩大匹配范围，涵盖 JSON 源头和 HTML 框架
                                if (url.contains("knowledge/cards") || url.contains("richvideo/initdatawithviewer") || url.contains("studentstudy")) {
                                    try {
                                        URL targetUrl = new URL(url);
                                        HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();

                                        // 复制原请求的方法与 Header
                                        if (requestObj != null) {
                                            conn.setRequestMethod((String) XposedHelpers.callMethod(requestObj, "getMethod"));
                                            @SuppressWarnings("unchecked")
                                            Map<String, String> headers = (Map<String, String>) XposedHelpers.callMethod(requestObj, "getRequestHeaders");
                                            if (headers != null) {
                                                for (Map.Entry<String, String> entry : headers.entrySet()) {
                                                    if (!entry.getKey().equalsIgnoreCase("Accept-Encoding")) {
                                                        conn.setRequestProperty(entry.getKey(), entry.getValue());
                                                    }
                                                }
                                            }
                                        } else {
                                            conn.setRequestMethod("GET");
                                        }

                                        // 同步 Cookie 证明合法登录，并请求 GZIP 压缩
                                        String cookie = android.webkit.CookieManager.getInstance().getCookie(url);
                                        if (cookie != null) {
                                            conn.setRequestProperty("Cookie", cookie);
                                        }
                                        conn.setRequestProperty("Accept-Encoding", "gzip");

                                        // 解除 GZIP 封印，获取明文流
                                        InputStream is = conn.getInputStream();
                                        String encoding = conn.getContentEncoding();
                                        if (encoding != null && encoding.toLowerCase().contains("gzip")) {
                                            is = new java.util.zip.GZIPInputStream(is);
                                        }

                                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                        byte[] buffer = new byte[4096];
                                        int len;
                                        while ((len = is.read(buffer)) != -1) {
                                            baos.write(buffer, 0, len);
                                        }
                                        String htmlStr = new String(baos.toByteArray(), "UTF-8");

                                        // ================= 实施终极基因改造 =================
                                        if (htmlStr.contains("fastforward") || htmlStr.contains("doublespeed")) {
                                            htmlStr = htmlStr
                                                    // 1. 彻底解除快进限制
                                                    .replace("\"fastforward\":\"true\"", "\"fastforward\":\"false\"")
                                                    .replace("\"fastforward\":true", "\"fastforward\":false")
                                                    .replace("&quot;fastforward&quot;:&quot;true&quot;", "&quot;fastforward&quot;:&quot;false&quot;")
                                                    .replace("&quot;fastforward&quot;:true", "&quot;fastforward&quot;:false")

                                                    // 2. 修复倍速误伤：超星中1是开启，0是关闭，我们要强制改为1！
                                                    .replace("\"doublespeed\":0", "\"doublespeed\":1")
                                                    .replace("\"doublespeed\":\"0\"", "\"doublespeed\":\"1\"")
                                                    .replace("&quot;doublespeed&quot;:0", "&quot;doublespeed&quot;:1")
                                                    .replace("&quot;doublespeed&quot;:&quot;0&quot;", "&quot;doublespeed&quot;:&quot;1&quot;")

                                                    // 3. 解除切屏暂停
                                                    .replace("\"switchwindow\":\"true\"", "\"switchwindow\":\"false\"")
                                                    .replace("\"switchwindow\":true", "\"switchwindow\":false")
                                                    .replace("&quot;switchwindow&quot;:&quot;true&quot;", "&quot;switchwindow&quot;:&quot;false&quot;")
                                                    .replace("&quot;switchwindow&quot;:true", "&quot;switchwindow&quot;:false");

                                            htmlStr = htmlStr.replace("var forbidImgClick = \"true\";", "var forbidImgClick = \"false\";");

                                            XposedBridge.log("Chaoxing [网课解锁]: 成功篡改最底层视频限制参数，快进、倍速均已完全自由！");
                                        }

                                        // ================= 重新打包返回 =================
                                        Class<?> responseClass = XposedHelpers.findClassIfExists("android.webkit.WebResourceResponse", lpparam.classLoader);
                                        if (responseClass != null) {
                                            String contentType = conn.getContentType();
                                            String mimeType = "text/html";
                                            String charset = "utf-8";
                                            if (contentType != null) {
                                                String[] parts = contentType.split(";");
                                                mimeType = parts[0].trim();
                                                if (parts.length > 1 && parts[1].toLowerCase().contains("charset=")) {
                                                    charset = parts[1].split("=")[1].trim();
                                                }
                                            }
                                            innerParam.setResult(XposedHelpers.newInstance(
                                                    responseClass,
                                                    mimeType,
                                                    charset,
                                                    new ByteArrayInputStream(htmlStr.getBytes(charset))
                                            ));
                                        }
                                        return; // 结束，原请求不再发出
                                    } catch (Exception e) {
                                        XposedBridge.log("Chaoxing Error (Video Unlock): " + e.getMessage());
                                    }
                                }

                                if (getSignConfig().enableCopyRestriction && url.contains("notAllowCopy.css")) {
                                    try {
                                        Class<?> responseClass = XposedHelpers.findClassIfExists("android.webkit.WebResourceResponse", lpparam.classLoader);
                                        if (responseClass != null) {
                                            String emptyCss = "";
                                            innerParam.setResult(XposedHelpers.newInstance(
                                                    responseClass,
                                                    "text/css",
                                                    "utf-8",
                                                    new java.io.ByteArrayInputStream(emptyCss.getBytes("UTF-8"))
                                            ));
                                            XposedBridge.log("Chaoxing AdSkip: 成功拦截 notAllowCopy.css，复制限制已解除！");
                                        }
                                    } catch (Exception e) {}
                                    return;
                                }

                                // POST body is handled by the page bridge; never replay an empty-body request.
                                if (url.contains("stuSignajax") &&
                                        ((requestObj instanceof android.webkit.WebResourceRequest &&
                                                "POST".equalsIgnoreCase(((android.webkit.WebResourceRequest) requestObj).getMethod())) ||
                                                !url.contains("latitude=") || !url.contains("longitude="))) {
                                    debugLog("POST-SIGN legacy replay skipped (POST/body coordinates)");
                                    return;
                                }
                                if (url.contains("stuSignajax")) {
                                    SignConfig config = getSignConfig();
                                    String newUrlString = url;
                                    boolean hasModified = false;
                                    double sendLat = 0, sendLon = 0;

                                    if (config.autoCalculateLocation) {
                                        if (calculatedTarget != null) {
                                            sendLat = calculatedTarget[0];
                                            sendLon = calculatedTarget[1];
                                        } else {
                                            double baseLat = 0, baseLon = 0;
                                            try {
                                                Matcher latMatcher = Pattern.compile("latitude=([^&]+)").matcher(url);
                                                Matcher lonMatcher = Pattern.compile("longitude=([^&]+)").matcher(url);
                                                if (latMatcher.find()) baseLat = Double.parseDouble(latMatcher.group(1));
                                                if (lonMatcher.find()) baseLon = Double.parseDouble(lonMatcher.group(1));
                                            } catch (Exception e) {}
                                            try {
                                                if (!config.latitude.isEmpty()) baseLat = Double.parseDouble(config.latitude);
                                                if (!config.longitude.isEmpty()) baseLon = Double.parseDouble(config.longitude);
                                            } catch (Exception e){}

                                            if (historyPoints.isEmpty()) {
                                                sendLat = baseLat;
                                                sendLon = baseLon;
                                            } else {
                                                LocationPoint lastP = historyPoints.get(historyPoints.size() - 1);
                                                double offset = Math.max(0.0001, lastP.distance / 200000.0);
                                                int attempt = historyPoints.size();

                                                if (attempt % 3 == 1) {
                                                    sendLat = lastP.lat + offset;
                                                    sendLon = lastP.lon;
                                                } else if (attempt % 3 == 2) {
                                                    sendLat = lastP.lat;
                                                    sendLon = lastP.lon + offset;
                                                } else {
                                                    sendLat = lastP.lat - offset;
                                                    sendLon = lastP.lon - offset;
                                                }
                                            }
                                        }
                                        newUrlString = newUrlString.replaceAll("latitude=[^&]*", "latitude=" + sendLat)
                                                .replaceAll("longitude=[^&]*", "longitude=" + sendLon);
                                        hasModified = true;

                                    } else if (config.modifyLocation && !config.latitude.isEmpty() && !config.longitude.isEmpty()) {
                                        newUrlString = newUrlString.replaceAll("latitude=[^&]*", "latitude=" + config.latitude)
                                                .replaceAll("longitude=[^&]*", "longitude=" + config.longitude);
                                        hasModified = true;
                                    }

                                    if (config.modifyAddress && !config.address.isEmpty()) {
                                        newUrlString = newUrlString.replaceAll("address=[^&]*", "address=" + URLEncoder.encode(config.address, "UTF-8"));
                                        hasModified = true;
                                    }
                                    if (config.modifyName && !config.name.isEmpty()) {
                                        newUrlString = newUrlString.replaceAll("name=[^&]*", "name=" + URLEncoder.encode(config.name, "UTF-8"));
                                        hasModified = true;
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
                                        if (cookie != null) { conn.setRequestProperty("Cookie", cookie); }

                                        InputStream is = conn.getInputStream();
                                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                        byte[] buffer = new byte[1024];
                                        int len;
                                        while ((len = is.read(buffer)) != -1) baos.write(buffer, 0, len);
                                        String jsonResp = new String(baos.toByteArray(), "UTF-8");

                                        if (config.autoCalculateLocation) {
                                            Matcher m = Pattern.compile("距.*?([0-9.]+)\\s*米").matcher(jsonResp);
                                            if (m.find()) {
                                                String originalMatch = m.group(0);
                                                double dist = Double.parseDouble(m.group(1));

                                                if (calculatedTarget != null) {
                                                    calculatedTarget = null;
                                                }

                                                historyPoints.add(new LocationPoint(sendLat, sendLon, dist));
                                                if (historyPoints.size() > 3) {
                                                    historyPoints.remove(0);
                                                }

                                                String customMsg = "";
                                                if (historyPoints.size() == 3) {
                                                    calculatedTarget = calculateTriangulation(historyPoints);
                                                    if (calculatedTarget != null) {
                                                        customMsg = "Xposed提示: 目标坐标已锁定，误差已过滤，请点击执行最终打卡。";
                                                    } else {
                                                        historyPoints.clear();
                                                        customMsg = "Xposed提示: 三点共线计算失败，正在重新采集，请再次点击。";
                                                    }
                                                } else {
                                                    customMsg = "Xposed提示: 距靶心 " + dist + " 米，采集进度(" + historyPoints.size() + "/3)。请再次点击。";
                                                }

                                                jsonResp = jsonResp.replace(originalMatch, customMsg);
                                                XposedBridge.log("Chaoxing AdSkip: " + customMsg);

                                            } else if (jsonResp.contains("success") || jsonResp.contains("成功")) {
                                                historyPoints.clear();
                                                calculatedTarget = null;
                                                XposedBridge.log("Chaoxing AdSkip: 定位爆破签到大成功！");
                                            }
                                        }

                                        String contentType = conn.getContentType();
                                        Class<?> responseClass = XposedHelpers.findClassIfExists("android.webkit.WebResourceResponse", lpparam.classLoader);
                                        if (responseClass != null) {
                                            innerParam.setResult(XposedHelpers.newInstance(
                                                    responseClass,
                                                    (contentType != null) ? contentType.split(";")[0].trim() : "application/json",
                                                    conn.getContentEncoding() != null ? conn.getContentEncoding() : "utf-8",
                                                    new ByteArrayInputStream(jsonResp.getBytes("UTF-8"))
                                            ));
                                        }
                                    } catch (Exception e) {}
                                }
                            }
                        });
                        // ===== 页面加载完成注入：手势/位置签到自动完成 =====
                        XposedBridge.hookAllMethods(targetClass, "onPageFinished", new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam innerParam) throws Throwable {
                                try {
                                    if (innerParam.args.length < 2 || !(innerParam.args[0] instanceof android.webkit.WebView)) return;
                                    android.webkit.WebView wv = (android.webkit.WebView) innerParam.args[0];
                                    injectPostSignScript(wv);
                                    SignConfig cfg = getSignConfig();

                                    // 复制解除（功能16）：7.0.1 中 notAllowCopy.css 已不再出现，
                                    // 改为通用"强制可选中/可复制"注入（所有页面，幂等）
                                    if (cfg.enableCopyRestriction) {
                                        wv.evaluateJavascript(buildCopyEnablerJs(), null);
                                    }
                                    // 手势/签到码自动签到：进页取校验码，驱动页面自身提交流
                                    if (cfg.autoGestureSign || cfg.autoCodeSign) {
                                        String cfgLat = cfg.modifyLocation ? cfg.latitude.replaceAll("[^0-9.\\-]", "") : "";
                                        String cfgLon = cfg.modifyLocation ? cfg.longitude.replaceAll("[^0-9.\\-]", "") : "";
                                        wv.evaluateJavascript(buildAutoCodeSignJs(cfg.autoGestureSign, cfg.autoCodeSign, cfgLat, cfgLon), null);
                                    }
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

                        // ===== 考试防切屏绕过（受"考试风控拦截"开关控制）=====
                        // 学习通切屏时原生注入 CLIENT_WEB_LIFECYCLE {status:10/0}(切出)，
                        // H5 考试页收到后弹警告+上报；把 status 全部伪装成 11(前台) 即可无感通过。
                        // 实测 3 次切屏全部拦截、警告弹窗消失
                        if (jsCode.contains("CLIENT_WEB_LIFECYCLE") && getSignConfig().bypassExamCheat) {
                            // 正则需与 cxanalysis 验证版完全一致：\\\\? = 可选反斜杠（兼容 JSON 转义），
                            // 漏一层转义会变成"字面问号"导致永远匹配不上
                            String replaced = jsCode.replaceAll("\\\\?\"status\\\\?\"\\s*:\\s*\\d+", "\"status\":11")
                                    .replaceAll("'status'\\s*:\\s*\\d+", "'status':11");
                            if (!replaced.equals(jsCode)) {
                                jsCode = replaced;
                                param.args[0] = jsCode;
                                XposedBridge.log("Chaoxing [防切屏]: CLIENT_WEB_LIFECYCLE status 已伪装为前台(11)");
                            }
                        }

                        if (jsCode.contains("CLIENT_DEVICE_FLAG")) {
                            SignConfig config = getSignConfig();
                            if (config.randomizeDeviceFlag) {
                                int start = jsCode.indexOf('{');
                                int end = jsCode.lastIndexOf('}');
                                if (start != -1 && end != -1 && start < end) {
                                    String wipeMemory = "window.localStorage.clear(); window.sessionStorage.clear(); ";
                                    String newJsCode = wipeMemory + jsCode.substring(0, start) + generateRandomDeviceFlag() + jsCode.substring(end + 1);
                                    param.args[0] = newJsCode;
                                }
                            }
                        }
                    }
                }
            };
            XposedBridge.hookAllMethods(webViewClass, "evaluateJavascript", jsInterceptHook);
            XposedBridge.hookAllMethods(webViewClass, "loadUrl", jsInterceptHook);

        } catch (Throwable t) {
            XposedBridge.log("Chaoxing AdSkip Error (WebView Hook): " + t.getMessage());
        }
    }

    /** 文件层 hook：考试监考截图替换（路径特征，抗混淆） */
    private void installFileReplaceHook(LoadPackageParam lpparam) {
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
                        XposedBridge.log("Chaoxing [绝杀]: 抓到监考截图上传！已成功替换为自定义图片 -> 拦截原图: " + path);
                    } else {
                        XposedBridge.log("Chaoxing [警告]: 找不到自定义伪装图片，未执行替换！请检查路径: " + imagePath);
                    }
                }
            };

            XposedBridge.hookAllConstructors(java.io.FileInputStream.class, fileReadHook);

        } catch (Throwable t) {
            XposedBridge.log("Chaoxing Error (File Replace Hook): " + t.getMessage());
        }
    }

    /**
     * 考试截图上传拦截：网络/协议层为主（抓包定位的 pan-yz 上传 URL，抗混淆），
     * f1.q0 类名 hook 已失效（6.7.8 中 f1 仅剩 execute），保留为静默回退不再依赖。
     */
    private void installExamSnapshotHook(LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.chaoxing.mobile.webapp.jsprotocal.common.f1",
                    lpparam.classLoader,
                    "q0",
                    File.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log("Chaoxing [终极斩杀]: 成功拦截 f1.q0，ExamKeeper 截图上传已被物理蒸发！");
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

    /**
     * 长按「我」页的"设置"行直达模块主页：
     * 「我」页 = com.chaoxing.study.mine.MineFragment2（jadx 7.0.1 确认），
     * 设置行 = CardView(cv_settings) 可点击，内层 TextView 不可点击。
     * 两路注入：
     *   A. 精准：hook MineFragment2.onViewCreated，根布局里找"设置"TextView，向上找可点击祖先注入；
     *   B. 兜底：hook View.dispatchAttachedToWindow（ViewGroup 回调 super，全视图必经），任何"设置"文本
     *      可点击行同样注入（不要求 TextView 自身可点击——CardView 结构下 TextView 均不可点击）。
     * 原单击行为不变；行视图弱引用表去重。
     */
    private void installLongPressModuleEntry(ClassLoader loader) {
        // A. 精准注入：MineFragment2.onViewCreated（viewBinding 静态布局，挂载一次即生效）
        try {
            Class<?> frag = XposedHelpers.findClassIfExists("com.chaoxing.study.mine.MineFragment2", loader);
            if (frag != null) {
                XposedBridge.hookAllMethods(frag, "onViewCreated", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (param.args.length < 1 || !(param.args[0] instanceof android.view.View)) return;
                            android.view.View root = (android.view.View) param.args[0];
                            root.postDelayed(() -> {
                                try { injectSettingsLongPressRecursive(root); } catch (Throwable ignored) {}
                            }, 500);
                        } catch (Throwable ignored) {}
                    }
                });
            }
        } catch (Throwable ignored) {}

        // B. 兜底注入：全局视图挂载
        XC_MethodHook attachHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object obj = param.thisObject;
                    if (!(obj instanceof android.widget.TextView)) return;
                    android.widget.TextView tv = (android.widget.TextView) obj;
                    CharSequence text = tv.getText();
                    if (text == null || !"设置".contentEquals(text)) return;
                    injectSettingsLongPress(tv);
                } catch (Throwable ignored) {}
            }
        };
        XposedBridge.hookAllMethods(android.view.View.class, "dispatchAttachedToWindow", attachHook);
    }

    /** 对"设置"TextView：向上最多 6 层找可点击行注入长按；找不到则注入 TextView 自身（长按仅覆盖文字区域） */
    private void injectSettingsLongPress(android.widget.TextView tv) {
        android.view.View row = tv;
        for (int i = 0; i < 6 && row.getParent() instanceof android.view.View; i++) {
            android.view.View parent = (android.view.View) row.getParent();
            row = parent;
            if (row.isClickable()) break;
        }
        if (injectedLongPressRows.containsKey(row)) return;
        injectedLongPressRows.put(row, Boolean.TRUE);
        row.setOnLongClickListener(v -> {
            try {
                EmbeddedSettings.show(v.getContext());
                return true;
            } catch (Throwable t) {
                return false;
            }
        });
    }

    /** 在视图树中递归查找文本恰为"设置"的 TextView 并注入长按 */
    private void injectSettingsLongPressRecursive(android.view.View view) {
        if (view instanceof android.widget.TextView) {
            android.widget.TextView tv = (android.widget.TextView) view;
            CharSequence text = tv.getText();
            if (text != null && "设置".contentEquals(text.toString().trim())) {
                injectSettingsLongPress(tv);
                return;
            }
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                injectSettingsLongPressRecursive(group.getChildAt(i));
            }
        }
    }

    /**
     * 通用"强制可选中/可复制"注入（功能16，7.0.1 起替代 notAllowCopy.css 拦截）：
     * 1. 覆盖 user-select 为 auto
     * 2. 清除 document/body 上的内联阻止属性（onselectstart/oncopy/oncut/oncontextmenu/ondragstart）
     * 3. 捕获阶段 stopPropagation，拦截页面注册的阻止事件
     * 幂等（__cxCopyOn 标志），每次 onPageFinished 注入一次
     */
    private static final String POST_SIGN_SCRIPT =
            "(function(){\n"
                    + " 'use strict';\n"
                    + " if(window.__cxPostSignV1Installed)return 'already';\n"
                    + " var bridge=window.__cxPostSignV1;\n"
                    + " if(!bridge)return 'bridge-missing';\n"
                    + " function target(url,method){try{var u=new URL(url,location.href);return String(method).toUpperCase()==='POST'&&u.protocol==='https:'&&u.hostname==='mobilelearn.chaoxing.com'&&u.pathname==='/pptSign/stuSignajax';}catch(e){return false;}}\n"
                    + " function prepare(url,body){\n"
                    + "  if(typeof body!=='string'&&!(typeof URLSearchParams!=='undefined'&&body instanceof URLSearchParams)){return null;}\n"
                    + "  try{var p=JSON.parse(bridge.prepare(new URL(url,location.href).href,String(body)));return p&&p.body!==undefined?p:null;}catch(e){return null;}\n"
                    + " }\n"
                    + " function report(p,status,text){if(p&&p.id)try{bridge.complete(String(p.id),Number(status),String(text).slice(0,65536));}catch(e){}}\n"
                    + " var open=XMLHttpRequest.prototype.open,send=XMLHttpRequest.prototype.send;\n"
                    + " XMLHttpRequest.prototype.open=function(method,url){var result=open.apply(this,arguments);this.__cxSignMethod=method;this.__cxSignUrl=String(url);return result;};\n"
                    + " XMLHttpRequest.prototype.send=function(body){\n"
                    + "  var p=target(this.__cxSignUrl,this.__cxSignMethod)?prepare(this.__cxSignUrl,body):null;\n"
                    + "  if(p){var xhr=this;var done=function(){xhr.removeEventListener('loadend',done);var text='';try{text=xhr.responseType==='json'?JSON.stringify(xhr.response):xhr.responseText;}catch(e){}report(p,xhr.status,text);};xhr.addEventListener('loadend',done);}\n"
                    + "  try{return send.call(this,p?p.body:body);}catch(e){report(p,0,'');throw e;}\n"
                    + " };\n"
                    + " if(typeof window.fetch==='function'){\n"
                    + "  var originalFetch=window.fetch;\n"
                    + "  window.fetch=function(input,init){\n"
                    + "   var receiver=this,url=typeof input==='string'?input:(input&&input.url)||String(input),method=(init&&init.method)||(input&&input.method)||'GET';\n"
                    + "   if(!target(url,method))return originalFetch.apply(receiver,arguments);\n"
                    + "   function run(body){var p=prepare(url,body);var options=init;if(p)options=Object.assign({},init||{},{body:p.body});var promise;try{promise=originalFetch.call(receiver,input,options);}catch(e){report(p,0,'');throw e;}\n"
                    + "    return promise.then(function(response){if(p)try{response.clone().text().then(function(text){report(p,response.status,text);},function(){report(p,0,'');});}catch(e){report(p,0,'');}return response;},function(e){report(p,0,'');throw e;});}\n"
                    + "   if(init&&init.body!==undefined)return run(init.body);\n"
                    + "   if(typeof Request!=='undefined'&&input instanceof Request){try{return input.clone().text().then(run);}catch(e){return originalFetch.call(receiver,input,init);}}\n"
                    + "   return originalFetch.call(receiver,input,init);\n"
                    + "  };\n"
                    + " }\n"
                    + " window.__cxPostSignV1Installed=true;\n"
                    + " return 'installed';\n"
                    + "})();\n"
            ;
    // POST compatibility: mutate the original page request, never replay it via HttpURLConnection.
    private final java.util.WeakHashMap<android.webkit.WebView, PostSignBridge> postSignBridges = new java.util.WeakHashMap<>();

    private void attachPostSignBridge(android.webkit.WebView view) {
        try {
            synchronized (postSignBridges) {
                if (postSignBridges.containsKey(view)) return;
                PostSignBridge bridge = new PostSignBridge(view.getContext().getApplicationContext(), view);
                view.addJavascriptInterface(bridge, "__cxPostSignV1");
                postSignBridges.put(view, bridge);
            }
        } catch (Throwable t) { debugLog("POST-SIGN attach FAILED: " + t); }
    }

    private void injectPostSignScript(android.webkit.WebView view) {
        attachPostSignBridge(view);
        try {
            view.evaluateJavascript(POST_SIGN_SCRIPT, null);
        } catch (Throwable t) { debugLog("POST-SIGN inject FAILED: " + t); }
    }

    public final class PostSignBridge {
        private final android.content.Context context;
        private final List<LocationPoint> points = new ArrayList<>();
        private final java.util.LinkedHashMap<String, PostAttempt> pending = new java.util.LinkedHashMap<>();
        private double[] target;
        private String session = "";
        private volatile boolean hunting = false;
        private String formTemplate = "";
        private long sequence;
        private long generation;
        private final class PostAttempt {
            final double lat, lon;
            final long generation, time;
            final boolean automatic;
            PostAttempt(double lat, double lon, boolean automatic) {
                this.lat = lat; this.lon = lon; this.automatic = automatic;
                this.generation = PostSignBridge.this.generation;
                this.time = android.os.SystemClock.elapsedRealtime();
            }
        }
        private String pageUrl;
        private String userAgent;
        PostSignBridge(android.content.Context context, android.webkit.WebView view) {
            this.context = context;
            try { this.pageUrl = view.getUrl(); this.userAgent = view.getSettings().getUserAgentString(); } catch (Throwable ignored) {}
        }

        @android.webkit.JavascriptInterface
        public synchronized String prepare(String url, String body) {
            try {
                java.net.URI uri = java.net.URI.create(url);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || !"mobilelearn.chaoxing.com".equalsIgnoreCase(uri.getHost())
                        || !"/pptSign/stuSignajax".equals(uri.getPath()) || body == null || body.length() > 1024 * 1024) return "{}";
                SignConfig cfg = getSignConfig();
                boolean changeLocation = cfg.autoCalculateLocation ||
                        (cfg.modifyLocation && !cfg.latitude.isEmpty() && !cfg.longitude.isEmpty());
                boolean changeAddress = cfg.modifyAddress && !cfg.address.isEmpty();
                boolean changeName = cfg.modifyName && !cfg.name.isEmpty();
                if (!changeLocation && !changeAddress && !changeName) return "{}";
                Map<String, String> form = new java.util.LinkedHashMap<>();
                for (String part : body.split("&", -1)) {
                    int eq = part.indexOf('=');
                    if (eq < 0) continue;
                    form.put(java.net.URLDecoder.decode(part.substring(0, eq), "UTF-8"),
                            java.net.URLDecoder.decode(part.substring(eq + 1), "UTF-8"));
                }
                if (!form.containsKey("latitude") || !form.containsKey("longitude")) {
                    if (changeLocation) debugLog("POST-SIGN coordinates absent; text changes remain independent");
                    changeLocation = false;
                }
                if (!changeLocation && !changeAddress && !changeName) return "{}";
                if (changeLocation) formTemplate = body;
                double lat = Double.NaN, lon = Double.NaN;
                if (changeLocation) {
                    String key = form.getOrDefault("activeId", "") + "|" + form.getOrDefault("courseId", "")
                            + "|" + form.getOrDefault("uid", "") + "|" + cfg.autoCalculateLocation + "|" + cfg.latitude + "|" + cfg.longitude;
                    if (!key.equals(session)) {
                        session = key; generation++; points.clear(); target = null; pending.clear();
                        debugLog("POST-SIGN session reset");
                    }
                    long now = android.os.SystemClock.elapsedRealtime();
                    pending.entrySet().removeIf(e -> now - e.getValue().time > 60000);
                    if (cfg.autoCalculateLocation && pending.values().stream().anyMatch(p -> p.automatic)) {
                        debugLog("POST-SIGN skipped: previous automatic sample pending"); return "{}";
                    }
                    if (cfg.autoCalculateLocation) {
                        if (target != null) { lat = target[0]; lon = target[1]; }
                        else if (points.isEmpty()) {
                            lat = Double.parseDouble(cfg.latitude.isEmpty() ? form.get("latitude") : cfg.latitude);
                            lon = Double.parseDouble(cfg.longitude.isEmpty() ? form.get("longitude") : cfg.longitude);
                        } else {
                            LocationPoint last = points.get(points.size() - 1);
                            double offset = Math.max(0.0001, last.distance / 200000.0);
                            int attempt = points.size();
                            lat = last.lat; lon = last.lon;
                            if (attempt % 3 == 1) lat += offset;
                            else if (attempt % 3 == 2) lon += offset;
                            else { lat -= offset; lon -= offset; }
                        }
                    } else {
                        if (cfg.latitude.isEmpty() || cfg.longitude.isEmpty()) return "{}";
                        lat = Double.parseDouble(cfg.latitude); lon = Double.parseDouble(cfg.longitude);
                    }
                    if (!validCoordinates(lat, lon)) { debugLog("POST-SIGN skipped: invalid coordinates"); return "{}"; }
                }
                org.json.JSONObject location = null;
                if ((changeLocation || changeAddress) && form.containsKey("locationResult") && !form.get("locationResult").isEmpty()) {
                    // Keep mockData/signConfig/token and all other JSON fields unchanged.
                    location = new org.json.JSONObject(form.get("locationResult"));
                    if (changeLocation) { location.put("latitude", lat); location.put("longitude", lon); }
                    if (changeAddress && location.has("address")) location.put("address", cfg.address);
                }
                StringBuilder updated = new StringBuilder();
                int partIndex = 0;
                for (String part : body.split("&", -1)) {
                    if (partIndex++ > 0) updated.append('&');
                    int eq = part.indexOf('=');
                    String name = eq < 0 ? "" : java.net.URLDecoder.decode(part.substring(0, eq), "UTF-8");
                    String value = null;
                    if (changeLocation && "latitude".equals(name)) value = Double.toString(lat);
                    else if (changeLocation && "longitude".equals(name)) value = Double.toString(lon);
                    else if (changeAddress && "address".equals(name)) value = cfg.address;
                    else if (changeName && "name".equals(name)) value = cfg.name;
                    else if ("locationResult".equals(name) && location != null) value = location.toString();
                    updated.append(value == null ? part : part.substring(0, eq + 1) + URLEncoder.encode(value, "UTF-8"));
                }
                String id = "";
                if (changeLocation) {
                    id = Long.toString(++sequence);
                    while (pending.size() >= 16) pending.remove(pending.keySet().iterator().next());
                    pending.put(id, new PostAttempt(lat, lon, cfg.autoCalculateLocation));
                }
                debugLog("POST-SIGN rewrite id=" + id + " automatic=" + cfg.autoCalculateLocation + " samples=" + points.size()
                        + " locationResult=" + (location != null) + " address=" + changeAddress + " name=" + changeName);
                return new org.json.JSONObject().put("id", id).put("body", updated.toString()).toString();
            } catch (Throwable t) { debugLog("POST-SIGN prepare FAILED: " + t.getClass().getSimpleName()); return "{}"; }
        }

        @android.webkit.JavascriptInterface
        public synchronized void complete(String id, int status, String response) {
            PostAttempt attempt = pending.remove(id);
            if (attempt == null || attempt.generation != generation || !attempt.automatic) return;
            if (status < 200 || status >= 300 || response == null || response.length() > 65536) {
                debugLog("POST-SIGN response ignored id=" + id + " status=" + status); return;
            }
            try {
                String messages = response;
                try {
                    StringBuilder text = new StringBuilder();
                    collectResponseStrings(new org.json.JSONTokener(response).nextValue(), text, 0);
                    messages += "\n" + text;
                } catch (Exception ignored) { }
                // 双格式距离反馈：距…X米 与 errorLocation2_X
                Double parsed = parseDistance(messages);
                if (parsed != null) {
                    double distance = parsed;
                    if (!Double.isFinite(distance) || distance < 0) return;
                    if (target != null) target = null;
                    points.add(new LocationPoint(attempt.lat, attempt.lon, distance));
                    if (points.size() > 3) points.remove(0);
                    if (!hunting && !formTemplate.isEmpty()) { startHunt(); return; }
                    if (points.size() == 3) {
                        target = calculateTriangulation(points); // Reuse the baseline solver verbatim.
                        if (target != null && validCoordinates(target[0], target[1])) {
                            notifyResult("已采集 3 个距离，目标坐标已计算，请再次点击签到");
                        } else {
                            target = null; points.clear(); notifyResult("三点计算失败，已清空采样，请重试");
                        }
                    } else notifyResult("距离 " + distance + " 米，采集进度 " + points.size() + "/3，请再次点击");
                    debugLog("POST-SIGN distance id=" + id + " meters=" + distance + " samples=" + points.size());
                } else {
                    String trimmed = response.trim();
                    if ("success".equalsIgnoreCase(trimmed) || messages.contains("签到成功")) {
                        points.clear(); target = null; notifyResult("签到成功，已清空距离采样");
                    }
                }
            } catch (Throwable t) { debugLog("POST-SIGN response FAILED: " + t.getClass().getSimpleName()); }
        }
        /** 后台靶心搜索：用捕获的表单模板自行发探测请求（页面跳走不受影响），采距→最小二乘→终射 */
        private void startHunt() {
            hunting = true;
            final String template = formTemplate;
            final long gen = generation;
            final LocationPoint first = points.get(points.size() - 1);
            new Thread(() -> {
                try {
                    hunt(template, gen, first.lat, first.lon, first.distance);
                } catch (Throwable t) {
                    debugLog("HUNT err: " + t);
                    notifyResult("靶心搜索异常：" + t.getClass().getSimpleName());
                } finally {
                    hunting = false;
                }
            }, "cx-hunt").start();
        }

        private void hunt(String template, long gen, double lat0, double lon0, double firstDist) throws Exception {
            notifyResult("开始自动搜索靶心（首个距离约 " + Math.round(firstDist) + " 米）");
            java.util.List<LocationPoint> samples = new java.util.ArrayList<>();
            samples.add(new LocationPoint(lat0, lon0, firstDist));
            // 抖动幅度随首距自适应：近距固定 ~2km，远距放到距离的 ~60%（防远场病态）
            double scale = Math.max(0.02, Math.min(3.0, firstDist / 111320.0 * 0.6));
            double[][] spread = {{1,1},{-1,1},{1,-1},{-1,-1},{0,1}};
            for (double[] s : spread) {
                Thread.sleep(500);
                if (gen != generation) { debugLog("HUNT abort: session changed"); return; }
                double plat = lat0 + s[0] * scale, plon = lon0 + s[1] * scale;
                String resp = httpSign(buildBody(template, plat, plon));
                if (isSuccess(resp)) { notifyResult("签到成功 ✔（探测阶段即命中）"); return; }
                Double dist = parseDistance(resp);
                if (dist == null) { debugLog("HUNT probe no-distance: " + trimResp(resp)); continue; }
                samples.add(new LocationPoint(plat, plon, dist));
                debugLog("HUNT probe (" + plat + "," + plon + ") -> " + Math.round(dist) + "m, samples=" + samples.size());
            }
            double[] t = solveTarget(samples);
            if (t == null || !validCoordinates(t[0], t[1])) { notifyResult("靶心求解失败（样本不足），可再进一次活动继续"); return; }
            debugLog("HUNT coarse target: " + t[0] + "," + t[1]);
            // 阶段2：迭代收缩——每轮射当前估计，再按当前距离等比抖动补 3 个局部样本重解
            for (int round = 0; round < 12; round++) {
                Thread.sleep(600);
                if (gen != generation) return;
                String resp = httpSign(buildBody(template, t[0], t[1]));
                if (isSuccess(resp)) {
                    synchronized (this) { target = t; }
                    debugLog("HUNT final success @ " + t[0] + "," + t[1]);
                    notifyResult("靶心已锁定，签到成功 ✔");
                    return;
                }
                Double dist = parseDistance(resp);
                debugLog("HUNT round#" + round + " est=" + t[0] + "," + t[1] + " resp=" + trimResp(resp));
                if (dist == null) break;
                samples.add(new LocationPoint(t[0], t[1], dist));
                if (dist < 120) break; // 距离已小于常见 range，服务端仍拒则非距离问题
                double s2 = Math.max(0.0003, dist / 111320.0 * 0.7);
                double[][] local = {{s2, 0}, {-s2, s2 * 0.5}, {0, -s2}};
                for (double[] off : local) {
                    Thread.sleep(400);
                    if (gen != generation) return;
                    double plat = t[0] + off[0], plon = t[1] + off[1];
                    String r2 = httpSign(buildBody(template, plat, plon));
                    if (isSuccess(r2)) {
                        synchronized (this) { target = new double[]{plat, plon}; }
                        debugLog("HUNT local success @ " + plat + "," + plon);
                        notifyResult("靶心已锁定，签到成功 ✔");
                        return;
                    }
                    Double d2 = parseDistance(r2);
                    if (d2 != null) samples.add(new LocationPoint(plat, plon, d2));
                }
                // 近场后丢弃远场样本再解算：距离阈值取当前距离的 4 倍（下限 3km），消除远场梯度干扰
                java.util.List<LocationPoint> solveSet = samples;
                if (dist < 3000) {
                    double limit = Math.max(3000, dist * 4);
                    java.util.List<LocationPoint> near = new java.util.ArrayList<>();
                    for (LocationPoint sp : samples) if (sp.distance <= limit) near.add(sp);
                    if (near.size() >= 3) solveSet = near;
                }
                double[] nt = solveTarget(solveSet);
                if (nt == null || !validCoordinates(nt[0], nt[1])) break;
                t = nt;
                debugLog("HUNT round#" + round + " refined: " + t[0] + "," + t[1] + " set=" + solveSet.size() + "/" + samples.size());
            }
            notifyResult("自动搜索未成功，已采集 " + samples.size() + " 点；可再进一次活动继续逼近");
        }

        private String buildBody(String template, double lat, double lon) throws Exception {
            StringBuilder sb = new StringBuilder();
            for (String part : template.split("&", -1)) {
                int eq = part.indexOf('=');
                if (eq < 0) continue;
                String k = java.net.URLDecoder.decode(part.substring(0, eq), "UTF-8");
                String v = java.net.URLDecoder.decode(part.substring(eq + 1), "UTF-8");
                if ("latitude".equals(k)) v = Double.toString(lat);
                else if ("longitude".equals(k)) v = Double.toString(lon);
                else if ("location".equals(k) || "locationResult".equals(k)) {
                    // 与 prepare() 同款：原 JSON 只换坐标，保留其余字段（含原生签名信息）
                    try {
                        org.json.JSONObject o = new org.json.JSONObject(v);
                        o.put("latitude", lat).put("longitude", lon);
                        if (!o.has("result")) o.put("result", 1);
                        v = o.toString();
                    } catch (Exception e) {
                        v = new org.json.JSONObject().put("result", 1).put("address", "")
                                .put("longitude", lon).put("latitude", lat).toString();
                    }
                }
                else if ("deviceCode".equals(k)) v = randomDeviceCode();
                if (sb.length() > 0) sb.append('&');
                sb.append(java.net.URLEncoder.encode(k, "UTF-8")).append('=')
                  .append(java.net.URLEncoder.encode(v, "UTF-8"));
            }
            return sb.toString();
        }

        private String randomDeviceCode() {
            byte[] b = new byte[32];
            new java.security.SecureRandom().nextBytes(b);
            return android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP);
        }

        private String httpSign(String body) throws Exception {
            String cookie = android.webkit.CookieManager.getInstance().getCookie("https://mobilelearn.chaoxing.com");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(
                    "https://mobilelearn.chaoxing.com/pptSign/stuSignajax").openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            if (cookie != null) conn.setRequestProperty("Cookie", cookie);
            if (userAgent != null) conn.setRequestProperty("User-Agent", userAgent);
            if (pageUrl != null) conn.setRequestProperty("Referer", pageUrl);
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            conn.setRequestProperty("Origin", "https://mobilelearn.chaoxing.com");
            conn.setRequestProperty("Accept", "*/*");
            conn.setDoOutput(true);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            java.io.InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            if (in != null) { byte[] buf = new byte[4096]; int n; while ((n = in.read(buf)) > 0) bo.write(buf, 0, n); in.close(); }
            String resp = new String(bo.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            debugLog("HUNT http " + code + " " + trimResp(resp));
            return resp;
        }

        private String trimResp(String s) {
            if (s == null) return "null";
            String t = s.trim().replace("\n", " ");
            return t.substring(0, Math.min(70, t.length()));
        }

        /** 兼容两种距离反馈：距…X米 与 errorLocation2_X */
        private Double parseDistance(String resp) {
            if (resp == null) return null;
            Matcher m = Pattern.compile("距.*?([0-9.]+)\\s*米").matcher(resp);
            if (m.find()) return Double.parseDouble(m.group(1));
            m = Pattern.compile("errorLocation2_([0-9.]+)").matcher(resp);
            if (m.find()) return Double.parseDouble(m.group(1));
            return null;
        }

        private boolean isSuccess(String resp) {
            return resp != null && ("success".equalsIgnoreCase(resp.trim()) || resp.contains("签到成功"));
        }

        /** 多点最小二乘反解靶心（等距柱面近似 + 迭代） */
        double[] solveTarget(java.util.List<LocationPoint> pts) {
            if (pts.size() < 3) return null;
            double lat0 = 0, lon0 = 0;
            for (LocationPoint p : pts) { lat0 += p.lat; lon0 += p.lon; }
            lat0 /= pts.size(); lon0 /= pts.size();
            double kx = 111320.0 * Math.cos(Math.toRadians(lat0)), ky = 111320.0;
            LocationPoint best = pts.get(0);
            for (LocationPoint p : pts) if (p.distance < best.distance) best = p;
            double x = (best.lon - lon0) * kx, y = (best.lat - lat0) * ky;
            for (int it = 0; it < 200; it++) {
                double a11 = 0, a12 = 0, a22 = 0, b1 = 0, b2 = 0;
                for (LocationPoint p : pts) {
                    double px = (p.lon - lon0) * kx, py = (p.lat - lat0) * ky;
                    double den = Math.hypot(x - px, y - py); if (den < 1e-9) den = 1e-9;
                    double ux = (x - px) / den, uy = (y - py) / den, r = p.distance - den;
                    a11 += ux * ux; a12 += ux * uy; a22 += uy * uy; b1 += ux * r; b2 += uy * r;
                }
                double det = a11 * a22 - a12 * a12;
                if (Math.abs(det) < 1e-18) return null;
                double dx = (b1 * a22 - a12 * b2) / det, dy = (a11 * b2 - b1 * a12) / det;
                x += dx; y += dy;
                if (Math.hypot(dx, dy) < 1e-3) break;
            }
            return new double[]{ lat0 + y / ky, lon0 + x / kx };
        }

        private void notifyResult(String message) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show());
        }
    }
    private static boolean validCoordinates(double lat, double lon) {
        return Double.isFinite(lat) && Double.isFinite(lon) && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }
    private static void collectResponseStrings(Object value, StringBuilder text, int depth) throws org.json.JSONException {
        if (depth > 8 || text.length() > 65536) return;
        if (value instanceof String) text.append(value).append('\n');
        else if (value instanceof org.json.JSONObject) {
            org.json.JSONObject object = (org.json.JSONObject) value;
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) collectResponseStrings(object.opt(keys.next()), text, depth + 1);
        } else if (value instanceof org.json.JSONArray) {
            org.json.JSONArray array = (org.json.JSONArray) value;
            for (int i = 0; i < array.length(); i++) collectResponseStrings(array.opt(i), text, depth + 1);
        }
    }

    private String buildCopyEnablerJs() {
        return "(function(){try{"
                + "if(window.__cxCopyOn)return;window.__cxCopyOn=1;"
                + "var s=document.createElement('style');"
                + "s.textContent='*,*::before,*::after{-webkit-user-select:auto!important;user-select:text!important;-webkit-touch-callout:default!important;}';"
                + "(document.head||document.documentElement).appendChild(s);"
                + "['onselectstart','oncopy','oncut','oncontextmenu','ondragstart'].forEach(function(k){"
                + "try{document[k]=null;var b=document.body;if(b)b[k]=null;}catch(e){}});"
                + "['selectstart','copy','cut','contextmenu','dragstart'].forEach(function(t){"
                + "window.addEventListener(t,function(e){e.stopPropagation();},true);"
                + "document.addEventListener(t,function(e){e.stopPropagation();},true);});"
                + "}catch(e){}})();";
    }

    /**
     * 手势/签到码自动签到注入。
     * 页面加载完成后向同源 /newsign/gesture?activeId=&type=0 请求该活动的校验码（该分支以登录态直接返回明文），
     * 手势页回填 #code（若存在）并触发 hasPasswd；签到码页逐格填写并同步隐藏主输入框 #mobileInput，
     * 之后复用页面自身的校验与提交流（deviceCode/name 等提交参数由页面逻辑携带，坐标由 POST 桥改写）。
     * 目标节点可能异步渲染或在同源 iframe 中，boot 轮询最多 ~6s。
     * 位置解锁：开启位置校验的页面会等 CLIENT_USER_LOCATION 回调才提交，
     * 填码后主动取活动靶心并模拟原生 jsBridge.trigger 推送；靶心不可见时退回配置坐标。
     */
    private static String buildAutoCodeSignJs(boolean gestureOn, boolean codeOn, String cfgLat, String cfgLon) {
        return "(function(){\n"
                + " 'use strict';\n"
                + " if(window.__cxAutoCodeV1)return 'already';\n"
                + " window.__cxAutoCodeV1=true;\n"
                + " var GESTURE_ON=" + gestureOn + ",CODE_ON=" + codeOn + ";\n"
                + " var CFG_LAT='" + cfgLat + "',CFG_LON='" + cfgLon + "';\n"
                + " function st(o){}\n"
                + " function aidOf(search){try{var q=new URLSearchParams(search);return q.get('activeId')||q.get('activePrimaryId')||'';}catch(e){return '';}}\n"
                + " function docs(){var a=[document];try{var f=document.querySelectorAll('iframe');for(var i=0;i<f.length;i++){var d=f[i].contentDocument;if(d)a.push(d);}}catch(e){}return a;}\n"
                + " var tries=0;\n"
                // 位置解锁：ifopenAddress=1 的页面要等原生定位回调才继续，模拟 jsBridge 推送
                + " function unlock(d2,aid2){\n"
                + "  try{\n"
                + "   var ioa=d2.getElementById('ifopenAddress');\n"
                + "   if(!ioa||String(ioa.value||'')!=='1'){st({step:'no-loc-needed'});return;}\n"
                + "   var x2=new XMLHttpRequest();\n"
                + "   x2.onreadystatechange=function(){\n"
                + "    if(x2.readyState!==4)return;\n"
                + "    var lat='',lon='',txt='';\n"
                + "    try{var j=JSON.parse(x2.responseText);var dd=(j&&j.data)?j.data:j;if(dd){lat=String(dd.locationLatitude||'');lon=String(dd.locationLongitude||'');txt=String(dd.locationText||'');}}catch(e){}\n"
                + "    if(!lat||!lon){\n"
                + "     if(CFG_LAT&&CFG_LON){lat=CFG_LAT;lon=CFG_LON;txt='';st({step:'loc-cfg-coords'});}\n"
                + "     else{st({step:'loc-no-coords',http:x2.status});return;}\n"
                + "    }else st({step:'loc-api-coords',lat:lat,lon:lon});\n"
                + "    var loc={result:1,address:txt,longitude:parseFloat(lon),latitude:parseFloat(lat)};\n"
                + "    setTimeout(function(){\n"
                + "     try{if(window.jsBridge&&typeof window.jsBridge.trigger==='function'){window.jsBridge.trigger('CLIENT_USER_LOCATION',loc);st({step:'loc-pushed',lat:loc.latitude,lon:loc.longitude});}else st({step:'no-jsbridge'});}catch(e){st({step:'loc-push-fail',err:String(e)});}\n"
                + "    },1200);\n"
                + "   };\n"
                + "   x2.open('GET','/v2/apis/active/getPPTActiveInfo?activeId='+encodeURIComponent(aid2),true);\n"
                + "   x2.send();\n"
                + "  }catch(e){}\n"
                + " }\n"
                + " function boot(){\n"
                + "  var ds=docs();\n"
                + "  for(var i=0;i<ds.length;i++){\n"
                + "   var d=ds[i];\n"
                + "   var isG=!!d.getElementById('gesturepwd');\n"
                + "   var isC=d.querySelectorAll('input[codeIndex]').length>=4;\n"
                + "   if(!isG&&!isC)continue;\n"
                + "   var mode=isG?'gesture':'code';\n"
                + "   if(mode==='gesture'&&!GESTURE_ON)return 'gesture-off';\n"
                + "   if(mode==='code'&&!CODE_ON)return 'code-off';\n"
                + "   var oi=d.getElementById('otherId');\n"
                + "   if(oi&&String(oi.value||'')==='0')return 'skip';\n"
                + "   var aid=aidOf(location.search);\n"
                + "   try{var f2=document.querySelectorAll('iframe');for(var k=0;!aid&&k<f2.length;k++)aid=aidOf(f2[k].contentWindow.location.search);}catch(e){}\n"
                + "   var ai=d.getElementById('activeId');\n"
                + "   if(!aid&&ai)aid=ai.value;\n"
                + "   if(!aid)return 'no-aid';\n"
                + "   var x=new XMLHttpRequest();\n"
                + "   x.onreadystatechange=function(){\n"
                + "    if(x.readyState!==4||x.status!==200)return;\n"
                + "    var rd=new DOMParser().parseFromString(x.responseText,'text/html');\n"
                + "    var el=rd.getElementById('code');\n"
                + "    var code=el?String(el.getAttribute('value')||el.value||''):'';\n"
                + "    if(!/^\\d{4,8}$/.test(code)){st({step:'bad-code'});return;}\n"
                + "    st({step:'fetched',mode:mode,len:code.length});\n"
                + "    if(mode==='gesture'){\n"
                + "     var hidden=d.getElementById('code');\n"
                + "     var jq=window.jQuery;\n"
                + "     if(!jq||!jq.fn){try{var f3=document.querySelectorAll('iframe');for(var z=0;z<f3.length;z++){var w=f3[z].contentWindow;if(w.jQuery&&w.jQuery.fn){jq=w.jQuery;break;}}}catch(e){}}\n"
                + "     if(!jq||!jq.fn)return;\n"
                // 部分 gesture 模板没有 #code 隐藏域，hasPasswd 处理器自行比对/上报
                + "     if(hidden)hidden.value=code;\n"
                + "     st({step:'triggered'});\n"
                + "     jq('#gesturepwd',d).trigger('hasPasswd',[code]);\n"
                + "     unlock(d,aid);\n"
                + "    }else{\n"
                // 签到码长度 4-8 位可变：格数由活动配置决定（页面按 numberCount 动态补格子），码长必须与格数一致
                + "     var by={},ins=d.querySelectorAll('input[codeIndex]');\n"
                + "     for(var n=0;n<ins.length;n++)by[ins[n].getAttribute('codeIndex')]=ins[n];\n"
                + "     var cnt=ins.length;\n"
                + "     if(cnt<4||cnt>8||code.length!==cnt)return;\n"
                // 逐格填写。页面的 keydown 处理器会先清空格子，因此绝不能派发 keydown，只走 input/change；
                // 填完再同步隐藏主输入框 #mobileInput（移动端真实输入通道）
                + "     var i2=0;\n"
                + "     function fillNext(){\n"
                + "      if(i2>=cnt){\n"
                + "       try{\n"
                + "        var mi=d.getElementById('mobileInput');\n"
                + "        if(mi){\n"
                + "         try{Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set.call(mi,code);}catch(e){mi.value=code;}\n"
                + "         mi.dispatchEvent(new Event('input',{bubbles:true}));\n"
                + "         mi.dispatchEvent(new Event('change',{bubbles:true}));\n"
                + "         try{if(window.jQuery&&window.jQuery.fn)window.jQuery(mi).trigger('input').trigger('propertychange');}catch(e){}\n"
                + "        }\n"
                + "       }catch(e){}\n"
                + "       st({step:'filled'});\n"
                + "       unlock(d,aid);\n"
                + "       return;\n"
                + "      }\n"
                + "      var inp=by[String(i2+1)];\n"
                + "      if(!inp)return;\n"
                + "      var ch=code.charAt(i2);\n"
                + "      try{inp.focus();}catch(e){}\n"
                + "      try{Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set.call(inp,ch);}catch(e){inp.value=ch;}\n"
                + "      try{inp.dispatchEvent(new Event('input',{bubbles:true}));}catch(e){}\n"
                + "      try{inp.dispatchEvent(new Event('change',{bubbles:true}));}catch(e){}\n"
                + "      try{if(window.jQuery&&window.jQuery.fn)window.jQuery(inp).trigger('input');}catch(e){}\n"
                + "      i2++;\n"
                + "      setTimeout(fillNext,250);\n"
                + "     }\n"
                + "     fillNext();\n"
                + "    }\n"
                + "   };\n"
                + "   x.open('GET','/newsign/gesture?activeId='+encodeURIComponent(aid)+'&type=0',true);\n"
                + "   x.send();\n"
                + "   return 'armed';\n"
                + "  }\n"
                + "  if(++tries<10){setTimeout(boot,600);return 'pending';}\n"
                + "  return 'not-found';\n"
                + " }\n"
                + " return boot();\n"
                + "})();\n";
    }

    private double[] calculateTriangulation(List<LocationPoint> points) {
        if (points.size() < 3) return null;

        LocationPoint p1 = points.get(0);
        LocationPoint p2 = points.get(1);
        LocationPoint p3 = points.get(2);

        double R = 6378137.0;
        double lat0 = p1.lat * Math.PI / 180.0;

        double x1 = 0, y1 = 0;
        double r1 = p1.distance;

        double x2 = (p2.lon - p1.lon) * (Math.PI / 180.0) * R * Math.cos(lat0);
        double y2 = (p2.lat - p1.lat) * (Math.PI / 180.0) * R;
        double r2 = p2.distance;

        double x3 = (p3.lon - p1.lon) * (Math.PI / 180.0) * R * Math.cos(lat0);
        double y3 = (p3.lat - p1.lat) * (Math.PI / 180.0) * R;
        double r3 = p3.distance;

        double A = 2 * x2;
        double B = 2 * y2;
        double C = r1 * r1 - r2 * r2 + x2 * x2 + y2 * y2;

        double D = 2 * x3;
        double E = 2 * y3;
        double F = r1 * r1 - r3 * r3 + x3 * x3 + y3 * y3;

        double det = A * E - B * D;
        if (Math.abs(det) < 1.0) return null;

        double targetX = (C * E - B * F) / det;
        double targetY = (A * F - C * D) / det;

        double targetLon = p1.lon + (targetX / (R * Math.cos(lat0) * (Math.PI / 180.0)));
        double targetLat = p1.lat + (targetY / (R * (Math.PI / 180.0)));

        return new double[]{targetLat, targetLon};
    }

    private String generateRandomDeviceFlag() {
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

    private static String defaultConfigTemplate() {
        return "是否开启定位修改: false\n经度: \n纬度: \n是否开启地址名修改: false\n地址名: \n是否开启名字修改: false\n名字: \n是否开启随机指纹: true\n是否开启经纬度爆破: false\n是否开启手势自动签到: false\n是否开启签到码自动签到: false\n是否开启考试风控拦截: true\n是否开启复制限制解除: true\n是否开启考试截图替换: false\n截图替换路径: " + FAKE_UPLOAD_FILE_PATH + "\n";
    }

    private SignConfig getSignConfig() {
        // 核心修复：防止底层死循环读取
        if (Boolean.TRUE.equals(READING_CONFIG.get())) {
            return cachedConfig != null ? cachedConfig : new SignConfig();
        }

        if (cachedConfig != null && (System.currentTimeMillis() - lastReadTime < 3000)) return cachedConfig;

        READING_CONFIG.set(true);
        try {
            SignConfig config = new SignConfig();
            // 走 ConfigStorage.read（宿主私有目录，上游实现）；读取异常由 catch 兜底默认配置
            String text = ConfigStorage.read(AndroidAppHelper.currentApplication(), defaultConfigTemplate());
            for (String line : text.split("\\r?\\n")) {
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
            cachedConfig = config;
            lastReadTime = System.currentTimeMillis();
            return config;

        } catch (Exception e) {
            debugLog("config-read FAILED: " + e);
            return cachedConfig != null ? cachedConfig : new SignConfig();
        } finally {
            READING_CONFIG.set(false);
        }
    }

    private boolean parseBooleanValue(String line) {
        try { return "true".equalsIgnoreCase(line.substring(line.indexOf(":") + 1).trim()); } catch (Exception e) { return false; }
    }

    private String parseStringValue(String line) {
        try { return line.substring(line.indexOf(":") + 1).trim(); } catch (Exception e) { return ""; }
    }
}