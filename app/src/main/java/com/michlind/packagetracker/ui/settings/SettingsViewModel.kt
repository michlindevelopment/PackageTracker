package com.michlind.packagetracker.ui.settings

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michlind.packagetracker.BuildConfig
import com.michlind.packagetracker.data.preferences.AliImportPreferenceRepository
import com.michlind.packagetracker.data.preferences.MockTrackingPreferenceRepository
import com.michlind.packagetracker.data.preferences.NotificationPreferenceRepository
import com.michlind.packagetracker.data.preferences.SyncOnResumePreferenceRepository
import com.michlind.packagetracker.data.preferences.ThemePreferenceRepository
import com.michlind.packagetracker.data.updater.AppUpdater
import com.michlind.packagetracker.data.updater.DownloadProgress
import com.michlind.packagetracker.domain.model.PackageStatus
import com.michlind.packagetracker.domain.model.ThemePreference
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.model.TrackingEvent
import com.michlind.packagetracker.domain.model.UpdateCheckResult
import com.michlind.packagetracker.domain.repository.PackageRepository
import com.michlind.packagetracker.domain.usecase.CheckForUpdateUseCase
import com.michlind.packagetracker.util.NotificationUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    private val mockTrackingPrefs: MockTrackingPreferenceRepository,
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

    val mockTrackingEnabled: StateFlow<Boolean> = mockTrackingPrefs.enabled
    fun setMockTrackingEnabled(value: Boolean) = mockTrackingPrefs.setEnabled(value)

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
    // same heuristic used by AliLoginScreen and BgAliImportWebView.
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
                        if (appUpdater.launchInstall(progress.file)) {
                            UpdateUiState.ReadyToInstall(progress.file)
                        } else {
                            UpdateUiState.Error("Couldn't start the installer. Please try again.")
                        }
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

    // ---- Debug-only: seed / remove mock packages -------------------------
    // Reached only from rows the screen gates behind BuildConfig.DEBUG.
    // Mocks are identifiable for cleanup two ways: name starts with
    // MOCK_NAME_PREFIX and any non-blank tracking number starts with
    // MOCK_TN_PREFIX.

    fun seedMockPackages() {
        viewModelScope.launch {
            val mocks = buildMockPackages()
            mocks.forEach { packageRepository.addPackage(it) }
            _message.value = "Added ${mocks.size} mock packages"
        }
    }

    fun removeMockPackages() {
        viewModelScope.launch {
            val all = packageRepository.getNonReceivedPackages() +
                packageRepository.getReceivedPackages().first()
            val mocks = all.filter {
                it.name.startsWith(MOCK_NAME_PREFIX) ||
                    it.trackingNumber.startsWith(MOCK_TN_PREFIX)
            }
            mocks.forEach { packageRepository.deletePackage(it.id) }
            _message.value =
                if (mocks.isEmpty()) "No mock packages found"
                else "Removed ${mocks.size} mock packages"
        }
    }

    private companion object {
        const val TAG = "Settings"
        const val MOCK_NAME_PREFIX = "Mock — "
        const val MOCK_TN_PREFIX = "MOCK"
        const val HOUR_MS = 60L * 60 * 1000
        const val DAY_MS = 24 * HOUR_MS
    }

    // Cainiao-style stage descriptions, borrowed from
    // data/api/MockCainiaoResponseGenerator's stage table.
    private fun buildMockPackages(): List<TrackedPackage> = listOf(
        // -- Not yet sent (blank TN): ungrouped cards on the "On the Way" tab
        mockPackage(
            name = "Mock — Phone Case", trackingNumber = "",
            status = PackageStatus.NOT_YET_SENT,
            createdDaysAgo = 2, updatedHoursAgo = 5, etaDaysFromNow = 24
        ),
        mockPackage(
            name = "Mock — USB-C Cables", trackingNumber = "",
            status = PackageStatus.NOT_YET_SENT,
            createdDaysAgo = 5, updatedHoursAgo = 30, etaDaysFromNow = 20
        ),
        mockPackage(
            name = "Mock — Desk Lamp", trackingNumber = "",
            status = PackageStatus.NOT_YET_SENT,
            createdDaysAgo = 9, updatedHoursAgo = 60, etaDaysFromNow = 15
        ),
        // -- In transit: distinct statuses, staggered dates
        mockPackage(
            name = "Mock — Wireless Earbuds", trackingNumber = "MOCK1000001CN",
            status = PackageStatus.SHIPPED,
            createdDaysAgo = 4, updatedHoursAgo = 8, etaDaysFromNow = 18,
            daysInTransit = "3 days",
            events = mockEvents(
                startDaysAgo = 3,
                "GWMS_ACCEPT" to "Order received successfully",
                "GWMS_OUTBOUND" to "Leave the warehouse"
            )
        ),
        mockPackage(
            name = "Mock — Bluetooth Speaker", trackingNumber = "MOCK1000002CN",
            status = PackageStatus.IN_FLIGHT,
            createdDaysAgo = 12, updatedHoursAgo = 14, etaDaysFromNow = 9,
            daysInTransit = "10 days",
            events = mockEvents(
                startDaysAgo = 10,
                "GWMS_ACCEPT" to "Order received successfully",
                "PU_PICKUP_SUCCESS" to "Accepted by carrier",
                "SC_OUTBOUND_SUCCESS" to "Outbound in sorting center",
                "LH_HO_AIRLINE" to "Leaving from departure country/region"
            )
        ),
        mockPackage(
            name = "Mock — Smart Watch Band", trackingNumber = "MOCK1000003CN",
            status = PackageStatus.CUSTOMS,
            createdDaysAgo = 18, updatedHoursAgo = 26, etaDaysFromNow = 5,
            daysInTransit = "16 days",
            events = mockEvents(
                startDaysAgo = 16,
                "GWMS_ACCEPT" to "Order received successfully",
                "PU_PICKUP_SUCCESS" to "Accepted by carrier",
                "LH_DEPART" to "Left from departure country/region",
                "LH_ARRIVE" to "Arrived at linehaul office",
                "CC_IM_START" to "Import clearance start"
            )
        ),
        mockPackage(
            name = "Mock — Laptop Sleeve", trackingNumber = "MOCK1000004CN",
            status = PackageStatus.OUT_FOR_DELIVERY,
            createdDaysAgo = 25, updatedHoursAgo = 3, etaDaysFromNow = 1,
            daysInTransit = "23 days",
            events = mockEvents(
                startDaysAgo = 23,
                "GWMS_ACCEPT" to "Order received successfully",
                "LH_DEPART" to "Left from departure country/region",
                "CC_IM_SUCCESS" to "Import customs clearance complete",
                "GTMS_ACCEPT" to "Received by local delivery company",
                "GTMS_DO_DEPART" to "Out for delivery"
            )
        ),
        // -- Group: two packages sharing ONE tracking number (combined parcel)
        mockPackage(
            name = "Mock — LED Strip Lights", trackingNumber = "MOCKGROUP01CN",
            status = PackageStatus.IN_TRANSIT,
            createdDaysAgo = 15, updatedHoursAgo = 20, etaDaysFromNow = 7,
            daysInTransit = "13 days",
            events = mockEvents(
                startDaysAgo = 13,
                "GWMS_ACCEPT" to "Order received successfully",
                "PU_PICKUP_SUCCESS" to "Accepted by carrier",
                "SC_INBOUND_SUCCESS" to "Inbound in sorting center"
            )
        ),
        mockPackage(
            name = "Mock — Cable Organizer", trackingNumber = "MOCKGROUP01CN",
            status = PackageStatus.IN_TRANSIT,
            createdDaysAgo = 15, updatedHoursAgo = 20, etaDaysFromNow = 7,
            daysInTransit = "13 days",
            events = mockEvents(
                startDaysAgo = 13,
                "GWMS_ACCEPT" to "Order received successfully",
                "PU_PICKUP_SUCCESS" to "Accepted by carrier",
                "SC_INBOUND_SUCCESS" to "Inbound in sorting center"
            )
        ),
        // -- Received
        mockPackage(
            name = "Mock — Screen Protector", trackingNumber = "MOCK2000001CN",
            status = PackageStatus.DELIVERED, isReceived = true,
            createdDaysAgo = 40, updatedHoursAgo = 10 * 24, etaDaysFromNow = null,
            daysInTransit = "28 days",
            events = mockEvents(
                startDaysAgo = 38,
                "GWMS_ACCEPT" to "Order received successfully",
                "LH_DEPART" to "Left from departure country/region",
                "GTMS_DO_DEPART" to "Out for delivery",
                "SIGN" to "Signed"
            )
        ),
        mockPackage(
            name = "Mock — Kitchen Scale", trackingNumber = "MOCK2000002CN",
            status = PackageStatus.DELIVERED, isReceived = true,
            createdDaysAgo = 33, updatedHoursAgo = 5 * 24, etaDaysFromNow = null,
            daysInTransit = "26 days",
            events = mockEvents(
                startDaysAgo = 31,
                "GWMS_ACCEPT" to "Order received successfully",
                "CC_IM_SUCCESS" to "Import customs clearance complete",
                "SIGN" to "Signed"
            )
        ),
        mockPackage(
            name = "Mock — Yoga Mat", trackingNumber = "MOCK2000003CN",
            status = PackageStatus.DELIVERED, isReceived = true,
            createdDaysAgo = 21, updatedHoursAgo = 2 * 24, etaDaysFromNow = null,
            daysInTransit = "18 days",
            events = mockEvents(
                startDaysAgo = 19,
                "GWMS_ACCEPT" to "Order received successfully",
                "GTMS_ACCEPT" to "Received by local delivery company",
                "SIGN" to "Signed"
            )
        )
    )

    private fun mockPackage(
        name: String,
        trackingNumber: String,
        status: PackageStatus,
        createdDaysAgo: Int,
        updatedHoursAgo: Int,
        etaDaysFromNow: Int?,
        isReceived: Boolean = false,
        daysInTransit: String? = null,
        events: List<TrackingEvent> = emptyList()
    ): TrackedPackage {
        val now = System.currentTimeMillis()
        return TrackedPackage(
            trackingNumber = trackingNumber,
            name = name,
            photoUri = null,
            status = status,
            statusDescription = status.displayName,
            lastEvent = events.lastOrNull(),
            events = events,
            lastUpdated = now - updatedHoursAgo * HOUR_MS,
            isReceived = isReceived,
            createdAt = now - createdDaysAgo * DAY_MS,
            estimatedDeliveryTime = etaDaysFromNow?.let { now + it * DAY_MS },
            daysInTransit = daysInTransit,
            originCountry = "Mainland China",
            destCountry = "Israel"
        )
    }

    // Chronological events spread from [startDaysAgo] up to ~now, one per
    // stage, so the Detail timeline looks plausible.
    private fun mockEvents(
        startDaysAgo: Int,
        vararg stages: Pair<String, String>
    ): List<TrackingEvent> {
        val now = System.currentTimeMillis()
        val start = now - startDaysAgo * DAY_MS
        val step = if (stages.size > 1) (now - HOUR_MS - start) / (stages.size - 1) else 0L
        val fmt = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US)
        return stages.mapIndexed { i, (actionCode, description) ->
            val time = start + i * step
            TrackingEvent(
                time = time,
                timeStr = fmt.format(Date(time)),
                description = description,
                standardDescription = description,
                actionCode = actionCode,
                groupDescription = null
            )
        }
    }
}
