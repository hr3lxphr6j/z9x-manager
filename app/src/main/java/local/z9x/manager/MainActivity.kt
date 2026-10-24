package local.z9x.manager

import android.app.Activity
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * 极米助手 / Z9X Manager — two-pane GMUI remote UI.
 *
 * Left categories, right full-width rows. Focus is a white bar with black text.
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

        private const val APP_VERSION = "1.2.0"

        private const val TAB_ADB = 0
        private const val TAB_PKGS = 1
        private const val TAB_AERIAL = 2
    }

    private val handler = Handler(Looper.getMainLooper())
    private var busy = false
    private var tab = -1
    private val checkedPkgs = linkedSetOf<String>()
    private val lastRowKey = arrayOfNulls<String>(3)

    private lateinit var actions: LinearLayout
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var deviceTitle: TextView
    private lateinit var deviceMeta: TextView
    private lateinit var deviceAdb: TextView

    private val categoryRows = arrayOfNulls<View>(3)
    private val categoryTitles = arrayOfNulls<TextView>(3)
    private val categorySubs = arrayOfNulls<TextView>(3)

    private lateinit var aerialApk: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        InstallReceiver.setCallback(this)
        aerialApk = File(getExternalFilesDir(null) ?: filesDir, "aerial-standard.apk")

        val bg = 0xFA292929.toInt()
        window.decorView.setBackgroundColor(bg)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(bg)
        }

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xA6000000.toInt())
            val pad = dp(16)
            setPadding(pad, dp(20), dp(12), pad)
        }
        left.addView(TextView(this).apply {
            text = "极米助手"
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
        })
        left.addView(TextView(this).apply {
            text = APP_VERSION
            setTextColor(0xA6FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(2), 0, 0)
        })
        left.addView(TextView(this).apply {
            text = "左右切换栏 · 上下选行 · 确认执行"
            setTextColor(0xA6FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(8), 0, dp(16))
        })
        addCategory(left, TAB_ADB, "网络 ADB", "端口 5555")
        addCategory(left, TAB_PKGS, "预装包", "卸载与恢复")
        addCategory(left, TAB_AERIAL, "Aerial", "下载与桌面")

        val divider = View(this).apply { setBackgroundColor(0x24FFFFFF) }

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(16), dp(16), dp(12))
        }
        right.addView(buildDeviceHeader())

        actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val actionScroll = ScrollView(this).apply {
            isFillViewport = true
            isFocusable = false
            isFocusableInTouchMode = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        actionScroll.addView(
            actions,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        right.addView(
            actionScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        right.addView(buildLogPanel())

        root.addView(
            left,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3.2f),
        )
        root.addView(
            divider,
            LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT),
        )
        root.addView(
            right,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 6.8f),
        )

        setContentView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        refreshDeviceLine()
        selectTab(TAB_ADB, focusRight = false)
        root.post { categoryRows[TAB_ADB]?.requestFocus() }
    }

    override fun onDestroy() {
        InstallReceiver.setCallback(null)
        super.onDestroy()
    }

    private fun buildDeviceHeader(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(12), dp(8))
            isFocusable = false
        }
        deviceTitle = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        deviceMeta = TextView(this).apply {
            setTextColor(0xA6FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(2), 0, 0)
        }
        deviceAdb = TextView(this).apply {
            setTextColor(0xA6FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(4), 0, 0)
        }
        box.addView(deviceTitle)
        box.addView(deviceMeta)
        box.addView(deviceAdb)
        return box
    }

    private fun buildLogPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(12), 0)
            isFocusable = false
        }
        panel.addView(TextView(this).apply {
            text = "输出"
            setTextColor(0xA6FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            isFocusable = false
        })
        logView = TextView(this).apply {
            setTextColor(0xE6FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(false)
            isFocusable = false
            isFocusableInTouchMode = false
            setPadding(0, dp(4), 0, dp(8))
        }
        logScroll = ScrollView(this).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        logScroll.addView(
            logView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        panel.addView(
            logScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        return panel.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(220),
            )
        }
    }

    private fun addCategory(parent: LinearLayout, index: Int, title: String, subtitle: String) {
        val titleView = TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subView = TextView(this).apply {
            text = subtitle
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(2), 0, 0)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(72)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setOnClickListener { selectTab(index, focusRight = true) }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && tab != index) selectTab(index, focusRight = false)
                else refreshCategoryStyles()
            }
        }
        row.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(titleView)
                addView(subView)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        row.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> true
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    selectTab(index, focusRight = true)
                    true
                }
                else -> false
            }
        }
        categoryRows[index] = row
        categoryTitles[index] = titleView
        categorySubs[index] = subView
        parent.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )
    }

    private fun selectTab(index: Int, focusRight: Boolean, force: Boolean = false) {
        val changed = tab != index
        tab = index
        if (changed || force) rebuildActions(resetLog = changed)
        refreshCategoryStyles()
        if (focusRight) actions.post { focusPreferredRow() }
    }

    private fun rebuildActions(resetLog: Boolean) {
        actions.removeAllViews()
        when (tab) {
            TAB_ADB -> buildAdbPanel(resetLog)
            TAB_PKGS -> buildPkgsPanel(resetLog)
            else -> buildAerialPanel(resetLog)
        }
    }

    private fun focusPreferredRow() {
        if (tab !in 0..2) return
        val key = lastRowKey[tab]
        val tagged = if (key != null) actions.findViewWithTag<View>(key) else null
        val target = if (tagged != null && tagged.isFocusable) tagged else firstFocusable(actions)
        target?.requestFocus()
    }

    private fun firstFocusable(group: ViewGroup): View? {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child.isFocusable && child.visibility == View.VISIBLE) return child
            if (child is ViewGroup) {
                val nested = firstFocusable(child)
                if (nested != null) return nested
            }
        }
        return null
    }

    private fun refreshCategoryStyles() {
        for (i in categoryRows.indices) {
            val row = categoryRows[i] ?: continue
            val title = categoryTitles[i] ?: continue
            val sub = categorySubs[i]
            val focused = row.hasFocus()
            val fill = when {
                focused -> 0xFFFFFFFF.toInt()
                i == tab -> 0x51FFFFFF
                else -> 0x24FFFFFF
            }
            row.background = rounded(fill)
            title.setTextColor(if (focused) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            sub?.setTextColor(if (focused) 0x99000000.toInt() else 0xA6FFFFFF.toInt())
        }
    }

    // ——— ADB ———

    private fun buildAdbPanel(resetLog: Boolean) {
        addSection("网络 ADB")
        addHint("与 Z9X Helper / Z7X-ADB 同类，失败安全。不会走售后 SSH，也不写 adb_keys。")
        addActionRow(
            key = "adb.refresh",
            title = "重新整理属性",
            subtitle = "读回端口、adbd 与机型",
        ) {
            refreshDeviceLine()
            dumpProps()
        }
        addActionRow(
            key = "adb.enable",
            title = "尝试开启网络 ADB",
            subtitle = "setprop 5555 并拉起 adbd",
        ) { tryEnableAdb() }
        addActionRow(
            key = "adb.copy",
            title = "复制 adb connect 提示",
            subtitle = "在电脑上连接投影机",
        ) {
            copyText(
                "adb connect <投影机IP>:5555\n" +
                    "adb devices\n" +
                    "# 若 unauthorized：需在电脑端完成授权（本 App 不写 adb_keys）",
            )
        }
        addCopyLogRow()
        if (resetLog) dumpProps()
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
        if (refuseIfBusy()) return
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

    // ——— Packages ———

    private fun buildPkgsPanel(resetLog: Boolean) {
        addSection("可卸载清单")
        addHint("默认全不勾选。对本用户 --user 0，可逆。普通 uid 直接 pm 多半会被拒绝，失败后用复制行在电脑执行。")
        val pm = packageManager
        PackageCatalog.SAFE.forEach { entry ->
            val status = pkgStatus(pm, entry.pkg)
            val titleViewHolder = arrayOfNulls<TextView>(1)
            addActionRow(
                key = "pkg:${entry.pkg}",
                title = packageTitle(entry.label, entry.pkg in checkedPkgs),
                subtitle = "${entry.pkg}  ·  $status",
                status = shortPkgStatus(status),
                statusActive = status.contains("启用"),
                titleOut = titleViewHolder,
            ) {
                if (!checkedPkgs.add(entry.pkg)) checkedPkgs.remove(entry.pkg)
                titleViewHolder[0]?.text = packageTitle(entry.label, entry.pkg in checkedPkgs)
            }
        }
        addActionRow(
            key = "pkg.uninstall",
            title = "尝试卸载选中",
            subtitle = "pm uninstall --user 0",
        ) { runPmSelected(restore = false) }
        addActionRow(
            key = "pkg.restore",
            title = "尝试恢复选中",
            subtitle = "cmd package install-existing --user 0",
        ) { runPmSelected(restore = true) }
        addActionRow(
            key = "pkg.copyU",
            title = "复制卸载命令",
            subtitle = "未勾选时复制全部可卸载项",
        ) {
            val picked = selectedPkgs().ifEmpty { PackageCatalog.SAFE.map { it.pkg } }
            copyText(adbPmScript(picked, restore = false))
        }
        addActionRow(
            key = "pkg.copyR",
            title = "复制恢复命令",
            subtitle = "未勾选时复制全部可卸载项",
        ) {
            val picked = selectedPkgs().ifEmpty { PackageCatalog.SAFE.map { it.pkg } }
            copyText(adbPmScript(picked, restore = true))
        }
        addSection("勿动清单")
        addHint("硬件、桌面和输入相关，不能在这里操作。")
        for (entry in PackageCatalog.LOCKED) {
            val status = pkgStatus(pm, entry.pkg)
            addInfo(
                label = entry.label,
                value = "${entry.pkg}  ·  $status",
                active = false,
            )
        }
        addCopyLogRow()
        if (resetLog) {
            setLog("勾选后点「尝试…」或「复制…」。恢复：cmd package install-existing --user 0 <包名>\n")
        }
    }

    private fun packageTitle(label: String, checked: Boolean): String =
        if (checked) "✓  $label" else label

    private fun shortPkgStatus(status: String): String = when {
        status.contains("启用") -> "启用"
        status.contains("停用") -> "停用"
        else -> "未装"
    }

    private fun selectedPkgs(): List<String> =
        PackageCatalog.SAFE.map { it.pkg }.filter { it in checkedPkgs }

    private fun adbPmScript(pkgs: List<String>, restore: Boolean): String {
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
        if (refuseIfBusy()) return
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
                log.append(adbPmScript(pkgs, restore))
            } else {
                log.append("\n全部 exit=0。状态已刷新。\n")
            }
            handler.post {
                busy = false
                appendLog(log.toString())
                selectTab(TAB_PKGS, focusRight = true, force = true)
            }
        }, "pm-ops").start()
    }

    // ——— Aerial ———

    private fun buildAerialPanel(resetLog: Boolean) {
        val pm = packageManager
        val aerialStatus = pkgStatus(pm, PackageCatalog.AERIAL_PKG)
        val homeStatus = pkgStatus(pm, PackageCatalog.XGIMI_HOME)
        addSection("Aerial 桌面")
        addHint("下载后用系统安装器确认。改默认桌面多数需要 adb shell。已验证做法：disable-user com.xgimi.home，再 set-home-activity。")
        addInfo("Aerial", aerialStatus, active = aerialStatus.contains("启用"))
        addInfo("极米桌面", homeStatus, active = homeStatus.contains("启用"))
        addInfo(
            "缓存 APK",
            if (aerialApk.exists()) "${aerialApk.length()} bytes" else "尚未下载",
            active = false,
        )
        addActionRow(
            key = "aerial.download",
            title = "下载 Aerial APK",
            subtitle = "GitHub，失败再试镜像",
        ) { downloadAerial() }
        addActionRow(
            key = "aerial.install",
            title = "安装已下载 APK",
            subtitle = "PackageInstaller，需在系统框确认",
        ) { installAerialApk() }
        addActionRow(
            key = "aerial.copy",
            title = "复制完整配置命令",
            subtitle = "授权、通知监听、替换极米桌面",
        ) { copyText(aerialConfigScript()) }
        addActionRow(
            key = "aerial.try",
            title = "尝试本机配置",
            subtitle = "普通 uid 多半会被拒绝",
        ) { tryAerialConfig() }
        addActionRow(
            key = "aerial.open",
            title = "打开 Aerial",
            subtitle = PackageCatalog.AERIAL_PKG,
        ) { openAerial() }
        addActionRow(
            key = "aerial.home",
            title = "复制恢复极米桌面命令",
            subtitle = "重新 enable com.xgimi.home",
        ) {
            copyText(
                "adb shell pm enable com.xgimi.home\n" +
                    "adb shell cmd package set-home-activity com.xgimi.home/.MainActivity\n" +
                    "# 若 MainActivity 名不同，请用 dumpsys package com.xgimi.home 查\n",
            )
        }
        addCopyLogRow()
        if (resetLog) {
            setLog(
                "推荐流程：下载 → 安装 → 用「复制完整配置命令」在电脑执行。\n\n" +
                    aerialConfigScript(),
            )
        }
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

    private fun openAerial() {
        try {
            val launch = packageManager.getLaunchIntentForPackage(PackageCatalog.AERIAL_PKG)
            if (launch != null) startActivity(launch) else toast("未安装 Aerial")
        } catch (e: Exception) {
            toast(e.message ?: "error")
        }
    }

    private fun downloadAerial() {
        if (refuseIfBusy()) return
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
                    selectTab(TAB_AERIAL, focusRight = true, force = true)
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
                setRequestProperty("User-Agent", "Z9X-Manager/1.2")
            }
            val code = conn.responseCode
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
        if (refuseIfBusy()) return
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
                selectTab(TAB_AERIAL, focusRight = true, force = true)
            }
        }
    }

    // ——— rows ———

    private fun addSection(text: String) {
        actions.addView(TextView(this).apply {
            this.text = text
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
            setPadding(dp(20), dp(12), dp(12), dp(2))
            isFocusable = false
        })
    }

    private fun addHint(text: String) {
        actions.addView(TextView(this).apply {
            this.text = text
            setTextColor(0xA6FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(20), dp(2), dp(12), dp(8))
            isFocusable = false
        })
    }

    private fun addInfo(label: String, value: String, active: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(6), dp(12), dp(6))
            isFocusable = false
        }
        row.addView(TextView(this).apply {
            text = label
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        })
        row.addView(TextView(this).apply {
            text = value
            setTextColor(if (active) 0xFF4A7DFF.toInt() else 0xA6FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(2), 0, 0)
        })
        actions.addView(row)
    }

    private fun addCopyLogRow() {
        addActionRow(
            key = "log.copy",
            title = "复制当前输出",
            subtitle = "日志不能划选，从这里复制",
        ) { copyLog() }
    }

    private fun addActionRow(
        key: String,
        title: String,
        subtitle: String? = null,
        status: String? = null,
        statusActive: Boolean = false,
        titleOut: Array<TextView?>? = null,
        onClick: () -> Unit,
    ) {
        val titleView = TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        titleOut?.set(0, titleView)
        val subView = subtitle?.let {
            TextView(this).apply {
                text = it
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(2), 0, 0)
            }
        }
        val statusView = status?.let {
            TextView(this).apply {
                text = it
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.END
            }
        }
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleView)
            if (subView != null) addView(subView)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            tag = key
            setPadding(dp(20), dp(12), dp(20), dp(12))
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setOnClickListener { onClick() }
        }
        row.addView(
            textCol,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        if (statusView != null) {
            row.addView(
                statusView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { leftMargin = dp(12) },
            )
        }
        fun paint() {
            val focused = row.hasFocus()
            row.background = rounded(if (focused) 0xFFFFFFFF.toInt() else 0x24FFFFFF)
            titleView.setTextColor(if (focused) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            subView?.setTextColor(if (focused) 0x99000000.toInt() else 0xA6FFFFFF.toInt())
            statusView?.setTextColor(
                when {
                    focused -> 0xFF000000.toInt()
                    statusActive -> 0xFF4A7DFF.toInt()
                    else -> 0xA6FFFFFF.toInt()
                },
            )
        }
        paint()
        row.setOnFocusChangeListener { _, hasFocus ->
            paint()
            if (hasFocus && tab in 0..2) {
                lastRowKey[tab] = key
                row.post {
                    row.requestRectangleOnScreen(
                        android.graphics.Rect(0, 0, row.width.coerceAtLeast(1), row.height.coerceAtLeast(1)),
                        false,
                    )
                }
            }
        }
        row.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    categoryRows.getOrNull(tab)?.requestFocus()
                    true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> true
                else -> false
            }
        }
        actions.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            },
        )
    }

    private fun rounded(color: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            setColor(color)
        }

    private fun refreshDeviceLine() {
        val model = ShellUtil.getprop("ro.product.model").ifBlank { "未知机型" }
        val inc = ShellUtil.getprop("ro.build.version.incremental")
        val chip = ShellUtil.getprop("ro.xgimi.board.chip")
        val port = ShellUtil.getprop("service.adb.tcp.port")
        val pers = ShellUtil.getprop("persist.adb.tcp.port")
        val dbg = ShellUtil.getprop("xgimi.remoteDebug.on")
        deviceTitle.text = model
        deviceMeta.text = "$inc  ·  chip=$chip  ·  remoteDebug=$dbg"
        val on = port == "5555" || pers == "5555"
        deviceAdb.text = if (on) {
            "网络 ADB 已打开  ·  $port / $pers"
        } else {
            "网络 ADB 未打开  ·  $port / $pers"
        }
        deviceAdb.setTextColor(if (on) 0xFF4A7DFF.toInt() else 0xA6FFFFFF.toInt())
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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun setLog(s: String) {
        logView.text = s
        logScroll.post { logScroll.fullScroll(View.FOCUS_UP) }
    }

    private fun appendLog(s: String) {
        logView.append(s)
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun copyLog() {
        val text = logView.text?.toString().orEmpty()
        if (text.isBlank()) {
            toast("输出是空的")
            return
        }
        copyText(text, echo = false)
    }

    private fun copyText(text: String, echo: Boolean = true) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("z9x-manager", text))
            toast("已复制到剪贴板")
            if (echo) appendLog("\n[已复制]\n$text\n")
        } else {
            appendLog("\n无法访问剪贴板，请手动抄写：\n$text\n")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun refuseIfBusy(): Boolean {
        if (!busy) return false
        toast("请稍候")
        return true
    }

    private fun padRight(s: String, n: Int): String {
        if (s.length >= n) return s
        return s + " ".repeat(n - s.length)
    }
}
