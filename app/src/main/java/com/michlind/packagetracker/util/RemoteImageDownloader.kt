package com.michlind.packagetracker.util

import android.content.Context
import androidx.core.net.toUri
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
     * Copies a user-chosen image into permanent app storage and returns the
     * new file:// URI, or null if the copy failed. The cropper writes its
     * output into the cache directory, which Android empties under storage
     * pressure and on "Clear cache", so a package photo left there silently
     * disappears later. The copy lands in [IMAGE_SUBDIR], so deleting the
     * package cleans it up with the rest.
     */
    suspend fun persistLocal(source: String, fileBaseName: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, IMAGE_SUBDIR).apply { if (!exists()) mkdirs() }
                val file = File(dir, "$fileBaseName.jpg")
                val sourceUri = source.toUri()
                val input = context.contentResolver.openInputStream(sourceUri)
                    ?: return@runCatching null
                input.use { src -> file.outputStream().use { out -> src.copyTo(out) } }
                // The cropper's cache copy is no longer needed.
                if (sourceUri.scheme == "file") {
                    sourceUri.path?.let { p ->
                        File(p).takeIf { it.parentFile == context.cacheDir }?.delete()
                    }
                }
                "file://${file.absolutePath}"
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
