package com.michlind.packagetracker.ui.aliimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.michlind.packagetracker.data.preferences.AliImportPreferenceRepository
import com.michlind.packagetracker.domain.model.AliOrderImport
import com.michlind.packagetracker.domain.model.ImportResult
import com.michlind.packagetracker.domain.repository.PackageRepository
import com.michlind.packagetracker.domain.usecase.ImportAliOrderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface AliImportState {
    data object Idle : AliImportState
    // After login is detected (sign=y cookie) but before the orders page has
    // finished loading. Used to show the overlay during the post-login
    // bounce — there's no user input needed here, just waiting.
    data object Authenticating : AliImportState
    data object ReadyToImport : AliImportState
    data class Importing(
        val statusText: String,
        val current: Int = 0,
        val total: Int? = null,
        val added: Int = 0,
        val upgraded: Int = 0,
        val skipped: Int = 0,
        val failed: Int = 0
    ) : AliImportState

    data class Completed(
        val added: Int,
        val upgraded: Int,
        val skipped: Int,
        val failed: Int
    ) : AliImportState

    data class Error(val message: String) : AliImportState
}

@HiltViewModel
class AliImportViewModel @Inject constructor(
    private val importOrder: ImportAliOrderUseCase,
    private val repository: PackageRepository,
    private val importPrefs: AliImportPreferenceRepository,
    private val gson: Gson
) : ViewModel() {

    private val _state = MutableStateFlow<AliImportState>(AliImportState.Idle)
    val state: StateFlow<AliImportState> = _state.asStateFlow()

    private val eventChannel = Channel<AliImportEvent>(capacity = Channel.UNLIMITED)

    val bridge = AliImportBridge { event -> eventChannel.trySend(event) }

    init {
        viewModelScope.launch {
            eventChannel.consumeAsFlow().collect { handle(it) }
        }
    }

    fun onOrdersPageLoaded() {
        val s = _state.value
        if (s is AliImportState.Idle || s is AliImportState.Authenticating) {
            _state.value = AliImportState.ReadyToImport
        }
    }

    fun onAuthenticated() {
        if (_state.value is AliImportState.Idle) {
            _state.value = AliImportState.Authenticating
        }
    }

    // Session cookie can expire — if AliExpress redirects back to login while
    // we already moved to Authenticating, drop back to Idle so the overlay
    // gets out of the way and the user can re-enter credentials.
    fun onLoginPageShown() {
        if (_state.value is AliImportState.Authenticating) {
            _state.value = AliImportState.Idle
        }
    }

    suspend fun beginImport() {
        // Flip to Importing immediately (before the suspending DB read) so the
        // Start button visibly disables on the first tap — otherwise the button
        // stays ReadyToImport for the duration of the bridge seed and feels
        // unresponsive.
        _state.value = AliImportState.Importing(statusText = "Starting…")

        // Seed the JS bridge with orderIds we already have a tracking number
        // for, so the script can skip the per-order iframe lookup for them.
        val ids = withContext(Dispatchers.IO) {
            runCatching { repository.getImportedAliOrderIdsWithTracking() }
                .getOrDefault(emptySet())
        }
        bridge.knownOrderIdsJson = gson.toJson(ids)

        // User-tunable per-tab "View more" page budgets.
        bridge.configOverridesJson = gson.toJson(
            mapOf(
                "toShipMaxPasses" to importPrefs.toShipPages.value,
                "shippedMaxPasses" to importPrefs.shippedPages.value,
                "processedMaxPasses" to importPrefs.processedPages.value
            )
        )
    }

    fun reset() {
        _state.value = AliImportState.ReadyToImport
    }

    /**
     * Called when the WebView's main frame fails to load (network error, redirect
     * to a page that white-screens, etc.). Only surfaced if we're not already
     * mid-import — otherwise the import event stream drives the state.
     */
    fun onLoadError(message: String) {
        if (_state.value !is AliImportState.Importing) {
            _state.value = AliImportState.Error(message)
        }
    }

    private suspend fun handle(event: AliImportEvent) {
        val current = _state.value
        when (event) {
            is AliImportEvent.Progress -> {
                if (current is AliImportState.Importing) {
                    _state.value = current.copy(statusText = event.message)
                }
            }

            is AliImportEvent.Total -> {
                if (current is AliImportState.Importing) {
                    _state.value = current.copy(
                        total = event.total,
                        statusText = "Found ${event.total} orders"
                    )
                }
            }

            is AliImportEvent.Order -> {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val order = gson.fromJson(event.json, AliOrderImport::class.java)
                        importOrder(order)
                    }.getOrElse { ImportResult.FAILED }
                }
                val snapshot = _state.value
                if (snapshot is AliImportState.Importing) {
                    _state.value = snapshot.copy(
                        current = event.index,
                        total = event.total,
                        statusText = "Importing ${event.index} / ${event.total}",
                        added = snapshot.added + if (result == ImportResult.ADDED) 1 else 0,
                        upgraded = snapshot.upgraded + if (result == ImportResult.UPGRADED) 1 else 0,
                        skipped = snapshot.skipped + if (result == ImportResult.SKIPPED) 1 else 0,
                        failed = snapshot.failed + if (result == ImportResult.FAILED) 1 else 0
                    )
                }
            }

            is AliImportEvent.Complete -> {
                val snapshot = _state.value
                if (snapshot is AliImportState.Importing) {
                    _state.value = AliImportState.Completed(
                        added = snapshot.added,
                        upgraded = snapshot.upgraded,
                        skipped = snapshot.skipped,
                        failed = snapshot.failed
                    )
                }
            }

            is AliImportEvent.Error -> {
                _state.value = AliImportState.Error(event.message)
            }
        }
    }
}
