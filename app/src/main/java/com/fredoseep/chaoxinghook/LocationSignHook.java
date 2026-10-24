package com.fredoseep.chaoxinghook;

import android.util.Log;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import static com.fredoseep.chaoxinghook.HookConfigRepository.getSignConfig;
import static com.fredoseep.chaoxinghook.SignLocationMath.*;

/** 兼容 GET 查询参数形式的定位签到；POST 交由 PostSignHook 处理。 */
final class LocationSignHook {
    private static final String TAG_LOCATION_SIGN = "CX.LocationSign";
    private static final String TAG_POST_SIGN = "CX.PostSign";
    private static final List<LocationPoint> historyPoints = new ArrayList<>();
    private static double[] calculatedTarget;

    static boolean intercept(String url, Object requestObj, XC_MethodHook.MethodHookParam innerParam, ClassLoader loader) throws Throwable {
        if (url.contains("stuSignajax") &&
                ((requestObj instanceof android.webkit.WebResourceRequest &&
                        "POST".equalsIgnoreCase(((android.webkit.WebResourceRequest) requestObj).getMethod())) ||
                        !url.contains("latitude=") || !url.contains("longitude="))) {
            Log.d(TAG_POST_SIGN, "POST-SIGN legacy replay skipped (POST/body coordinates)");
            return true;
        }
        if (url.contains("stuSignajax")) {
            SignConfig config = getSignConfig();
            String newUrlString = url;
            boolean hasModified = false;
            double sendLat = 0, sendLon = 0;

            if (config.autoCalculateLocation) {
                if (calculatedTarget != null) {
                    sendLat = calculatedTarget[0];
                    sendLon = calculatedTarget[1];
                } else {
                    double baseLat = 0, baseLon = 0;
                    try {
                        Matcher latMatcher = Pattern.compile("latitude=([^&]+)").matcher(url);
                        Matcher lonMatcher = Pattern.compile("longitude=([^&]+)").matcher(url);
                        if (latMatcher.find()) baseLat = Double.parseDouble(latMatcher.group(1));
                        if (lonMatcher.find()) baseLon = Double.parseDouble(lonMatcher.group(1));
                    } catch (Exception e) {}
                    try {
                        if (!config.latitude.isEmpty()) baseLat = Double.parseDouble(config.latitude);
                        if (!config.longitude.isEmpty()) baseLon = Double.parseDouble(config.longitude);
                    } catch (Exception e){}

                    if (historyPoints.isEmpty()) {
                        sendLat = baseLat;
                        sendLon = baseLon;
                    } else {
                        LocationPoint lastP = historyPoints.get(historyPoints.size() - 1);
                        double offset = Math.max(0.0001, lastP.distance / 200000.0);
                        int attempt = historyPoints.size();

                        if (attempt % 3 == 1) {
                            sendLat = lastP.lat + offset;
                            sendLon = lastP.lon;
                        } else if (attempt % 3 == 2) {
                            sendLat = lastP.lat;
                            sendLon = lastP.lon + offset;
                        } else {
                            sendLat = lastP.lat - offset;
                            sendLon = lastP.lon - offset;
                        }
                    }
                }
                newUrlString = newUrlString.replaceAll("latitude=[^&]*", "latitude=" + sendLat)
                        .replaceAll("longitude=[^&]*", "longitude=" + sendLon);
                hasModified = true;

            } else if (config.modifyLocation && !config.latitude.isEmpty() && !config.longitude.isEmpty()) {
                newUrlString = newUrlString.replaceAll("latitude=[^&]*", "latitude=" + config.latitude)
                        .replaceAll("longitude=[^&]*", "longitude=" + config.longitude);
                hasModified = true;
            }

            if (config.modifyAddress && !config.address.isEmpty()) {
                newUrlString = newUrlString.replaceAll("address=[^&]*", "address=" + URLEncoder.encode(config.address, "UTF-8"));
                hasModified = true;
            }
            if (config.modifyName && !config.name.isEmpty()) {
                newUrlString = newUrlString.replaceAll("name=[^&]*", "name=" + URLEncoder.encode(config.name, "UTF-8"));
                hasModified = true;
            }

            if (!hasModified) return true;

            try {
                URL newUrl = new URL(newUrlString);
                HttpURLConnection conn = (HttpURLConnection) newUrl.openConnection();
                if (requestObj != null) {
                    conn.setRequestMethod((String) XposedHelpers.callMethod(requestObj, "getMethod"));
                    @SuppressWarnings("unchecked")
                    Map<String, String> headers = (Map<String, String>) XposedHelpers.callMethod(requestObj, "getRequestHeaders");
                    if (headers != null) {
                        for (Map.Entry<String, String> entry : headers.entrySet()) {
                            conn.setRequestProperty(entry.getKey(), entry.getValue());
                        }
                    }
                } else {
                    conn.setRequestMethod("GET");
                }

                String cookie = android.webkit.CookieManager.getInstance().getCookie(newUrlString);
                if (cookie != null) { conn.setRequestProperty("Cookie", cookie); }

                InputStream is = conn.getInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = is.read(buffer)) != -1) baos.write(buffer, 0, len);
                String jsonResp = new String(baos.toByteArray(), "UTF-8");

                if (config.autoCalculateLocation) {
                    Matcher m = Pattern.compile("距.*?([0-9.]+)\\s*米").matcher(jsonResp);
                    if (m.find()) {
                        String originalMatch = m.group(0);
                        double dist = Double.parseDouble(m.group(1));

                        if (calculatedTarget != null) {
                            calculatedTarget = null;
                        }

                        historyPoints.add(new LocationPoint(sendLat, sendLon, dist));
                        if (historyPoints.size() > 3) {
                            historyPoints.remove(0);
                        }

                        String customMsg = "";
                        if (historyPoints.size() == 3) {
                            calculatedTarget = calculateTriangulation(historyPoints);
                            if (calculatedTarget != null) {
                                customMsg = "Xposed提示: 目标坐标已锁定，误差已过滤，请点击执行最终打卡。";
                            } else {
                                historyPoints.clear();
                                customMsg = "Xposed提示: 三点共线计算失败，正在重新采集，请再次点击。";
                            }
                        } else {
                            customMsg = "Xposed提示: 距靶心 " + dist + " 米，采集进度(" + historyPoints.size() + "/3)。请再次点击。";
                        }

                        jsonResp = jsonResp.replace(originalMatch, customMsg);
                        Log.d(TAG_LOCATION_SIGN, "Chaoxing AdSkip: " + customMsg);

                    } else if (jsonResp.contains("success") || jsonResp.contains("成功")) {
                        historyPoints.clear();
                        calculatedTarget = null;
                        Log.i(TAG_LOCATION_SIGN, "Chaoxing AdSkip: 定位爆破签到大成功！");
                    }
                }

                String contentType = conn.getContentType();
                Class<?> responseClass = XposedHelpers.findClassIfExists("android.webkit.WebResourceResponse", loader);
                if (responseClass != null) {
                    innerParam.setResult(XposedHelpers.newInstance(
                            responseClass,
                            (contentType != null) ? contentType.split(";")[0].trim() : "application/json",
                            conn.getContentEncoding() != null ? conn.getContentEncoding() : "utf-8",
                            new ByteArrayInputStream(jsonResp.getBytes("UTF-8"))
                    ));
                }
            } catch (Exception e) {}
        }
        return false;
    }
}
