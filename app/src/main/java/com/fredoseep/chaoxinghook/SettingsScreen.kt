package com.fredoseep.chaoxinghook

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * MIUIX（HyperOS 风格）设置页
 * 覆盖原 XML 设置页全部功能：定位修改/经纬度爆破（互斥）、地址/名字修改、
 * 随机指纹、考试风控拦截、复制限制解除、考试截图替换、地图选点、实时保存、重置。
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val controller = remember { ThemeController(ColorSchemeMode.System) }

    var config by remember { mutableStateOf(ConfigManager.load()) }
    val currentConfig by rememberUpdatedState(config)

    fun update(block: ConfigManager.HookConfig.() -> Unit) {
        // 必须 copy() 创建新实例：mutableStateOf 按引用比较，apply 原地修改不触发重组
        config = config.copy().apply(block)
        val ok = ConfigManager.save(config)
        Toast.makeText(context, if (ok) "配置已保存" else "保存失败，可能需要 Root", Toast.LENGTH_SHORT).show()
    }

    val mapPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val lat = result.data!!.getDoubleExtra("latitude", 0.0)
            val lng = result.data!!.getDoubleExtra("longitude", 0.0)
            update {
                latitude = lat.toString()
                longitude = lng.toString()
            }
        }
    }

    fun launchMapPicker() {
        val intent = Intent(context, MapPickerActivity::class.java)
        mapPickerLauncher.launch(intent)
    }

    // ==================== Root / 应用列表权限 ====================
    // 工信部规范权限（ColorOS/MIUI 等国产 ROM 定义），未授权时申请 Root 前需先弹窗申请
    val APP_LIST_PERMISSION = "com.android.permission.GET_INSTALLED_APPS"

    /** Magisk 是否安装（Manifest queries 已声明其包名保证可见性；Magisk 隐藏包名时检测不到，走兜底提示） */
    fun isMagiskInstalled(): Boolean = try {
        context.packageManager.getPackageInfo("com.topjohnwu.magisk", 0)
        true
    } catch (_: Exception) {
        false
    }

    fun requestRoot() {
        // su 请求会阻塞等待用户在 Magisk 弹窗中确认，必须放后台线程
        Thread {
            val output = try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                val out = p.inputStream.bufferedReader().readText()
                p.waitFor()
                out
            } catch (_: Exception) {
                ""
            }
            Handler(Looper.getMainLooper()).post {
                when {
                    // su -c id 成功且确为 uid=0(root) 才算授权
                    output.contains("uid=0") ->
                        Toast.makeText(context, "已获得 Root 授权", Toast.LENGTH_SHORT).show()
                    // Magisk 在但被拒：多半是之前勾过"记住拒绝"，或超级用户列表里策略为拒绝
                    isMagiskInstalled() ->
                        Toast.makeText(
                            context,
                            "Root 申请未通过：请打开 Magisk → 超级用户，允许本应用（若勾选过\"记住拒绝\"需先删除该记录）",
                            Toast.LENGTH_LONG
                        ).show()
                    else ->
                        Toast.makeText(context, "未检测到 Magisk，无法申请 Root", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    val appListPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // ColorOS 16 等 ROM 的授权结果可能不反映在标准回调中，以实际权限状态为准
        val nowGranted = try {
            context.checkSelfPermission(APP_LIST_PERMISSION) == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
        if (nowGranted) {
            // 应用列表权限到手，继续申请 Root
            requestRoot()
        } else {
            Toast.makeText(
                context,
                "未获得应用列表权限，请到系统设置 → 应用 → ChaoxingHook → 权限 中手动开启",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** 应用列表权限已授权（或 ROM 不管控）→ 直接申请 Root；否则先弹应用列表权限申请 */
    fun requestRootOrAppList() {
        val defined = try {
            context.packageManager.getPermissionInfo(APP_LIST_PERMISSION, 0); true
        } catch (_: Exception) { false }
        val hasAppList = try {
            context.checkSelfPermission(APP_LIST_PERMISSION) == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) { false }
        if (defined && !hasAppList) {
            appListPermissionLauncher.launch(APP_LIST_PERMISSION)
        } else {
            requestRoot()
        }
    }

    MiuixTheme(controller = controller) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = "ChaoxingHook 设置",
                )
            },
        ) { innerPadding ->
            LazyColumn(contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding(),
            )) {

                // ============ 签到（原定位） ============
                item { SmallTitle(text = "签到") }
                item {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        SwitchPreference(
                            title = "定位修改",
                            summary = "打卡/签到提交自定义经纬度",
                            checked = config.modifyLocation,
                            onCheckedChange = { on ->
                                if (on) update { autoCalculateLocation = false }
                                update { modifyLocation = on }
                            },
                        )
                        AnimatedVisibility(visible = currentConfig.modifyLocation) {
                            CoordinateFields(
                                latitude = currentConfig.latitude,
                                longitude = currentConfig.longitude,
                                onLatitudeChange = { update { latitude = it } },
                                onLongitudeChange = { update { longitude = it } },
                                onMapPick = ::launchMapPicker,
                            )
                        }
                        SwitchPreference(
                            title = "经纬度爆破",
                            summary = "通过三点距离自动逼近目标坐标",
                            checked = config.autoCalculateLocation,
                            onCheckedChange = { on ->
                                if (on) update { modifyLocation = false }
                                update { autoCalculateLocation = on }
                            },
                        )
                        AnimatedVisibility(visible = currentConfig.autoCalculateLocation) {
                            CoordinateFields(
                                latitude = currentConfig.latitude,
                                longitude = currentConfig.longitude,
                                onLatitudeChange = { update { latitude = it } },
                                onLongitudeChange = { update { longitude = it } },
                                onMapPick = ::launchMapPicker,
                            )
                        }
                    }
                }

                // ============ 信息修改 ============
                item { SmallTitle(text = "信息修改") }
                item {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        SwitchPreference(
                            title = "地址名修改",
                            summary = "签到提交自定义地址名",
                            checked = config.modifyAddress,
                            onCheckedChange = { update { modifyAddress = it } },
                        )
                        AnimatedVisibility(visible = currentConfig.modifyAddress) {
                            SingleTextField(
                                value = currentConfig.address,
                                label = "地址名",
                                onValueChange = { update { address = it } },
                            )
                        }
                        SwitchPreference(
                            title = "名字修改",
                            summary = "签到提交自定义名字",
                            checked = config.modifyName,
                            onCheckedChange = { update { modifyName = it } },
                        )
                        AnimatedVisibility(visible = currentConfig.modifyName) {
                            SingleTextField(
                                value = currentConfig.name,
                                label = "名字",
                                onValueChange = { update { name = it } },
                            )
                        }
                    }
                }

                // ============ 风控与考试 ============
                item { SmallTitle(text = "风控与考试") }
                item {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        SwitchPreference(
                            title = "随机指纹",
                            summary = "用于单设备多账号签到",
                            checked = config.randomizeDeviceFlag,
                            onCheckedChange = { update { randomizeDeviceFlag = it } },
                        )
                        SwitchPreference(
                            title = "考试风控拦截",
                            summary = "拦截考试日志、切屏检测和异常进程退出",
                            checked = config.bypassExamCheat,
                            onCheckedChange = { update { bypassExamCheat = it } },
                        )
                        SwitchPreference(
                            title = "复制限制解除",
                            summary = "拦截 notAllowCopy.css，允许网页复制",
                            checked = config.enableCopyRestriction,
                            onCheckedChange = { update { enableCopyRestriction = it } },
                        )
                        SwitchPreference(
                            title = "考试截图替换",
                            summary = "监考截图上传时替换为指定图片",
                            checked = config.replaceExamScreenshot,
                            onCheckedChange = { update { replaceExamScreenshot = it } },
                        )
                        AnimatedVisibility(visible = currentConfig.replaceExamScreenshot) {
                            SingleTextField(
                                value = currentConfig.fakeImagePath,
                                label = "截图替换路径",
                                onValueChange = { update { fakeImagePath = it } },
                            )
                        }
                    }
                }

                // ============ 其他 ============
                item {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        ArrowPreference(
                            title = "申请 Root 权限",
                            summary = "弹出 Magisk 授权确认（未授权应用列表时先申请）",
                            onClick = { requestRootOrAppList() },
                        )
                        ArrowPreference(
                            title = "重置所有配置",
                            summary = "恢复默认值并立即保存",
                            onClick = {
                                config = ConfigManager.reset()
                                Toast.makeText(context, "配置已重置", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 经度/纬度输入 + 地图选点（两处复用：普通定位与爆破）
 *  注意：必须包 Column —— AnimatedVisibility 内多个并列组件会重叠堆叠 */
@Composable
private fun CoordinateFields(
    latitude: String,
    longitude: String,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onMapPick: () -> Unit,
) {
    Column {
        SingleTextField(
            value = longitude,
            label = "经度",
            onValueChange = onLongitudeChange,
        )
        SingleTextField(
            value = latitude,
            label = "纬度",
            onValueChange = onLatitudeChange,
        )
        ArrowPreference(
            title = "地图选点",
            summary = "打开地图选择精确坐标",
            onClick = onMapPick,
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
