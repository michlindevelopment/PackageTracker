package com.michlind.packagetracker.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.michlind.packagetracker.MainActivity
import com.michlind.packagetracker.R

object NotificationUtils {

    private const val TAG = "NotifUtils"

    const val CHANNEL_ID = "package_updates"
    private const val CHANNEL_NAME = "Package Updates"
    private const val CHANNEL_DESCRIPTION = "Notifications when your package status changes"

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESCRIPTION
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
        Log.d(TAG, "channel '$CHANNEL_ID' created/updated")
    }

    fun sendStatusUpdateNotification(
        context: Context,
        packageId: Long,
        packageName: String,
        newStatus: String,
        photoUri: String? = null
    ) {
        Log.d(TAG, "sendStatusUpdateNotification id=$packageId name=\"$packageName\" status=\"$newStatus\" photoUri=\"${photoUri ?: "<none>"}\"")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "POST_NOTIFICATIONS permission granted=$granted")
            if (!granted) {
                Log.w(TAG, "permission missing — silently dropping notification id=$packageId")
                return
            }
        }

        val nmc = NotificationManagerCompat.from(context)
        val areEnabled = nmc.areNotificationsEnabled()
        Log.d(TAG, "areNotificationsEnabled=$areEnabled")

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("package_id", packageId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            packageId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = loadBitmap(context, photoUri)
        Log.d(TAG, "largeIcon loaded=${largeIcon != null}")

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_empty_transit)
            .setContentTitle(packageName)
            .setContentText(newStatus)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (largeIcon != null) {
            builder
                .setLargeIcon(largeIcon)
                // Expanded layout shows the photo big with the standard text below.
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(largeIcon)
                        .bigLargeIcon(null as Bitmap?)
                )
        }
        val notification = builder.build()

        try {
            nmc.notify(packageId.toInt(), notification)
            Log.d(TAG, "notify() called for id=$packageId (channel=$CHANNEL_ID)")
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException posting notification id=$packageId", se)
        } catch (e: Exception) {
            Log.e(TAG, "Exception posting notification id=$packageId", e)
        }
    }

    // Decode a stored package image (file:// URI from RemoteImageDownloader,
    // a content:// URI, or a bare path) into a Bitmap suitable for
    // setLargeIcon / BigPictureStyle. Returns null if anything fails.
    private fun loadBitmap(context: Context, photoUri: String?): Bitmap? {
        if (photoUri.isNullOrBlank()) return null
        return runCatching {
            if (photoUri.startsWith("/")) {
                BitmapFactory.decodeFile(photoUri)
            } else {
                val uri = Uri.parse(photoUri)
                if (uri.scheme == "file") {
                    BitmapFactory.decodeFile(uri.path)
                } else {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
            }
        }.onFailure { Log.w(TAG, "loadBitmap failed for \"$photoUri\"", it) }.getOrNull()
    }
}
