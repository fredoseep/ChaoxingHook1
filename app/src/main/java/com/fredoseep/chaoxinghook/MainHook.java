package com.fredoseep.chaoxinghook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MainHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.chaoxing.mobile")) {
            return;
        }

        try {
            Class<?> splashViewModelClass = XposedHelpers.findClass(
                    "com.chaoxing.mobile.activity.SplashViewModel",
                    lpparam.classLoader
            );

            XposedBridge.hookAllMethods(splashViewModelClass, "a", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(null);
                    XposedBridge.log("Chaoxing AdSkip: a 执行完毕，成功将返回值劫持为 null");
                }
            });

        } catch (Throwable t) {
            XposedBridge.log("Chaoxing AdSkip Error: " + t.getMessage());
        }
        try {
            Class<?> homePageHeaderClass = XposedHelpers.findClass(
                    "com.chaoxing.mobile.study.home.mainpage.view.HomePageHeader",
                    lpparam.classLoader
            );

            // 2. 拦截设置 Banner 数据的方法 'g'
            XposedBridge.hookAllMethods(homePageHeaderClass, "g", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length > 0) {
                        param.args[0] = null;
                        XposedBridge.log("Chaoxing AdSkip: 成功拦截 HomePageHeader.g()，Banner 数据已置空，触发官方隐藏逻辑！");
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("Chaoxing AdSkip Error (HomePageHeader): " + t.getMessage());
        }
        try {
            Class<?> categoryHolderClass = XposedHelpers.findClass(
                    "com.chaoxing.mobile.study.home.mainpage2.adapter.viewholder.MainRecordCategoryHolder",
                    lpparam.classLoader
            );

            XposedBridge.hookAllMethods(categoryHolderClass, "o", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object viewHolder = param.thisObject;

                    android.widget.TextView tvLeft = (android.widget.TextView) XposedHelpers.getObjectField(viewHolder, "c");

                    if (tvLeft != null && tvLeft.getText() != null) {
                        String title = tvLeft.getText().toString();

                        if (title.contains("推荐")||title.contains("Recommend")) {
                            android.view.View itemView = (android.view.View) XposedHelpers.getObjectField(viewHolder, "itemView");

                            if (itemView != null) {
                                itemView.setVisibility(android.view.View.GONE);
                                android.view.ViewGroup.LayoutParams layoutParams = itemView.getLayoutParams();
                                layoutParams.height = 0;
                                layoutParams.width = 0;
                                itemView.setLayoutParams(layoutParams);

                                XposedBridge.log("Chaoxing AdSkip: 成功隐藏首页的 [推荐] 标题栏");
                            }
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("Chaoxing AdSkip Error (CategoryHolder): " + t.getMessage());
        }
        try {
            Class<?> adapterClass = XposedHelpers.findClass(
                    "com.chaoxing.mobile.study.home.mainpage2.adapter.MainPageRecordAdapter",
                    lpparam.classLoader
            );

            XposedBridge.hookAllMethods(adapterClass, "getItemCount", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        java.util.List<?> listA = (java.util.List<?>) XposedHelpers.getObjectField(param.thisObject, "a");
                        if (listA != null) {
                            param.setResult(listA.size());
                        }
                    } catch (NoSuchFieldError e) {
                        java.util.List<?> listA = (java.util.List<?>) XposedHelpers.getObjectField(param.thisObject, "f82688a");
                        if (listA != null) {
                            param.setResult(listA.size());
                        }
                    }
                }
            });

            XposedBridge.log("Chaoxing AdSkip: 成功拦截 MainPageRecordAdapter.getItemCount，第一页帖子已清空！");

        } catch (Throwable t) {
            XposedBridge.log("Chaoxing AdSkip Error (Adapter): " + t.getMessage());
        }
        try {
            Class<?> dbQueryClass = XposedHelpers.findClass("lo.b0", lpparam.classLoader);

            XposedBridge.hookAllMethods(dbQueryClass, "W", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length == 3 && param.args[2] instanceof Integer) {
                        int recentLimit = (Integer) param.args[2];
                        if (recentLimit == 3) {
                            param.args[2] = 15;
                            XposedBridge.log("Chaoxing AdSkip: 成功拦截 lo.b0.W，最近使用记录数量已从 3 提升至 10！");
                        }
                    }
                }
            });

        } catch (Throwable t) {
            XposedBridge.log("Chaoxing AdSkip Error (Record Limit): " + t.getMessage());
        }
    }
}