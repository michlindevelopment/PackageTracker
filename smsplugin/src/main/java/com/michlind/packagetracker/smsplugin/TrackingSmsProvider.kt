package com.michlind.packagetracker.smsplugin

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import android.provider.Telephony
import android.util.Log

private const val TAG = "TrackingSmsProvider"

private const val PATH_MESSAGES = "messages"
private const val PATH_STATUS = "status"
private const val MATCH_MESSAGES = 1
private const val MATCH_STATUS = 2

/**
 * Read-only window onto the SMS inbox, filtered to messages that mention a
 * tracking number. The main app has no SMS permission of its own and reaches
 * the inbox only through here.
 *
 * Contract (mirrored in the main app's SmsRepository — keep the two in step):
 *
 *   content://<authority>/messages?tn=A&tn=B
 *       Rows for inbox messages whose body contains any of the `tn` values.
 *       Columns: [COL_ID], [COL_TRACKING_NUMBER], [COL_ADDRESS], [COL_BODY],
 *       [COL_DATE]. A message mentioning two tracking numbers yields one row
 *       per number, matching how the app keys its cache.
 *
 *   content://<authority>/status
 *       Exactly one row, column [COL_GRANTED] = 1 when this plugin actually
 *       holds READ_SMS. Lets the app tell "plugin missing" from "plugin
 *       installed but not yet granted", which need different prompts.
 *
 * Only writes are unsupported; every mutating method throws.
 */
class TrackingSmsProvider : ContentProvider() {

    companion object {
        const val COL_ID = "_id"
        const val COL_TRACKING_NUMBER = "tracking_number"
        const val COL_ADDRESS = "address"
        const val COL_BODY = "body"
        const val COL_DATE = "date"
        const val COL_GRANTED = "granted"
        const val QUERY_PARAM_TN = "tn"
    }

    private lateinit var matcher: UriMatcher

    override fun onCreate(): Boolean {
        val authority = "${context!!.packageName}.provider"
        matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(authority, PATH_MESSAGES, MATCH_MESSAGES)
            addURI(authority, PATH_STATUS, MATCH_STATUS)
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        requireTrustedCaller()
        return when (matcher.match(uri)) {
            MATCH_STATUS -> statusCursor()
            MATCH_MESSAGES -> messagesCursor(uri.getQueryParameters(QUERY_PARAM_TN))
            else -> throw IllegalArgumentException("Unsupported URI: $uri")
        }
    }

    /**
     * Authorises by signing certificate rather than by a declared permission.
     * SIGNATURE_MATCH means the calling UID's packages are signed with the same
     * certificate as this plugin, which only the real app can be. Anything else
     * — including another app that guessed the authority — is refused.
     */
    private fun requireTrustedCaller() {
        val pm = context!!.packageManager
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid()) return
        if (pm.checkSignatures(callingUid, Process.myUid()) != PackageManager.SIGNATURE_MATCH) {
            val who = pm.getNameForUid(callingUid) ?: "uid $callingUid"
            Log.w(TAG, "refusing query from $who — signature mismatch")
            throw SecurityException("Caller is not signed with the plugin's certificate")
        }
    }

    private fun hasSmsPermission(): Boolean =
        context!!.checkSelfPermission(Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private fun statusCursor(): Cursor =
        MatrixCursor(arrayOf(COL_GRANTED)).apply {
            addRow(arrayOf<Any>(if (hasSmsPermission()) 1 else 0))
        }

    /**
     * One LIKE query per tracking number. Returning an empty cursor when the
     * permission is missing (rather than throwing) keeps the app's refresh path
     * simple — it can query unconditionally and consult /status only when it
     * needs to explain an empty result to the user.
     */
    private fun messagesCursor(trackingNumbers: List<String>): Cursor {
        val out = MatrixCursor(
            arrayOf(COL_ID, COL_TRACKING_NUMBER, COL_ADDRESS, COL_BODY, COL_DATE)
        )
        if (!hasSmsPermission()) {
            Log.d(TAG, "query with no READ_SMS granted — returning empty")
            return out
        }

        val wanted = trackingNumbers.filter { it.isNotBlank() }.distinct()
        if (wanted.isEmpty()) return out

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        wanted.forEach { tn ->
            runCatching {
                context!!.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    projection,
                    "${Telephony.Sms.BODY} LIKE ?",
                    arrayOf("%$tn%"),
                    "${Telephony.Sms.DATE} DESC"
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                    val addrCol = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    val bodyCol = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    val dateCol = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                    while (c.moveToNext()) {
                        out.addRow(
                            arrayOf<Any>(
                                c.getLong(idCol),
                                tn,
                                c.getString(addrCol).orEmpty(),
                                c.getString(bodyCol).orEmpty(),
                                c.getLong(dateCol)
                            )
                        )
                    }
                }
            }.onFailure { Log.w(TAG, "inbox query failed for $tn", it) }
        }
        return out
    }

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        MATCH_MESSAGES -> "vnd.android.cursor.dir/vnd.$PATH_MESSAGES"
        MATCH_STATUS -> "vnd.android.cursor.item/vnd.$PATH_STATUS"
        else -> null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = throw UnsupportedOperationException("read-only")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = throw UnsupportedOperationException("read-only")
}
