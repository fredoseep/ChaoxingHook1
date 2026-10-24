package com.fredoseep.chaoxinghook;

import android.util.Log;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.matchers.MethodsMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.ClassDataList;

/** DexKit 匹配、反射查找及 Hook 安装共用工具；日志归属到对应功能。 */
final class HookReflection {
    private static final String TAG_SPLASH = "CX.Splash";
    private static final String TAG_HOME_HEADER = "CX.HomeHeader";
    private static final String TAG_CATEGORY = "CX.Category";
    private static final String TAG_RECORD_LIST = "CX.RecordList";
    private static final String TAG_DB_QUERY = "CX.DbQuery";
    private static final String TAG_CHAT_FILTER = "CX.ChatFilter";
    private static String featureLogTag(String feature) {
        if (feature.startsWith("splash")) return TAG_SPLASH;
        if (feature.startsWith("home-header")) return TAG_HOME_HEADER;
        if (feature.startsWith("category-holder")) return TAG_CATEGORY;
        if (feature.startsWith("record-adapter")) return TAG_RECORD_LIST;
        if (feature.startsWith("db-query")) return TAG_DB_QUERY;
        if (feature.startsWith("chat-filter")) return TAG_CHAT_FILTER;
        return "CX.DexKit";
    }

    static Class<?> findClassByMethods(DexKitBridge bridge, ClassLoader loader,
                                               String tag, String className, String returnType, String... paramTypes) {
        return findClassByMethodsImpl(bridge, loader, tag, className, false, returnType, paramTypes);
    }

    static Class<?> findClassByMethodsEverywhere(DexKitBridge bridge, ClassLoader loader,
                                                         String tag, String className, String returnType, String... paramTypes) {
        return findClassByMethodsImpl(bridge, loader, tag, className, true, returnType, paramTypes);
    }

    static Class<?> findClassByMethodsImpl(DexKitBridge bridge, ClassLoader loader,
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
            Log.d(featureLogTag(tag), "Chaoxing DexKit[" + tag + "]: " + clazz.getName());
            return clazz;
        } catch (Throwable t) {
            Log.e(featureLogTag(tag), "DexKit[" + tag + "] 结构匹配失败: " + t.getMessage());
        }
        if (className != null) {
            try {
                Class<?> clazz = XposedHelpers.findClassIfExists(className, loader);
                if (clazz != null) {
                    Log.i(featureLogTag(tag), "Chaoxing DexKit[" + tag + "]: fallback class " + clazz.getName());
                    return clazz;
                }
            } catch (Throwable ignored) {}
            Log.e(featureLogTag(tag), "DexKit[" + tag + "] 回退类不存在: " + className);
        }
        return null;
    }

    static Method findMethodBySignature(Class<?> clazz, String returnType, String... paramTypes) {
        List<Method> all = findMethodsBySignature(clazz, returnType, paramTypes);
        return all.isEmpty() ? null : all.get(0);
    }

    static List<Method> findMethodsBySignature(Class<?> clazz, String returnType, String... paramTypes) {
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

    static void hookMethodSafe(Method method, XC_MethodHook callback, String tag) {
        if (method == null) {
            Log.e(featureLogTag(tag), "Hook[" + tag + "]: 方法未找到（类名或签名已变化）");
            return;
        }
        try {
            XposedBridge.hookMethod(method, callback);
            Log.i(featureLogTag(tag), "Chaoxing DexKit[" + tag + "]: hooked " + method.toGenericString());
        } catch (Throwable t) {
            Log.e(featureLogTag(tag), "Chaoxing DexKit[" + tag + "]: hook failed " + t);
        }
    }

    static android.widget.TextView findTextViewField(Object obj, String preferredName) {
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

    static java.util.List<?> findListField(Object obj, String[] preferredNames) {
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
}
