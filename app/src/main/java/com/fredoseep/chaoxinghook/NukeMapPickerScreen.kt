package com.fredoseep.chaoxinghook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import dev.nuke.ui.NukeButton
import dev.nuke.ui.NukePreferenceRow
import dev.nuke.ui.NukeSearchField
import dev.nuke.ui.NukeText
import dev.nuke.ui.NukeTheme
import dev.nuke.ui.NukeTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val AMAP_TIPS_URL = "https://restapi.amap.com/v3/assistant/inputtips"
private const val AMAP_SEARCH_URL = "https://restapi.amap.com/v3/place/text"

/**
 * 地图选点页 —— **Nuke 风格专属**的窗口内页面。
 *
 * 与 [com.fredoseep.chaoxinghook.function.MapPickerActivity] 的区别：它不是独立 Activity，而是 [SettingsNukeScreen] 里
 * `NukeRevealStackNavigator` 的一个路由，所以能配合 Nuke 的圆形揭示转场
 * —— 从「地图选点」那一行的触点圆形扩散展开，返回时收缩回去。
 * `MapView` 是 `FrameLayout`（不是 `SurfaceView`），因此会被揭示层的
 * `graphicsLayer { clip = true }` 一起裁切，不是"贴上去"的假动画。
 *
 * MIUIX / Material 3 风格仍走 [com.fredoseep.chaoxinghook.function.MapPickerActivity]（`ActivityResultContracts`）。
 *
 * 地图实例生命周期必须手工接力：`MapView` 是为 Activity 设计的，
 * 在 Compose 里要自己转发 onCreate / onResume / onPause / onDestroy。
 */
