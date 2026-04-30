package com.michlind.packagetracker.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteImageDownloader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val client: OkHttpClient
) {
    suspend fun download(url: String, fileBaseName: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, IMAGE_SUBDIR).apply { if (!exists()) mkdirs() }
            val ext = url.substringAfterLast('.', "jpg").substringBefore('?').take(4).ifBlank { "jpg" }
            val file = File(dir, "$fileBaseName.$ext")
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                file.outputStream().use { out -> resp.body.byteStream().copyTo(out) }
                "file://${file.absolutePath}"
            }
        }.getOrNull()
    }

    /**
     * Best-effort delete of a previously-downloaded image. Only deletes files
     * that live in our [IMAGE_SUBDIR] so we never wipe out a user-supplied
     * photo (e.g. a content:// URI from the gallery).
     */
    suspend fun delete(photoUri: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (!photoUri.startsWith("file://")) return@runCatching false
            val file = File(photoUri.removePrefix("file://"))
            if (file.parentFile?.name != IMAGE_SUBDIR) return@runCatching false
            file.exists() && file.delete()
        }.getOrDefault(false)
    }

    companion object {
        private const val IMAGE_SUBDIR = "ali_images"
    }
}
