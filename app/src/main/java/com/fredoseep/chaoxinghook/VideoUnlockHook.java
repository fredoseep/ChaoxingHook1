package com.fredoseep.chaoxinghook;

import android.util.Log;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/** 视频课程响应中的快进、倍速和切屏播放参数处理。 */
final class VideoUnlockHook {
    private static final String TAG_VIDEO = "CX.Video";
    static boolean intercept(String url, Object requestObj, XC_MethodHook.MethodHookParam innerParam, ClassLoader loader) throws Throwable {
        if (url.contains("knowledge/cards") || url.contains("richvideo/initdatawithviewer") || url.contains("studentstudy")) {
            try {
                URL targetUrl = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();

                // 复制原请求的方法与 Header
                if (requestObj != null) {
                    conn.setRequestMethod((String) XposedHelpers.callMethod(requestObj, "getMethod"));
                    @SuppressWarnings("unchecked")
                    Map<String, String> headers = (Map<String, String>) XposedHelpers.callMethod(requestObj, "getRequestHeaders");
                    if (headers != null) {
                        for (Map.Entry<String, String> entry : headers.entrySet()) {
                            if (!entry.getKey().equalsIgnoreCase("Accept-Encoding")) {
                                conn.setRequestProperty(entry.getKey(), entry.getValue());
                            }
                        }
                    }
                } else {
                    conn.setRequestMethod("GET");
                }

                // 同步 Cookie 证明合法登录，并请求 GZIP 压缩
                String cookie = android.webkit.CookieManager.getInstance().getCookie(url);
                if (cookie != null) {
                    conn.setRequestProperty("Cookie", cookie);
                }
                conn.setRequestProperty("Accept-Encoding", "gzip");

                // 解除 GZIP 封印，获取明文流
                InputStream is = conn.getInputStream();
                String encoding = conn.getContentEncoding();
                if (encoding != null && encoding.toLowerCase().contains("gzip")) {
                    is = new java.util.zip.GZIPInputStream(is);
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                String htmlStr = new String(baos.toByteArray(), "UTF-8");

                // ================= 实施终极基因改造 =================
                if (htmlStr.contains("fastforward") || htmlStr.contains("doublespeed")) {
                    htmlStr = htmlStr
                            // 1. 彻底解除快进限制
                            .replace("\"fastforward\":\"true\"", "\"fastforward\":\"false\"")
                            .replace("\"fastforward\":true", "\"fastforward\":false")
                            .replace("&quot;fastforward&quot;:&quot;true&quot;", "&quot;fastforward&quot;:&quot;false&quot;")
                            .replace("&quot;fastforward&quot;:true", "&quot;fastforward&quot;:false")

                            // 2. 修复倍速误伤：超星中1是开启，0是关闭，我们要强制改为1！
                            .replace("\"doublespeed\":0", "\"doublespeed\":1")
                            .replace("\"doublespeed\":\"0\"", "\"doublespeed\":\"1\"")
                            .replace("&quot;doublespeed&quot;:0", "&quot;doublespeed&quot;:1")
                            .replace("&quot;doublespeed&quot;:&quot;0&quot;", "&quot;doublespeed&quot;:&quot;1&quot;")

                            // 3. 解除切屏暂停
                            .replace("\"switchwindow\":\"true\"", "\"switchwindow\":\"false\"")
                            .replace("\"switchwindow\":true", "\"switchwindow\":false")
                            .replace("&quot;switchwindow&quot;:&quot;true&quot;", "&quot;switchwindow&quot;:&quot;false&quot;")
                            .replace("&quot;switchwindow&quot;:true", "&quot;switchwindow&quot;:false");

                    htmlStr = htmlStr.replace("var forbidImgClick = \"true\";", "var forbidImgClick = \"false\";");

                    Log.i(TAG_VIDEO, "Chaoxing [网课解锁]: 成功篡改最底层视频限制参数，快进、倍速均已完全自由！");
                }

                // ================= 重新打包返回 =================
                Class<?> responseClass = XposedHelpers.findClassIfExists("android.webkit.WebResourceResponse", loader);
                if (responseClass != null) {
                    String contentType = conn.getContentType();
                    String mimeType = "text/html";
                    String charset = "utf-8";
                    if (contentType != null) {
                        String[] parts = contentType.split(";");
                        mimeType = parts[0].trim();
                        if (parts.length > 1 && parts[1].toLowerCase().contains("charset=")) {
                            charset = parts[1].split("=")[1].trim();
                        }
                    }
                    innerParam.setResult(XposedHelpers.newInstance(
                            responseClass,
                            mimeType,
                            charset,
                            new ByteArrayInputStream(htmlStr.getBytes(charset))
                    ));
                }
                return true; // 结束，原请求不再发出
            } catch (Exception e) {
                Log.e(TAG_VIDEO, "Chaoxing Error (Video Unlock): ", e);
            }
        }
        return false;
    }
}
