package local.z9x.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

/** Receives PackageInstaller session status; forwards to MainActivity via static callback. */
class InstallReceiver : BroadcastReceiver() {

    fun interface Callback {
        fun onInstallStatus(status: Int, message: String)
    }

    companion object {
        const val ACTION = "local.z9x.manager.INSTALL_STATUS"
        private const val TAG = "Z9xManagerInstall"

        @Volatile
        private var callback: Callback? = null

        fun setCallback(c: Callback?) {
            callback = c
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.i(TAG, "status=$status msg=$msg")
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(confirm)
                } catch (e: Exception) {
                    Log.e(TAG, "confirm failed", e)
                }
            }
        }
        callback?.onInstallStatus(status, msg ?: "")
    }
}
