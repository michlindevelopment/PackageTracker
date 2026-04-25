package com.michlind.packagetracker.ui.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michlind.packagetracker.data.preferences.ThemePreferenceRepository
import com.michlind.packagetracker.domain.model.ThemePreference
import com.michlind.packagetracker.domain.repository.PackageRepository
import com.michlind.packagetracker.util.NotificationUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeRepo: ThemePreferenceRepository,
    private val packageRepository: PackageRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val theme: StateFlow<ThemePreference> = themeRepo.theme

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setTheme(value: ThemePreference) {
        themeRepo.setTheme(value)
    }

    fun sendTestNotification() {
        Log.d(TAG, "sendTestNotification() invoked")
        viewModelScope.launch {
            val packages = packageRepository.getNonReceivedPackages()
            Log.d(TAG, "fetched non-received packages: count=${packages.size}")
            if (packages.isEmpty()) {
                _message.value = "No packages to test with — add one first."
                Log.d(TAG, "no packages — aborting test notification")
                return@launch
            }
            val pkg = packages.random()
            val displayName = pkg.name.ifBlank { pkg.trackingNumber }
            Log.d(TAG, "picked package id=${pkg.id} name=\"$displayName\" status=${pkg.status}")
            NotificationUtils.sendStatusUpdateNotification(
                context = context,
                packageId = pkg.id,
                packageName = displayName,
                newStatus = pkg.status.displayName,
                photoUri = pkg.photoUri
            )
            _message.value = "Test notification sent for \"$displayName\""
            Log.d(TAG, "test notification request completed for id=${pkg.id}")
        }
    }

    fun clearMessage() { _message.value = null }

    private companion object { const val TAG = "Settings" }
}
