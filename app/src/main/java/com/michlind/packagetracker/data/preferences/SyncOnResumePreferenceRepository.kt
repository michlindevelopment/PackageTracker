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
 * User toggle for the auto-sync that runs whenever the app comes to the
 * foreground (cold start + resume from background). When off, the user has
 * to pull updates manually from the Refresh sheet. Default: on.
 */
@Singleton
class SyncOnResumePreferenceRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, value) }
        _enabled.value = value
    }

    private companion object {
        const val PREFS_NAME = "ptracker_settings"
        const val KEY_ENABLED = "sync_on_resume_enabled"
    }
}
