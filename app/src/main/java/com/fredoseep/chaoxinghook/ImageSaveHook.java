package com.fredoseep.chaoxinghook;

import android.util.Log;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** WebView 图片长按菜单与图片下载。 */
final class ImageSaveHook {
    private static final String TAG_IMAGE_SAVE = "CX.ImageSave";
    static void attach(final android.webkit.WebView webView) {
        // 覆盖可能存在的原有长按逻辑
        webView.setOnLongClickListener(new android.view.View.OnLongClickListener() {
            @Override
            public boolean onLongClick(android.view.View v) {
                // 探针：获取手指按压位置的内容类型
                final android.webkit.WebView.HitTestResult result = webView.getHitTestResult();
                if (result != null) {
                    int type = result.getType();

                    // 如果长按的是 纯图片 或 带有链接的图片
                    if (type == android.webkit.WebView.HitTestResult.IMAGE_TYPE ||
                            type == android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {

                        final String imageUrl = result.getExtra();
                        if (imageUrl != null && imageUrl.startsWith("http")) {

                            // 呼叫 Android 原生系统弹窗
                            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(webView.getContext());
                            builder.setTitle("chaoxingHook: ");
                            builder.setMessage("锁定目标图片，是否执行下载？\n\n" + imageUrl);
                            builder.setPositiveButton("立即下载", new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(android.content.DialogInterface dialog, int which) {
                                    // 用户点击下载，开启后台线程拉取文件
                                    new Thread(() -> {
                                        try {
                                            File dir = new File("/storage/emulated/0/Download/ChaoxingExam");
                                            if (!dir.exists()) dir.mkdirs();

                                            String fileName = "IMG_" + System.currentTimeMillis() + ".png";
                                            String[] parts = imageUrl.split("/");
                                            if (parts.length > 5) {
                                                fileName = parts[parts.length - 2] + "_" + parts[parts.length - 1];
                                                if (fileName.contains("?")) fileName = fileName.substring(0, fileName.indexOf("?"));
                                            }

                                            File saveFile = new File(dir, fileName);

                                            URL targetUrl = new URL(imageUrl);
                                            HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();
                                            conn.setRequestMethod("GET");
                                            InputStream is = conn.getInputStream();
                                            java.io.FileOutputStream fos = new java.io.FileOutputStream(saveFile);
                                            byte[] buf = new byte[4096];
                                            int len;
                                            while ((len = is.read(buf)) != -1) fos.write(buf, 0, len);
                                            fos.close();
                                            is.close();

                                            // 可选：在这里可以用 Looper 抛出一个 Toast 提示下载完成
                                            Log.i(TAG_IMAGE_SAVE, "Chaoxing [精准打击]: 图片已成功降维至本地 -> " + saveFile.getAbsolutePath());
                                        } catch (Exception e) {
                                            Log.e(TAG_IMAGE_SAVE, "Chaoxing Error: ", e);
                                        }
                                    }).start();
                                }
                            });
                            builder.setNegativeButton("取消", null);
                            builder.show(); // 弹出对话框

                            return true; // 返回 true 代表我们消费了这个长按动作，拦截原本的事件传递
                        }
                    }
                }
                return false; // 如果长按的不是图片，原样放行，不影响网页滑动或系统功能
            }
        });

    }
}
