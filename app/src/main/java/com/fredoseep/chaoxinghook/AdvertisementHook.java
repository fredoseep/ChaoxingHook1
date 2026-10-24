package com.fredoseep.chaoxinghook;

import android.util.Log;
import java.lang.reflect.Method;
import de.robv.android.xposed.XC_MethodHook;
import org.luckypray.dexkit.DexKitBridge;
import static com.fredoseep.chaoxinghook.HookReflection.*;

/** 开屏广告及首页头部广告过滤。 */
final class AdvertisementHook {
    private static final String TAG_SPLASH = "CX.Splash";
    private static final String TAG_HOME_HEADER = "CX.HomeHeader";

    static void install(DexKitBridge bridge, ClassLoader loader) {
        // 1. SplashViewModel.a(Activity) -> Ad：拦截开屏广告数据（返回 null 使广告不展示）
        try {
            Class<?> clazz = findClassByMethods(bridge, loader, "splash",
                    ObfuscationMap.CLASS_SPLASH_VIEW_MODEL, null, "android.app.Activity");
            Method m = findMethodBySignature(clazz, null, "android.app.Activity");
            hookMethodSafe(m, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) { param.setResult(null); }
            }, "splash.a");
        } catch (Throwable t) { Log.e(TAG_SPLASH, "core-group-1 FAILED: ", t); }

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
        } catch (Throwable t) { Log.e(TAG_HOME_HEADER, "core-group-2 FAILED: ", t); }
    }
}
