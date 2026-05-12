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
 * Debug toggle: when on, `PackageRepositoryImpl.trackPackage` short-circuits
 * the real Cainiao network call and returns a synthesized random response
 * instead. Lets you exercise the app's status/sort/UI flows without burning
 * Cainiao requests (which trigger the slide-puzzle CAPTCHA when hammered).
 */
@Singleton
class MockTrackingPreferenceRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, value) }
        _enabled.value = value
    }

    private companion object {
        const val PREFS_NAME = "ptracker_settings"
        const val KEY_ENABLED = "mock_tracking_enabled"
    }
}
