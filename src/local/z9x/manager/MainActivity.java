package local.z9x.manager;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 极米助手 / Z9X Manager — leanback-friendly management UI.
 *
 * Does NOT: xgimi_ssh, write /data/ssh, write adb_keys, Magisk, firmware repack.
 * pm uninstall / Aerial shell config usually need adb shell; app tries then exports cmds.
 */
public class MainActivity extends Activity implements InstallReceiver.Callback {

    private static final String[] PROPS = new String[] {
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
    };

    private static final String AERIAL_URL_PRIMARY =
            "https://github.com/pengchujin/aerial-tv/releases/latest/download/aerial-standard.apk";
    private static final String AERIAL_URL_FALLBACK =
            "https://aerial.qust.me/aerial-standard.apk";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mBusy;

    private LinearLayout mContent;
    private TextView mLog;
    private TextView mDeviceLine;
    private Button mTabAdb, mTabPkgs, mTabAerial;
    private int mTab; // 0 adb, 1 pkgs, 2 aerial

    private final CheckBox[] mSafeChecks = new CheckBox[PackageCatalog.SAFE.length];
    private File mAerialApk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InstallReceiver.setCallback(this);
        mAerialApk = new File(getExternalFilesDir(null), "aerial-standard.apk");
        if (mAerialApk.getParentFile() == null) {
            mAerialApk = new File(getFilesDir(), "aerial-standard.apk");
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0D1117);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("极米助手 1.0.0（Z9X Manager）");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        root.addView(title);

        TextView note = new TextView(this);
        note.setText("开启网络 ADB · 可选卸载/恢复预装 · 安装配置 Aerial\n"
                + "不含售后 SSH / 写 adb_keys / Magisk。多数 pm 操作需电脑端 adb shell。");
        note.setTextColor(0xFF8B949E);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        note.setPadding(0, dp(6), 0, dp(10));
        root.addView(note);

        mDeviceLine = new TextView(this);
        mDeviceLine.setTextColor(0xFF58A6FF);
        mDeviceLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mDeviceLine.setTypeface(Typeface.MONOSPACE);
        mDeviceLine.setPadding(0, 0, 0, dp(10));
        root.addView(mDeviceLine);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(tabs);

        mTabAdb = tvButton("① 网络 ADB");
        mTabPkgs = tvButton("② 预装包");
        mTabAerial = tvButton("③ Aerial");
        tabs.addView(mTabAdb);
        LinearLayout.LayoutParams g = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        g.leftMargin = dp(8);
        tabs.addView(mTabPkgs, g);
        tabs.addView(mTabAerial, g);

