package com.michlind.packagetracker.data.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

private const val TAG = "InstallResult"

/**
 * Receives the outcome of the session-based install started by [AppUpdater].
 *
 * The case that actually matters is [PackageInstaller.STATUS_PENDING_USER_ACTION]:
 * the framework doesn't show the "install this update?" confirmation itself,
 * it hands back an Intent and expects the installer app to launch it. Miss
 * this and the session just sits there — the update silently never happens.
 *
 * The terminal statuses are logged only. On success the process is replaced by
 * the new build, so there's rarely anyone left to observe it.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                if (confirm == null) {
                    Log.w(TAG, "pending user action with no confirmation intent")
                    return
                }
                // We're a receiver, not an Activity — the dialog needs its own task.
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
                    .onFailure { Log.w(TAG, "couldn't show install confirmation", it) }
            }

            PackageInstaller.STATUS_SUCCESS -> Log.d(TAG, "install succeeded")

            else -> Log.w(
                TAG,
                "install failed (status=$status): " +
                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            )
        }
    }
}
