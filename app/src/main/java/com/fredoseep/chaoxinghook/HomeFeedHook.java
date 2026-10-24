package com.fredoseep.chaoxinghook;

import android.util.Log;
import java.lang.reflect.Method;
import java.util.List;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import org.luckypray.dexkit.DexKitBridge;
import static com.fredoseep.chaoxinghook.HookReflection.*;

/** 首页推荐分类、列表计数和数据库分页处理。 */
final class HomeFeedHook {
    private static final String TAG_CATEGORY = "CX.Category";
    private static final String TAG_RECORD_LIST = "CX.RecordList";
    private static final String TAG_DB_QUERY = "CX.DbQuery";

    static void install(DexKitBridge bridge, ClassLoader loader) {
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
        } catch (Throwable t) { Log.e(TAG_CATEGORY, "core-group-3 FAILED: ", t); }

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
        } catch (Throwable t) { Log.e(TAG_RECORD_LIST, "core-group-4 FAILED: ", t); }

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
            Log.d(TAG_DB_QUERY, "db-query: clazz=" + (clazz != null ? clazz.getName() : "null")
                    + " matchedMethods=" + methods.size());
            for (Method m : methods) {
                hookMethodSafe(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (param.args.length == 3 && param.args[2] instanceof Integer) {
                            int pageSize = (Integer) param.args[2];
                            if (pageSize == 3) {
                                param.args[2] = 15;
                                Log.d(TAG_DB_QUERY, "db-query." + param.method.getName() + ": pageSize 3 -> 15");
                            }
                        }
                    }
                }, "db-query." + m.getName());
            }
        } catch (Throwable t) { Log.e(TAG_DB_QUERY, "core-group-5 FAILED: ", t); }
    }
}
