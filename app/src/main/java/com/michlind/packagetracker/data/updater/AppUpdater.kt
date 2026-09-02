package com.michlind.packagetracker.data.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AppUpdater"

/** Name of the APK inside the install session — arbitrary, but must be stable. */
private const val SESSION_APK_NAME = "update.apk"

sealed interface DownloadProgress {
    data class Progress(val bytesRead: Long, val total: Long) : DownloadProgress
    data class Complete(val file: File) : DownloadProgress
    data class Failed(val message: String) : DownloadProgress
}

@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) {
    fun canInstallApks(): Boolean = context.packageManager.canRequestPackageInstalls()

    // Send the user to the system "Install unknown apps" page for *this* app.
    // After they grant the permission they'll need to come back and tap update again.
    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri()
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Download an APK to the cache. [fileName] keeps concurrent downloads of
     * different APKs (the app itself vs. the SMS plugin) off each other's file.
     */
    fun download(url: String, fileName: String = "app-update.apk"): Flow<DownloadProgress> = flow {
        val target = File(context.cacheDir, "updates/$fileName").apply {
            parentFile?.mkdirs()
            if (exists()) delete()
        }

        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            emit(DownloadProgress.Failed("Download failed: HTTP ${response.code}"))
            response.close()
            return@flow
        }

        val body = response.body
        val total = body.contentLength()
        body.byteStream().use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    bytesRead += read
                    emit(DownloadProgress.Progress(bytesRead, total))
                }
            }
        }
        response.close()
        emit(DownloadProgress.Complete(target))
    }.flowOn(Dispatchers.IO)

    /**
     * Installs [file] through the session-based PackageInstaller API. Returns
     * false if the session couldn't even be started (the user-facing install
     * confirmation is asynchronous — see [InstallResultReceiver]).
     *
     * Using a session here rather than the old "fire an ACTION_VIEW intent at
     * the APK" approach is load-bearing, not a cleanup. Android's Restricted
     * Settings lock — which greys out SMS, accessibility, notification
     * listener, overlay and usage-access for apps that didn't come from a
     * store — keys off *how* the app was installed: session-based installs are
     * exempt, legacy intent installs are not. Installing our own updates the
     * legacy way re-armed that lock on every single update, which is why
     * READ_SMS kept reverting to un-grantable no matter what the user did in
     * Settings. Committing a session makes the app its own installer of record
     * and keeps the exemption across updates.
     *
     * Note this only covers APKs *we* install. A first install downloaded in a
     * browser still arrives via the legacy path and still hits the lock once.
     *
     * [packageName] is the id of the APK being installed — it defaults to this
     * app (self-update) but is set explicitly when installing the SMS plugin,
     * which is a different package. That case is the whole reason this is a
     * parameter: the plugin is the APK that actually needs READ_SMS grantable,
     * and installing it from here is what keeps it out of the lock.
     */
    suspend fun launchInstall(
        file: File,
        packageName: String = context.packageName
    ): Boolean = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            setAppPackageName(packageName)
            // Restricted permissions are allowlisted by default, but state it
            // explicitly — keeping READ_SMS holdable is the entire reason this
            // code path exists, and a default is a thing that can change.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setWhitelistedRestrictedPermissions(
                    PackageInstaller.SessionParams.RESTRICTED_PERMISSIONS_ALL
                )
            }
        }

        var sessionId = -1
        runCatching {
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite(SESSION_APK_NAME, 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                session.commit(statusPendingIntent(sessionId).intentSender)
            }
            true
        }.getOrElse { error ->
            Log.w(TAG, "couldn't start install session", error)
            // A created-but-uncommitted session lingers and eats disk until the
            // system reaps it, so drop it on the way out.
            if (sessionId != -1) runCatching { installer.abandonSession(sessionId) }
            false
        }
    }

    // The session id keys the PendingIntent so concurrent/retried installs
    // don't clobber each other's callback. Must be mutable: the framework
    // fills in EXTRA_STATUS and friends before delivering it.
    private fun statusPendingIntent(sessionId: Int): PendingIntent {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }
        return PendingIntent.getBroadcast(
            context,
            sessionId,
            Intent(context, InstallResultReceiver::class.java),
            flags
        )
    }
}
