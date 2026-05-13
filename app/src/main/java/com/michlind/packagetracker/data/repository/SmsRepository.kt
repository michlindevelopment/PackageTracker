package com.michlind.packagetracker.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.michlind.packagetracker.data.db.TrackingSmsDao
import com.michlind.packagetracker.data.db.TrackingSmsEntity
import com.michlind.packagetracker.domain.model.TrackingSms
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SmsRepo"

/**
 * Scans the device SMS inbox for messages whose body mentions a tracking
 * number and caches the matches in [TrackingSmsDao]. Reads from the
 * [Telephony.Sms.Inbox] content provider; needs `READ_SMS` granted at
 * runtime.
 *
 * Permission failures are swallowed silently so syncStatus() can call this
 * unconditionally — callers don't need to gate on permission state. The
 * DetailScreen SMS tab is what surfaces "permission required" UX.
 */
@Singleton
class SmsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TrackingSmsDao
) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Reactive list of cached SMS hits for [trackingNumber], newest first.
     * Doesn't trigger a scan — call [scanForTrackingNumbers] (or wait for
     * the next syncStatus() to do it) to populate / refresh.
     */
    fun observeForTrackingNumber(trackingNumber: String): Flow<List<TrackingSms>> =
        dao.observeForTrackingNumber(trackingNumber).map { list ->
            list.map { it.toDomain() }
        }

    /**
     * Reactive list of cached SMS hits for any of [trackingNumbers], newest
     * first; rows matching more than one TN appear once. Used for the
     * package detail screen so the SMS tab shows Cainiao-TN hits plus
     * local-courier-TN hits in one stream.
     */
    fun observeForTrackingNumbers(trackingNumbers: List<String>): Flow<List<TrackingSms>> =
        dao.observeForTrackingNumbers(trackingNumbers).map { list ->
            list.map { it.toDomain() }
        }

    /**
     * For each tracking number, query the SMS inbox for messages whose body
     * contains it as a substring, and upsert hits into the cache. No-op if
     * READ_SMS isn't granted (so this is safe to call from syncStatus()
     * without a pre-check). Idempotent: re-scans don't duplicate rows.
     */
    suspend fun scanForTrackingNumbers(trackingNumbers: List<String>) {
        if (trackingNumbers.isEmpty()) return
        if (!hasPermission()) {
            Log.d(TAG, "scan skipped — READ_SMS not granted")
            return
        }
        withContext(Dispatchers.IO) {
            trackingNumbers
                .filter { it.isNotBlank() }
                .distinct()
                .forEach { tn -> scanOne(tn) }
        }
    }

    private suspend fun scanOne(trackingNumber: String) {
        val resolver = context.contentResolver
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        // LIKE %tn% — substring search inside the body. Cainiao TNs are
        // ~12-16 chars and unique enough that false-positives in arbitrary
        // SMS bodies are practically impossible.
        val selection = "${Telephony.Sms.BODY} LIKE ?"
        val args = arrayOf("%$trackingNumber%")

        val rows = mutableListOf<TrackingSmsEntity>()
        runCatching {
            resolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                args,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addrCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (cursor.moveToNext()) {
                    rows += TrackingSmsEntity(
                        trackingNumber = trackingNumber,
                        smsId = cursor.getLong(idCol),
                        sender = cursor.getString(addrCol).orEmpty(),
                        body = cursor.getString(bodyCol).orEmpty(),
                        timestamp = cursor.getLong(dateCol)
                    )
                }
            }
        }.onFailure { Log.w(TAG, "scan failed for $trackingNumber", it) }

        if (rows.isNotEmpty()) {
            dao.upsertAll(rows)
            Log.d(TAG, "scan $trackingNumber → ${rows.size} hit(s)")
        }
    }

    private fun TrackingSmsEntity.toDomain() = TrackingSms(
        id = id,
        trackingNumber = trackingNumber,
        smsId = smsId,
        sender = sender,
        body = body,
        timestamp = timestamp
    )
}
