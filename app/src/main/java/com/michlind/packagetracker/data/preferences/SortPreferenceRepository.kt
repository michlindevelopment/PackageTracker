package com.michlind.packagetracker.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.michlind.packagetracker.domain.model.SortMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SortPreferenceRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(loadMode())
    val mode: StateFlow<SortMode> = _mode.asStateFlow()

    fun setMode(value: SortMode) {
        prefs.edit { putString(KEY_SORT_MODE, value.name) }
        _mode.value = value
    }

    private fun loadMode(): SortMode {
        val raw = prefs.getString(KEY_SORT_MODE, null) ?: return SortMode.FIRST_SHIPPED
        return runCatching { SortMode.valueOf(raw) }.getOrDefault(SortMode.FIRST_SHIPPED)
    }

    private companion object {
        const val PREFS_NAME = "ptracker_settings"
        const val KEY_SORT_MODE = "home_sort_mode"
    }
}
