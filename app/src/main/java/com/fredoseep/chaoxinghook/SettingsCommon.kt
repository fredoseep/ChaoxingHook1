package com.fredoseep.chaoxinghook

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * 三套 UI 风格（MIUIX / Nuke / Material 3）共用的设置页外壳。
 *
 * 业务逻辑只写一份：这里持有 [ConfigManager.HookConfig] 与「实时保存」流程，
 * 各风格实现只负责把状态渲染成自己的组件语言（行模型见 [HookSettingsRows]）。
 */

/**
 * 设置页数据源。所有变更都立刻落盘（通过本进程文件 API 读写，见 [ConfigManager]）。
 *
 * 用 [MutableState] 持有配置：hook 配置是 data class，修改必须走 `copy()` ——
 * `mutableStateOf` 按引用比较，原地 apply 不会触发重组。
 */
class HookSettingsState internal constructor(
    private val context: Context,
    initial: ConfigManager.HookConfig,
) {
    internal val configState: MutableState<ConfigManager.HookConfig> = mutableStateOf(initial)

    val config: ConfigManager.HookConfig get() = configState.value

    init {
        // root 桥不通时必须说清楚：显示的是本 App 私有副本/默认值而非宿主那份
        if (!ConfigManager.rootBridgeOk) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "未获得 Root 授权，当前读写本 App 私有配置（与学习通内那份不互通）。请在 KernelSU/Magisk 中授权后重开本页。",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    /** 改一项就存一次；保存失败（通常是没 root，或本次没读到配置）会明确提示，不再静默 */
    fun update(block: ConfigManager.HookConfig.() -> Unit) {
        val next = configState.value.copy().apply(block)
        configState.value = next
        val ok = ConfigManager.save(next)
        Toast.makeText(
            context,
            when {
                ok && ConfigManager.rootBridgeOk -> "配置已保存（宿主配置）"
                ok -> "已保存到本 App 私有配置（未获得 Root）"
                else -> "保存失败，请检查存储空间"
            },
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun reset() {
        val ok = ConfigManager.reset()
        if (ok) configState.value = ConfigManager.HookConfig()
        Toast.makeText(
            context,
            if (ok) "配置已重置" else "重置失败，请检查存储空间",
            Toast.LENGTH_SHORT,
        ).show()
    }

    /** 文件工具关闭后重读，反映导入或重置结果。 */
    fun reload() {
        configState.value = ConfigManager.load()
    }
}

/**
 * 记住一份设置页状态。
 *
 * 注意：**不要**把 `rememberSaveable` 用在这里 —— 配置里同时存在开关与文本，
 * 而 `rememberSaveable` 恢复的是「重建前那一刻的快照」，会把切换 UI 风格前
 * 刚输入还没落盘的文本又覆盖回来。重建后从文件重读才是唯一事实来源。
 */
@Composable
fun rememberHookSettingsState(): HookSettingsState {
    val context = LocalContext.current
    return remember {
        ConfigManager.initialize(context)
        HookSettingsState(context, ConfigManager.load())
    }
}

/**
 * 设置页的公共依赖：本地文件工具 + 地图选点回填。
 *
 * 地图回填走 Activity Result API，必须挂在 `@Composable` 上，
 * 所以三套风格各调用一次本函数即可，逻辑仍然只有一份。
 */
class SettingsScaffold internal constructor(
    /** 打开无需 Root 的原生配置与文件工具 */
    val openFileTools: () -> Unit,
    /** 打开高德地图选点；返回后自动回填经纬度 */
    val launchMapPicker: () -> Unit,
)

/**
 * 创建 [SettingsScaffold]。
 *
 * @param settings 状态对象；地图选点回填会直接写进它
 */
@Composable
fun rememberSettingsScaffold(settings: HookSettingsState): SettingsScaffold {
    val context = LocalContext.current

    // ==================== 地图选点 ====================
    val mapPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val lat = result.data!!.getDoubleExtra("latitude", 0.0)
            val lng = result.data!!.getDoubleExtra("longitude", 0.0)
            settings.update {
                latitude = lat.toString()
                longitude = lng.toString()
            }
        }
    }

    fun launchMapPicker() {
        mapPickerLauncher.launch(Intent(context, MapPickerActivity::class.java))
    }

    // 局部函数每次重组都是新实例，用 rememberUpdatedState 兜住最新引用，
    // 这样返回出去的 SettingsScaffold 能稳定 remember（不需要把函数当 remember key）
    val currentFileTools by rememberUpdatedState { EmbeddedSettings.show(context, Runnable { settings.reload() }) }
    val currentLaunchMapPicker by rememberUpdatedState { launchMapPicker() }

    return remember {
        SettingsScaffold(
            openFileTools = { currentFileTools() },
            launchMapPicker = { currentLaunchMapPicker() },
        )
    }
}

/**
 * 当前风格：整屏只读一次。
 *
 * 切风格会重建 Activity，重建后 composition 从零开始，所以这里读到的永远是最终值；
 * 用 [remember] 而不是监听 SharedPreferences，可以避免重建瞬间出现两种风格混排的闪帧。
 */
@Composable
fun rememberCurrentUiStyle(): UiStyle {
    val context = LocalContext.current
    return remember { SettingStore.loadUiStyle(context) }
}

/**
 * 切换 UI 风格：先落盘，再重建 Activity。
 *
 * Compose 的主题（MIUIX / Nuke / Material 3）无法就地热替换，重建是最省事也最可靠的做法；
 * 配置是实时保存的，重建不会丢数据。
 *
 * 返回一个 `(UiStyle) -> Unit`，由各风格的选择控件调用。用 [rememberCurrentUiStyle] 拿到当前值，
 * 选中值相同就不做任何事（避免点当前项也白重建一次）。
 */
@Composable
fun rememberUiStyleSwitcher(onSwitched: () -> Unit = {}): (UiStyle) -> Unit {
    val context = LocalContext.current
    val activity = context as? Activity
    val current by rememberUpdatedState(SettingStore.loadUiStyle(context))
    val currentOnSwitched by rememberUpdatedState(onSwitched)
    return remember(activity) {
        { style: UiStyle ->
            if (style != current) {
                SettingStore.saveUiStyle(context, style)
                currentOnSwitched()
                Toast.makeText(context, "已切换到 ${style.label} 风格", Toast.LENGTH_SHORT).show()
                activity?.recreate()
            }
        }
    }
}

/**
 * 风格切换的选项列表（各风格的选择控件都用它，保证三处文案一致）。
 */
val uiStyleOptions: List<UiStyle> get() = UiStyle.entries.toList()