package com.michlind.packagetracker.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-tab "max expand passes" budget for the AliExpress import.
 * 0 = skip the tab entirely, >0 = max number of "View more" clicks before
 * we stop expanding that tab. Default 20/20/1.
 *
 * The storage keys predate AliExpress's tab rename and are kept as-is so
 * saved user values survive. They now map to the current tabs:
 *   toShipPages  (ali_pages_to_ship)  -> "Processing" tab (awaiting shipment)
 *   shippedPages (ali_pages_shipped)  -> "Processed"  tab (in transit)
 *   processedPages (ali_pages_processed) -> "Completed" tab (received) — kept
 *     at 1 because it's usually huge; we only pull the first page unless the
 *     user opts in.
 */
@Singleton
class AliImportPreferenceRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _toShipPages = MutableStateFlow(loadInt(KEY_TO_SHIP, DEFAULT_TO_SHIP))
    val toShipPages: StateFlow<Int> = _toShipPages.asStateFlow()

    private val _shippedPages = MutableStateFlow(loadInt(KEY_SHIPPED, DEFAULT_SHIPPED))
    val shippedPages: StateFlow<Int> = _shippedPages.asStateFlow()

    private val _processedPages = MutableStateFlow(loadInt(KEY_PROCESSED, DEFAULT_PROCESSED))
    val processedPages: StateFlow<Int> = _processedPages.asStateFlow()

    fun setToShipPages(value: Int) = saveAndPublish(KEY_TO_SHIP, value, _toShipPages)
    fun setShippedPages(value: Int) = saveAndPublish(KEY_SHIPPED, value, _shippedPages)
    fun setProcessedPages(value: Int) = saveAndPublish(KEY_PROCESSED, value, _processedPages)

    /**
     * True once the user has successfully connected to AliExpress at least
     * once (a background import that actually reached the order list). Used to
     * decide whether a later "no session" outcome should surface the
     * "Disconnected from AliExpress" prompt — we only nag users who were
     * connected before, never ones who never signed in.
     */
    var hasConnectedBefore: Boolean
        get() = prefs.getBoolean(KEY_CONNECTED, false)
        set(value) { prefs.edit { putBoolean(KEY_CONNECTED, value) } }

    /**
     * True when we believe the AliExpress session is gone (last known state).
     * Persisted so the home screen knows on cold launch — before any network
     * work — to show the reconnect prompt and hide the "Sync packages from
     * AliExpress" option. Cleared once a sync succeeds again.
     */
    var aliDisconnected: Boolean
        get() = prefs.getBoolean(KEY_DISCONNECTED, false)
        set(value) { prefs.edit { putBoolean(KEY_DISCONNECTED, value) } }

    /**
     * True once the user has dismissed the reconnect dialog for the CURRENT
     * disconnected episode. Persisted so "Dismiss" sticks across launches (the
     * dialog won't nag again). Reset when the session reconnects, so a fresh
     * disconnection later still prompts.
     */
    var disconnectDialogDismissed: Boolean
        get() = prefs.getBoolean(KEY_DISCONNECT_DISMISSED, false)
        set(value) { prefs.edit { putBoolean(KEY_DISCONNECT_DISMISSED, value) } }

    private fun saveAndPublish(key: String, raw: Int, flow: MutableStateFlow<Int>) {
        val clamped = raw.coerceIn(0, MAX_PAGES)
        prefs.edit { putInt(key, clamped) }
        flow.value = clamped
    }

    private fun loadInt(key: String, default: Int): Int =
        prefs.getInt(key, default).coerceIn(0, MAX_PAGES)

    private companion object {
        const val PREFS_NAME = "ptracker_settings"
        const val KEY_TO_SHIP = "ali_pages_to_ship"
        const val KEY_SHIPPED = "ali_pages_shipped"
        const val KEY_PROCESSED = "ali_pages_processed"
        const val KEY_CONNECTED = "ali_has_connected"
        const val KEY_DISCONNECTED = "ali_disconnected"
        const val KEY_DISCONNECT_DISMISSED = "ali_disconnect_dismissed"
        const val DEFAULT_TO_SHIP = 20
        const val DEFAULT_SHIPPED = 20
        const val DEFAULT_PROCESSED = 1
        const val MAX_PAGES = 100
    }
}
