package com.fredoseep.chaoxinghook

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * 设置页入口：MIUIX（HyperOS 风格）Compose UI
 * 类名与 AndroidManifest 保持一致，原 XML 布局实现已由 Compose 重写。
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAppListPermission()
        setContent {
            SettingsScreen()
        }
    }

    /**
     * 触发系统"获取应用列表"权限弹窗（工信部 TTAF 108-2022 规范，国产 ROM 统一管控）。
     * - ColorOS 16 / OPPO：open.oppomobile.com《读取应用列表权限适配方案》
     * - MIUI 13+ / HyperOS：dev.mi.com《获取应用列表权限的适配说明》（权限由 com.lbe.security.miui 定义）
     * 两者均需 Manifest 声明 QUERY_ALL_PACKAGES + com.android.permission.GET_INSTALLED_APPS，
     * 后者为 dangerous 运行时权限，requestPermissions 弹出系统授权弹窗。
     * ROM 未定义该权限（原生 Android 等）时 getPermissionInfo 抛异常，静默跳过。
     * 延迟 600ms 再申请：MIUI 对 onCreate 立即发起的权限请求可能静默拒绝。
     */
    private fun requestAppListPermission() {
        Handler(Looper.getMainLooper()).postDelayed({
            val perm = "com.android.permission.GET_INSTALLED_APPS"
            try {
                packageManager.getPermissionInfo(perm, 0)
                if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(perm), 1001)
                }
            } catch (_: Exception) {
            }
        }, 600)
    }
}
