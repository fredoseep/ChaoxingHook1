package com.fredoseep.chaoxinghook;

import android.util.Log;
import java.lang.reflect.Method;
import java.util.List;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.luckypray.dexkit.DexKitBridge;
import static com.fredoseep.chaoxinghook.HookReflection.*;

/** 聊天推送会话过滤及撤回消息处理。 */
final class ChatHook {
    private static final String TAG_CHAT_FILTER = "CX.ChatFilter";

    static void install(DexKitBridge bridge, ClassLoader loader) {
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
                                if (removed > 0) Log.d(TAG_CHAT_FILTER, "chat-filter: 移除 " + removed + " 个 type==20 会话");
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
                Log.e(TAG_CHAT_FILTER, "Hook[chat-filter]: manager 类未找到");
            }
        } catch (Throwable t) { Log.e(TAG_CHAT_FILTER, "core-group-6 FAILED: ", t); }

        // 7. EMCmdMessageBody.action()：环信 SDK 库类（第三方库不混淆，保持硬编码）
        try {
            XposedBridge.hookAllMethods(XposedHelpers.findClass(ObfuscationMap.CLASS_EM_CMD_MESSAGE_BODY, loader), ObfuscationMap.METHOD_EM_CMD_ACTION, new XC_MethodHook() { @Override protected void afterHookedMethod(MethodHookParam param) { Object result = param.getResult(); if (result != null && "REVOKE_FLAG".equals(result.toString())) { param.setResult("BLOCK_REVOKE_FLAG"); } } });
        } catch (Throwable t) {}
    }
}
