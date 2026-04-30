package com.michlind.packagetracker.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.usecase.DeletePackageUseCase
import com.michlind.packagetracker.domain.usecase.MarkAsReceivedUseCase
import com.michlind.packagetracker.domain.usecase.RefreshPackageUseCase
import com.michlind.packagetracker.domain.repository.PackageRepository
import com.michlind.packagetracker.util.RemoteImageDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Success(val pkg: TrackedPackage) : DetailUiState
    data class Error(val message: String) : DetailUiState
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: PackageRepository,
    private val deletePackage: DeletePackageUseCase,
    private val markAsReceived: MarkAsReceivedUseCase,
    private val refreshPackage: RefreshPackageUseCase,
    private val imageDownloader: RemoteImageDownloader
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun load(packageId: Long) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            val pkg = repository.getPackageById(packageId)
            _uiState.value = if (pkg != null) {
                DetailUiState.Success(pkg)
            } else {
                DetailUiState.Error("Package not found")
            }
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
