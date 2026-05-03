package com.michlind.packagetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michlind.packagetracker.data.preferences.SortPreferenceRepository
import com.michlind.packagetracker.domain.model.PackageStatus
import com.michlind.packagetracker.domain.model.SortMode
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.usecase.AddPackageUseCase
import com.michlind.packagetracker.domain.usecase.DeletePackageUseCase
import com.michlind.packagetracker.domain.usecase.GetActivePackagesUseCase
import com.michlind.packagetracker.domain.usecase.GetNotYetSentPackagesUseCase
import com.michlind.packagetracker.domain.usecase.GetReceivedPackagesUseCase
import com.michlind.packagetracker.domain.usecase.MarkAsReceivedUseCase
import com.michlind.packagetracker.domain.usecase.RefreshTrackingNumberUseCase
import com.michlind.packagetracker.util.RemoteImageDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PackageGroup(
    val trackingNumber: String,
    val packages: List<TrackedPackage>,
    val status: PackageStatus,
    val lastUpdated: Long
) {
    val isMultiple: Boolean get() = packages.size > 1
    val displayName: String
        get() = if (isMultiple) "Multi - ${packages.size} items"
                else packages.first().name.ifBlank { trackingNumber.ifBlank { "Package" } }
}

// "Order date" = earliest tracking event time for the package (when the order
// was first recorded by the carrier).
//   - With events: use the earliest event time.
//   - Active without events: use createdAt (order-placement time).
//   - Received without events: createdAt is unreliable for AliExpress imports
//     (legacy buggy fallback inverted the order; new fallback is 0 sentinel),
//     so fall back to lastUpdated instead — that's set by setReceived(now)
//     for manual marks, and 0 for imported-as-received, which lets the DAO's
//     `id DESC` tiebreaker order them by newest-scraped-first.
private fun TrackedPackage.orderDate(): Long =
    events.minOfOrNull { it.time }
        ?: if (isReceived) lastUpdated else createdAt

private fun List<TrackedPackage>.toGroups(sortMode: SortMode): List<PackageGroup> =
    groupBy { it.trackingNumber.ifBlank { it.id.toString() } }
        .entries
        .map { (tn, pkgs) ->
            // Sub-items inside a group: sort alphabetically by name (case-insensitive).
            val sorted = pkgs.sortedBy { it.name.lowercase() }
            PackageGroup(
                trackingNumber = tn,
                packages = sorted,
                status = sorted.maxByOrNull { it.status.stepIndex.coerceAtLeast(0) }?.status
                    ?: PackageStatus.UNKNOWN,
                lastUpdated = sorted.maxOf { it.lastUpdated }
            )
        }
        .let { groups ->
            when (sortMode) {
                SortMode.LAST_SHIPPED ->
                    groups.sortedByDescending { it.packages.maxOf { p -> p.orderDate() } }
                SortMode.FIRST_SHIPPED ->
                    groups.sortedBy { it.packages.minOf { p -> p.orderDate() } }
                SortMode.A_TO_Z ->
                    groups.sortedBy { it.displayName.lowercase() }
            }
        }

