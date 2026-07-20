package com.michlind.packagetracker.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks which app version the user has already seen the "What's New" screen
 * for, so the changelog pops exactly once after an update (and never on a
 * fresh install).
 */
@Singleton
class ChangelogPreferenceRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * The versionCode the user last launched. 0 means "never launched before"
     * (fresh install) — used to suppress the changelog on first run.
     */
    var lastSeenVersionCode: Int
        get() = prefs.getInt(KEY_LAST_SEEN, 0)
        set(value) { prefs.edit { putInt(KEY_LAST_SEEN, value) } }

    private companion object {
        const val PREFS_NAME = "ptracker_settings"
        const val KEY_LAST_SEEN = "last_seen_version_code"
    }
}
