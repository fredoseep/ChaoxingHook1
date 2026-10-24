package com.fredoseep.chaoxinghook;

import android.util.Log;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static com.fredoseep.chaoxinghook.HookConfigRepository.getSignConfig;
import static com.fredoseep.chaoxinghook.SignLocationMath.*;

/** POST 签到表单桥接、请求关联及距离采样；每个 WebView 单独维护状态。 */
public final class PostSignHook {
    private static final String TAG_POST_SIGN = "CX.PostSign";
    private static final String POST_SIGN_SCRIPT =
            "(function(){\n"
                    + " 'use strict';\n"
                    + " if(window.__cxPostSignV1Installed)return 'already';\n"
                    + " var bridge=window.__cxPostSignV1;\n"
                    + " if(!bridge)return 'bridge-missing';\n"
                    + " function target(url,method){try{var u=new URL(url,location.href);return String(method).toUpperCase()==='POST'&&u.protocol==='https:'&&u.hostname==='mobilelearn.chaoxing.com'&&u.pathname==='/pptSign/stuSignajax';}catch(e){return false;}}\n"
                    + " function prepare(url,body){\n"
                    + "  if(typeof body!=='string'&&!(typeof URLSearchParams!=='undefined'&&body instanceof URLSearchParams)){return null;}\n"
                    + "  try{var p=JSON.parse(bridge.prepare(new URL(url,location.href).href,String(body)));return p&&p.body!==undefined?p:null;}catch(e){return null;}\n"
                    + " }\n"
                    + " function report(p,status,text){if(p&&p.id)try{bridge.complete(String(p.id),Number(status),String(text).slice(0,65536));}catch(e){}}\n"
                    + " var open=XMLHttpRequest.prototype.open,send=XMLHttpRequest.prototype.send;\n"
                    + " XMLHttpRequest.prototype.open=function(method,url){var result=open.apply(this,arguments);this.__cxSignMethod=method;this.__cxSignUrl=String(url);return result;};\n"
                    + " XMLHttpRequest.prototype.send=function(body){\n"
                    + "  var p=target(this.__cxSignUrl,this.__cxSignMethod)?prepare(this.__cxSignUrl,body):null;\n"
                    + "  if(p){var xhr=this;var done=function(){xhr.removeEventListener('loadend',done);var text='';try{text=xhr.responseType==='json'?JSON.stringify(xhr.response):xhr.responseText;}catch(e){}report(p,xhr.status,text);};xhr.addEventListener('loadend',done);}\n"
                    + "  try{return send.call(this,p?p.body:body);}catch(e){report(p,0,'');throw e;}\n"
                    + " };\n"
                    + " if(typeof window.fetch==='function'){\n"
                    + "  var originalFetch=window.fetch;\n"
                    + "  window.fetch=function(input,init){\n"
                    + "   var receiver=this,url=typeof input==='string'?input:(input&&input.url)||String(input),method=(init&&init.method)||(input&&input.method)||'GET';\n"
                    + "   if(!target(url,method))return originalFetch.apply(receiver,arguments);\n"
                    + "   function run(body){var p=prepare(url,body);var options=init;if(p)options=Object.assign({},init||{},{body:p.body});var promise;try{promise=originalFetch.call(receiver,input,options);}catch(e){report(p,0,'');throw e;}\n"
                    + "    return promise.then(function(response){if(p)try{response.clone().text().then(function(text){report(p,response.status,text);},function(){report(p,0,'');});}catch(e){report(p,0,'');}return response;},function(e){report(p,0,'');throw e;});}\n"
                    + "   if(init&&init.body!==undefined)return run(init.body);\n"
                    + "   if(typeof Request!=='undefined'&&input instanceof Request){try{return input.clone().text().then(run);}catch(e){return originalFetch.call(receiver,input,init);}}\n"
                    + "   return originalFetch.call(receiver,input,init);\n"
                    + "  };\n"
                    + " }\n"
                    + " window.__cxPostSignV1Installed=true;\n"
                    + " return 'installed';\n"
                    + "})();\n"
            ;
    // POST compatibility: mutate the original page request, never replay it via HttpURLConnection.
    private final java.util.WeakHashMap<android.webkit.WebView, PostSignBridge> postSignBridges = new java.util.WeakHashMap<>();

    void attach(android.webkit.WebView view) {
        try {
            synchronized (postSignBridges) {
                if (postSignBridges.containsKey(view)) return;
                PostSignBridge bridge = new PostSignBridge(view.getContext().getApplicationContext());
                view.addJavascriptInterface(bridge, "__cxPostSignV1");
                postSignBridges.put(view, bridge);
            }
            Log.i(TAG_POST_SIGN, "POST-SIGN bridge attached");
        } catch (Throwable t) { Log.e(TAG_POST_SIGN, "POST-SIGN attach FAILED: ", t); }
    }

