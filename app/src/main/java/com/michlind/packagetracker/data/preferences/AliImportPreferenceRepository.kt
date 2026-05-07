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
 * we stop expanding that tab. Default 20/20/1 — the Processed tab is
 * usually huge so we only pull the first page unless the user opts in.
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
        const val DEFAULT_TO_SHIP = 20
        const val DEFAULT_SHIPPED = 20
        const val DEFAULT_PROCESSED = 1
        const val MAX_PAGES = 100
    }
}
