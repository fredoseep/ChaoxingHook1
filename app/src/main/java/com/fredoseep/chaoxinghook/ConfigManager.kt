package com.fredoseep.chaoxinghook

import android.content.Context

/** 独立 APK 使用自己的私有文件。宿主内设置直接使用宿主文件，无需 Root。 */
object ConfigManager {
    const val CONFIG_PATH = "/storage/emulated/0/Android/data/com.chaoxing.mobile/files/chaoxing_loc.txt"
    const val DEFAULT_FAKE_IMAGE_PATH = "/storage/emulated/0/Download/fake_exam_image.png"
    private var appContext: Context? = null
    fun initialize(context: Context) { appContext = context.applicationContext }
    private fun context(): Context = checkNotNull(appContext) { "请先初始化 ConfigManager" }
    @Volatile var loadFailed: Boolean = false
        private set

    data class HookConfig(
        var modifyLocation: Boolean = false,
        var longitude: String = "",
        var latitude: String = "",
        var modifyAddress: Boolean = false,
        var address: String = "",
        var modifyName: Boolean = false,
        var name: String = "",
        var randomizeDeviceFlag: Boolean = true,
        var autoCalculateLocation: Boolean = false,
        var bypassExamCheat: Boolean = true,
        var enableCopyRestriction: Boolean = true,
        var replaceExamScreenshot: Boolean = false,
        var fakeImagePath: String = DEFAULT_FAKE_IMAGE_PATH,
    )

    fun load(): HookConfig = try {
        val text = ConfigStorage.read(context(), serialize(HookConfig()))
        loadFailed = false
        parseConfig(text)
    } catch (_: Exception) {
        loadFailed = true
        HookConfig()
    }

    fun save(config: HookConfig): Boolean {
        if (loadFailed) return false
        return write(config)
    }
    fun reset(): Boolean = write(HookConfig())
    private fun write(config: HookConfig): Boolean = try {
        ConfigStorage.write(context(), serialize(config))
        loadFailed = false
        true
    } catch (_: Exception) { false }

    // ==================== 序列化 ====================

    /** 顺序与 MainHook 的默认模板、解析分支逐字对应，勿随意调整 */
    private fun serialize(config: HookConfig): String = buildString {
        append("是否开启定位修改: ").append(config.modifyLocation).append('\n')
        append("经度: ").append(config.longitude).append('\n')
        append("纬度: ").append(config.latitude).append('\n')
        append("是否开启地址名修改: ").append(config.modifyAddress).append('\n')
        append("地址名: ").append(config.address).append('\n')
        append("是否开启名字修改: ").append(config.modifyName).append('\n')
        append("名字: ").append(config.name).append('\n')
        append("是否开启随机指纹: ").append(config.randomizeDeviceFlag).append('\n')
        append("是否开启经纬度爆破: ").append(config.autoCalculateLocation).append('\n')
        append("是否开启考试风控拦截: ").append(config.bypassExamCheat).append('\n')
        append("是否开启复制限制解除: ").append(config.enableCopyRestriction).append('\n')
        append("是否开启考试截图替换: ").append(config.replaceExamScreenshot).append('\n')
        append("截图替换路径: ").append(config.fakeImagePath).append('\n')
    }

    private fun parseConfig(text: String): HookConfig {
        val config = HookConfig()
        text.lineSequence().forEach { raw ->
            val l = raw.trim()
            when {
                l.startsWith("是否开启定位修改:") -> config.modifyLocation = parseBoolean(l)
                l.startsWith("经度:") -> config.longitude = parseString(l)
                l.startsWith("纬度:") -> config.latitude = parseString(l)
                l.startsWith("是否开启地址名修改:") -> config.modifyAddress = parseBoolean(l)
                l.startsWith("地址名:") -> config.address = parseString(l)
                l.startsWith("是否开启名字修改:") -> config.modifyName = parseBoolean(l)
                l.startsWith("名字:") -> config.name = parseString(l)
                l.startsWith("是否开启随机指纹:") -> config.randomizeDeviceFlag = parseBoolean(l)
                l.startsWith("是否开启经纬度爆破:") -> config.autoCalculateLocation = parseBoolean(l)
                l.startsWith("是否开启考试风控拦截:") -> config.bypassExamCheat = parseBoolean(l)
                l.startsWith("是否开启复制限制解除:") -> config.enableCopyRestriction = parseBoolean(l)
                l.startsWith("是否开启考试截图替换:") -> config.replaceExamScreenshot = parseBoolean(l)
                l.startsWith("截图替换路径:") -> config.fakeImagePath = parseString(l)
            }
        }
        return config
    }

    private fun parseBoolean(line: String): Boolean =
        try { line.substring(line.indexOf(":") + 1).trim().equals("true", ignoreCase = true) } catch (_: Exception) { false }

    private fun parseString(line: String): String =
        try { line.substring(line.indexOf(":") + 1).trim() } catch (_: Exception) { "" }
}