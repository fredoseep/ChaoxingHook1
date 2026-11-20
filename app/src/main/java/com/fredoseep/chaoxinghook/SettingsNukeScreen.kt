package com.fredoseep.chaoxinghook

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fredoseep.chaoxinghook.function.HookSettingRow
import com.fredoseep.chaoxinghook.function.buildHookSettingGroups
import dev.nuke.ui.NukeCountAndChevron
import dev.nuke.ui.NukeDivider
import dev.nuke.ui.NukePageScaffold
import dev.nuke.ui.NukePopupAnimationMode
import dev.nuke.ui.NukePopupMotionConfig
import dev.nuke.ui.NukePreferenceRow
import dev.nuke.ui.NukeRevealStackNavigator
import dev.nuke.ui.NukeSelectPreference
import dev.nuke.ui.NukeSettingGroup
import dev.nuke.ui.NukeSimpleDialog
import dev.nuke.ui.NukeSwitch
import dev.nuke.ui.NukeText
import dev.nuke.ui.NukeTextField
import dev.nuke.ui.NukeTheme
import dev.nuke.ui.NukeThemeConfig
import dev.nuke.ui.rememberNukeRevealStackState

/**
 * 设置页 —— **Nuke** 实现（`../nuke` 的 `dev.nuke.ui` 组件库）。
 *
 * 卡片分组照 WeKit 的设置页来：**一个分组 = 一张四角圆角的卡片**（[NukeSettingGroup]），
 * 组内每行用 [NukeDivider] 分隔，每行自己仍带独立果冻按压。
 * 组与组之间靠 `NukePageScaffold` 的 `itemSpacing` 留 12dp。
 *
 * 为什么不用 `nukeGroupedCardItem`（每行独立 Lazy item 拼卡片）：
 * 那条路是给几千行的超大列表用的，行与行之间没有分割线，观感与 WeKit 不一致。
 * `NukeSettingGroup` 的高度上限（约 8192px 纹理）对本设置页无威胁 ——
 * 最长的一组（签到全展开 = 2 开关 + 2 输入 + 1 选点）约 1000px，
 * 而且它内部用 `CompositingStrategy.ModulateAlpha` + `RoundedCornerShape` 正是为了规避该问题。
 *
 * ⚠️ **每个可点行都必须传 `onClick`**：[NukePreferenceRow] 内部是
 * `if (onClick == null) Modifier else nukeJellyClickable(...)`，
 * 漏传的行不会有任何按压反馈 —— 开关行最容易踩这个坑。
 */
@Composable
fun SettingsNukeScreen() {
    val style = rememberCurrentUiStyle()
    val darkTheme = isSystemInDarkTheme()
    val config = remember(style, darkTheme) {
        NukeThemeConfig(
            darkTheme = darkTheme,
            accent = style.accent,
            hapticsEnabled = true,
            // 快速轻点也要立刻给按压反馈，否则轻点会觉得"没反应"
            immediatePressFeedback = true,
            popupMotion = NukePopupMotionConfig(
                animationMode = NukePopupAnimationMode.ExitAlignedToEnter,
                // 只有 Dialog 承载才能把返回手势完整分发进浮层（预测性返回）
                useDialogHost = true,
                predictiveExit = true,
            ),
        )
    }

    NukeTheme(config = config) {
        val settings = rememberHookSettingsState()
        val scaffold = rememberSettingsScaffold(settings)

        // 待确认的破坏性动作（重置所有配置）—— null 表示没有待确认项
        var pendingConfirm by remember { mutableStateOf<HookSettingRow.Action?>(null) }

        // 圆形揭示转场路由栈。**只有 Nuke 风格有这套窗口内导航**；
        // MIUIX / Material 3 的「地图选点」仍走 MapPickerActivity（见 NukeSettingRow 里的分支）。
        val navigator = rememberNukeRevealStackState<SettingsRoute>()

        // 返回手势接进揭示转场：跟手播放一半退场动画，松手决定是完成还是复位。
        // optimizeExitOrigin = false → 退场圆心保持**进入时点的那个「地图选点」触点**
        // （手势边缘与返回键位置都不参与），满足"从哪点开就从哪收回"。
        PredictiveBackHandler(enabled = navigator.canPop) { events ->
            navigator.predictivePop(events = events, optimizeExitOrigin = false)
        }

        NukeRevealStackNavigator(
            state = navigator,
            base = {
                NukeSettingsPage(
                    style = style,
                    settings = settings,
                    scaffold = scaffold,
                    onConfirmRequest = { pendingConfirm = it },
                    onOpenMapPicker = { origin -> navigator.push(SettingsRoute.MapPicker, origin) },
                )
            },
        ) { route ->
            when (route) {
                SettingsRoute.MapPicker -> NukeMapPickerScreen(
                    initialLatitude = settings.config.latitude,
                    initialLongitude = settings.config.longitude,
                    // 顶栏返回键：optimizeExitOrigin = false 让 from 被丢弃，
                    // 圆心保持进入时的「地图选点」触点（传 true 才会缩回返回键位置）
                    onBack = { navigator.pop(optimizeExitOrigin = false) },
                    onConfirm = { lat, lng ->
                        settings.update {
                            latitude = lat.toString()
                            longitude = lng.toString()
                        }
                        navigator.pop(optimizeExitOrigin = false)
                    },
                )
            }
        }

        // 破坏性动作的确认框。NukeDialogSurface 自带 0.94/0.92 → 1.025/1.04 三段 keyframe
        // 入场与 180ms 退场延迟；弹窗走独立窗口，NukeTheme 由外层 CompositionLocal 供给。
        pendingConfirm?.let { action ->
            NukeSimpleDialog(
                title = action.title,
                message = action.confirm ?: action.summary,
                onDismiss = { pendingConfirm = null },
                confirmText = "确认重置",
                dismissText = "取消",
                onConfirm = { action.onClick() },
            )
        }
    }
}

