package local.z9x.manager;

/** Curated XGIMI GMUI package lists for Z9X-class devices. */
public final class PackageCatalog {
    private PackageCatalog() {}

    /** Safe-to-remove (user 0). Labels in Chinese. */
    public static final Entry[] SAFE = new Entry[] {
            e("com.xgimi.adservice", "广告服务"),
            e("com.xgimi.atmosphere", "氛围/运营"),
            e("com.xgimi.datareporter", "数据上报"),
            e("com.xgimi.upgrade", "系统升级（防 OTA 装回广告）"),
            e("com.xgimi.newappmarket", "应用市场"),
            e("com.xgimi.bugreportsender", "故障上报"),
            e("com.xgimi.stream.video", "爱奇艺 SDK / 流媒体"),
            e("com.xgimi.remote", "远程调试客户端"),
            e("com.xgimi.doubanfm", "豆瓣电台预装"),
    };

    /** Do-not-touch hardware / desktop / input. Shown locked. */
    public static final Entry[] LOCKED = new Entry[] {
            e("com.xgimi.home", "极米桌面"),
            e("com.android.systemui", "系统界面"),
            e("com.xgimi.xgimiservice", "极米核心服务"),
            e("com.xgimi.gmservice", "GM 服务"),
            e("com.xgimi.tof", "TOF 测距"),
            e("com.xgimi.autokst", "自动梯形/校正"),
            e("com.xgimi.xhplayer", "播放器"),
            e("com.xgimi.xrmservice", "XR 服务"),
            e("com.xgimi.tvinput", "电视输入"),
            e("com.xgimi.duertts", "语音 TTS"),
            e("com.xgimi.bluetoothservice", "蓝牙服务"),
            e("com.xgimi.bootwizard", "开机向导"),
            e("com.xgimi.filemanager", "文件管理"),
            e("com.xgimi.vcontrol", "语音控制"),
            e("com.xgimi.config", "配置"),
    };

    public static final String AERIAL_PKG = "app.orchard.tv";
    public static final String XGIMI_HOME = "com.xgimi.home";

    public static final class Entry {
        public final String pkg;
        public final String label;
        public Entry(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }

    private static Entry e(String pkg, String label) {
        return new Entry(pkg, label);
    }
}
