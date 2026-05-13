package com.michlind.packagetracker.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michlind.packagetracker.data.repository.SmsRepository
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.model.TrackingSms
import com.michlind.packagetracker.domain.usecase.DeletePackageUseCase
import com.michlind.packagetracker.domain.usecase.MarkAsReceivedUseCase
import com.michlind.packagetracker.domain.usecase.RefreshPackageUseCase
import com.michlind.packagetracker.domain.repository.PackageRepository
import com.michlind.packagetracker.util.RemoteImageDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Success(val pkg: TrackedPackage) : DetailUiState
    data class Error(val message: String) : DetailUiState
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PackageRepository,
    private val deletePackage: DeletePackageUseCase,
    private val markAsReceived: MarkAsReceivedUseCase,
    private val refreshPackage: RefreshPackageUseCase,
    private val imageDownloader: RemoteImageDownloader,
    private val smsRepository: SmsRepository
) : ViewModel() {

    // The home list keeps the repository's in-memory cache warm — when the
    // user taps a card we hydrate Detail synchronously from there so the
    // first frame already has real content during the slide. Falls back to
    // Loading only if the cache miss is real (e.g. deep link from a
    // notification before the home list ever loaded).
    private val initialCached: TrackedPackage? =
        savedStateHandle.get<Long>("packageId")?.let { repository.peekById(it) }

    private val _uiState = MutableStateFlow<DetailUiState>(
        initialCached?.let { DetailUiState.Success(it) } ?: DetailUiState.Loading
    )
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    // Re-emitted whenever the loaded tracking number(s) change; the SMS list
    // below switches its underlying DAO query in lockstep. Two slots: the
    // Cainiao TN (always present) and the user-supplied local-courier TN
    // (nullable, set via the Courier tab).
    private val _trackingNumbers = MutableStateFlow(
        listOfNotNull(
            initialCached?.trackingNumber?.takeIf { it.isNotBlank() },
            initialCached?.localTrackingNumber?.takeIf { it.isNotBlank() }
        )
    )

    // Lazily re-checked snapshot of READ_SMS state. The screen calls
    // refreshSmsPermission() after the runtime permission dialog closes so
    // the SMS tab can flip from "Allow access" to the list view.
    private val _hasSmsPermission = MutableStateFlow(smsRepository.hasPermission())
    val hasSmsPermission: StateFlow<Boolean> = _hasSmsPermission.asStateFlow()

    val smsList: StateFlow<List<TrackingSms>> = _trackingNumbers
        .flatMapLatest { tns ->
            if (tns.isEmpty()) flowOf(emptyList())
            else smsRepository.observeForTrackingNumbers(tns)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun load(packageId: Long) {
        viewModelScope.launch {
            // Don't force back to Loading — if we already rendered a cached
            // Success on the first frame, keep showing it while the Room
            // read confirms (avoids a Success → Loading → Success churn).
            val pkg = repository.getPackageById(packageId)
            when {
                pkg != null -> {
                    _uiState.value = DetailUiState.Success(pkg)
                    _trackingNumbers.value = listOfNotNull(
                        pkg.trackingNumber.takeIf { it.isNotBlank() },
                        pkg.localTrackingNumber?.takeIf { it.isNotBlank() }
                    )
                }
                _uiState.value !is DetailUiState.Success -> {
                    _uiState.value = DetailUiState.Error("Package not found")
                }
                // else: cached Success stays — Room returned null but we have
                // a memory copy. Don't downgrade on a delete-in-flight race.
            }
        }
    }

    /**
     * Save (or clear, with null/blank) the user-supplied local-courier
     * tracking number. Re-seeds the SMS observer with the updated set, then
     * runs a one-shot scan so the SMS tab populates immediately for the new
     * TN instead of waiting for the next syncStatus().
     */
    fun setLocalTrackingNumber(packageId: Long, trackingNumber: String?) {
        val normalized = trackingNumber?.trim()?.takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            repository.setLocalTrackingNumber(packageId, normalized)
            val pkg = repository.getPackageById(packageId)
            if (pkg != null) {
                _uiState.value = DetailUiState.Success(pkg)
                _trackingNumbers.value = listOfNotNull(
                    pkg.trackingNumber.takeIf { it.isNotBlank() },
                    pkg.localTrackingNumber?.takeIf { it.isNotBlank() }
                )
            }
            if (normalized != null && _hasSmsPermission.value) {
                smsRepository.scanForTrackingNumbers(listOf(normalized))
            }
        }
    }

    fun refreshSmsPermission() {
        _hasSmsPermission.value = smsRepository.hasPermission()
    }

    /** Targeted scan for the currently-loaded TN(s) — Cainiao TN plus the
     *  local-courier TN if set. Used after the user grants READ_SMS so the
     *  SMS tab populates without waiting for the next sync. */
    fun scanSmsForCurrent() {
        val tns = _trackingNumbers.value
        if (tns.isEmpty()) return
        viewModelScope.launch {
            smsRepository.scanForTrackingNumbers(tns)
        }
    }

    fun refresh(packageId: Long) {
        viewModelScope.launch {
            _isRefreshing.value = true
            refreshPackage(packageId)
            val pkg = repository.getPackageById(packageId)
            if (pkg != null) {
                _uiState.value = DetailUiState.Success(pkg)
            }
            _isRefreshing.value = false
        }
    }

    fun toggleReceived(packageId: Long, isReceived: Boolean) {
        viewModelScope.launch {
            markAsReceived(packageId, isReceived)
            load(packageId)
        }
    }

    fun delete(packageId: Long) {
        viewModelScope.launch {
            // Detail-screen delete has no undo — clean up the image immediately.
            val photoUri = (_uiState.value as? DetailUiState.Success)?.pkg?.photoUri
            deletePackage(packageId)
            photoUri?.let { imageDownloader.delete(it) }
            _deleted.value = true
        }
    }
}
