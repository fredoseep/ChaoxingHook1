package com.fredoseep.chaoxinghook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fredoseep.chaoxinghook.function.HookSettingRow
import com.fredoseep.chaoxinghook.function.buildHookSettingGroups

/**
 * 设置页 —— **Material 3** 实现。
 *
 * 用 `lightColorScheme` / `darkColorScheme` 的 `primary` 承接所选风格的强调色
 * （[UiStyle.Material3] 即是 Material You 的种子紫），不引动态取色 ——
 * 设置页是独立入口，跟随壁纸取色会让「切风格」这件事失去参照。
 *
 * `TopAppBar` 在 material3 1.4.0 仍是实验 API，这里显式 opt-in。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMaterial3Screen() {
    val style = rememberCurrentUiStyle()
    val dark = isSystemInDarkTheme()
    val colorScheme = remember(style, dark) {
        if (dark) darkColorScheme(primary = style.accent) else lightColorScheme(primary = style.accent)
    }

    MaterialTheme(colorScheme = colorScheme) {
        val settings = rememberHookSettingsState()
        val scaffold = rememberSettingsScaffold(settings)
        val groups = settings.buildHookSettingGroups(scaffold)

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("ChaoxingHook 设置") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            },
        ) { innerPadding ->
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEach { group ->
                    item(key = "card-${group.title}") {
                        M3GroupCard(title = group.title) {
                            group.rows.forEachIndexed { i, row ->
                                if (i > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                                M3Row(row, scaffold)
                            }
                        }
                    }
                }

                // ============ 界面风格（三套 UI 风格切换） ============
                item(key = "card-ui-style") {
                    M3GroupCard(title = "界面风格") {
                        M3UiStylePicker(rememberCurrentUiStyle(), rememberUiStyleSwitcher())
                    }
                }
            }
        }
    }
}

/**
 * 一个分组 = **卡外小标题 + 一张四角圆角的卡片**，组内用 [HorizontalDivider] 分隔。
 *
 * 这与 MIUIX（`SmallTitle` + `Card`）和 Nuke（`NukeSettingGroup`）的分组方式一致；
 * 小标题刻意放在卡片**外面** —— 之前放在卡内做成透明 ListItem，
 * 结果标题和内容糊在同一块底色上，分组边界看不出来。
 */
@Composable
private fun M3GroupCard(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun M3Row(row: HookSettingRow, scaffold: SettingsScaffold) {
    when (row) {
        is HookSettingRow.Toggle -> ListItem(
            headlineContent = { Text(row.title) },
            supportingContent = { Text(row.summary) },
            trailingContent = {
                Switch(
                    checked = row.checked,
                    onCheckedChange = row.onCheckedChange,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )

        is HookSettingRow.Action -> ListItem(
            headlineContent = { Text(row.title) },
            supportingContent = { Text(row.summary) },
            modifier = Modifier.clickableRow(row.onClick),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )

        is HookSettingRow.MapPick -> ListItem(
            headlineContent = { Text(row.title) },
            supportingContent = { Text(row.summary) },
            modifier = Modifier.clickableRow { scaffold.launchMapPicker() },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )

        is HookSettingRow.Text -> ListItem(
            headlineContent = { Text(row.label) },
            trailingContent = {
                OutlinedTextField(
                    value = row.value,
                    onValueChange = row.onValueChange,
                    modifier = Modifier.fillMaxWidth(0.68f),
                    singleLine = true,
                    trailingIcon = {
                        if (row.value.isNotEmpty()) {
                            IconButton(onClick = { row.onValueChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "清空")
                            }
                        }
                    },
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

/** 动作行整行可点（Material 3 的 ListItem 自身不带点击） */
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

/**
 * 三套风格的单选。
 *
 * 用 `RadioButton` 而不是开关：单选语义在 Material 3 里就该是 radio，
 * 并且点已选中项不会有副作用。
 */
@Composable
private fun M3UiStylePicker(selected: UiStyle, onStyleChange: (UiStyle) -> Unit) {
    Column {
        uiStyleOptions.forEach { style ->
            ListItem(
                headlineContent = { Text(style.label) },
                supportingContent = { Text(style.description) },
                leadingContent = {
                    RadioButton(
                        selected = selected == style,
                        onClick = { onStyleChange(style) },
                    )
                },
                modifier = Modifier.clickableRow { onStyleChange(style) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}
