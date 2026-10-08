package com.fredoseep.chaoxinghook

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import java.io.File

/**
 * 设置页入口：Compose UI，支持 MIUIX（HyperOS）/ Nuke / Material 3 三套风格。
 *
 * 具体渲染哪一套由 [SettingsRoot] 按 [SettingStore] 里的偏好分发；
 * 类名与 AndroidManifest 保持一致，原 XML 布局实现已由 Compose 重写。
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfigManager.initialize(this)
        if (!canAccessUnifiedConfig()) requestAllFilesAccess()
        setContent {
            SettingsRoot()
        }
    }

    /**
     * 统一配置在公共 Download（/sdcard/Download/chaoxing_loc.txt）。
     * 本 App 读写它需要「所有文件访问」；缺权限时自动弹引导，授权后重开本页即生效。
     */
    private fun canAccessUnifiedConfig(): Boolean {
        if (Environment.isExternalStorageManager()) return true
        // 兜底实测：个别 ROM 的旧存储视图不加权限也能直连，用探针文件验真避免误弹
        return try {
            val dir = File(UNIFIED_CONFIG_DIR)
            (dir.isDirectory || dir.mkdirs()) && File(dir, ".cxprobe").let { p ->
                (p.createNewFile() || p.exists()).also { if (p.exists()) p.delete() }
            }
        } catch (t: Throwable) {
            false
        }
    }

    private fun requestAllFilesAccess() {
        android.app.AlertDialog.Builder(this)
            .setTitle("需要「所有文件访问」权限")
            .setMessage(
                "模块与学习通共用同一份配置文件，位置在公共 Download 目录：\n" +
                    UNIFIED_CONFIG_DIR + "\n\n" +
                    "本应用需要「所有文件访问」权限才能稳定读写它。\n" +
                    "点击「去授权」跳转系统设置打开开关，然后返回重开本页即可。"
            )
            .setNegativeButton("取消", null)
            .setPositiveButton("去授权") { _, _ ->
                try {
                    startActivity(
                        Intent(
                            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (t: Throwable) {
                    try {
                        startActivity(
                            Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        )
                    } catch (ignored: Throwable) {}
                }
            }
            .show()
    }

    @Deprecated("Compatibility with native file tools")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!EmbeddedSettings.onActivityResult(this, requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private companion object {
        const val UNIFIED_CONFIG_DIR = "/storage/emulated/0/Download"
    }
}
