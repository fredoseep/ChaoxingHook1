package com.fredoseep.chaoxinghook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.*;
import android.view.View;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Native host dialog: no module Activity, Compose, resources, maps SDK or su required. */
public final class EmbeddedSettings {
    private static final String[] KEYS = {
        "是否开启定位修改", "经度", "纬度", "是否开启地址名修改", "地址名",
        "是否开启名字修改", "名字", "是否开启随机指纹", "是否开启经纬度爆破",
        "是否开启手势自动签到", "是否开启签到码自动签到",
        "是否开启考试风控拦截", "是否开启复制限制解除", "是否开启考试截图替换", "截图替换路径"
    };
    private static final String[] DEFAULTS = {
        "false", "", "", "false", "", "false", "", "true", "false", "false", "false",
        "true", "true", "false",
        "/storage/emulated/0/Download/fake_exam_image.png"
    };
    private static final int REQUEST = 0x6C58;
    private interface ResultAction { void run(Uri uri) throws Exception; }
    private static final Map<Activity, ResultAction> PENDING = new WeakHashMap<>();
    private final Activity activity;
    private final Runnable onClosed;
    private final Map<String, View> fields = new LinkedHashMap<>();
    private AlertDialog dialog;
    private boolean readable;
    private EmbeddedSettings(Activity activity, Runnable onClosed) {
        this.activity = activity;
        this.onClosed = onClosed;
    }
    public static void show(Context context) { show(context, null); }
    public static void show(Context context, Runnable onClosed) {
        Activity activity = findActivity(context);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            Toast.makeText(context, "请在学习通前台页面打开设置", Toast.LENGTH_LONG).show();
            return;
        }
        activity.runOnUiThread(() -> new EmbeddedSettings(activity, onClosed).open());
    }
    private static Activity findActivity(Context context) {
        Set<Context> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (context != null && seen.add(context)) {
            if (context instanceof Activity) return (Activity) context;
            if (!(context instanceof ContextWrapper)) break;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
    private static String defaults() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < KEYS.length; i++) text.append(KEYS[i]).append(": ").append(DEFAULTS[i]).append('\n');
        return text.toString();
    }
    private static Map<String, String> parse(String text, boolean validate) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < KEYS.length; i++) values.put(KEYS[i], DEFAULTS[i]);
        int recognized = 0;
        for (String line : text.split("\\r?\\n")) {
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (!values.containsKey(key)) continue;
            if (validate && key.startsWith("是否") && !value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
                throw new IOException("开关值必须为 true 或 false：" + key);
            values.put(key, value);
            recognized++;
        }
        if (validate && recognized == 0) throw new IOException("不是本模块的配置文件");
        return values;
    }
    private void open() {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        body.setPadding(pad, pad, pad, pad);
        TextView intro = new TextView(activity);
        intro.setText("配置保存在学习通私有目录，约 3 秒后生效。\n模块 App 需 Root 授权后可直接读写这份配置。\n经纬度可直接输入；图片请用下方系统选择器导入。\n" + ConfigStorage.file(activity).getPath());
        body.addView(intro);
        for (String key : KEYS) {
            if (key.startsWith("是否")) {
                Switch toggle = new Switch(activity);
                toggle.setText(key);
                body.addView(toggle);
                fields.put(key, toggle);
            } else {
                TextView label = new TextView(activity);
                label.setText(key);
                body.addView(label);
                EditText edit = new EditText(activity);
                edit.setSingleLine(true);
                body.addView(edit);
                fields.put(key, edit);
            }
        }
        reload();
        // 定位修改 / 经纬度爆破 互斥（与模块 App 设置页一致）：开启一个自动关掉另一个。
        // 必须在 reload() 之后挂监听，否则载入「两者同开」的历史配置时会互相清掉。
        Switch modifyLocSwitch = (Switch) fields.get("是否开启定位修改");
        Switch autoCalcSwitch = (Switch) fields.get("是否开启经纬度爆破");
        if (modifyLocSwitch != null && autoCalcSwitch != null) {
            modifyLocSwitch.setOnCheckedChangeListener((btn, on) -> { if (on) autoCalcSwitch.setChecked(false); });
            autoCalcSwitch.setOnCheckedChangeListener((btn, on) -> { if (on) modifyLocSwitch.setChecked(false); });
        }
        button(body, "导入配置文件", () -> choose("text/*", false, uri -> {
            String text;
            try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("无法打开配置文件");
                text = ConfigStorage.readText(in);
            }
            parse(text, true); // Validate before touching the existing file.
            ConfigStorage.write(activity, text);
            reload();
            toast("配置已导入");
        }));
        button(body, "导出当前配置", () -> {
            final String text = serializeFields();
            choose("text/plain", true, uri -> {
                try (OutputStream out = activity.getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new IOException("无法写入文档");
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                }
                toast("配置已导出；导出不会自动保存当前修改");
            });
        });
        button(body, "选择图片并复制到当前应用", () -> choose("image/*", false, uri -> {
            File image = copyImage(uri);
            ((EditText) fields.get("截图替换路径")).setText(image.getAbsolutePath());
            toast("图片已导入，请点击保存");
        }));
        button(body, "重新读取已保存配置", this::reload);
        button(body, "恢复默认配置", () -> new AlertDialog.Builder(activity)
            .setMessage("确认覆盖当前应用的配置？")
            .setNegativeButton("取消", null)
            .setPositiveButton("重置", (d, w) -> {
                try { ConfigStorage.write(activity, defaults()); reload(); toast("配置已重置"); }
                catch (Exception e) { error(e); }
            }).show());
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(body);
        dialog = new AlertDialog.Builder(activity).setTitle("ChaoxingHook 设置")
            .setView(scroll).setNegativeButton("关闭", null).setPositiveButton("保存", null).create();
        dialog.setOnDismissListener(d -> {
            synchronized (PENDING) { PENDING.remove(activity); }
            if (onClosed != null && !activity.isDestroyed()) onClosed.run();
        });
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!readable) { toast("未成功读取原配置，请重读或显式导入／重置"); return; }
            try { ConfigStorage.write(activity, serializeFields()); toast("配置已保存"); }
            catch (Exception e) { error(e); }
        }));
        dialog.show();
    }
    private void reload() {
        try {
            Map<String, String> values = parse(ConfigStorage.read(activity, defaults()), true);
            for (String key : KEYS) {
                View field = fields.get(key);
                if (field instanceof Switch) ((Switch) field).setChecked(Boolean.parseBoolean(values.get(key)));
                else ((EditText) field).setText(values.get(key));
            }
            readable = true;
        } catch (Exception e) { readable = false; error(e); }
    }

    private String serializeFields() {
        StringBuilder text = new StringBuilder();
        for (String key : KEYS) {
            View field = fields.get(key);
            String value = field instanceof Switch ? Boolean.toString(((Switch) field).isChecked())
                : ((EditText) field).getText().toString().replace("\r", "").replace("\n", "");
            text.append(key).append(": ").append(value).append('\n');
        }
        return text.toString();
    }
    private void button(LinearLayout body, String title, Runnable action) {
        Button button = new Button(activity);
        button.setText(title);
        button.setOnClickListener(v -> { try { action.run(); } catch (Exception e) { error(e); } });
        body.addView(button);
    }
    private void choose(String mime, boolean create, ResultAction action) {
        try {
            if ("com.chaoxing.mobile".equals(activity.getPackageName())) ResultHooks.install(activity.getClass());
            synchronized (PENDING) {
                if (PENDING.containsKey(activity)) { toast("已有文件选择操作，请先完成"); return; }
                PENDING.put(activity, action);
            }
            Intent intent = new Intent(create ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(mime);
            if (create) intent.putExtra(Intent.EXTRA_TITLE, "chaoxing_loc.txt");
            activity.startActivityForResult(intent, REQUEST);
        } catch (Throwable e) {
            synchronized (PENDING) { PENDING.remove(activity); }
            toast("无法打开系统文件选择器：" + e.getMessage());
        }
    }
    /** Companion calls this directly; host only intercepts our request while one is pending. */
    public static boolean onActivityResult(Activity activity, int request, int result, Intent data) {
        if (request != REQUEST) return false;
        ResultAction action;
        synchronized (PENDING) { action = PENDING.remove(activity); }
        if (action == null) return false;
        if (result == Activity.RESULT_OK && data != null && data.getData() != null) {
            try { action.run(data.getData()); }
            catch (Exception e) { Toast.makeText(activity, "文件操作失败：" + e.getMessage(), Toast.LENGTH_LONG).show(); }
        }
        return true;
    }
    // Loaded only inside Xposed host; the independent APK has no runtime Xposed dependency.
    private static final class ResultHooks {
        private static final Set<Method> HOOKED = new HashSet<>();
        static synchronized void install(Class<?> activityClass) throws Exception {
            Class<?> current = activityClass;
            while (current != null && Activity.class.isAssignableFrom(current)) {
                try {
                    Method method = current.getDeclaredMethod("onActivityResult", int.class, int.class, Intent.class);
                    if (!HOOKED.contains(method)) {
                        de.robv.android.xposed.XposedBridge.hookMethod(method, new de.robv.android.xposed.XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam param) {
                                if (EmbeddedSettings.onActivityResult((Activity) param.thisObject,
                                    (Integer) param.args[0], (Integer) param.args[1], (Intent) param.args[2])) param.setResult(null);
                            }
                        });
                        HOOKED.add(method);
                    }
                } catch (NoSuchMethodException ignored) { }
                current = current.getSuperclass();
            }
        }
    }
    private File copyImage(Uri uri) throws IOException {
        File dir = new File(activity.getFilesDir(), "chaoxinghook_images");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("无法创建图片目录");
        File tmp = File.createTempFile("picked-", ".tmp", dir);
        try {
            try (InputStream in = activity.getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(tmp)) {
                if (in == null) throw new IOException("无法读取图片");
                byte[] buffer = new byte[8192];
                int total = 0, n;
                while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > 32 * 1024 * 1024) throw new IOException("图片超过 32MB");
                    out.write(buffer, 0, n);
                }
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(tmp.getPath(), options);
            if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("无效图片");
            String ext = "image/jpeg".equals(options.outMimeType) ? ".jpg"
                : "image/webp".equals(options.outMimeType) ? ".webp"
                : "image/gif".equals(options.outMimeType) ? ".gif" : ".png";
            File target = new File(dir, tmp.getName().replace(".tmp", ext));
            if (!tmp.renameTo(target)) throw new IOException("保存图片失败");
            return target;
        } finally { if (tmp.exists()) tmp.delete(); }
    }
    private void toast(String text) { Toast.makeText(activity, text, Toast.LENGTH_LONG).show(); }
    private void error(Exception e) { toast("配置操作失败：" + e.getMessage()); }
}
