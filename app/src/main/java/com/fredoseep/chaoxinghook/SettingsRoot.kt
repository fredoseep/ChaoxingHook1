package com.fredoseep.chaoxinghook

import androidx.compose.runtime.Composable

/**
 * 设置页入口 —— 按当前 UI 风格分发到三套实现之一。
 *
 * | 风格 | 实现 | 组件库 |
 * |---|---|---|
 * | [UiStyle.Miuix] | [SettingsMiuixScreen] | MIUIX / HyperOS（`top.yukonga.miuix.kmp`） |
 * | [UiStyle.Nuke] | [SettingsNukeScreen] | Nuke（`dev.nuke.ui`，../nuke） |
 * | [UiStyle.Material3] | [SettingsMaterial3Screen] | Material 3（`androidx.compose.material3`） |
 *
 * 三者的业务逻辑完全共用（[HookSettingsState] + [com.fredoseep.chaoxinghook.function.buildHookSettingGroups] + [SettingsScaffold]），
 * 差别只在渲染层。风格存于 [SettingStore]，切换时由 [rememberUiStyleSwitcher] 重建 Activity。
 */
@Composable
fun SettingsRoot() {
    when (rememberCurrentUiStyle()) {
        UiStyle.Miuix -> SettingsMiuixScreen()
        UiStyle.Nuke -> SettingsNukeScreen()
        UiStyle.Material3 -> SettingsMaterial3Screen()
    }
}
