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

    // Re-emitted whenever the loaded tracking number changes; the SMS list
    // below switches its underlying DAO query in lockstep.
    private val _trackingNumber = MutableStateFlow<String?>(initialCached?.trackingNumber)

    // Lazily re-checked snapshot of READ_SMS state. The screen calls
    // refreshSmsPermission() after the runtime permission dialog closes so
    // the SMS tab can flip from "Allow access" to the list view.
    private val _hasSmsPermission = MutableStateFlow(smsRepository.hasPermission())
    val hasSmsPermission: StateFlow<Boolean> = _hasSmsPermission.asStateFlow()

    val smsList: StateFlow<List<TrackingSms>> = _trackingNumber
        .flatMapLatest { tn ->
            if (tn.isNullOrBlank()) flowOf(emptyList())
            else smsRepository.observeForTrackingNumber(tn)
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
                    _trackingNumber.value = pkg.trackingNumber
                }
                _uiState.value !is DetailUiState.Success -> {
                    _uiState.value = DetailUiState.Error("Package not found")
                }
                // else: cached Success stays — Room returned null but we have
                // a memory copy. Don't downgrade on a delete-in-flight race.
            }
        }
    }

    fun refreshSmsPermission() {
        _hasSmsPermission.value = smsRepository.hasPermission()
    }

    /** Targeted scan for the currently-loaded TN. Used after the user grants
     *  READ_SMS so the SMS tab populates without waiting for the next sync. */
    fun scanSmsForCurrent() {
        val tn = _trackingNumber.value ?: return
        if (tn.isBlank()) return
        viewModelScope.launch {
            smsRepository.scanForTrackingNumbers(listOf(tn))
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
