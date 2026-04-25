package com.michlind.packagetracker.ui.settings

import androidx.lifecycle.ViewModel
import com.michlind.packagetracker.data.preferences.ThemePreferenceRepository
import com.michlind.packagetracker.domain.model.ThemePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeRepo: ThemePreferenceRepository
) : ViewModel() {
    val theme: StateFlow<ThemePreference> = themeRepo.theme

    fun setTheme(value: ThemePreference) {
        themeRepo.setTheme(value)
    }
}
