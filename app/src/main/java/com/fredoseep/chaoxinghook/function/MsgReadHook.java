package com.fredoseep.chaoxinghook.function;

import android.util.Log;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;

public class MsgReadHook {
    private static final String TAG = "MsgReadHook";

    public static void hook(ClassLoader classLoader){
        try {
            Log.d(TAG,"injected");
            XposedHelpers.findAndHookMethod("com.hyphenate.chat.EMChatManager", classLoader, "ackMessageRead", "java.lang.String", "java.lang.String", XC_MethodReplacement.DO_NOTHING);
        } catch (Exception e) {
            Log.e(TAG,"Error 1 "+e.getMessage());
        }
    }
}
