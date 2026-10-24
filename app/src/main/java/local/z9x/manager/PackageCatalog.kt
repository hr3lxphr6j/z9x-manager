package local.z9x.manager

/** Curated XGIMI GMUI package lists for Z9X-class devices. */
object PackageCatalog {
    data class Entry(val pkg: String, val label: String)

    /** Safe-to-remove (user 0). Labels in Chinese. */
    val SAFE: List<Entry> = listOf(
        Entry("com.xgimi.adservice", "广告服务"),
        Entry("com.xgimi.atmosphere", "氛围/运营"),
        Entry("com.xgimi.datareporter", "数据上报"),
        Entry("com.xgimi.upgrade", "系统升级（防 OTA 装回广告）"),
        Entry("com.xgimi.newappmarket", "应用市场"),
        Entry("com.xgimi.bugreportsender", "故障上报"),
        Entry("com.xgimi.stream.video", "爱奇艺 SDK / 流媒体"),
        Entry("com.xgimi.remote", "远程调试客户端"),
        Entry("com.xgimi.doubanfm", "豆瓣电台预装"),
    )

    /** Do-not-touch hardware / desktop / input. Shown locked. */
    val LOCKED: List<Entry> = listOf(
        Entry("com.xgimi.home", "极米桌面"),
        Entry("com.android.systemui", "系统界面"),
        Entry("com.xgimi.xgimiservice", "极米核心服务"),
        Entry("com.xgimi.gmservice", "GM 服务"),
        Entry("com.xgimi.tof", "TOF 测距"),
        Entry("com.xgimi.autokst", "自动梯形/校正"),
        Entry("com.xgimi.xhplayer", "播放器"),
        Entry("com.xgimi.xrmservice", "XR 服务"),
        Entry("com.xgimi.tvinput", "电视输入"),
        Entry("com.xgimi.duertts", "语音 TTS"),
        Entry("com.xgimi.bluetoothservice", "蓝牙服务"),
        Entry("com.xgimi.bootwizard", "开机向导"),
        Entry("com.xgimi.filemanager", "文件管理"),
        Entry("com.xgimi.vcontrol", "语音控制"),
        Entry("com.xgimi.config", "配置"),
    )

    const val AERIAL_PKG = "app.orchard.tv"
    const val XGIMI_HOME = "com.xgimi.home"
}
