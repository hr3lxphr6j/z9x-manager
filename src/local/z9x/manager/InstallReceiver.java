package local.z9x.manager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;

/** Receives PackageInstaller session status; forwards to MainActivity via static callback. */
public class InstallReceiver extends BroadcastReceiver {
    public static final String ACTION = "local.z9x.manager.INSTALL_STATUS";
    private static final String TAG = "Z9xManagerInstall";

    public interface Callback {
        void onInstallStatus(int status, String message);
    }

    private static volatile Callback sCallback;

    public static void setCallback(Callback c) {
        sCallback = c;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        Log.i(TAG, "status=" + status + " msg=" + msg);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(confirm);
                } catch (Exception e) {
                    Log.e(TAG, "confirm failed", e);
                }
            }
        }
        Callback c = sCallback;
        if (c != null) {
            c.onInstallStatus(status, msg != null ? msg : "");
        }
    }
}
