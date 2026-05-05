package com.michlind.packagetracker.data.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

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

    fun download(url: String): Flow<DownloadProgress> = flow {
        val target = File(context.cacheDir, "updates/app-update.apk").apply {
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

    fun launchInstall(file: File) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
