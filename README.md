# 极米助手 / Z9X Manager

面向极米投影（GMUI / Z9X 等）的 **Leanback 友好** 管理 App。

包名：`local.z9x.manager`  
功能：

1. **开启网络 ADB** — best-effort `setprop` `persist/service.adb.tcp.port=5555` 并尝试拉起 `adbd`（与公开 Z7X-ADB / Z9X Helper 同类，失败安全）
2. **可选卸载 / 恢复预装** — 勾选清单后尝试 `pm uninstall --user 0` / `install-existing`；普通 uid 失败时一键复制电脑端 `adb shell` 命令
3. **安装并配置 [Aerial](https://github.com/pengchujin/aerial-tv)** — 下载 `aerial-standard.apk`、PackageInstaller 安装、导出已验证的配置命令

## 明确不做

- 不写 `/data/ssh`、不起 `xgimi_ssh`、不写 `adb_keys`
- 不含 Magisk / 固件重打包 / 售后提权链
- 默认清单不会静默禁用 TOF / 校正 / 桌面核心等硬件相关包

## 权限现实

本 App 以普通应用身份运行。多数 `pm` / `pm grant` / `disable-user` 需要 **已授权的网络 ADB（shell）**。UI 会先尝试本机执行，失败则给出可复制的 `adb` 命令。

## 构建

无 Gradle。需要：

- JDK **11**（`JDK11` 环境变量，默认 `/workspace/jdk-11.0.25+9`）
- Android SDK：`platforms;android-28`、`build-tools;34.0.0`
- `ANDROID_SDK_ROOT` 或 `ANDROID_HOME`

```bash
./build.sh
# 产物：build/apk/Z9XManager-v1.0.0.apk
```

CI（GitHub Actions）在 tag `v*` 或手动 `workflow_dispatch` 时用相同脚本打包，并上传 Release 资产。

本地调试签名 keystore 由 `build.sh` 自动生成（**不入库**）。发行版签名由 Actions 每次临时生成，或自行替换。

## 实机侧载（GMUI）

1. 将 `Z9XManager-v1.0.0.apk` 拷到 U 盘 → 文件管理器安装（允许未知来源）
2. 打开 **极米助手** → ① 网络 ADB / ② 预装包 / ③ Aerial
3. 若需完整 debloat / Aerial 设为桌面：电脑 `adb connect <IP>:5555` 后执行 App 内「复制」给出的命令

### Aerial 配置参考（需 adb shell）

```bash
adb install -r aerial-standard.apk
adb shell pm grant app.orchard.tv android.permission.WRITE_SECURE_SETTINGS
adb shell cmd notification allow_listener app.orchard.tv/app.orchard.tv.guard.RevivalListener
adb shell appops set app.orchard.tv SYSTEM_ALERT_WINDOW allow
adb shell pm disable-user --user 0 com.xgimi.home
adb shell cmd package set-home-activity app.orchard.tv/.MainActivity
adb shell am start -n app.orchard.tv/.MainActivity
```

恢复极米桌面：`adb shell pm enable com.xgimi.home`

## 许可

MIT。Aerial 为独立项目，请遵循其上游许可。