/**
 * Nuke 风格的路由。目前只有地图选点一个页面 —— 它需要圆形揭示转场，
 * 所以必须是同一窗口内的 Compose 页面，不能是独立 Activity。
 */
private sealed interface SettingsRoute {
    data object MapPicker : SettingsRoute
}

/** 设置主页（转场栈的 base 层） */
@Composable
private fun NukeSettingsPage(
    style: UiStyle,
    settings: HookSettingsState,
    scaffold: SettingsScaffold,
    onConfirmRequest: (HookSettingRow.Action) -> Unit,
    onOpenMapPicker: (Offset) -> Unit,
) {
    NukePageScaffold(
        title = "ChaoxingHook 设置",
        // 模块主页就是本页，没有上一级可回；传空实现即不渲染返回键
        onBack = {},
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 28.dp),
    ) {
        settings.buildHookSettingGroups(scaffold).forEach { group ->
            item(key = "group-${group.title}") {
                NukeSettingGroup(title = group.title) {
                    group.rows.forEachIndexed { i, row ->
                        // 行间分割线：缩进 64dp 对齐正文，与 WeKit 的观感一致
                        if (i > 0) NukeDivider()
                        NukeSettingRow(
                            row = row,
                            scaffold = scaffold,
                            onConfirmRequest = onConfirmRequest,
                            onOpenMapPicker = onOpenMapPicker,
                        )
                    }
                }
            }
        }

        // ============ 界面风格 ============
        item(key = "group-ui-style") {
            NukeSettingGroup(title = "界面风格") {
                NukeUiStylePreference(current = style)
            }
        }
    }
}

/**
 * 三套风格的单选浮层。
 *
 * `NukeSelectPreference.onSelected` 是普通 `() -> Unit` 风格的回调（不是 `(Offset) -> Unit`），
 * 所以这里不需要触点；切换后 [rememberUiStyleSwitcher] 会落盘并重建 Activity。
 */
@Composable
private fun NukeUiStylePreference(current: UiStyle) {
    val onStyleChange = rememberUiStyleSwitcher()
    val colors = NukeTheme.colors

    Column {
        NukeText(
            text = "UI 风格",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
            color = colors.textPrimary,
            fontSize = 15,
            lineHeight = 20,
            fontWeight = FontWeight.Medium,
        )
        NukeSelectPreference(
            title = "当前风格",
            description = "切换后设置页立即重建，学习通内的 hook 不受影响",
            options = uiStyleOptions,
            selected = current,
            optionLabel = { it.label },
            onSelected = { selected -> onStyleChange(selected) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 单行渲染：把与 UI 库无关的 [HookSettingRow] 映射到 Nuke 组件 */
@Composable
private fun NukeSettingRow(
    row: HookSettingRow,
    scaffold: SettingsScaffold,
    onConfirmRequest: (HookSettingRow.Action) -> Unit,
    onOpenMapPicker: (Offset) -> Unit,
) {
    when (row) {
        // 开关行必须给 onClick：NukePreferenceRow 内部是
        // `if (onClick == null) Modifier else nukeJellyClickable(...)`，
        // 不给就整行不挂按压 —— 果冻缩放 0.92、3D 倾斜、原点跟随手指、触感全都不会发生。
        // 点行任意位置 = 切开关，这也是该组件的标准交互区。
        is HookSettingRow.Toggle -> NukePreferenceRow(
            title = row.title,
            description = row.summary,
            onClick = { row.onCheckedChange(!row.checked) },
            trailing = {
                NukeSwitch(
                    checked = row.checked,
                    onCheckedChange = row.onCheckedChange,
                )
            },
        )

        // 破坏性动作（reset）交给 NukeSimpleDialog 确认，顺带让弹窗的三段 keyframe 缩放生效
        is HookSettingRow.Action -> NukePreferenceRow(
            title = row.title,
            description = row.summary,
            onClick = {
                if (row.confirm != null) onConfirmRequest(row) else row.onClick()
            },
            trailing = { NukeCountAndChevron(text = null) },
        )

        // 地图选点：Nuke 风格走窗口内路由，触点直接作为圆形揭示的圆心。
        // （MIUIX / Material 3 仍走 scaffold.launchMapPicker() → MapPickerActivity）
        is HookSettingRow.MapPick -> NukePreferenceRow(
            title = row.title,
            description = row.summary,
            onClick = { origin -> onOpenMapPicker(origin) },
            trailing = { NukeCountAndChevron(text = null) },
        )

        // 输入行用 NukeTextField：聚焦圆角 11→13dp、2dp 边框浮现、0.996/1.012 拉伸、
        // 内容下移 1dp、placeholder 淡化这一整套聚焦动效都在这个组件里。
        // 不给 chevron —— 输入框自己就是编辑区，再加箭头会误导向"点开新页"。
        is HookSettingRow.Text -> NukePreferenceRow(
            title = row.label,
            trailing = {
                NukeTextField(
                    value = row.value,
                    onValueChange = row.onValueChange,
                    modifier = Modifier.fillMaxWidth(0.62f),
                    singleLine = true,
                )
            },
        )
    }
}
