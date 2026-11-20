package com.fredoseep.chaoxinghook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fredoseep.chaoxinghook.function.HookSettingRow
import com.fredoseep.chaoxinghook.function.buildHookSettingGroups
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 设置页 —— **MIUIX（HyperOS 风格）** 实现。
 *
 * 覆盖全部功能：定位修改/经纬度爆破（互斥）、地址/名字修改、随机指纹、
 * 考试风控拦截、复制限制解除、考试截图替换、地图选点、实时保存、重置，
 * 以及三套 UI 风格（MIUIX / Nuke / Material 3）的切换。
 */
@Composable
fun SettingsMiuixScreen() {
    val controller = remember { ThemeController(ColorSchemeMode.System) }

    MiuixTheme(controller = controller) {
        Scaffold(
            topBar = {
                TopAppBar(title = "ChaoxingHook 设置")
            },
        ) { innerPadding ->
            val settings = rememberHookSettingsState()
            val scaffold = rememberSettingsScaffold(settings)
            val groups = settings.buildHookSettingGroups(scaffold)

            LazyColumn(
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding(),
                ),
            ) {
                groups.forEach { group ->
                    item(key = "title-${group.title}") { SmallTitle(text = group.title) }
                    item(key = "card-${group.title}") {
                        Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                            MiuixGroupContent(group.rows, scaffold)
                        }
                    }
                }

                // ============ 界面风格（三套 UI 风格切换） ============
                item { SmallTitle(text = "界面风格") }
                item {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        MiuixUiStylePicker()
                    }
                }
            }
        }
    }
}

/**
 * 三套风格的开关式单选：选中的那个开关是「开」，其余是「关」。
 *
 * 用 SwitchPreference 而不是下拉：这样复用现有组件、三套风格各自的选择控件也都符合
 * 自己的设计语言（Nuke 用 NukeSelectPreference、Material 3 用 RadioButton）。
 */
@Composable
private fun MiuixUiStylePicker() {
    val selected = rememberCurrentUiStyle()
    val onStyleChange = rememberUiStyleSwitcher()

    Column {
        uiStyleOptions.forEach { style ->
            SwitchPreference(
                title = style.label,
                summary = style.description,
                checked = selected == style,
                onCheckedChange = {
                    // 开关只是个「单选」外观，点当前项不做事
                    onStyleChange(style)
                },
            )
        }
    }
}

/**
 * 一组设置行。
 *
 * 行模型里「表单行只在开关打开时出现」这件事已经在 [buildHookSettingGroups] 里决定，
 * 这里只需按序渲染。整组包一个 [Column] 是必需的 ——
 * `AnimatedVisibility` 内多个并列组件会重叠堆叠，这是原实现的踩坑记录。
 */
@Composable
private fun MiuixGroupContent(rows: List<HookSettingRow>, scaffold: SettingsScaffold) {
    Column {
        rows.forEach { MiuixRow(it, scaffold) }
    }
}

@Composable
private fun MiuixRow(row: HookSettingRow, scaffold: SettingsScaffold) {
    when (row) {
        is HookSettingRow.Toggle -> SwitchPreference(
            title = row.title,
            summary = row.summary,
            checked = row.checked,
            onCheckedChange = row.onCheckedChange,
        )

        is HookSettingRow.Action -> ArrowPreference(
            title = row.title,
            summary = row.summary,
            onClick = row.onClick,
        )

        // 地图选点必须走 scaffold：启动 Activity Result 只能在 @Composable 里接线，
        // 行模型里的 onClick 是空实现（见 HookSettingsRows.coordinateRows）
        is HookSettingRow.MapPick -> ArrowPreference(
            title = row.title,
            summary = row.summary,
            onClick = { scaffold.launchMapPicker() },
        )

        is HookSettingRow.Text -> SingleTextField(
            value = row.value,
            label = row.label,
            onValueChange = row.onValueChange,
        )
    }
}

/** 带清空按钮的单行输入框 */
@Composable
private fun SingleTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
) {
    TextField(
        value = TextFieldValue(value),
        onValueChange = { onValueChange(it.text) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
        label = label,
        singleLine = true,
        trailingIcon = {
            if (value.isNotEmpty()) {
                Text(
                    text = "✕",
                    fontSize = 16.sp,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .clickable { onValueChange("") },
                )
            }
        },
    )
}
