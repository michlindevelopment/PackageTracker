package com.michlind.packagetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.michlind.packagetracker.data.preferences.AliImportPreferenceRepository
import com.michlind.packagetracker.data.preferences.SortPreferenceRepository
import com.michlind.packagetracker.domain.model.AliOrderImport
import com.michlind.packagetracker.domain.model.PackageStatus
import com.michlind.packagetracker.domain.model.SortMode
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.repository.PackageRepository
import com.michlind.packagetracker.domain.usecase.AddPackageUseCase
import com.michlind.packagetracker.domain.usecase.DeletePackageUseCase
import com.michlind.packagetracker.domain.usecase.GetActivePackagesUseCase
import com.michlind.packagetracker.domain.usecase.GetNotYetSentPackagesUseCase
import com.michlind.packagetracker.domain.usecase.GetReceivedPackagesUseCase
import com.michlind.packagetracker.domain.usecase.ImportAliOrderUseCase
import com.michlind.packagetracker.domain.usecase.MarkAsReceivedUseCase
import com.michlind.packagetracker.domain.usecase.RefreshTrackingNumberUseCase
import com.michlind.packagetracker.ui.aliimport.AliImportBridge
import com.michlind.packagetracker.ui.aliimport.AliImportEvent
import com.michlind.packagetracker.util.RemoteImageDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
                SortMode.CLOSEST_TO_DELIVERY ->
                    // Higher stepIndex = further along the pipeline. Tie-break
                    // by most recent activity so packages on the same step keep
                    // a sensible "fresh first" order.
                    groups.sortedWith(
                        compareByDescending<PackageGroup> { it.status.stepIndex }
                            .thenByDescending { g -> g.packages.maxOf { it.lastUpdated } }
                    )
                SortMode.LAST_SHIPPED ->
                    groups.sortedByDescending { it.packages.maxOf { p -> p.orderDate() } }
                SortMode.FIRST_SHIPPED ->
                    groups.sortedBy { it.packages.minOf { p -> p.orderDate() } }
                SortMode.A_TO_Z ->
                    groups.sortedBy { it.displayName.lowercase() }
            }
        }

