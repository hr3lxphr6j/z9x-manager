# 极米助手 / Z9X Manager

面向极米投影（GMUI / Z9X 等）的 **Leanback 友好** 管理 App。

包名：`local.z9x.manager`  
当前版本：**1.1.0**（Gradle + Kotlin 重构）

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

## 构建（Gradle + Kotlin）

需要：

- **JDK 17**
- Android SDK：`platforms;android-34`、`build-tools;34.0.0`（或更新）
- `ANDROID_HOME` / `sdk.dir`（本地可写 `local.properties`）

```bash
./gradlew assembleRelease
# 或
./build.sh

# 产物：
#   app/build/outputs/apk/named/Z9XManager-v1.1.0.apk
#   （亦保留 app/build/outputs/apk/release/*-release.apk）
```

CI（GitHub Actions）在 tag `v*` 或手动 `workflow_dispatch` 时用同一 Gradle 任务打包，并上传 Release 资产。

### 签名

**默认用 Android Debug Keystore 签 release**（CI 无需额外 Secret，Release 可持续产出）。  
换自己的签名（推荐长期更新同一包）：

```bash
# 生成
keytool -genkeypair -keystore release.keystore -alias z9xmanager \
  -keyalg RSA -keysize 2048 -validity 10000

# 在 app/build.gradle.kts 的 android { } 内增加：
# signingConfigs {
#   create("release") {
#     storeFile = file("release.keystore")   // 或从环境变量读
#     storePassword = System.getenv("STORE_PASSWORD")
#     keyAlias = "z9xmanager"
#     keyPassword = System.getenv("KEY_PASSWORD")
#   }
# }
# buildTypes.release.signingConfig = signingConfigs.getByName("release")
```

GitHub Actions 可用 Secrets：`STORE_PASSWORD` / `KEY_PASSWORD` / `KEYSTORE_BASE64`（把 keystore base64 后写入），在 workflow 里解码再签。未配置时继续走 debug 签名。

> 从 1.0.x（手搓 keystore）升到 1.1.0（debug 签）时，**签名指纹会变**，需先卸载旧包再装，或自行用同一 keystore 重签。

## 实机侧载（GMUI）

1. 将 `Z9XManager-v1.1.0.apk` 拷到 U 盘 → 文件管理器安装（允许未知来源）
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