        mTabAdb.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(0); }
        });
        mTabPkgs.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(1); }
        });
        mTabAerial.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(2); }
        });

        mContent = new LinearLayout(this);
        mContent.setOrientation(LinearLayout.VERTICAL);
        mContent.setPadding(0, dp(12), 0, 0);
        root.addView(mContent);

        mLog = new TextView(this);
        mLog.setTextColor(0xFFC9D1D9);
        mLog.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mLog.setTypeface(Typeface.MONOSPACE);
        mLog.setTextIsSelectable(true);
        mLog.setPadding(0, dp(12), 0, dp(24));
        root.addView(mLog);

        setContentView(scroll);
        refreshDeviceLine();
        showTab(0);
        mTabAdb.requestFocus();
    }

    @Override
    protected void onDestroy() {
        InstallReceiver.setCallback(null);
        super.onDestroy();
    }

    private void showTab(int tab) {
        mTab = tab;
        highlightTab();
        mContent.removeAllViews();
        if (tab == 0) buildAdbPanel();
        else if (tab == 1) buildPkgsPanel();
        else buildAerialPanel();
    }

    private void highlightTab() {
        styleTab(mTabAdb, mTab == 0);
        styleTab(mTabPkgs, mTab == 1);
        styleTab(mTabAerial, mTab == 2);
    }

    private void styleTab(Button b, boolean on) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        if (on) {
            bg.setColor(0xFF1F6FEB);
            bg.setStroke(dp(2), Color.WHITE);
        } else {
            bg.setColor(0xFF21262D);
            bg.setStroke(dp(2), 0xFF30363D);
        }
        b.setBackground(bg);
        b.setTextColor(Color.WHITE);
    }

    // ——— ADB panel ———

    private void buildAdbPanel() {
        mContent.addView(section("网络 ADB（与 Z9X Helper / Z7X-ADB 同类，失败安全）"));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        mContent.addView(row);

        Button refresh = tvButton("重新整理属性");
        Button enable = tvButton("尝试开启网络 ADB :5555");
        Button copy = tvButton("复制 adb connect 提示");
        row.addView(refresh);
        LinearLayout.LayoutParams g = gap();
        row.addView(enable, g);
        row.addView(copy, g);

        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                refreshDeviceLine();
                dumpProps();
            }
        });
        enable.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tryEnableAdb(); }
        });
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                copyText("adb connect <投影机IP>:5555\n"
                        + "adb devices\n"
                        + "# 若 unauthorized：需在电脑端完成授权（本 App 不写 adb_keys）");
            }
        });

        dumpProps();
    }

    private void dumpProps() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 系统属性 ===\n");
        for (String p : PROPS) {
            sb.append(padRight(p, 30)).append(" = ").append(ShellUtil.getprop(p)).append('\n');
        }
        sb.append("\nUID=").append(android.os.Process.myUid())
                .append("  pkg=local.z9x.manager\n");
        setLog(sb.toString());
    }

    private void tryEnableAdb() {
        if (mBusy) return;
        mBusy = true;
        appendLog("\n>>> 尝试 setprop 网络 ADB …\n");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final StringBuilder log = new StringBuilder();
                try {
                    log.append(ShellUtil.runCmd("/system/bin/setprop", "service.adb.tcp.port", "5555"));
                    log.append(ShellUtil.runCmd("/system/bin/setprop", "persist.adb.tcp.port", "5555"));
                    log.append(ShellUtil.runCmd("/system/bin/setprop", "ctl.stop", "adbd"));
                    try { Thread.sleep(400); } catch (InterruptedException ignored) {}
                    log.append(ShellUtil.runCmd("/system/bin/setprop", "ctl.start", "adbd"));
                    try { Thread.sleep(800); } catch (InterruptedException ignored) {}
                    log.append("\n读回：\n");
                    String svc = ShellUtil.getprop("service.adb.tcp.port");
                    String pers = ShellUtil.getprop("persist.adb.tcp.port");
                    String adbd = ShellUtil.getprop("init.svc.adbd");
                    log.append("service.adb.tcp.port=").append(svc).append('\n');
                    log.append("persist.adb.tcp.port=").append(pers).append('\n');
                    log.append("init.svc.adbd=").append(adbd).append('\n');
                    if ("5555".equals(svc) || "5555".equals(pers)) {
                        log.append("\n结果：属性像是 5555。电脑：adb connect <IP>:5555\n");
                    } else {
                        log.append("\n结果：setprop 可能被拒绝（失败安全）。不会走售后 SSH。\n");
                    }
                } catch (Throwable t) {
                    log.append("例外：").append(t.getMessage()).append('\n');
                }
                mHandler.post(new Runnable() {
                    @Override public void run() {
                        mBusy = false;
                        appendLog(log.toString());
                        refreshDeviceLine();
                    }
                });
            }
        }, "try-adb").start();
    }

    // ——— Packages panel ———

    private void buildPkgsPanel() {
        mContent.addView(section("可卸载清单（默认全不勾选；对本用户 --user 0，可逆）"));
        mContent.addView(hint("本 App 为普通 uid，直接 pm 多半 Permission denied——会尝试执行，"
                + "失败则请用「复制 adb 命令」在已授权的电脑端执行。"));

        PackageManager pm = getPackageManager();
        for (int i = 0; i < PackageCatalog.SAFE.length; i++) {
            PackageCatalog.Entry e = PackageCatalog.SAFE[i];
            CheckBox cb = new CheckBox(this);
            cb.setText(e.label + "\n" + e.pkg + "  [" + pkgStatus(pm, e.pkg) + "]");
            cb.setTextColor(Color.WHITE);
            cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            cb.setFocusable(true);
            cb.setPadding(dp(4), dp(6), dp(4), dp(6));
            mSafeChecks[i] = cb;
            mContent.addView(cb);
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        mContent.addView(row);
        Button uninstall = tvButton("尝试卸载选中");
        Button restore = tvButton("尝试恢复选中");
        Button copyU = tvButton("复制卸载命令");
        Button copyR = tvButton("复制恢复命令");
        row.addView(uninstall);
        LinearLayout.LayoutParams g = gap();
        row.addView(restore, g);
        row.addView(copyU, g);
        row.addView(copyR, g);

        uninstall.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runPmSelected(false); }
        });
        restore.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runPmSelected(true); }
        });
        copyU.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { copyText(buildAdbPmCommands(false)); }
        });
        copyR.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { copyText(buildAdbPmCommands(true)); }
        });

        mContent.addView(section("勿动清单（硬件 / 桌面 / 输入，已锁定）"));
        StringBuilder locked = new StringBuilder();
        for (PackageCatalog.Entry e : PackageCatalog.LOCKED) {
            locked.append("🔒 ").append(e.label).append("  ")
                    .append(e.pkg).append("  [").append(pkgStatus(pm, e.pkg)).append("]\n");
        }
        TextView lv = new TextView(this);
        lv.setText(locked.toString());
        lv.setTextColor(0xFF8B949E);
        lv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        lv.setTypeface(Typeface.MONOSPACE);
        mContent.addView(lv);

        setLog("勾选后点「尝试…」或「复制…」。恢复：cmd package install-existing --user 0 <包名>\n");
    }

    private List<String> selectedPkgs() {
        List<String> list = new ArrayList<String>();
        for (int i = 0; i < mSafeChecks.length; i++) {
            if (mSafeChecks[i] != null && mSafeChecks[i].isChecked()) {
                list.add(PackageCatalog.SAFE[i].pkg);
            }
        }
        return list;
    }

    private String buildAdbPmCommands(boolean restore) {
        List<String> pkgs = selectedPkgs();
        if (pkgs.isEmpty()) {
            // export all safe as template when nothing selected
            for (PackageCatalog.Entry e : PackageCatalog.SAFE) pkgs.add(e.pkg);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# 在已授权的电脑端执行（投影机 IP:5555）\n");
        sb.append("adb connect <IP>:5555\n");
        for (String pkg : pkgs) {
            if (restore) {
                sb.append("adb shell cmd package install-existing --user 0 ").append(pkg).append('\n');
            } else {
                sb.append("adb shell pm uninstall --user 0 ").append(pkg).append('\n');
            }
        }
        return sb.toString();
    }

    private void runPmSelected(final boolean restore) {
        final List<String> pkgs = selectedPkgs();
        if (pkgs.isEmpty()) {
            toast("请先勾选套件");
            return;
        }
        if (mBusy) return;
        mBusy = true;
        appendLog(restore ? "\n>>> 尝试恢复 …\n" : "\n>>> 尝试卸载 …\n");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final StringBuilder log = new StringBuilder();
                int fail = 0;
                for (String pkg : pkgs) {
                    ShellUtil.Result r;
                    if (restore) {
                        r = ShellUtil.run("cmd", "package", "install-existing", "--user", "0", pkg);
                    } else {
                        r = ShellUtil.run("pm", "uninstall", "--user", "0", pkg);
                    }
                    log.append(r.summary());
                    if (!r.ok()) fail++;
                }
                if (fail > 0) {
                    log.append("\n部分失败（普通 App 无 shell 权限属预期）。请复制 adb 命令在电脑执行：\n");
                    log.append(buildAdbPmCommands(restore));
                } else {
                    log.append("\n全部 exit=0。请切回本页刷新状态。\n");
                }
                mHandler.post(new Runnable() {
                    @Override public void run() {
                        mBusy = false;
                        appendLog(log.toString());
                        // rebuild to refresh status labels
                        showTab(1);
                    }
                });
            }
        }, "pm-ops").start();
    }

    // ——— Aerial panel ———

    private void buildAerialPanel() {
        PackageManager pm = getPackageManager();
        mContent.addView(section("Aerial 桌面（app.orchard.tv）"));
        mContent.addView(hint("下载 → 本机 PackageInstaller 安装（需确认）→ 配置步骤多数需 adb shell。"
                + "已验证的 GMUI 做法：disable-user com.xgimi.home + set-home-activity。"));

        TextView st = new TextView(this);
        st.setText("Aerial: " + pkgStatus(pm, PackageCatalog.AERIAL_PKG)
                + "\n极米桌面: " + pkgStatus(pm, PackageCatalog.XGIMI_HOME)
                + "\n缓存 APK: " + (mAerialApk.exists()
                ? (mAerialApk.getAbsolutePath() + " (" + mAerialApk.length() + " bytes)")
                : "尚未下载"));
        st.setTextColor(0xFFE6EDF3);
        st.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        st.setTypeface(Typeface.MONOSPACE);
        st.setPadding(0, 0, 0, dp(10));
        mContent.addView(st);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        mContent.addView(row1);
        Button dl = tvButton("下载 Aerial APK");
        Button inst = tvButton("安装已下载 APK");
        Button copyCfg = tvButton("复制完整配置命令");
        row1.addView(dl);
        LinearLayout.LayoutParams g = gap();
        row1.addView(inst, g);
        row1.addView(copyCfg, g);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(8), 0, 0);
        mContent.addView(row2);
        Button tryCfg = tvButton("尝试本机配置（需权限）");
        Button openAerial = tvButton("打开 Aerial");
        Button reenableHome = tvButton("复制恢复极米桌面命令");
        row2.addView(tryCfg);
        row2.addView(openAerial, g);
        row2.addView(reenableHome, g);

        dl.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { downloadAerial(); }
        });
        inst.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { installAerialApk(); }
        });
        copyCfg.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { copyText(aerialConfigScript()); }
        });
        tryCfg.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tryAerialConfig(); }
        });
        openAerial.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = getPackageManager().getLaunchIntentForPackage(PackageCatalog.AERIAL_PKG);
                    if (i != null) startActivity(i);
                    else toast("未安装 Aerial");
                } catch (Exception e) {
                    toast(e.getMessage());
                }
            }
        });
        reenableHome.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                copyText("adb shell pm enable com.xgimi.home\n"
                        + "adb shell cmd package set-home-activity com.xgimi.home/.MainActivity\n"
                        + "# 若 MainActivity 名不同，请用 dumpsys package com.xgimi.home 查\n");
            }
        });

        TextView cmds = new TextView(this);
        cmds.setText(aerialConfigScript());
        cmds.setTextColor(0xFF8B949E);
        cmds.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        cmds.setTypeface(Typeface.MONOSPACE);
        cmds.setTextIsSelectable(true);
        cmds.setPadding(0, dp(12), 0, 0);
        mContent.addView(cmds);

        setLog("推荐流程：下载 → 安装 → 电脑执行「完整配置命令」。\n");
    }

    private String aerialConfigScript() {
        return ""
                + "# Aerial 完整配置（已在 Z9X / GMUI 实机验证过同类步骤）\n"
                + "adb install -r aerial-standard.apk\n"
                + "adb shell pm grant app.orchard.tv android.permission.WRITE_SECURE_SETTINGS\n"
                + "adb shell cmd notification allow_listener app.orchard.tv/app.orchard.tv.guard.RevivalListener\n"
                + "adb shell appops set app.orchard.tv SYSTEM_ALERT_WINDOW allow\n"
                + "adb shell pm disable-user --user 0 com.xgimi.home\n"
                + "adb shell cmd package set-home-activity app.orchard.tv/.MainActivity\n"
                + "adb shell am start -n app.orchard.tv/.MainActivity\n";
    }

    private void downloadAerial() {
        if (mBusy) return;
        mBusy = true;
        appendLog("\n>>> 下载 Aerial …\n");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String err = null;
                try {
                    err = downloadTo(AERIAL_URL_PRIMARY, mAerialApk);
                    if (err != null) {
                        err = downloadTo(AERIAL_URL_FALLBACK, mAerialApk);
                    }
                } catch (Throwable t) {
                    err = t.getMessage();
                }
                final String ferr = err;
                mHandler.post(new Runnable() {
                    @Override public void run() {
                        mBusy = false;
                        if (ferr == null) {
                            appendLog("下载完成: " + mAerialApk.getAbsolutePath()
                                    + " (" + mAerialApk.length() + " bytes)\n");
                            toast("下载完成");
                            showTab(2);
                        } else {
                            appendLog("下载失败: " + ferr + "\n可改用电脑 wget 后 adb install。\n");
                            toast("下载失败");
                        }
                    }
                });
            }
        }, "dl-aerial").start();
    }

    /** @return null on success, error message on failure */
    private String downloadTo(String urlStr, File dest) {
        HttpURLConnection conn = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(120000);
            conn.setRequestProperty("User-Agent", "Z9X-Manager/1.0");
            int code = conn.getResponseCode();
            // handle redirect manually if needed
            if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == 307 || code == 308) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                return downloadTo(loc, dest);
            }
            if (code != 200) return "HTTP " + code + " from " + urlStr;
            in = conn.getInputStream();
            File tmp = new File(dest.getAbsolutePath() + ".part");
            out = new FileOutputStream(tmp);
            byte[] buf = new byte[8192];
            int n;
            long total = 0;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
                total += n;
            }
            out.close();
            out = null;
            if (dest.exists()) dest.delete();
            if (!tmp.renameTo(dest)) {
                // fallback copy
                FileInputStream fis = new FileInputStream(tmp);
                FileOutputStream fos = new FileOutputStream(dest);
                while ((n = fis.read(buf)) >= 0) fos.write(buf, 0, n);
                fis.close();
                fos.close();
                tmp.delete();
            }
            if (dest.length() < 10000) return "文件过小 (" + dest.length() + ")，可能不是 APK";
            return null;
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private void installAerialApk() {
        if (!mAerialApk.exists()) {
            toast("请先下载");
            appendLog("未找到 " + mAerialApk.getAbsolutePath() + "\n");
            return;
        }
        appendLog("\n>>> PackageInstaller 安装 …\n");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PackageInstaller installer = getPackageManager().getPackageInstaller();
                    PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                            PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                    int sessionId = installer.createSession(params);
                    PackageInstaller.Session session = installer.openSession(sessionId);
                    InputStream in = new FileInputStream(mAerialApk);
                    OutputStream out = session.openWrite("aerial.apk", 0, mAerialApk.length());
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                    session.fsync(out);
                    out.close();
                    in.close();

                    Intent callback = new Intent(MainActivity.this, InstallReceiver.class);
                    callback.setAction(InstallReceiver.ACTION);
                    // Compile against API 28: avoid FLAG_MUTABLE symbol; UPDATE_CURRENT is enough here.
                    PendingIntent pi = PendingIntent.getBroadcast(
                            MainActivity.this, sessionId, callback,
                            PendingIntent.FLAG_UPDATE_CURRENT);
                    session.commit(pi.getIntentSender());
                    session.close();
                    mHandler.post(new Runnable() {
                        @Override public void run() {
                            appendLog("已提交安装会话，请在系统确认框点允许。\n");
                        }
                    });
                } catch (final Exception e) {
                    mHandler.post(new Runnable() {
                        @Override public void run() {
                            appendLog("安装失败: " + e.getMessage()
                                    + "\n可改：adb install -r " + mAerialApk.getAbsolutePath() + "\n");
                        }
                    });
                }
            }
        }, "install-aerial").start();
    }

    private void tryAerialConfig() {
        if (mBusy) return;
        mBusy = true;
        appendLog("\n>>> 尝试本机配置 Aerial（普通 uid 多半失败）…\n");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final StringBuilder log = new StringBuilder();
                log.append(ShellUtil.run("pm", "grant", PackageCatalog.AERIAL_PKG,
                        "android.permission.WRITE_SECURE_SETTINGS").summary());
                log.append(ShellUtil.run("cmd", "notification", "allow_listener",
                        "app.orchard.tv/app.orchard.tv.guard.RevivalListener").summary());
                log.append(ShellUtil.run("appops", "set", PackageCatalog.AERIAL_PKG,
                        "SYSTEM_ALERT_WINDOW", "allow").summary());
                log.append(ShellUtil.run("pm", "disable-user", "--user", "0",
                        PackageCatalog.XGIMI_HOME).summary());
                log.append(ShellUtil.run("cmd", "package", "set-home-activity",
                        "app.orchard.tv/.MainActivity").summary());
                log.append(ShellUtil.run("am", "start", "-n",
                        "app.orchard.tv/.MainActivity").summary());
                log.append("\n若多处 Permission denied，请复制完整配置命令到电脑执行。\n");
                mHandler.post(new Runnable() {
                    @Override public void run() {
                        mBusy = false;
                        appendLog(log.toString());
                    }
                });
            }
        }, "aerial-cfg").start();
    }

    @Override
    public void onInstallStatus(final int status, final String message) {
        mHandler.post(new Runnable() {
            @Override public void run() {
                appendLog("安装回调 status=" + status + " " + message + "\n");
                if (status == PackageInstaller.STATUS_SUCCESS) {
                    toast("Aerial 安装成功");
                    showTab(2);
                }
            }
        });
    }

    // ——— shared UI helpers ———

    private void refreshDeviceLine() {
        String model = ShellUtil.getprop("ro.product.model");
        String inc = ShellUtil.getprop("ro.build.version.incremental");
        String chip = ShellUtil.getprop("ro.xgimi.board.chip");
        String port = ShellUtil.getprop("service.adb.tcp.port");
        String pers = ShellUtil.getprop("persist.adb.tcp.port");
        String dbg = ShellUtil.getprop("xgimi.remoteDebug.on");
        mDeviceLine.setText(model + " · " + inc + " · chip=" + chip
                + " · adb.port=" + port + "/" + pers + " · remoteDebug=" + dbg);
    }

    private String pkgStatus(PackageManager pm, String pkg) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            int state = pm.getApplicationEnabledSetting(pkg);
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
                    || !ai.enabled) {
                return "已安装/停用";
            }
            return "已安装/启用";
        } catch (PackageManager.NameNotFoundException e) {
            return "未见（未装或已对本用户卸载）";
        }
    }

    private TextView section(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(0xFF58A6FF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, dp(4), 0, dp(8));
        return tv;
    }

    private TextView hint(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(0xFF8B949E);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setPadding(0, 0, 0, dp(8));
        return tv;
    }

    private Button tvButton(String label) {
        final Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        applyFocusChrome(b);
        return b;
    }

    private void applyFocusChrome(final Button b) {
        final GradientDrawable normal = new GradientDrawable();
        normal.setColor(0xFF21262D);
        normal.setCornerRadius(dp(8));
        normal.setStroke(dp(2), 0xFF30363D);

        final GradientDrawable focused = new GradientDrawable();
        focused.setColor(0xFF30363D);
        focused.setCornerRadius(dp(8));
        focused.setStroke(dp(3), Color.WHITE);

        b.setBackground(normal);
        b.setTextColor(Color.WHITE);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                b.setBackground(hasFocus ? focused : normal);
                float s = hasFocus ? 1.04f : 1f;
                b.animate().scaleX(s).scaleY(s).setDuration(100).start();
            }
        });
    }

    private LinearLayout.LayoutParams gap() {
        LinearLayout.LayoutParams g = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        g.leftMargin = dp(8);
        return g;
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }

    private void setLog(String s) {
        mLog.setText(s);
    }

    private void appendLog(String s) {
        CharSequence cur = mLog.getText();
        mLog.setText(cur == null ? s : cur.toString() + s);
    }

    private void copyText(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("z9x-manager", text));
            toast("已复制到剪贴板");
            appendLog("\n[已复制]\n" + text + "\n");
        } else {
            appendLog("\n无法访问剪贴板，请手动抄写：\n" + text + "\n");
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private static String padRight(String s, int n) {
        if (s.length() >= n) return s;
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) b.append(' ');
        return b.toString();
    }
}
