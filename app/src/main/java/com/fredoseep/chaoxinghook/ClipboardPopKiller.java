package com.fredoseep.chaoxinghook;

import android.util.Log;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ClipboardPopKiller {
    private static final String TAG = "clipboardPopKiller";

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam){
        Log.d(TAG,"injected");
        XposedHelpers.findAndHookMethod("com.chaoxing.mobile.main.clipboard.ClipboardChangeMonitor", lpparam.classLoader, "m", "android.app.Activity", XC_MethodReplacement.DO_NOTHING);
    }
}
