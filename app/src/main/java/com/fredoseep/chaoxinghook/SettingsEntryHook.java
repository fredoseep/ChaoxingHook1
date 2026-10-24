package com.fredoseep.chaoxinghook;

import android.app.AndroidAppHelper;
import android.util.Log;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** 我的页面长按设置入口：生命周期、文本绑定和布局观察。 */
final class SettingsEntryHook {
    private static final String TAG_SETTINGS = "CX.Settings";
    private static final java.util.WeakHashMap<android.view.View, Boolean> injectedLongPressRows = new java.util.WeakHashMap<>();

    // 值只持有根视图弱引用，避免 WeakHashMap 的值反向保活 Activity。
    private final java.util.WeakHashMap<android.view.View, SettingsEntryWatcher> settingsEntryWatchers =
            new java.util.WeakHashMap<>();

    /** 生命周期、文字绑定、布局变化共同覆盖首次创建和底部 Tab 切换。 */
    void install(ClassLoader loader) {
        int installed = 0;
        try {
            int count = XposedBridge.hookAllMethods(android.app.Activity.class, "onResume", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    android.app.Activity activity = (android.app.Activity) param.thisObject;
                    if (activity.getWindow() != null) {
                        watchSettingsEntry(activity.getWindow().getDecorView(), "Activity.onResume");
                    }
                }
            }).size();
            installed += count;
            Log.i(TAG_SETTINGS, "Activity.onResume 实际 Hook 数=" + count);
        } catch (Throwable t) { Log.e(TAG_SETTINGS, "Activity 生命周期入口安装失败", t); }
        try {
            int count = XposedBridge.hookAllMethods(android.widget.TextView.class, "setText", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    android.widget.TextView tv = (android.widget.TextView) param.thisObject;
                    CharSequence text = tv.getText();
                    if (text == null || !"设置".equals(text.toString().trim())) return;
                    // setText 可能发生在 addView 之前，下一轮主线程再获取根视图。
                    tv.post(() -> watchSettingsEntry(tv.getRootView(), "TextView.setText(设置)"));
                }
            }).size();
            installed += count;
            Log.i(TAG_SETTINGS, "TextView.setText 实际 Hook 数=" + count);
        } catch (Throwable t) { Log.e(TAG_SETTINGS, "设置文本绑定入口安装失败", t); }
        try {
            Class<?> frag = XposedHelpers.findClassIfExists("com.chaoxing.study.mine.MineFragment2", loader);
            int count = frag == null ? 0 : XposedBridge.hookAllMethods(frag, "onViewCreated", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length > 0 && param.args[0] instanceof android.view.View) {
                        watchSettingsEntry((android.view.View) param.args[0], "MineFragment2.onViewCreated");
                    }
                }
            }).size();
            installed += count;
            Log.i(TAG_SETTINGS, "MineFragment2.onViewCreated 实际 Hook 数=" + count
                    + "（0 表示类或声明方法未匹配，使用其他入口）");
        } catch (Throwable t) { Log.e(TAG_SETTINGS, "我的页面精准入口安装失败", t); }
        if (installed == 0) throw new IllegalStateException("未安装任何设置入口 Hook");
        android.app.Application current = AndroidAppHelper.currentApplication();
        // 已有前台页会由文本绑定/后续 onResume 覆盖，此处不使用 Application Context 打开窗口。
        Log.i(TAG_SETTINGS, "设置入口观察已安装，总 Hook 数=" + installed
                + "，等待实际控件匹配；Application=" + (current != null));
    }

    private void watchSettingsEntry(android.view.View root, String source) {
        if (root == null) return;
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            root.post(() -> watchSettingsEntry(root, source));
            return;
        }
        SettingsEntryWatcher watcher = settingsEntryWatchers.get(root);
        if (watcher == null) {
            watcher = new SettingsEntryWatcher(root, source);
            settingsEntryWatchers.put(root, watcher);
            root.getViewTreeObserver().addOnGlobalLayoutListener(watcher);
            Log.d(TAG_SETTINGS, "开始观察设置入口 source=" + source + " root=" + root.getClass().getName());
        }
        watcher.schedule();
    }

    private final class SettingsEntryWatcher implements android.view.ViewTreeObserver.OnGlobalLayoutListener {
        private final java.lang.ref.WeakReference<android.view.View> rootRef;
        private final String source;
        private boolean pending;
        private int lastMatches = -1;
        SettingsEntryWatcher(android.view.View root, String source) {
            rootRef = new java.lang.ref.WeakReference<>(root);
            this.source = source;
        }
        @Override public void onGlobalLayout() { schedule(); }
        void schedule() {
            android.view.View root = rootRef.get();
            if (root == null || pending) return;
            pending = true;
            root.postDelayed(() -> {
                pending = false;
                android.view.View view = rootRef.get();
                if (view == null) return;
                try {
                    int matches = injectSettingsLongPressRecursive(view);
                    if (matches != lastMatches) {
                        Log.d(TAG_SETTINGS, "设置入口扫描 source=" + source + " 可见匹配数=" + matches);
                        lastMatches = matches;
                    }
                } catch (Throwable t) { Log.e(TAG_SETTINGS, "扫描设置入口失败", t); }
            }, 300);
        }
    }

    private static String settingsViewId(android.view.View view) {
        if (view.getId() == android.view.View.NO_ID) return "no-id";
        try { return view.getResources().getResourceEntryName(view.getId()); }
        catch (RuntimeException ignored) { return Integer.toHexString(view.getId()); }
    }

    /** 优先 cv_settings；否则只选择确实可点击的最近祖先，找不到时保留文字自身。 */
    private void injectSettingsLongPress(android.view.View candidate) {
        android.view.View row = candidate;
        android.view.View cursor = candidate;
        android.view.View nearestClickable = candidate.isClickable() ? candidate : null;
        for (int i = 0; i < 8; i++) {
            if ("cv_settings".equals(settingsViewId(cursor))) { row = cursor; break; }
            if (nearestClickable == null && cursor.isClickable()) nearestClickable = cursor;
            if (!(cursor.getParent() instanceof android.view.View) || i == 7) {
                row = nearestClickable != null ? nearestClickable : candidate;
                break;
            }
            cursor = (android.view.View) cursor.getParent();
        }
        if (injectedLongPressRows.containsKey(row)) return;
        row.setOnLongClickListener(v -> {
            Log.i(TAG_SETTINGS, "收到设置行长按 id=" + settingsViewId(v)
                    + " context=" + v.getContext().getClass().getName());
            try {
                EmbeddedSettings.show(v.getContext());
                return true;
            } catch (Throwable t) {
                Log.e(TAG_SETTINGS, "长按打开设置面板失败", t);
                return false;
            }
        });
        // 成功安装后才标记，失败可在后续布局中重试。
        injectedLongPressRows.put(row, Boolean.TRUE);
        Log.i(TAG_SETTINGS, "已绑定设置行长按 id=" + settingsViewId(row)
                + " class=" + row.getClass().getName() + " clickable=" + row.isClickable()
                + " longClickable=" + row.isLongClickable());
    }

    private int injectSettingsLongPressRecursive(android.view.View view) {
        if (view.getVisibility() != android.view.View.VISIBLE) return 0;
        if ("cv_settings".equals(settingsViewId(view))) {
            injectSettingsLongPress(view);
            return 1;
        }
        if (view instanceof android.widget.TextView) {
            CharSequence text = ((android.widget.TextView) view).getText();
            if (text != null && "设置".equals(text.toString().trim())) {
                injectSettingsLongPress(view);
                return 1;
            }
        }
        int matches = 0;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                matches += injectSettingsLongPressRecursive(group.getChildAt(i));
            }
        }
        return matches;
    }
}
