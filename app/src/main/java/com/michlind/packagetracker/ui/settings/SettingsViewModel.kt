package com.michlind.packagetracker.ui.settings

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michlind.packagetracker.BuildConfig
import com.michlind.packagetracker.data.preferences.AliImportPreferenceRepository
import com.michlind.packagetracker.data.preferences.NotificationPreferenceRepository
import com.michlind.packagetracker.data.preferences.SyncOnResumePreferenceRepository
import com.michlind.packagetracker.data.preferences.ThemePreferenceRepository
import com.michlind.packagetracker.data.updater.AppUpdater
import com.michlind.packagetracker.data.updater.DownloadProgress
import com.michlind.packagetracker.domain.model.ThemePreference
import com.michlind.packagetracker.domain.model.UpdateCheckResult
import com.michlind.packagetracker.domain.repository.PackageRepository
import com.michlind.packagetracker.domain.usecase.CheckForUpdateUseCase
import com.michlind.packagetracker.util.NotificationUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface UpdateUiState {
    object Idle : UpdateUiState
    object Checking : UpdateUiState
    object UpToDate : UpdateUiState
    data class Available(val latestVersion: String, val sizeBytes: Long) : UpdateUiState
    data class Downloading(val percent: Int) : UpdateUiState
    data class ReadyToInstall(val file: File) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
    object NeedsInstallPermission : UpdateUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeRepo: ThemePreferenceRepository,
    private val importPrefs: AliImportPreferenceRepository,
    private val notificationPrefs: NotificationPreferenceRepository,
    private val syncOnResumePrefs: SyncOnResumePreferenceRepository,
    private val packageRepository: PackageRepository,
    private val checkForUpdate: CheckForUpdateUseCase,
    private val appUpdater: AppUpdater,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val theme: StateFlow<ThemePreference> = themeRepo.theme

    val toShipPages: StateFlow<Int> = importPrefs.toShipPages
    val shippedPages: StateFlow<Int> = importPrefs.shippedPages
    val processedPages: StateFlow<Int> = importPrefs.processedPages

    fun setToShipPages(value: Int) = importPrefs.setToShipPages(value)
    fun setShippedPages(value: Int) = importPrefs.setShippedPages(value)
    fun setProcessedPages(value: Int) = importPrefs.setProcessedPages(value)

    val notificationsEnabled: StateFlow<Boolean> = notificationPrefs.enabled
    fun setNotificationsEnabled(value: Boolean) = notificationPrefs.setEnabled(value)

    val syncOnResumeEnabled: StateFlow<Boolean> = syncOnResumePrefs.enabled
    fun setSyncOnResumeEnabled(value: Boolean) = syncOnResumePrefs.setEnabled(value)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private val _isAliConnected = MutableStateFlow(false)
    val isAliConnected: StateFlow<Boolean> = _isAliConnected.asStateFlow()

    private var pendingDownloadUrl: String? = null

    init {
        refreshAliConnection()
    }

    // The AliExpress login marker is the `sign=y` cookie on aliexpress.com —
    // same heuristic used by AliImportScreen to decide login vs orders page.
    fun refreshAliConnection() {
        val cookies = try {
            CookieManager.getInstance().getCookie("https://www.aliexpress.com").orEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "refreshAliConnection failed", e)
            ""
        }
        _isAliConnected.value = cookies.contains("sign=y")
    }

    fun setTheme(value: ThemePreference) {
        themeRepo.setTheme(value)
    }

    fun sendTestNotification() {
        Log.d(TAG, "sendTestNotification() invoked")
        viewModelScope.launch {
            val packages = packageRepository.getNonReceivedPackages()
            Log.d(TAG, "fetched non-received packages: count=${packages.size}")
            if (packages.isEmpty()) {
                // The "no packages" case still needs the snackbar — there's no
                // notification to confirm success, so the user needs feedback.
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
            // Success feedback is the notification itself — no snackbar needed.
            Log.d(TAG, "test notification request completed for id=${pkg.id}")
        }
    }

    fun checkForUpdates() {
        if (_updateState.value is UpdateUiState.Downloading) return
        _updateState.value = UpdateUiState.Checking
        viewModelScope.launch {
            checkForUpdate().fold(
                onSuccess = { result ->
                    _updateState.value = when (result) {
                        is UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate
                        is UpdateCheckResult.Available -> {
                            pendingDownloadUrl = result.downloadUrl
                            UpdateUiState.Available(result.latestVersion, result.sizeBytes)
                        }
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "checkForUpdates failed", e)
                    _updateState.value = UpdateUiState.Error(
                        e.message ?: "Couldn't check for updates."
                    )
                }
            )
        }
    }

    // The user tapped "Update". If they haven't granted "Install unknown apps"
    // permission yet, we surface that as a state — the screen will show a
    // dialog and we'll send them to settings. Otherwise, start downloading.
    fun startUpdate() {
        val url = pendingDownloadUrl ?: return
        if (!appUpdater.canInstallApks()) {
            _updateState.value = UpdateUiState.NeedsInstallPermission
            return
        }
        downloadAndInstall(url)
    }

    fun openInstallPermissionSettings() {
        appUpdater.openInstallPermissionSettings()
        // After the user returns from settings, re-check so the Update button
        // reappears (with up-to-date version info) and the permission is
        // re-evaluated when they tap it again.
        checkForUpdates()
    }

    private fun downloadAndInstall(url: String) {
        viewModelScope.launch {
            appUpdater.download(url).collect { progress ->
                _updateState.value = when (progress) {
                    is DownloadProgress.Progress -> {
                        val percent = if (progress.total > 0) {
                            ((progress.bytesRead * 100) / progress.total).toInt()
                        } else 0
                        UpdateUiState.Downloading(percent)
                    }
                    is DownloadProgress.Complete -> {
                        appUpdater.launchInstall(progress.file)
                        UpdateUiState.ReadyToInstall(progress.file)
                    }
                    is DownloadProgress.Failed -> UpdateUiState.Error(progress.message)
                }
            }
        }
    }

    fun dismissUpdateState() {
        _updateState.value = UpdateUiState.Idle
    }

    // Clears the WebView session AliExpress uses for import — cookies (notably
    // `sign=y`, the login marker) and localStorage. Next import opens login.
    fun disconnectFromAliExpress() {
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            _isAliConnected.value = false
            _message.value = "Disconnected from AliExpress"
        } catch (e: Exception) {
            Log.e(TAG, "disconnectFromAliExpress failed", e)
            _message.value = "Couldn't disconnect from AliExpress"
        }
    }

    fun clearMessage() { _message.value = null }

    private companion object { const val TAG = "Settings" }
}
