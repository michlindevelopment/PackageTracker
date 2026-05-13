package com.michlind.packagetracker.ui.home

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.michlind.packagetracker.data.preferences.AliImportPreferenceRepository
import com.michlind.packagetracker.data.preferences.SortPreferenceRepository
import com.michlind.packagetracker.data.preferences.SyncOnResumePreferenceRepository
import com.michlind.packagetracker.data.repository.SmsRepository
import com.michlind.packagetracker.di.CainiaoRateLimitException
import com.michlind.packagetracker.domain.model.AliImportMode
import com.michlind.packagetracker.domain.model.AliOrderImport
import com.michlind.packagetracker.domain.model.ImportResult
import com.michlind.packagetracker.domain.model.PackageStatus
import com.michlind.packagetracker.domain.model.SortMode
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.model.UpdateCheckResult
import com.michlind.packagetracker.domain.repository.PackageRepository
import com.michlind.packagetracker.domain.usecase.AddPackageUseCase
import com.michlind.packagetracker.domain.usecase.CheckForUpdateUseCase
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        get() = if (isMultiple) "Combined package - ${packages.size} items"
                else packages.first().name.ifBlank { trackingNumber.ifBlank { "Package" } }

    // Best-known journey progress within the group: real `progressRate` if
    // any sub-package has one, otherwise derived from `status.stepIndex`
    // (max stepIndex is 6 → DELIVERED). Used for the "Progress" sort.
    val progress: Float
        get() = packages.maxOf { it.effectiveProgress() }
}

