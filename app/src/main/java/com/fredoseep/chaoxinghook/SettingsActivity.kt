package com.fredoseep.chaoxinghook

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

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
        setContent {
            SettingsRoot()
        }
    }

    @Deprecated("Compatibility with native file tools")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!EmbeddedSettings.onActivityResult(this, requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}