    void inject(android.webkit.WebView view) {
        attach(view);
        try {
            view.evaluateJavascript(POST_SIGN_SCRIPT, result -> Log.d(TAG_POST_SIGN, "POST-SIGN inject: " + result));
        } catch (Throwable t) { Log.e(TAG_POST_SIGN, "POST-SIGN inject FAILED: ", t); }
    }

    public final class PostSignBridge {
        private final android.content.Context context;
        private final List<LocationPoint> points = new ArrayList<>();
        private final java.util.LinkedHashMap<String, PostAttempt> pending = new java.util.LinkedHashMap<>();
        private double[] target;
        private String session = "";
        private long sequence;
        private long generation;
        private final class PostAttempt {
            final double lat, lon;
            final long generation, time;
            final boolean automatic;
            PostAttempt(double lat, double lon, boolean automatic) {
                this.lat = lat; this.lon = lon; this.automatic = automatic;
                this.generation = PostSignBridge.this.generation;
                this.time = android.os.SystemClock.elapsedRealtime();
            }
        }
        PostSignBridge(android.content.Context context) { this.context = context; }

        @android.webkit.JavascriptInterface
        public synchronized String prepare(String url, String body) {
            try {
                java.net.URI uri = java.net.URI.create(url);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || !"mobilelearn.chaoxing.com".equalsIgnoreCase(uri.getHost())
                        || !"/pptSign/stuSignajax".equals(uri.getPath()) || body == null || body.length() > 1024 * 1024) return "{}";
                SignConfig cfg = getSignConfig();
                boolean changeLocation = cfg.autoCalculateLocation ||
                        (cfg.modifyLocation && !cfg.latitude.isEmpty() && !cfg.longitude.isEmpty());
                boolean changeAddress = cfg.modifyAddress && !cfg.address.isEmpty();
                boolean changeName = cfg.modifyName && !cfg.name.isEmpty();
                if (!changeLocation && !changeAddress && !changeName) return "{}";
                Map<String, String> form = new java.util.LinkedHashMap<>();
                for (String part : body.split("&", -1)) {
                    int eq = part.indexOf('=');
                    if (eq < 0) continue;
                    form.put(java.net.URLDecoder.decode(part.substring(0, eq), "UTF-8"),
                            java.net.URLDecoder.decode(part.substring(eq + 1), "UTF-8"));
                }
                if (!form.containsKey("latitude") || !form.containsKey("longitude")) {
                    if (changeLocation) Log.d(TAG_POST_SIGN, "POST-SIGN coordinates absent; text changes remain independent");
                    changeLocation = false;
                }
                if (!changeLocation && !changeAddress && !changeName) return "{}";
                double lat = Double.NaN, lon = Double.NaN;
                if (changeLocation) {
                    String key = form.getOrDefault("activeId", "") + "|" + form.getOrDefault("courseId", "")
                            + "|" + form.getOrDefault("uid", "") + "|" + cfg.autoCalculateLocation + "|" + cfg.latitude + "|" + cfg.longitude;
                    if (!key.equals(session)) {
                        session = key; generation++; points.clear(); target = null; pending.clear();
                        Log.d(TAG_POST_SIGN, "POST-SIGN session reset");
                    }
                    long now = android.os.SystemClock.elapsedRealtime();
                    pending.entrySet().removeIf(e -> now - e.getValue().time > 60000);
                    if (cfg.autoCalculateLocation && pending.values().stream().anyMatch(p -> p.automatic)) {
                        Log.d(TAG_POST_SIGN, "POST-SIGN skipped: previous automatic sample pending"); return "{}";
                    }
                    if (cfg.autoCalculateLocation) {
                        if (target != null) { lat = target[0]; lon = target[1]; }
                        else if (points.isEmpty()) {
                            lat = Double.parseDouble(cfg.latitude.isEmpty() ? form.get("latitude") : cfg.latitude);
                            lon = Double.parseDouble(cfg.longitude.isEmpty() ? form.get("longitude") : cfg.longitude);
                        } else {
                            LocationPoint last = points.get(points.size() - 1);
                            double offset = Math.max(0.0001, last.distance / 200000.0);
                            int attempt = points.size();
                            lat = last.lat; lon = last.lon;
                            if (attempt % 3 == 1) lat += offset;
                            else if (attempt % 3 == 2) lon += offset;
                            else { lat -= offset; lon -= offset; }
                        }
                    } else {
                        if (cfg.latitude.isEmpty() || cfg.longitude.isEmpty()) return "{}";
                        lat = Double.parseDouble(cfg.latitude); lon = Double.parseDouble(cfg.longitude);
                    }
                    if (!validCoordinates(lat, lon)) { Log.e(TAG_POST_SIGN, "POST-SIGN skipped: invalid coordinates"); return "{}"; }
                }
                org.json.JSONObject location = null;
                if ((changeLocation || changeAddress) && form.containsKey("locationResult") && !form.get("locationResult").isEmpty()) {
                    // Keep mockData/signConfig/token and all other JSON fields unchanged.
                    location = new org.json.JSONObject(form.get("locationResult"));
                    if (changeLocation) { location.put("latitude", lat); location.put("longitude", lon); }
                    if (changeAddress && location.has("address")) location.put("address", cfg.address);
                }
                StringBuilder updated = new StringBuilder();
                int partIndex = 0;
                for (String part : body.split("&", -1)) {
                    if (partIndex++ > 0) updated.append('&');
                    int eq = part.indexOf('=');
                    String name = eq < 0 ? "" : java.net.URLDecoder.decode(part.substring(0, eq), "UTF-8");
                    String value = null;
                    if (changeLocation && "latitude".equals(name)) value = Double.toString(lat);
                    else if (changeLocation && "longitude".equals(name)) value = Double.toString(lon);
                    else if (changeAddress && "address".equals(name)) value = cfg.address;
                    else if (changeName && "name".equals(name)) value = cfg.name;
                    else if ("locationResult".equals(name) && location != null) value = location.toString();
                    updated.append(value == null ? part : part.substring(0, eq + 1) + URLEncoder.encode(value, "UTF-8"));
                }
                String id = "";
                if (changeLocation) {
                    id = Long.toString(++sequence);
                    while (pending.size() >= 16) pending.remove(pending.keySet().iterator().next());
                    pending.put(id, new PostAttempt(lat, lon, cfg.autoCalculateLocation));
                }
                Log.d(TAG_POST_SIGN, "POST-SIGN rewrite id=" + id + " automatic=" + cfg.autoCalculateLocation + " samples=" + points.size()
                        + " locationResult=" + (location != null) + " address=" + changeAddress + " name=" + changeName);
                return new org.json.JSONObject().put("id", id).put("body", updated.toString()).toString();
            } catch (Throwable t) { Log.e(TAG_POST_SIGN, "POST-SIGN prepare FAILED", t); return "{}"; }
        }

