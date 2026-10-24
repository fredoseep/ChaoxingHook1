package com.fredoseep.chaoxinghook;

import android.util.Log;
import android.webkit.WebView;

import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class QRTesting {

    private static final String TAG = "CX.QRSign";


    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        Log.d(TAG, "二维码签到测试入口执行（当前无 Hook 实现）");
        try {

        } catch (Exception e) {
            Log.e(TAG, "二维码签到测试失败", e);
        }

    }

}