package com.fredoseep.chaoxinghook

/**
 * 设置项的「行模型」：把原来的 MD3 设置页逐行抽象成与 UI 库无关的描述。
 *
 * 三套风格（MIUIX / Nuke / Material 3）各自把 [HookSettingRow] 渲染成自己的组件：
 * 开关 → `SwitchPreference` / `NukePreferenceRow + NukeSwitch` / `ListItem + Switch`，
 * 文案、显隐条件、读写逻辑全部只在这一个文件里定义，避免三份实现漂移。
 */
sealed interface HookSettingRow {

    /** 一行开关 */
    data class Toggle(
        val title: String,
        val summary: String,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
    ) : HookSettingRow

    /** 一行文本输入（只在对应开关打开时出现） */
    data class Text(
        val label: String,
        val value: String,
        val onValueChange: (String) -> Unit,
    ) : HookSettingRow

    /**
     * 一行动作（点击立即执行，例如重置 / 打开文件工具 / 打开地图）
     *
     * @param confirm 非空时表示这是破坏性动作，渲染层应先弹确认框再执行。
     *   三套风格各用各的对话框（Nuke 用 `NukeSimpleDialog`，MIUIX / M3 各自实现），
     *   文案只有这一份。
     */
    data class Action(
        val title: String,
        val summary: String,
        val onClick: () -> Unit,
        val confirm: String? = null,
    ) : HookSettingRow

    /**
     * 动作行的特殊形态：把地图选点塞进「经纬度输入」组里。
     * 单独留一个类型是为了让 Nuke 有机会在它上面用 `NukePreferenceRow` 的 chevron 样式。
     */
    data class MapPick(
        val title: String,
        val summary: String,
        val onClick: () -> Unit,
    ) : HookSettingRow
}

/** 一个分组 = 小标题 + 若干行 */
data class HookSettingGroup(
    val title: String,
    val rows: List<HookSettingRow>,
)

/**
 * 构建三套风格共用的四个业务分组。
 *
 * 顺序与原来的实现一致：签到 → 信息修改 → 风控与考试 → 其他。
 * 「界面风格」分组由各风格自己拼（它需要风格切换回调，不属于 hook 业务）。
 *
 * @param scaffold 打开文件工具 与地图选点要走 Activity Result，只能由 `@Composable` 提供
 */
fun HookSettingsState.buildHookSettingGroups(scaffold: SettingsScaffold): List<HookSettingGroup> {
    val cfg = config

    /** 经纬度两行输入 + 地图选点，定位与爆破共用（两处都展开时各自一份） */
    fun coordinateRows(): List<HookSettingRow> = listOf(
        HookSettingRow.Text(
            label = "经度",
            value = cfg.longitude,
            onValueChange = { v -> update { longitude = v } },
        ),
        HookSettingRow.Text(
            label = "纬度",
            value = cfg.latitude,
            onValueChange = { v -> update { latitude = v } },
        ),
        // onClick 是空实现：地图选点要用 Activity Result 启动器，只有 @Composable 能拿到，
        // 所以由各风格渲染时接上 SettingsScaffold.launchMapPicker（见各 Screen 文件）
        HookSettingRow.MapPick(
            title = "地图选点",
            summary = "打开地图选择精确坐标",
            onClick = {},
        ),
    )

    return listOf(
        // ============ 签到（原定位） ============
        HookSettingGroup(
            title = "签到",
            rows = buildList {
                add(
                    HookSettingRow.Toggle(
                        title = "定位修改",
                        summary = "打卡/签到提交自定义经纬度",
                        checked = cfg.modifyLocation,
                        onCheckedChange = { on ->
                            // 与「经纬度爆破」互斥
                            if (on) update { autoCalculateLocation = false }
                            update { modifyLocation = on }
                        },
                    )
                )
                if (cfg.modifyLocation) {
                    addAll(coordinateRows())
                }
                add(
                    HookSettingRow.Toggle(
                        title = "经纬度爆破",
                        summary = "通过三点距离自动逼近目标坐标",
                        checked = cfg.autoCalculateLocation,
                        onCheckedChange = { on ->
                            if (on) update { modifyLocation = false }
                            update { autoCalculateLocation = on }
                        },
                    )
                )
                if (cfg.autoCalculateLocation) {
                    addAll(coordinateRows())
                }
            },
        ),

        // ============ 信息修改 ============
        HookSettingGroup(
            title = "信息修改",
            rows = buildList {
                add(
                    HookSettingRow.Toggle(
                        title = "地址名修改",
                        summary = "签到提交自定义地址名",
                        checked = cfg.modifyAddress,
                        onCheckedChange = { on -> update { modifyAddress = on } },
                    )
                )
                if (cfg.modifyAddress) {
                    add(
                        HookSettingRow.Text(
                            label = "地址名",
                            value = cfg.address,
                            onValueChange = { v -> update { address = v } },
                        )
                    )
                }
                add(
                    HookSettingRow.Toggle(
                        title = "名字修改",
                        summary = "签到提交自定义名字",
                        checked = cfg.modifyName,
                        onCheckedChange = { on -> update { modifyName = on } },
                    )
                )
                if (cfg.modifyName) {
                    add(
                        HookSettingRow.Text(
                            label = "名字",
                            value = cfg.name,
                            onValueChange = { v -> update { name = v } },
                        )
                    )
                }
            },
        ),

        // ============ 风控与考试 ============
        HookSettingGroup(
            title = "风控与考试",
            rows = buildList {
                add(
                    HookSettingRow.Toggle(
                        title = "随机指纹",
                        summary = "用于单设备多账号签到",
                        checked = cfg.randomizeDeviceFlag,
                        onCheckedChange = { on -> update { randomizeDeviceFlag = on } },
                    )
                )
                add(
                    HookSettingRow.Toggle(
                        title = "考试风控拦截",
                        summary = "拦截考试日志、切屏检测和异常进程退出",
                        checked = cfg.bypassExamCheat,
                        onCheckedChange = { on -> update { bypassExamCheat = on } },
                    )
                )
                add(
                    HookSettingRow.Toggle(
                        title = "复制限制解除",
                        summary = "拦截 notAllowCopy.css，允许网页复制",
                        checked = cfg.enableCopyRestriction,
                        onCheckedChange = { on -> update { enableCopyRestriction = on } },
                    )
                )
                add(
                    HookSettingRow.Toggle(
                        title = "考试截图替换",
                        summary = "监考截图上传时替换为指定图片",
                        checked = cfg.replaceExamScreenshot,
                        onCheckedChange = { on -> update { replaceExamScreenshot = on } },
                    )
                )
                if (cfg.replaceExamScreenshot) {
                    add(
                        HookSettingRow.Text(
                            label = "截图替换路径",
                            value = cfg.fakeImagePath,
                            onValueChange = { v -> update { fakeImagePath = v } },
                        )
                    )
                }
            },
        ),

        // ============ 其他 ============
        HookSettingGroup(
            title = "其他",
            rows = listOf(
                HookSettingRow.Action(
                    title = "本地配置与文件工具",
                    summary = "无需 Root；支持配置导入、导出与图片选择",
                    onClick = { scaffold.openFileTools() },
                ),
                HookSettingRow.Action(
                    title = "重置所有配置",
                    summary = "恢复默认值并立即保存",
                    onClick = { reset() },
                    confirm = "所有开关与坐标都会恢复默认值，且立即写入配置文件。此操作不可撤销。",
                ),
            ),
        ),
    )
}