// Cainiao's progressRate when available; otherwise a coarse rate derived
// from the status step index so packages without a fresh rate still sort
// in a sensible spot relative to packages that have one.
fun TrackedPackage.effectiveProgress(): Float {
    progressRate?.let { return it.coerceIn(0f, 1f) }
    val step = status.stepIndex
    return if (step <= 0) 0f else step / 6f
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

// Cainiao reports days-in-transit as "26 day(s)" (post tab-cleanup) — strip
// the digits and parse. Missing/unparseable → 0 so the package sorts last
// among same-progress ties.
private fun TrackedPackage.daysInTransitInt(): Int =
    daysInTransit?.filter { it.isDigit() }?.toIntOrNull() ?: 0

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
                    // Sort by carrier-reported journey progress (0..1). Falls
                    // back to a step-index-derived rate for packages without
                    // a fresh progressRate (e.g. old DB rows, non-Cainiao).
                    // Tie-breakers: days-in-transit (longer first — among
                    // same-progress packages, the older shipment is closer to
                    // arrival), then orderDate (newest first). Both are stable
                    // across syncs unlike lastUpdated.
                    groups.sortedWith(
                        compareByDescending<PackageGroup> { it.progress }
                            .thenByDescending { g -> g.packages.maxOf { it.daysInTransitInt() } }
                            .thenByDescending { g -> g.packages.maxOf { it.orderDate() } }
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

/**
 * Live progress of the in-flight background AliExpress import — surfaced via
 * BgImportProgressBanner so the user gets "loading orders / loading 'To
 * ship' / N of M imported" feedback while Quick or Full from the Refresh
 * sheet runs.
 */
data class BgImportProgress(
    val statusText: String,
    val total: Int? = null,
    val current: Int = 0,
    val added: Int = 0,
    val upgraded: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0
)

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
    private val syncOnResumePrefs: SyncOnResumePreferenceRepository,
    private val checkForUpdate: CheckForUpdateUseCase,
    private val smsRepository: SmsRepository,
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

    // Set to a representative tracking number when Cainiao's bot detection
    // hits — HomeScreen reads this to attach a "Verify" action to the
    // snackbar that opens CaptchaScreen with that tracking number's URL.
    // Cleared once the snackbar is dismissed (via clearError).
    private val _captchaTrackingNumber = MutableStateFlow<String?>(null)
    val captchaTrackingNumber: StateFlow<String?> = _captchaTrackingNumber.asStateFlow()

    // Populated on cold start by checkForUpdate(). When non-null, HomeScreen
    // pops an "Update available" dialog with Update / Later buttons.
    // dismissUpdate() clears it for the rest of this app session — the next
    // cold start re-checks and re-pops if still applicable.
    private val _updateAvailable = MutableStateFlow<UpdateCheckResult.Available?>(null)
    val updateAvailable: StateFlow<UpdateCheckResult.Available?> = _updateAvailable.asStateFlow()

    // ─── Background AliExpress import (chained after refreshAll) ─────────────
    // Set true while HomeScreen should host the hidden WebView. The WebView's
    // bridge events flow into bgEventChannel below, and one of the on*() funcs
    // settles bgImportOutcome to release the refreshAll() coroutine.
    private val _bgImportActive = MutableStateFlow(false)
    val bgImportActive: StateFlow<Boolean> = _bgImportActive.asStateFlow()

    // Live progress feed for the bg AliExpress import (Quick / Full from
    // the Refresh sheet). Null when no import is in flight; populated and
    // updated in real time while one is.
    private val _bgImportProgress = MutableStateFlow<BgImportProgress?>(null)
    val bgImportProgress: StateFlow<BgImportProgress?> = _bgImportProgress.asStateFlow()

    private val bgEventChannel = Channel<AliImportEvent>(Channel.UNLIMITED)
    val bgBridge = AliImportBridge { event -> bgEventChannel.trySend(event) }

    private var bgImportOutcome: CompletableDeferred<BgImportOutcome>? = null
    // Set by startRefreshChain() before flipping bgImportActive on; the
    // bridge handler reads it to know whether existing packages should have
    // their tracking number overwritten on a change (FullSync) or left alone
    // (Quick).
    @Volatile private var bgImportMode: AliImportMode = AliImportMode.Quick

    // Serializes refresh-chain runs so combined methods like
    // fullFetchThenSyncStatus can sequence two phases without their internal
    // state (bgImportActive, _isRefreshing) racing each other. Declared
    // before init because the lifecycle observer registered there fires an
    // immediate ON_START on cold start, which transitively touches the mutex.
    private val refreshMutex = Mutex()

    // App-foreground listener: every time the process moves to STARTED
    // (cold launch + resume from background), pull a fresh carrier status
    // sync. addObserver replays events to bring the observer up to the
    // current state, so on cold start (process already STARTED when the
    // ViewModel is created) we immediately get an ON_START callback —
    // which is exactly what we want for the initial sync.
    private val processLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            // Honor the user's "sync on app resume" setting — off means the
            // user wants explicit control via the Refresh sheet only.
            if (syncOnResumePrefs.enabled.value) {
                syncStatus()
            }
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)

        // One-shot update check on cold start. Failures are silent — we'd
        // rather not nag the user with a network-error snackbar just because
        // they're offline; the Settings screen has an explicit "Check for
        // updates" button if they want to retry by hand.
        viewModelScope.launch {
            checkForUpdate().getOrNull()?.let { result ->
                if (result is UpdateCheckResult.Available) {
                    _updateAvailable.value = result
                }
            }
        }

        viewModelScope.launch {
            bgEventChannel.consumeAsFlow().collect { event ->
                when (event) {
                    is AliImportEvent.Progress -> {
                        // "Loading orders…" / "Loading 'To ship' orders…"
                        // status text from the JS, mirrored to the banner.
                        _bgImportProgress.value = _bgImportProgress.value
                            ?.copy(statusText = event.message)
                    }
                    is AliImportEvent.Total -> {
                        _bgImportProgress.value = _bgImportProgress.value?.copy(
                            total = event.total,
                            statusText = "Found ${event.total} orders"
                        )
                    }
                    is AliImportEvent.Order -> {
                        // Same path as the manual import: parse the JSON and
                        // run it through the use case (which handles ADD /
                        // UPGRADE / SKIP and refreshes the carrier API for new
                        // tracked items inline). Mode controls whether an
                        // already-imported order's TN can be overwritten.
                        val mode = bgImportMode
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val order = gson.fromJson(event.json, AliOrderImport::class.java)
                                importOrder(order, mode)
                            }.getOrElse { ImportResult.FAILED }
                        }
                        _bgImportProgress.value = _bgImportProgress.value?.let { p ->
                            p.copy(
                                current = event.index,
                                total = event.total,
                                statusText = "Importing ${event.index} / ${event.total}",
                                added = p.added + if (result == ImportResult.ADDED) 1 else 0,
                                upgraded = p.upgraded + if (result == ImportResult.UPGRADED) 1 else 0,
                                skipped = p.skipped + if (result == ImportResult.SKIPPED) 1 else 0,
                                failed = p.failed + if (result == ImportResult.FAILED) 1 else 0
                            )
                        }
                    }
                    is AliImportEvent.Complete -> {
                        bgImportOutcome?.complete(BgImportOutcome.Completed)
                    }
                    is AliImportEvent.Error -> {
                        bgImportOutcome?.complete(BgImportOutcome.Aborted)
                    }
                }
            }
        }
    }

    /**
     * Called by HomeScreen right before injecting ali_import.js into the
     * hidden WebView. Seeds the bridge with the orderIds we already have a
     * tracking number for, plus the per-tab page-budget overrides from user
     * prefs.
     *
     * In FullSync mode the seed is empty, so the JS does the per-order
     * iframe lookup for every order — including ones we've imported before —
     * and the use case sees the latest tracking number AliExpress is willing
     * to report for each. That's how we detect TN changes for already-known
     * orders (slower; intended for the explicit full-sync path).
     */
    suspend fun prepareBgImport() {
        val mode = bgImportMode
        val ids = if (mode == AliImportMode.FullSync) {
            emptySet()
        } else {
            withContext(Dispatchers.IO) {
                runCatching { repository.getImportedAliOrderIdsWithTracking() }
                    .getOrDefault(emptySet())
            }
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
     * (1/3) Carrier status sync only — pulls Cainiao for tracked packages
     * that could still see new events. No AliExpress import at all.
     */
    fun syncStatus() = viewModelScope.launch {
        runChain(mode = AliImportMode.Quick, runStatusSync = true, runBgImport = false)
    }

    /**
     * (2/3) Quick fetch — runs a background AliExpress import that *skips
     * orders we've already enriched* (fast). New orders get added, blank
     * tracking numbers get filled in. Does NOT do a broad carrier status
     * refresh; only the targeted post-import refresh for newly-acquired
     * tracking numbers runs.
     */
    fun quickFetch() = viewModelScope.launch {
        runChain(mode = AliImportMode.Quick, runStatusSync = false, runBgImport = true)
    }

    /**
     * (3/3) Full fetch — runs a background AliExpress import that re-reads
     * every order. Same as quickFetch plus: detects tracking-number changes
     * for orders already imported (only for `ali:`-owned orders, so manual
     * edits stay untouched). Like quickFetch, no broad carrier status
     * refresh — only targeted refresh for tracking numbers that changed.
     */
    fun fullFetch() = viewModelScope.launch {
        runChain(mode = AliImportMode.FullSync, runStatusSync = false, runBgImport = true)
    }

    /** quickFetch followed by syncStatus — for the Refresh sheet. */
    fun quickFetchThenSyncStatus() = viewModelScope.launch {
        // Skip Phase 4 in the import pass — the trailing syncStatus refreshes
        // every eligible TN anyway, so the targeted post-import refresh
        // would just hit Cainiao a second time for the same TNs (which is
        // exactly what the rate limiter is trying to avoid).
        runChain(
            AliImportMode.Quick,
            runStatusSync = false,
            runBgImport = true,
            runTargetedRefresh = false
        )
        runChain(AliImportMode.Quick, runStatusSync = true, runBgImport = false)
    }

    /** fullFetch followed by syncStatus — for the Refresh sheet and the
     *  post-login flow. */
    fun fullFetchThenSyncStatus() = viewModelScope.launch {
        runChain(
            AliImportMode.FullSync,
            runStatusSync = false,
            runBgImport = true,
            runTargetedRefresh = false
        )
        runChain(AliImportMode.Quick, runStatusSync = true, runBgImport = false)
    }

    private suspend fun runChain(
        mode: AliImportMode,
        runStatusSync: Boolean,
        runBgImport: Boolean,
        // When true, after the import completes we refresh the carrier
        // status for any TNs that changed during it. Standalone fetches
        // (quickFetch / fullFetch) want this so newly-enriched orders show
        // status immediately. The chained-with-syncStatus variants pass
        // false to avoid a duplicate Cainiao hit — syncStatus's broad
        // refresh covers the changed TNs along with everything else.
        runTargetedRefresh: Boolean = true
    ) {
        refreshMutex.withLock {
            _isRefreshing.value = true
            try {
                // Phase 1 — refresh the carrier-side state for everything
                // that could still see new events. Active packages plus any
                // in the Received column that the user marked early (status
                // hasn't reached DELIVERED yet, so Cainiao may still have
                // updates). Only syncStatus() runs this; the fetch flows
                // skip it because their interesting work is the AliExpress
                // pull plus a targeted post-import refresh.
                if (runStatusSync) {
                    // Query the DB directly rather than reading from
                    // activeGroups.value / receivedGroups.value — on cold
                    // start the StateFlows are still emitting their initial
                    // empty list at the moment ProcessLifecycle ON_START
                    // fires, which would silently make syncStatus a no-op.
                    // getPackagesEligibleForRefresh covers both active and
                    // marked-received-but-not-yet-DELIVERED rows.
                    //
                    // Refresh in the same order packages appear on screen:
                    // group by tracking number and sort by the user's current
                    // sortMode so the topmost card is refreshed first. Matters
                    // if Cainiao rate-limits us mid-loop — we want the user's
                    // most-visible packages to have fresh data first.
                    val currentSort = sortMode.value
                    val tnsForRefresh = withContext(Dispatchers.IO) {
                        runCatching {
                            repository.getPackagesEligibleForRefresh()
                                .toGroups(currentSort)
                                .map { it.trackingNumber }
                                .filter { it.isNotBlank() }
                                .distinct()
                        }.getOrDefault(emptyList())
                    }
                    refreshTnsRateLimited(tnsForRefresh)
                    _refreshingTrackingNumber.value = null

                    // SMS scan: walk the inbox for matches and cache them.
                    // Includes the user-supplied local-courier TNs (typed in
                    // the Courier tab) on top of the Cainiao TNs, so the
                    // SMS tab catches handover-courier notifications too.
                    // No-ops silently if the user hasn't granted READ_SMS.
                    runCatching {
                        val localTns = withContext(Dispatchers.IO) {
                            runCatching { repository.getActiveLocalTrackingNumbers() }
                                .getOrDefault(emptyList())
                        }
                        smsRepository.scanForTrackingNumbers(tnsForRefresh + localTns)
                    }
                }

                if (!runBgImport) return@withLock

                // Phase 2 — snapshot for the post-import diff. Quick mode
                // only cares about packages without a TN yet (blank → set is
                // the only change Quick can produce). Full Sync also has to
                // catch non-blank → different-non-blank, so it captures the
                // current TN of every non-received package.
                val snapshot: Map<Long, String> = withContext(Dispatchers.IO) {
                    runCatching {
                        when (mode) {
                            AliImportMode.Quick ->
                                repository.getBlankTrackingPackageIds()
                                    .associateWith { "" }
                            AliImportMode.FullSync ->
                                repository.getNonReceivedTrackingSnapshot()
                        }
                    }.getOrDefault(emptyMap())
                }

                // Phase 3 — chain a background AliExpress import. HomeScreen
                // mounts a hidden WebView in response to bgImportActive=true.
                // The WebView short-circuits with onBgImportSkipped() if the
                // user isn't logged in; otherwise it injects ali_import.js
                // and our bridge handler walks the order list. Any of the
                // on*() callbacks settles the deferred and releases us here.
                bgImportMode = mode
                _bgImportProgress.value = BgImportProgress(statusText = "Starting…")
                val deferred = CompletableDeferred<BgImportOutcome>()
                bgImportOutcome = deferred
                _bgImportActive.value = true
                val outcome = withTimeoutOrNull(BG_IMPORT_TIMEOUT_MS) { deferred.await() }
                    ?: BgImportOutcome.Aborted
                _bgImportActive.value = false
                bgImportOutcome = null
                _bgImportProgress.value = null

                // Phase 4 — only if the import actually completed AND the
                // caller asked for the targeted refresh: refresh each
                // snapshot id whose tracking number changed during the
                // import. Quick mode captured "" for everyone, so any
                // non-blank value in the DB now is a "blank → set" change.
                // Full Sync compares against the real prior TN, so it
                // picks up both blank → set AND set → different-set. The
                // `*ThenSyncStatus` chains pass runTargetedRefresh=false
                // because the trailing syncStatus would otherwise refresh
                // these TNs a second time.
                if (runTargetedRefresh &&
                    outcome == BgImportOutcome.Completed &&
                    snapshot.isNotEmpty()
                ) {
                    val tns = withContext(Dispatchers.IO) {
                        snapshot.entries.mapNotNull { (id, prevTn) ->
                            val current = repository.getPackageById(id)?.trackingNumber
                                .orEmpty()
                            current.takeIf { it.isNotBlank() && it != prevTn }
                        }.distinct()
                    }
                    refreshTnsRateLimited(tns)
                }
            } catch (_: Exception) {
                _errorMessage.value = "Failed to refresh packages"
            } finally {
                _refreshingTrackingNumber.value = null
                _isRefreshing.value = false
                _bgImportActive.value = false
                _bgImportProgress.value = null
            }
        }
    }

    /**
     * Iterates [tns], calling refreshTrackingNumber for each with a small
     * gap between calls so we don't trip Cainiao's bot detection (which
     * starts replying with a CAPTCHA HTML page tagged `bxpunish: 1` once
     * we hit the endpoint too quickly). If a CainiaoRateLimitException
     * does come back mid-loop, bail immediately — every subsequent request
     * would just hit the same wall — and surface a snackbar so the user
     * understands why sync stopped.
     */
    private suspend fun refreshTnsRateLimited(tns: List<String>) {
        var first = true
        for (tn in tns) {
            if (!first) delay(REFRESH_INTER_REQUEST_MS)
            first = false
            _refreshingTrackingNumber.value = tn
            val result = refreshTrackingNumber(tn)
            val ex = result.exceptionOrNull()
            if (ex is CainiaoRateLimitException) {
                _errorMessage.value = "Cainiao is asking us to verify."
                // Hand the failing TN to the UI so the captcha WebView can
                // load that exact tracking page — the slide puzzle Cainiao
                // serves is the same regardless, but the URL keeps the
                // user oriented and gives a real page to land on after
                // they solve it.
                _captchaTrackingNumber.value = tn
                return
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
        _captchaTrackingNumber.value = null
    }

    fun dismissUpdate() {
        _updateAvailable.value = null
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processLifecycleObserver)
        super.onCleared()
    }

    private companion object {
        // Hard cap so a hung WebView (network black-hole, AliExpress redirect
        // loop, etc.) can't pin isRefreshing forever. Manual imports with
        // default page budgets (20/20/1) typically finish well under this.
        const val BG_IMPORT_TIMEOUT_MS = 30L * 60L * 1000L

        // Gap between successive Cainiao tracking lookups. Cainiao's
        // anti-bot ("baxia") flips us to CAPTCHA mode if we fire requests
        // too quickly back-to-back; ~750 ms keeps us well under that
        // threshold while still finishing 20 packages in ~15 s.
        const val REFRESH_INTER_REQUEST_MS = 750L
    }
}
