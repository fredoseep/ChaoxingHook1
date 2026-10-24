package com.fredoseep.chaoxinghook;

import android.util.Log;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ClipboardPopKiller {
    private static final String TAG = "CX.Clipboard";

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam){
        hook(lpparam.classLoader);
    }

    public static void hook(ClassLoader loader) {
        XposedHelpers.findAndHookMethod("com.chaoxing.mobile.main.clipboard.ClipboardChangeMonitor", loader, "m", "android.app.Activity", XC_MethodReplacement.DO_NOTHING);
        Log.i(TAG, "剪贴板弹窗拦截已安装");
    }
}