        @android.webkit.JavascriptInterface
        public synchronized void complete(String id, int status, String response) {
            PostAttempt attempt = pending.remove(id);
            if (attempt == null || attempt.generation != generation || !attempt.automatic) return;
            if (status < 200 || status >= 300 || response == null || response.length() > 65536) {
                Log.d(TAG_POST_SIGN, "POST-SIGN response ignored id=" + id + " status=" + status); return;
            }
            try {
                String messages = response;
                try {
                    StringBuilder text = new StringBuilder();
                    collectResponseStrings(new org.json.JSONTokener(response).nextValue(), text, 0);
                    messages += "\n" + text;
                } catch (Exception ignored) { }
                Matcher m = Pattern.compile("距.*?([0-9.]+)\\s*米").matcher(messages);
                if (m.find()) {
                    double distance = Double.parseDouble(m.group(1));
                    if (!Double.isFinite(distance) || distance < 0) return;
                    if (target != null) target = null;
                    points.add(new LocationPoint(attempt.lat, attempt.lon, distance));
                    if (points.size() > 3) points.remove(0);
                    if (points.size() == 3) {
                        target = calculateTriangulation(points); // Reuse the baseline solver verbatim.
                        if (target != null && validCoordinates(target[0], target[1])) {
                            notifyResult("已采集 3 个距离，目标坐标已计算，请再次点击签到");
                        } else {
                            target = null; points.clear(); notifyResult("三点计算失败，已清空采样，请重试");
                        }
                    } else notifyResult("距离 " + distance + " 米，采集进度 " + points.size() + "/3，请再次点击");
                    Log.d(TAG_POST_SIGN, "POST-SIGN distance id=" + id + " meters=" + distance + " samples=" + points.size());
                } else {
                    Log.d(TAG_POST_SIGN, "POST-SIGN response id=" + id + " status=" + status + " distance-not-found chars=" + response.length());
                    String trimmed = response.trim();
                    if ("success".equalsIgnoreCase(trimmed) || messages.contains("签到成功")) {
                        points.clear(); target = null; notifyResult("签到成功，已清空距离采样");
                    }
                }
            } catch (Throwable t) { Log.e(TAG_POST_SIGN, "POST-SIGN response FAILED", t); }
        }
        private void notifyResult(String message) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show());
        }
    }

    private static void collectResponseStrings(Object value, StringBuilder text, int depth) throws org.json.JSONException {
        if (depth > 8 || text.length() > 65536) return;
        if (value instanceof String) text.append(value).append('\n');
        else if (value instanceof org.json.JSONObject) {
            org.json.JSONObject object = (org.json.JSONObject) value;
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) collectResponseStrings(object.opt(keys.next()), text, depth + 1);
        } else if (value instanceof org.json.JSONArray) {
            org.json.JSONArray array = (org.json.JSONArray) value;
            for (int i = 0; i < array.length(); i++) collectResponseStrings(array.opt(i), text, depth + 1);
        }
    }
}
