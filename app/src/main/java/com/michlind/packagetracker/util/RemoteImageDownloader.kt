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

    companion object {
        private const val IMAGE_SUBDIR = "ali_images"
    }
}
