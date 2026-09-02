package com.michlind.packagetracker.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.michlind.packagetracker.BuildConfig
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

/** Availability of the companion SMS plugin, in the order the UI must explain it. */
enum class SmsPluginState {
    /** Plugin APK isn't installed. */
    NOT_INSTALLED,

    /** Installed, but the user hasn't granted it READ_SMS yet. */
    NOT_GRANTED,

    /** Installed and granted — scans will return results. */
    READY
}

/**
 * Finds SMS messages mentioning a tracking number and caches the hits in
 * [TrackingSmsDao].
 *
 * This app holds **no** SMS permission. Google Play Protect hard-blocks the
 * *install* of any sideloaded APK that declares READ_SMS, so the permission —
 * and the only code that touches the inbox — lives in a separate companion APK
 * (`:smsplugin`). We reach it through its ContentProvider, which authorises us
 * by signing certificate; both APKs are signed with the same key.
 *
 * Everything degrades quietly: with no plugin installed the scans no-op and the
 * cache simply stays empty. [pluginState] is what the UI uses to explain why.
 */
@Singleton
class SmsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TrackingSmsDao
) {

    // Debug builds of both APKs carry a .debug suffix so a debug pair can sit
    // alongside a release pair; the authority has to follow suit or the debug
    // app would query the release plugin (and be refused — different keys).
    private val authority: String =
        "com.michlind.packagetracker.smsplugin" +
            (if (BuildConfig.DEBUG) ".debug" else "") +
            ".provider"

    private val messagesUri: Uri = "content://$authority/messages".toUri()
    private val statusUri: Uri = "content://$authority/status".toUri()

    /**
     * Whether the plugin is installed and usable. Resolved fresh each call —
     * the user can install or grant while the app is in the background, and a
     * cached "no" would strand them until a restart.
     */
    fun pluginState(): SmsPluginState {
        val installed = context.packageManager.resolveContentProvider(authority, 0) != null
        if (!installed) return SmsPluginState.NOT_INSTALLED
        val granted = runCatching {
            context.contentResolver.query(statusUri, null, null, null, null)?.use { c ->
                c.moveToFirst() && c.getInt(c.getColumnIndexOrThrow(COL_GRANTED)) == 1
            } ?: false
        }.getOrElse {
            Log.w(TAG, "plugin status query failed", it)
            false
        }
        return (if (granted) SmsPluginState.READY else SmsPluginState.NOT_GRANTED)
            .also { Log.d(TAG, "plugin state: $it (authority=$authority)") }
    }

    /** Convenience for callers that only care whether a scan can return anything. */
    fun hasPermission(): Boolean = pluginState() == SmsPluginState.READY

    /**
     * Reactive list of cached SMS hits for [trackingNumber], oldest first.
     * Doesn't trigger a scan — call [scanForTrackingNumbers] (or wait for
     * the next syncStatus() to do it) to populate / refresh.
     */
    fun observeForTrackingNumber(trackingNumber: String): Flow<List<TrackingSms>> =
        dao.observeForTrackingNumber(trackingNumber).map { list ->
            list.map { it.toDomain() }
        }

    /**
     * Reactive list of cached SMS hits for any of [trackingNumbers], oldest
     * first; rows matching more than one TN appear once. Used for the
     * package detail screen so the SMS tab shows Cainiao-TN hits plus
     * local-courier-TN hits in one stream.
     */
    fun observeForTrackingNumbers(trackingNumbers: List<String>): Flow<List<TrackingSms>> =
        dao.observeForTrackingNumbers(trackingNumbers).map { list ->
            list.map { it.toDomain() }
        }

    /**
     * Ask the plugin for messages mentioning any of [trackingNumbers] and upsert
     * the hits into the cache. Safe to call unconditionally — no plugin, no
     * permission, or a plugin that refuses us all end in a silent no-op.
     * Idempotent: re-scans don't duplicate rows.
     */
    suspend fun scanForTrackingNumbers(trackingNumbers: List<String>) {
        val wanted = trackingNumbers.filter { it.isNotBlank() }.distinct()
        if (wanted.isEmpty()) return

        withContext(Dispatchers.IO) {
            val uri = messagesUri.buildUpon().apply {
                wanted.forEach { appendQueryParameter(QUERY_PARAM_TN, it) }
            }.build()

            val rows = mutableListOf<TrackingSmsEntity>()
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(COL_ID)
                    val tnCol = c.getColumnIndexOrThrow(COL_TRACKING_NUMBER)
                    val addrCol = c.getColumnIndexOrThrow(COL_ADDRESS)
                    val bodyCol = c.getColumnIndexOrThrow(COL_BODY)
                    val dateCol = c.getColumnIndexOrThrow(COL_DATE)
                    while (c.moveToNext()) {
                        rows += TrackingSmsEntity(
                            trackingNumber = c.getString(tnCol).orEmpty(),
                            smsId = c.getLong(idCol),
                            sender = c.getString(addrCol).orEmpty(),
                            body = c.getString(bodyCol).orEmpty(),
                            timestamp = c.getLong(dateCol)
                        )
                    }
                }
            }.onFailure {
                // SecurityException here means the signatures don't match —
                // most likely a debug app against a release plugin.
                Log.w(TAG, "plugin scan failed", it)
            }

            if (rows.isNotEmpty()) {
                dao.upsertAll(rows)
                Log.d(TAG, "plugin scan → ${rows.size} hit(s)")
            }
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

    // Mirrors TrackingSmsProvider's contract. Duplicated rather than shared via
    // a common module: two APKs that ship separately shouldn't be coupled by a
    // build dependency, and it's five strings.
    private companion object {
        const val COL_ID = "_id"
        const val COL_TRACKING_NUMBER = "tracking_number"
        const val COL_ADDRESS = "address"
        const val COL_BODY = "body"
        const val COL_DATE = "date"
        const val COL_GRANTED = "granted"
        const val QUERY_PARAM_TN = "tn"
    }
}
