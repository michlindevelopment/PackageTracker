package com.michlind.packagetracker.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.michlind.packagetracker.domain.model.ThemePreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemePreferenceRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(loadTheme())
    val theme: StateFlow<ThemePreference> = _theme.asStateFlow()

    fun setTheme(value: ThemePreference) {
        prefs.edit().putString(KEY_THEME, value.name).apply()
        _theme.value = value
    }

    private fun loadTheme(): ThemePreference {
        val raw = prefs.getString(KEY_THEME, null) ?: return ThemePreference.SYSTEM
        return runCatching { ThemePreference.valueOf(raw) }
            .getOrDefault(ThemePreference.SYSTEM)
    }

    private companion object {
        const val PREFS_NAME = "ptracker_settings"
        const val KEY_THEME = "theme"
    }
}
