package com.fredoseep.chaoxinghook

import android.content.Context
import androidx.compose.ui.graphics.Color

/**
 * 设置页可选的三套 UI 风格。
 *
 * 三套风格共用同一份 [com.fredoseep.chaoxinghook.function.ConfigManager.HookConfig] 与同一份业务逻辑，
 * 只是渲染层分别走 MIUIX（HyperOS）、Nuke（iOS 果冻风）与 Material 3 组件库。
 *
 * 切换风格会重建 Activity（Compose 的主题无法就地热替换），
 * 因此本类与 [SettingStore] 都是无状态常量 / 单例，重建后仍能读到同一个选择。
 */
enum class UiStyle(
    /** 持久化用的稳定 id：不要改已有取值，否则老用户的选择会丢失 */
    val id: Int,
    val key: String,
    val label: String,
    val description: String,
    /** 该风格的主题色（Nuke 的 accent / Material 3 的 primary） */
    val accent: Color,
) {
    Miuix(
        id = 0,
        key = "miuix",
        label = "MIUIX",
        description = "HyperOS 风格，小米澎湃 OS 设计语言",
        accent = Color(0xFF3482FF),
    ),
    Nuke(
        id = 1,
        key = "nuke",
        label = "Nuke",
        description = "果冻按压 + 方圆角卡片 + 圆形揭示转场",
        accent = Color(0xFFEC4899),
    ),
    Material3(
        id = 2,
        key = "material3",
        label = "Material 3",
        description = "Google Material You 动态取色",
        accent = Color(0xFF6750A4),
    ),
    ;

    companion object {
        val Default: UiStyle = Miuix

        /** 解析持久化的 id，非法 / 缺失一律回落到 [Default] */
        fun fromId(id: Int): UiStyle = entries.firstOrNull { it.id == id } ?: Default
    }
}

/**
 * 模块自身的界面偏好（只影响设置页长什么样），与 [com.fredoseep.chaoxinghook.function.ConfigManager] 存放的 hook 配置完全分开。
 *
 * 刻意不复用 `chaoxing_loc.txt`：那个文件是「学习通进程要读的行为开关」，
 * 写入还需要 root（su cp）。界面偏好没有 root 也必须能保存，所以走本应用私有 SharedPreferences。
 */
object SettingStore {

    private const val PREF_NAME = "chaoxinghook_ui"
    private const val KEY_UI_STYLE = "ui_style"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** 当前设置页风格，默认 MIUIX（与重写设置页时的原始实现一致） */
    fun loadUiStyle(context: Context): UiStyle =
        UiStyle.fromId(prefs(context).getInt(KEY_UI_STYLE, UiStyle.Default.id))

    fun saveUiStyle(context: Context, style: UiStyle) {
        prefs(context).edit().putInt(KEY_UI_STYLE, style.id).apply()
    }
}
