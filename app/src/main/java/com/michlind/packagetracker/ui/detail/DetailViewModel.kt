package com.michlind.packagetracker.ui.detail

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
    private val repository: PackageRepository,
    private val deletePackage: DeletePackageUseCase,
    private val markAsReceived: MarkAsReceivedUseCase,
    private val refreshPackage: RefreshPackageUseCase,
    private val imageDownloader: RemoteImageDownloader,
    private val smsRepository: SmsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    // Re-emitted whenever the loaded tracking number changes; the SMS list
    // below switches its underlying DAO query in lockstep.
    private val _trackingNumber = MutableStateFlow<String?>(null)

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
            _uiState.value = DetailUiState.Loading
            val pkg = repository.getPackageById(packageId)
            _uiState.value = if (pkg != null) {
                DetailUiState.Success(pkg)
            } else {
                DetailUiState.Error("Package not found")
            }
            _trackingNumber.value = pkg?.trackingNumber
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