private enum class BgImportOutcome { Completed, Skipped, Aborted }

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
    private val sortPrefs: SortPreferenceRepository,
    private val repository: PackageRepository,
    private val importOrder: ImportAliOrderUseCase,
    private val importPrefs: AliImportPreferenceRepository,
    private val gson: Gson
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
            // modes still produce a stable, sensible order. CLOSEST_TO_DELIVERY
            // also collapses to createdAt since every NOT_YET_SENT package shares
            // the same step.
            when (mode) {
                SortMode.CLOSEST_TO_DELIVERY,
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

    // ─── Background AliExpress import (chained after refreshAll) ─────────────
    // Set true while HomeScreen should host the hidden WebView. The WebView's
    // bridge events flow into bgEventChannel below, and one of the on*() funcs
    // settles bgImportOutcome to release the refreshAll() coroutine.
    private val _bgImportActive = MutableStateFlow(false)
    val bgImportActive: StateFlow<Boolean> = _bgImportActive.asStateFlow()

    private val bgEventChannel = Channel<AliImportEvent>(Channel.UNLIMITED)
    val bgBridge = AliImportBridge { event -> bgEventChannel.trySend(event) }

    private var bgImportOutcome: CompletableDeferred<BgImportOutcome>? = null

    init {
        viewModelScope.launch {
            bgEventChannel.consumeAsFlow().collect { event ->
                when (event) {
                    is AliImportEvent.Order -> {
                        // Same path as the manual import: parse the JSON and
                        // run it through the use case (which handles ADD /
                        // UPGRADE / SKIP and refreshes the carrier API for new
                        // tracked items inline).
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val order = gson.fromJson(event.json, AliOrderImport::class.java)
                                importOrder(order)
                            }
                        }
                    }
                    is AliImportEvent.Complete -> {
                        bgImportOutcome?.complete(BgImportOutcome.Completed)
                    }
                    is AliImportEvent.Error -> {
                        bgImportOutcome?.complete(BgImportOutcome.Aborted)
                    }
                    // Progress / Total are noise in bg mode — no UI to update.
                    else -> Unit
                }
            }
        }
    }

    /**
     * Called by HomeScreen right before injecting ali_import.js into the
     * hidden WebView. Mirrors AliImportViewModel.beginImport() — seeds the
     * bridge with the orderIds we already have a tracking number for, plus
     * the per-tab page-budget overrides from user prefs.
     */
    suspend fun prepareBgImport() {
        val ids = withContext(Dispatchers.IO) {
            runCatching { repository.getImportedAliOrderIdsWithTracking() }
                .getOrDefault(emptySet())
        }
        bgBridge.knownOrderIdsJson = gson.toJson(ids)
        bgBridge.configOverridesJson = gson.toJson(
            mapOf(
                "toShipMaxPasses" to importPrefs.toShipPages.value,
                "shippedMaxPasses" to importPrefs.shippedPages.value,
                "processedMaxPasses" to importPrefs.processedPages.value
            )
        )
    }

    fun onBgImportSkipped() { bgImportOutcome?.complete(BgImportOutcome.Skipped) }
    fun onBgImportError() { bgImportOutcome?.complete(BgImportOutcome.Aborted) }
    fun onBgImportAborted() { bgImportOutcome?.complete(BgImportOutcome.Aborted) }

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

    /**
     * Refresh the carrier-side state for in-flight packages. When
     * [runBgImport] is true (the default — Refresh button on Home), a
     * background AliExpress import is chained after the API refresh and a
     * targeted second refresh is run for any packages whose tracking number
     * went from blank to non-blank during that import. The manual AliImport
     * flow's post-Done refresh passes false here — the user just ran a full
     * import explicitly, no point doing it again silently.
     */
    fun refreshAll(runBgImport: Boolean = true) {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // Phase 1 — refresh the carrier-side state for everything that
                // could still see new events. Active packages plus any in the
                // Received column that the user marked early (status hasn't
                // reached DELIVERED yet, so Cainiao may still have updates).
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
                _refreshingTrackingNumber.value = null

                if (!runBgImport) return@launch

                // Phase 2 — snapshot ids of non-received packages with a blank
                // tracking number. After the bg import we'll re-check just
                // these ids; the ones whose tracking went blank → non-blank
                // are the only ones worth a fresh API call.
                val blankIds = withContext(Dispatchers.IO) {
                    runCatching { repository.getBlankTrackingPackageIds().toSet() }
                        .getOrDefault(emptySet())
                }

                // Phase 3 — chain a background AliExpress import. HomeScreen
                // mounts a hidden WebView in response to bgImportActive=true.
                // The WebView short-circuits with onBgImportSkipped() if the
                // user isn't logged in; otherwise it injects ali_import.js
                // and our bridge handler walks the order list. Any of the
                // on*() callbacks settles the deferred and releases us here.
                val deferred = CompletableDeferred<BgImportOutcome>()
                bgImportOutcome = deferred
                _bgImportActive.value = true
                val outcome = withTimeoutOrNull(BG_IMPORT_TIMEOUT_MS) { deferred.await() }
                    ?: BgImportOutcome.Aborted
                _bgImportActive.value = false
                bgImportOutcome = null

                // Phase 4 — only if the import actually completed: refresh the
                // snapshot ids whose tracking is now non-blank. Skips packages
                // that still have no tracking and the ones we never had a
                // chance to enrich (skipped / aborted import).
                if (outcome == BgImportOutcome.Completed && blankIds.isNotEmpty()) {
                    val tns = withContext(Dispatchers.IO) {
                        blankIds.mapNotNull { id ->
                            repository.getPackageById(id)?.trackingNumber
                                ?.takeIf { it.isNotBlank() }
                        }.distinct()
                    }
                    tns.forEach { tn ->
                        _refreshingTrackingNumber.value = tn
                        refreshTrackingNumber(tn)
                    }
                }
            } catch (_: Exception) {
                _errorMessage.value = "Failed to refresh packages"
            } finally {
                _refreshingTrackingNumber.value = null
                _isRefreshing.value = false
                _bgImportActive.value = false
            }
        }
    }

    fun clearError() { _errorMessage.value = null }

    private companion object {
        // Hard cap so a hung WebView (network black-hole, AliExpress redirect
        // loop, etc.) can't pin isRefreshing forever. Manual imports with
        // default page budgets (20/20/1) typically finish well under this.
        const val BG_IMPORT_TIMEOUT_MS = 30L * 60L * 1000L
    }
}