@Composable
fun NukeMapPickerScreen(
    initialLatitude: String,
    initialLongitude: String,
    onBack: (Offset) -> Unit,
    onConfirm: (lat: Double, lng: Double) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 地图实例与 aMap 句柄：onCreate 之后 aMap 才可用
    val mapView = remember {
        MapsInitializer.updatePrivacyShow(context, true, true)
        MapsInitializer.updatePrivacyAgree(context, true)
        MapView(context)
    }
    var aMap by remember { mutableStateOf<AMap?>(null) }
    DisposableEffect(mapView) {
        mapView.onCreate(null)
        mapView.onResume()
        aMap = mapView.map
        onDispose {
            mapView.onPause()
            mapView.onDestroy()
        }
    }

    var selected by remember { mutableStateOf<LatLng?>(null) }
    var searchText by remember { mutableStateOf("") }
    var suggestionNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var suggestionLocations by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    /** 选中坐标：落标记 + 移动镜头 */
    fun selectLocation(lat: Double, lng: Double, zoom: Float) {
        val latLng = LatLng(lat, lng)
        selected = latLng
        aMap?.let { map ->
            map.clear()
            map.addMarker(MarkerOptions().position(latLng))
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
        }
    }

    /** 解析 "经度,纬度" 字符串并落点 */
    fun selectByLocationString(location: String, zoom: Float): Boolean {
        val parts = location.split(",")
        if (parts.size != 2) return false
        val lng = parts[0].trim().toDoubleOrNull() ?: return false
        val lat = parts[1].trim().toDoubleOrNull() ?: return false
        selectLocation(lat, lng, zoom)
        return true
    }

    /** 建议项没带坐标时，退回用关键字做一次 POI 搜索再落点 */
    fun selectByPoiSearch(keyword: String) {
        scope.launch {
            val location = runCatching { fetchFirstPoiLocation(keyword) }.getOrNull().orEmpty()
            val parts = location.split(",")
            if (parts.size != 2) return@launch
            val lng = parts[0].trim().toDoubleOrNull() ?: return@launch
            val lat = parts[1].trim().toDoubleOrNull() ?: return@launch
            selectLocation(lat, lng, 17f)
        }
    }

    // 初始位置：优先用已配置坐标，否则北京天安门（与 MapPickerActivity 一致）
    LaunchedEffect(aMap) {
        val map = aMap ?: return@LaunchedEffect
        map.uiSettings.isZoomControlsEnabled = false
        map.setOnMapClickListener { latLng ->
            selected = latLng
            map.clear()
            map.addMarker(MarkerOptions().position(latLng))
        }
        val lat = initialLatitude.toDoubleOrNull()
        val lng = initialLongitude.toDoubleOrNull()
        val start = if (lat != null && lng != null) LatLng(lat, lng) else LatLng(39.90923, 116.397428)
        if (lat != null && lng != null) {
            selected = start
            map.addMarker(MarkerOptions().position(start))
        }
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(start, 15f))
    }

    // 输入建议：防抖 300ms
    LaunchedEffect(searchText) {
        val keyword = searchText.trim()
        searchJob?.cancel()
        if (keyword.isEmpty()) {
            suggestionNames = emptyList()
            suggestionLocations = emptyList()
            return@LaunchedEffect
        }
        searchJob = launch {
            delay(300)
            val tips = runCatching { fetchInputTips(keyword) }.getOrDefault(emptyList())
            suggestionNames = tips.map { it.first }
            suggestionLocations = tips.map { it.second }
        }
    }

    // 版式与 MIUIX 的 activity_map_picker.xml 一致：**地图铺满整屏**，
    // 顶栏 / 搜索框 / 建议列表 / 底部坐标+确认 全部浮在地图之上。
    // （之前把地图框成固定 380dp，比 MIUIX 那边小了一大截。）
    val colors = NukeTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.fillMaxSize()) {
            NukeTopAppBar(title = "地图选点", onBack = onBack)

            // NukeTextField 自身不带底色（设计上它贴在卡片里），浮在地图上必须自己补不透明背景，
            // 否则地图 POI 文字会从输入框位置透出来
            NukeSearchField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = "搜索地点",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface),
            )

            if (suggestionNames.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                ) {
                    suggestionNames.forEachIndexed { index, name ->
                        NukePreferenceRow(
                            title = name,
                            onClick = {
                                searchText = name
                                val location = suggestionLocations.getOrNull(index).orEmpty()
                                suggestionNames = emptyList()
                                suggestionLocations = emptyList()
                                // 建议项没带坐标时，退回用关键字做一次 POI 搜索
                                if (!selectByLocationString(location, 17f)) {
                                    selectByPoiSearch(name)
                                }
                            },
                        )
                    }
                }
            }
        }

        // 底部面板：坐标 + 确认，浮在地图上
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .padding(16.dp)
        ) {
            NukeText(
                text = if (selected == null) "点击地图选取坐标" else "已选取坐标",
                color = colors.textSecondary,
                fontSize = 13,
                lineHeight = 18,
            )
            selected?.let { latLng ->
                NukeText(
                    text = "经度 ${latLng.longitude}    纬度 ${latLng.latitude}",
                    modifier = Modifier.padding(top = 4.dp),
                    color = colors.textPrimary,
                    fontSize = 14,
                    lineHeight = 20,
                )
            }
            NukeButton(
                text = "确认选取",
                primary = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                onClick = { selected?.let { onConfirm(it.latitude, it.longitude) } },
            )
        }
    }
}

/** 输入提示：返回 (名称, "经度,纬度") 列表 */
private suspend fun fetchInputTips(keyword: String): List<Pair<String, String>> =
    withContext(Dispatchers.IO) {
        val url = "$AMAP_TIPS_URL?key=${BuildConfig.AMAP_WEB_KEY}" +
            "&keywords=${URLEncoder.encode(keyword, "UTF-8")}&datatype=all"
        val json = JSONObject(httpGet(url))
        val tips = json.optJSONArray("tips") ?: return@withContext emptyList()
        (0 until minOf(tips.length(), 6)).mapNotNull { i ->
            val tip = tips.optJSONObject(i) ?: return@mapNotNull null
            val name = tip.optString("name")
            if (name.isEmpty()) return@mapNotNull null
            name to tip.optString("location")
        }
    }

/** POI 搜索：取第一条结果的 "经度,纬度" */
private suspend fun fetchFirstPoiLocation(keyword: String): String? =
    withContext(Dispatchers.IO) {
        val url = "$AMAP_SEARCH_URL?key=${BuildConfig.AMAP_WEB_KEY}" +
            "&keywords=${URLEncoder.encode(keyword, "UTF-8")}&offset=10&page=1&extensions=all"
        val json = JSONObject(httpGet(url))
        if (json.optString("status") != "1") return@withContext null
        json.optJSONArray("pois")?.optJSONObject(0)?.optString("location")
    }

private fun httpGet(url: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    return try {
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.requestMethod = "GET"
        conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}
