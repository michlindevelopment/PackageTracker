package com.michlind.packagetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michlind.packagetracker.domain.model.PackageStatus
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
        get() = if (isMultiple) "Combined package - ${packages.size} items"
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

private enum class GroupSortMode {
    // Sort by orderDate DESC — used for active packages, where event time
    // gives a meaningful "freshness" ordering.
    BY_ORDER_DATE,
    // Used for received packages: the JS scrapes AliExpress's Processed tab
    // top-down (newest first on the page), so the newest order is inserted
    // first and gets the LOWEST auto-increment id in the batch. To keep the
    // visual order of AliExpress, we sort by min id ASC within a group, with
    // a lastUpdated DESC primary so manually-marked items (which set
    // lastUpdated = now) float above the import block.
    BY_RECEIVED
}

private fun List<TrackedPackage>.toGroups(
    sortMode: GroupSortMode = GroupSortMode.BY_ORDER_DATE
): List<PackageGroup> =
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
                GroupSortMode.BY_ORDER_DATE ->
                    groups.sortedByDescending { it.packages.maxOf { p -> p.orderDate() } }
                GroupSortMode.BY_RECEIVED ->
                    groups.sortedWith(
                        compareByDescending<PackageGroup> { g ->
                            g.packages.maxOf { it.lastUpdated }
                        }.thenBy { g ->
                            g.packages.minOf { it.id }
                        }
                    )
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
    private val imageDownloader: RemoteImageDownloader
) : ViewModel() {

    val activeGroups: StateFlow<List<PackageGroup>> = getActivePackages()
        .map { it.toGroups() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val receivedGroups: StateFlow<List<PackageGroup>> = getReceivedPackages()
        .map { it.toGroups(GroupSortMode.BY_RECEIVED) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notYetSentPackages: StateFlow<List<TrackedPackage>> = getNotYetSentPackages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                activeGroups.value
                    .map { it.trackingNumber }
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
