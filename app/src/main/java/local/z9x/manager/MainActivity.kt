package local.z9x.manager

import android.app.Activity
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 极米助手 / Z9X Manager — leanback-friendly management UI.
 *
 * Does NOT: xgimi_ssh, write /data/ssh, write adb_keys, Magisk, firmware repack.
 * pm uninstall / Aerial shell config usually need adb shell; app tries then exports cmds.
 */
class MainActivity : Activity(), InstallReceiver.Callback {

    companion object {
        private val PROPS = arrayOf(
            "ro.product.model",
            "ro.build.version.incremental",
            "ro.build.display.id",
            "ro.xgimi.board.chip",
            "ro.build.version.release",
            "service.adb.tcp.port",
            "persist.adb.tcp.port",
            "init.svc.adbd",
            "xgimi.remoteDebug.on",
            "persist.sys.usb.config",
            "ro.adb.secure",
        )

        private const val AERIAL_URL_PRIMARY =
            "https://github.com/pengchujin/aerial-tv/releases/latest/download/aerial-standard.apk"
        private const val AERIAL_URL_FALLBACK =
            "https://aerial.qust.me/aerial-standard.apk"

        private const val APP_VERSION = "1.1.0"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var busy = false

    private lateinit var content: LinearLayout
    private lateinit var logView: TextView
    private lateinit var deviceLine: TextView
    private lateinit var tabAdb: Button
    private lateinit var tabPkgs: Button
    private lateinit var tabAerial: Button
    private var tab = 0

    private val safeChecks = arrayOfNulls<CheckBox>(PackageCatalog.SAFE.size)
    private lateinit var aerialApk: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        InstallReceiver.setCallback(this)
        aerialApk = File(getExternalFilesDir(null) ?: filesDir, "aerial-standard.apk")

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF0D1117.toInt())
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
        }
        scroll.addView(
            root,
            android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        root.addView(TextView(this).apply {
            text = "极米助手 $APP_VERSION（Z9X Manager）"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "开启网络 ADB · 可选卸载/恢复预装 · 安装配置 Aerial\n" +
                "不含售后 SSH / 写 adb_keys / Magisk。多数 pm 操作需电脑端 adb shell。"
            setTextColor(0xFF8B949E.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(6), 0, dp(10))
        })

        deviceLine = TextView(this).apply {
            setTextColor(0xFF58A6FF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(10))
        }
        root.addView(deviceLine)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(tabs)

        tabAdb = tvButton("① 网络 ADB")
        tabPkgs = tvButton("② 预装包")
        tabAerial = tvButton("③ Aerial")
        tabs.addView(tabAdb)
        tabs.addView(tabPkgs, gap())
        tabs.addView(tabAerial, gap())

        tabAdb.setOnClickListener { showTab(0) }
        tabPkgs.setOnClickListener { showTab(1) }
        tabAerial.setOnClickListener { showTab(2) }

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(content)

        logView = TextView(this).apply {
            setTextColor(0xFFC9D1D9.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, dp(12), 0, dp(24))
        }
        root.addView(logView)

        setContentView(scroll)
        refreshDeviceLine()
        showTab(0)
        tabAdb.requestFocus()
    }

    override fun onDestroy() {
        InstallReceiver.setCallback(null)
        super.onDestroy()
    }

    private fun showTab(t: Int) {
        tab = t
        highlightTab()
        content.removeAllViews()
        when (t) {
            0 -> buildAdbPanel()
            1 -> buildPkgsPanel()
            else -> buildAerialPanel()
        }
    }

    private fun highlightTab() {
        styleTab(tabAdb, tab == 0)
        styleTab(tabPkgs, tab == 1)
        styleTab(tabAerial, tab == 2)
    }

    private fun styleTab(b: Button, on: Boolean) {
        val bg = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            if (on) {
                setColor(0xFF1F6FEB.toInt())
                setStroke(dp(2), Color.WHITE)
            } else {
                setColor(0xFF21262D.toInt())
                setStroke(dp(2), 0xFF30363D.toInt())
            }
        }
        b.background = bg
        b.setTextColor(Color.WHITE)
    }

    // ——— ADB panel ———

    private fun buildAdbPanel() {
        content.addView(section("网络 ADB（与 Z9X Helper / Z7X-ADB 同类，失败安全）"))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(row)

        val refresh = tvButton("重新整理属性")
        val enable = tvButton("尝试开启网络 ADB :5555")
        val copy = tvButton("复制 adb connect 提示")
        row.addView(refresh)
        row.addView(enable, gap())
        row.addView(copy, gap())

        refresh.setOnClickListener {
            refreshDeviceLine()
            dumpProps()
        }
        enable.setOnClickListener { tryEnableAdb() }
        copy.setOnClickListener {
            copyText(
                "adb connect <投影机IP>:5555\n" +
                    "adb devices\n" +
                    "# 若 unauthorized：需在电脑端完成授权（本 App 不写 adb_keys）",
            )
        }

        dumpProps()
    }

    private fun dumpProps() {
        val sb = StringBuilder()
        sb.append("=== 系统属性 ===\n")
        for (p in PROPS) {
            sb.append(padRight(p, 30)).append(" = ").append(ShellUtil.getprop(p)).append('\n')
        }
        sb.append("\nUID=").append(android.os.Process.myUid())
            .append("  pkg=local.z9x.manager\n")
        setLog(sb.toString())
    }

    private fun tryEnableAdb() {
        if (busy) return
        busy = true
        appendLog("\n>>> 尝试 setprop 网络 ADB …\n")
        Thread({
            val log = StringBuilder()
            try {
                log.append(ShellUtil.runCmd("/system/bin/setprop", "service.adb.tcp.port", "5555"))
                log.append(ShellUtil.runCmd("/system/bin/setprop", "persist.adb.tcp.port", "5555"))
                log.append(ShellUtil.runCmd("/system/bin/setprop", "ctl.stop", "adbd"))
                try {
                    Thread.sleep(400)
                } catch (_: InterruptedException) {
                }
                log.append(ShellUtil.runCmd("/system/bin/setprop", "ctl.start", "adbd"))
                try {
                    Thread.sleep(800)
                } catch (_: InterruptedException) {
                }
                log.append("\n读回：\n")
                val svc = ShellUtil.getprop("service.adb.tcp.port")
                val pers = ShellUtil.getprop("persist.adb.tcp.port")
                val adbd = ShellUtil.getprop("init.svc.adbd")
                log.append("service.adb.tcp.port=").append(svc).append('\n')
                log.append("persist.adb.tcp.port=").append(pers).append('\n')
                log.append("init.svc.adbd=").append(adbd).append('\n')
                if (svc == "5555" || pers == "5555") {
                    log.append("\n结果：属性像是 5555。电脑：adb connect <IP>:5555\n")
                } else {
                    log.append("\n结果：setprop 可能被拒绝（失败安全）。不会走售后 SSH。\n")
                }
            } catch (t: Throwable) {
                log.append("例外：").append(t.message).append('\n')
            }
            handler.post {
                busy = false
                appendLog(log.toString())
                refreshDeviceLine()
            }
        }, "try-adb").start()
    }

    // ——— Packages panel ———

    private fun buildPkgsPanel() {
        content.addView(section("可卸载清单（默认全不勾选；对本用户 --user 0，可逆）"))
        content.addView(
            hint(
                "本 App 为普通 uid，直接 pm 多半 Permission denied——会尝试执行，" +
                    "失败则请用「复制 adb 命令」在已授权的电脑端执行。",
            ),
        )

        val pm = packageManager
        PackageCatalog.SAFE.forEachIndexed { i, e ->
            val cb = CheckBox(this).apply {
                text = "${e.label}\n${e.pkg}  [${pkgStatus(pm, e.pkg)}]"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                isFocusable = true
                setPadding(dp(4), dp(6), dp(4), dp(6))
            }
            safeChecks[i] = cb
            content.addView(cb)
        }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(row)
        val uninstall = tvButton("尝试卸载选中")
        val restore = tvButton("尝试恢复选中")
        val copyU = tvButton("复制卸载命令")
        val copyR = tvButton("复制恢复命令")
        row.addView(uninstall)
        row.addView(restore, gap())
        row.addView(copyU, gap())
        row.addView(copyR, gap())

        uninstall.setOnClickListener { runPmSelected(restore = false) }
        restore.setOnClickListener { runPmSelected(restore = true) }
        copyU.setOnClickListener { copyText(buildAdbPmCommands(restore = false)) }
        copyR.setOnClickListener { copyText(buildAdbPmCommands(restore = true)) }

        content.addView(section("勿动清单（硬件 / 桌面 / 输入，已锁定）"))
        val locked = StringBuilder()
        for (e in PackageCatalog.LOCKED) {
            locked.append("🔒 ").append(e.label).append("  ")
                .append(e.pkg).append("  [").append(pkgStatus(pm, e.pkg)).append("]\n")
        }
        content.addView(TextView(this).apply {
            text = locked.toString()
            setTextColor(0xFF8B949E.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
        })

        setLog("勾选后点「尝试…」或「复制…」。恢复：cmd package install-existing --user 0 <包名>\n")
    }

    private fun selectedPkgs(): MutableList<String> {
        val list = mutableListOf<String>()
        for (i in safeChecks.indices) {
            val cb = safeChecks[i]
            if (cb != null && cb.isChecked) {
                list.add(PackageCatalog.SAFE[i].pkg)
            }
        }
        return list
    }

    private fun buildAdbPmCommands(restore: Boolean): String {
        val pkgs = selectedPkgs()
        if (pkgs.isEmpty()) {
            PackageCatalog.SAFE.forEach { pkgs.add(it.pkg) }
        }
        val sb = StringBuilder()
        sb.append("# 在已授权的电脑端执行（投影机 IP:5555）\n")
        sb.append("adb connect <IP>:5555\n")
        for (pkg in pkgs) {
            if (restore) {
                sb.append("adb shell cmd package install-existing --user 0 ").append(pkg).append('\n')
            } else {
                sb.append("adb shell pm uninstall --user 0 ").append(pkg).append('\n')
            }
        }
        return sb.toString()
    }

    private fun runPmSelected(restore: Boolean) {
        val pkgs = selectedPkgs()
        if (pkgs.isEmpty()) {
            toast("请先勾选套件")
            return
        }
        if (busy) return
        busy = true
        appendLog(if (restore) "\n>>> 尝试恢复 …\n" else "\n>>> 尝试卸载 …\n")
        Thread({
            val log = StringBuilder()
            var fail = 0
            for (pkg in pkgs) {
                val r = if (restore) {
                    ShellUtil.run("cmd", "package", "install-existing", "--user", "0", pkg)
                } else {
                    ShellUtil.run("pm", "uninstall", "--user", "0", pkg)
                }
                log.append(r.summary())
                if (!r.ok()) fail++
            }
            if (fail > 0) {
                log.append("\n部分失败（普通 App 无 shell 权限属预期）。请复制 adb 命令在电脑执行：\n")
                log.append(buildAdbPmCommands(restore))
            } else {
                log.append("\n全部 exit=0。请切回本页刷新状态。\n")
            }
            handler.post {
                busy = false
                appendLog(log.toString())
                showTab(1)
            }
        }, "pm-ops").start()
    }

    // ——— Aerial panel ———

    private fun buildAerialPanel() {
        val pm = packageManager
        content.addView(section("Aerial 桌面（app.orchard.tv）"))
        content.addView(
            hint(
                "下载 → 本机 PackageInstaller 安装（需确认）→ 配置步骤多数需 adb shell。" +
                    "已验证的 GMUI 做法：disable-user com.xgimi.home + set-home-activity。",
            ),
        )

        content.addView(TextView(this).apply {
            text = "Aerial: ${pkgStatus(pm, PackageCatalog.AERIAL_PKG)}\n" +
                "极米桌面: ${pkgStatus(pm, PackageCatalog.XGIMI_HOME)}\n" +
                "缓存 APK: " + if (aerialApk.exists()) {
                    "${aerialApk.absolutePath} (${aerialApk.length()} bytes)"
                } else {
                    "尚未下载"
                }
            setTextColor(0xFFE6EDF3.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(10))
        })

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(row1)
        val dl = tvButton("下载 Aerial APK")
        val inst = tvButton("安装已下载 APK")
        val copyCfg = tvButton("复制完整配置命令")
        row1.addView(dl)
        row1.addView(inst, gap())
        row1.addView(copyCfg, gap())

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        content.addView(row2)
        val tryCfg = tvButton("尝试本机配置（需权限）")
        val openAerial = tvButton("打开 Aerial")
        val reenableHome = tvButton("复制恢复极米桌面命令")
        row2.addView(tryCfg)
        row2.addView(openAerial, gap())
        row2.addView(reenableHome, gap())

        dl.setOnClickListener { downloadAerial() }
        inst.setOnClickListener { installAerialApk() }
        copyCfg.setOnClickListener { copyText(aerialConfigScript()) }
        tryCfg.setOnClickListener { tryAerialConfig() }
        openAerial.setOnClickListener {
            try {
                val i = packageManager.getLaunchIntentForPackage(PackageCatalog.AERIAL_PKG)
                if (i != null) startActivity(i) else toast("未安装 Aerial")
            } catch (e: Exception) {
                toast(e.message ?: "error")
            }
        }
        reenableHome.setOnClickListener {
            copyText(
                "adb shell pm enable com.xgimi.home\n" +
                    "adb shell cmd package set-home-activity com.xgimi.home/.MainActivity\n" +
                    "# 若 MainActivity 名不同，请用 dumpsys package com.xgimi.home 查\n",
            )
        }

        content.addView(TextView(this).apply {
            text = aerialConfigScript()
            setTextColor(0xFF8B949E.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, dp(12), 0, 0)
        })

        setLog("推荐流程：下载 → 安装 → 电脑执行「完整配置命令」。\n")
    }

    private fun aerialConfigScript(): String =
        "# Aerial 完整配置（已在 Z9X / GMUI 实机验证过同类步骤）\n" +
            "adb install -r aerial-standard.apk\n" +
            "adb shell pm grant app.orchard.tv android.permission.WRITE_SECURE_SETTINGS\n" +
            "adb shell cmd notification allow_listener app.orchard.tv/app.orchard.tv.guard.RevivalListener\n" +
            "adb shell appops set app.orchard.tv SYSTEM_ALERT_WINDOW allow\n" +
            "adb shell pm disable-user --user 0 com.xgimi.home\n" +
            "adb shell cmd package set-home-activity app.orchard.tv/.MainActivity\n" +
            "adb shell am start -n app.orchard.tv/.MainActivity\n"

    private fun downloadAerial() {
        if (busy) return
        busy = true
        appendLog("\n>>> 下载 Aerial …\n")
        Thread({
            val ferr = try {
                var err = downloadTo(AERIAL_URL_PRIMARY, aerialApk)
                if (err != null) {
                    err = downloadTo(AERIAL_URL_FALLBACK, aerialApk)
                }
                err
            } catch (t: Throwable) {
                t.message
            }
            handler.post {
                busy = false
                if (ferr == null) {
                    appendLog(
                        "下载完成: ${aerialApk.absolutePath} (${aerialApk.length()} bytes)\n",
                    )
                    toast("下载完成")
                    showTab(2)
                } else {
                    appendLog("下载失败: $ferr\n可改用电脑 wget 后 adb install。\n")
                    toast("下载失败")
                }
            }
        }, "dl-aerial").start()
    }

    /** @return null on success, error message on failure */
    private fun downloadTo(urlStr: String, dest: File): String? {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 120_000
                setRequestProperty("User-Agent", "Z9X-Manager/1.1")
            }
            var code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == 307 || code == 308
            ) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                return downloadTo(loc, dest)
            }
            if (code != 200) return "HTTP $code from $urlStr"
            conn.inputStream.use { input ->
                val tmp = File(dest.absolutePath + ".part")
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                }
                if (dest.exists()) dest.delete()
                if (!tmp.renameTo(dest)) {
                    FileInputStream(tmp).use { fis ->
                        FileOutputStream(dest).use { fos ->
                            val buf = ByteArray(8192)
                            while (true) {
                                val n = fis.read(buf)
                                if (n < 0) break
                                fos.write(buf, 0, n)
                            }
                        }
                    }
                    tmp.delete()
                }
            }
            if (dest.length() < 10_000) return "文件过小 (${dest.length()})，可能不是 APK"
            return null
        } catch (e: Exception) {
            return "${e.javaClass.simpleName}: ${e.message}"
        } finally {
            conn?.disconnect()
        }
    }

    private fun installAerialApk() {
        if (!aerialApk.exists()) {
            toast("请先下载")
            appendLog("未找到 ${aerialApk.absolutePath}\n")
            return
        }
        appendLog("\n>>> PackageInstaller 安装 …\n")
        Thread({
            try {
                val installer = packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL,
                )
                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    FileInputStream(aerialApk).use { input ->
                        session.openWrite("aerial.apk", 0, aerialApk.length()).use { out ->
                            val buf = ByteArray(65536)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                            }
                            session.fsync(out)
                        }
                    }
                    val callback = Intent(this, InstallReceiver::class.java).apply {
                        action = InstallReceiver.ACTION
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    val pi = PendingIntent.getBroadcast(this, sessionId, callback, flags)
                    session.commit(pi.intentSender)
                }
                handler.post {
                    appendLog("已提交安装会话，请在系统确认框点允许。\n")
                }
            } catch (e: Exception) {
                handler.post {
                    appendLog(
                        "安装失败: ${e.message}\n可改：adb install -r ${aerialApk.absolutePath}\n",
                    )
                }
            }
        }, "install-aerial").start()
    }

    private fun tryAerialConfig() {
        if (busy) return
        busy = true
        appendLog("\n>>> 尝试本机配置 Aerial（普通 uid 多半失败）…\n")
        Thread({
            val log = StringBuilder()
            log.append(
                ShellUtil.run(
                    "pm", "grant", PackageCatalog.AERIAL_PKG,
                    "android.permission.WRITE_SECURE_SETTINGS",
                ).summary(),
            )
            log.append(
                ShellUtil.run(
                    "cmd", "notification", "allow_listener",
                    "app.orchard.tv/app.orchard.tv.guard.RevivalListener",
                ).summary(),
            )
            log.append(
                ShellUtil.run(
                    "appops", "set", PackageCatalog.AERIAL_PKG,
                    "SYSTEM_ALERT_WINDOW", "allow",
                ).summary(),
            )
            log.append(
                ShellUtil.run(
                    "pm", "disable-user", "--user", "0",
                    PackageCatalog.XGIMI_HOME,
                ).summary(),
            )
            log.append(
                ShellUtil.run(
                    "cmd", "package", "set-home-activity",
                    "app.orchard.tv/.MainActivity",
                ).summary(),
            )
            log.append(
                ShellUtil.run(
                    "am", "start", "-n",
                    "app.orchard.tv/.MainActivity",
                ).summary(),
            )
            log.append("\n若多处 Permission denied，请复制完整配置命令到电脑执行。\n")
            handler.post {
                busy = false
                appendLog(log.toString())
            }
        }, "aerial-cfg").start()
    }

    override fun onInstallStatus(status: Int, message: String) {
        handler.post {
            appendLog("安装回调 status=$status $message\n")
            if (status == PackageInstaller.STATUS_SUCCESS) {
                toast("Aerial 安装成功")
                showTab(2)
            }
        }
    }

    // ——— shared UI helpers ———

    private fun refreshDeviceLine() {
        val model = ShellUtil.getprop("ro.product.model")
        val inc = ShellUtil.getprop("ro.build.version.incremental")
        val chip = ShellUtil.getprop("ro.xgimi.board.chip")
        val port = ShellUtil.getprop("service.adb.tcp.port")
        val pers = ShellUtil.getprop("persist.adb.tcp.port")
        val dbg = ShellUtil.getprop("xgimi.remoteDebug.on")
        deviceLine.text = "$model · $inc · chip=$chip · adb.port=$port/$pers · remoteDebug=$dbg"
    }

    private fun pkgStatus(pm: PackageManager, pkg: String): String {
        return try {
            val ai = pm.getApplicationInfo(pkg, 0)
            val state = pm.getApplicationEnabledSetting(pkg)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED ||
                !ai.enabled
            ) {
                "已安装/停用"
            } else {
                "已安装/启用"
            }
        } catch (_: PackageManager.NameNotFoundException) {
            "未见（未装或已对本用户卸载）"
        }
    }

    private fun section(t: String): TextView = TextView(this).apply {
        text = t
        setTextColor(0xFF58A6FF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(4), 0, dp(8))
    }

    private fun hint(t: String): TextView = TextView(this).apply {
        text = t
        setTextColor(0xFF8B949E.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(0, 0, 0, dp(8))
    }

    private fun tvButton(label: String): Button {
        val b = Button(this).apply {
            text = label
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        applyFocusChrome(b)
        return b
    }

    private fun applyFocusChrome(b: Button) {
        val normal = GradientDrawable().apply {
            setColor(0xFF21262D.toInt())
            cornerRadius = dp(8).toFloat()
            setStroke(dp(2), 0xFF30363D.toInt())
        }
        val focused = GradientDrawable().apply {
            setColor(0xFF30363D.toInt())
            cornerRadius = dp(8).toFloat()
            setStroke(dp(3), Color.WHITE)
        }
        b.background = normal
        b.setTextColor(Color.WHITE)
        b.setOnFocusChangeListener { _, hasFocus ->
            b.background = if (hasFocus) focused else normal
            val s = if (hasFocus) 1.04f else 1f
            b.animate().scaleX(s).scaleY(s).setDuration(100).start()
        }
    }

    private fun gap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { leftMargin = dp(8) }

    private fun dp(v: Int): Int {
        val d = resources.displayMetrics.density
        return Math.round(v * d)
    }

    private fun setLog(s: String) {
        logView.text = s
    }

    private fun appendLog(s: String) {
        val cur = logView.text
        logView.text = if (cur == null) s else cur.toString() + s
    }

    private fun copyText(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("z9x-manager", text))
            toast("已复制到剪贴板")
            appendLog("\n[已复制]\n$text\n")
        } else {
            appendLog("\n无法访问剪贴板，请手动抄写：\n$text\n")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun padRight(s: String, n: Int): String {
        if (s.length >= n) return s
        return s + " ".repeat(n - s.length)
    }
}