@HiltViewModel
class HomeViewModel @Inject constructor(
    getActivePackages: GetActivePackagesUseCase,
    getReceivedPackages: GetReceivedPackagesUseCase,
    getNotYetSentPackages: GetNotYetSentPackagesUseCase,
    private val deletePackage: DeletePackageUseCase,
    private val addPackage: AddPackageUseCase,
    private val markAsReceived: MarkAsReceivedUseCase,
    private val refreshTrackingNumber: RefreshTrackingNumberUseCase,
    private val imageDownloader: RemoteImageDownloader,
    private val sortPrefs: SortPreferenceRepository
) : ViewModel() {

    val sortMode: StateFlow<SortMode> = sortPrefs.mode

    fun setSortMode(mode: SortMode) { sortPrefs.setMode(mode) }

    val activeGroups: StateFlow<List<PackageGroup>> =
        combine(getActivePackages(), sortMode) { pkgs, mode -> pkgs.toGroups(mode) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val receivedGroups: StateFlow<List<PackageGroup>> =
        combine(getReceivedPackages(), sortMode) { pkgs, mode -> pkgs.toGroups(mode) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notYetSentPackages: StateFlow<List<TrackedPackage>> =
        combine(getNotYetSentPackages(), sortMode) { pkgs, mode ->
            // No real "ship date" yet — fall back to createdAt so the time-based
            // modes still produce a stable, sensible order.
            when (mode) {
                SortMode.LAST_SHIPPED -> pkgs.sortedByDescending { it.createdAt }
                SortMode.FIRST_SHIPPED -> pkgs.sortedBy { it.createdAt }
                SortMode.A_TO_Z -> pkgs.sortedBy { it.name.lowercase() }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Tracking number currently being refreshed (null when nothing is in flight).
    // Used by the home screen to animate the matching card's StatusBadge while
    // its server-side refresh is happening.
    private val _refreshingTrackingNumber = MutableStateFlow<String?>(null)
    val refreshingTrackingNumber: StateFlow<String?> = _refreshingTrackingNumber.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var lastDeletedPackages: List<TrackedPackage>? = null
    private var pendingPhotoCleanup: Job? = null

    fun deleteGroup(group: PackageGroup) {
        finalizePendingPhotoCleanup()
        lastDeletedPackages = group.packages
        viewModelScope.launch {
            group.packages.forEach { deletePackage(it.id) }
        }
        scheduleDeferredPhotoCleanup(group.packages)
    }

    fun delete(pkg: TrackedPackage) {
        finalizePendingPhotoCleanup()
        lastDeletedPackages = listOf(pkg)
        viewModelScope.launch { deletePackage(pkg.id) }
        scheduleDeferredPhotoCleanup(listOf(pkg))
    }

    fun undoDelete() {
        val pkgs = lastDeletedPackages ?: return
        // Cancel cleanup BEFORE re-adding so the file (still on disk) stays.
        pendingPhotoCleanup?.cancel()
        pendingPhotoCleanup = null
        lastDeletedPackages = null
        viewModelScope.launch {
            pkgs.forEach { addPackage(it) }
        }
    }

    // The delete snackbar uses SnackbarDuration.Short (~4s) plus a dismissal
    // animation. Wait a bit longer to make sure undo had its chance, then
    // delete the underlying image files.
    private fun scheduleDeferredPhotoCleanup(pkgs: List<TrackedPackage>) {
        pendingPhotoCleanup = viewModelScope.launch {
            delay(8_000)
            pkgs.forEach { it.photoUri?.let { uri -> imageDownloader.delete(uri) } }
            if (lastDeletedPackages === pkgs) lastDeletedPackages = null
            pendingPhotoCleanup = null
        }
    }

    // If a previous deletion's grace window is still open when a new delete
    // arrives, finalize it immediately — its undo affordance just got
    // replaced by the new snackbar.
    private fun finalizePendingPhotoCleanup() {
        val prev = lastDeletedPackages ?: return
        pendingPhotoCleanup?.cancel()
        pendingPhotoCleanup = null
        viewModelScope.launch {
            prev.forEach { it.photoUri?.let { uri -> imageDownloader.delete(uri) } }
        }
    }

    fun toggleReceived(id: Long, isReceived: Boolean) {
        viewModelScope.launch { markAsReceived(id, isReceived) }
    }

    fun toggleGroupReceived(group: PackageGroup, isReceived: Boolean) {
        viewModelScope.launch {
            group.packages.forEach { markAsReceived(it.id, isReceived) }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // Active packages plus any in the Received column that the user
                // marked manually before delivery actually completed (status
                // hasn't reached DELIVERED yet, so Cainiao may still have new
                // events for them).
                val activeTns = activeGroups.value.map { it.trackingNumber }
                val receivedTns = receivedGroups.value
                    .filter { g -> g.packages.any { it.status != PackageStatus.DELIVERED } }
                    .map { it.trackingNumber }
                (activeTns + receivedTns)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .forEach { tn ->
                        _refreshingTrackingNumber.value = tn
                        refreshTrackingNumber(tn)
                    }
            } catch (_: Exception) {
                _errorMessage.value = "Failed to refresh packages"
            } finally {
                _refreshingTrackingNumber.value = null
                _isRefreshing.value = false
            }
        }
    }

    fun clearError() { _errorMessage.value = null }
}
