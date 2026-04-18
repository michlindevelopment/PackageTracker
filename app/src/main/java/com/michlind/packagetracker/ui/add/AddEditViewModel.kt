package com.michlind.packagetracker.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michlind.packagetracker.domain.model.PackageStatus
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.model.TrackingResult
import com.michlind.packagetracker.domain.repository.PackageRepository
import com.michlind.packagetracker.domain.usecase.AddPackageUseCase
import com.michlind.packagetracker.domain.usecase.TrackPackageUseCase
import com.michlind.packagetracker.domain.usecase.UpdatePackageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddEditViewModel @Inject constructor(
    private val repository: PackageRepository,
    private val addPackage: AddPackageUseCase,
    private val updatePackage: UpdatePackageUseCase,
    private val trackPackageUseCase: TrackPackageUseCase
) : ViewModel() {

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _trackingNumber = MutableStateFlow("")
    val trackingNumber: StateFlow<String> = _trackingNumber.asStateFlow()

    private val _photoUri = MutableStateFlow<String?>(null)
    val photoUri: StateFlow<String?> = _photoUri.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _trackingResult = MutableStateFlow<TrackingResult?>(null)
    val trackingResult: StateFlow<TrackingResult?> = _trackingResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _savedPackageId = MutableStateFlow<Long?>(null)
    val savedPackageId: StateFlow<Long?> = _savedPackageId.asStateFlow()

    private var editingPackageId: Long? = null

    val isEditMode: Boolean get() = editingPackageId != null

    fun loadForEdit(packageId: Long) {
        viewModelScope.launch {
            val pkg = repository.getPackageById(packageId) ?: return@launch
            editingPackageId = packageId
            _name.value = pkg.name
            _trackingNumber.value = pkg.trackingNumber
            _photoUri.value = pkg.photoUri
        }
    }

    fun updateName(value: String) { _name.value = value }
    fun updateTrackingNumber(value: String) { _trackingNumber.value = value }
    fun updatePhotoUri(uri: String?) { _photoUri.value = uri }

    fun track() {
        val tn = _trackingNumber.value.trim()
        if (tn.isBlank()) {
            _errorMessage.value = "Please enter a tracking number"
            return
        }
        viewModelScope.launch {
            _isTracking.value = true
            _trackingResult.value = null
            _errorMessage.value = null
            val result = trackPackageUseCase(tn)
            result.onSuccess { tracking ->
                _trackingResult.value = tracking
            }.onFailure { e ->
                _errorMessage.value = e.message ?: "Failed to track package"
            }
            _isTracking.value = false
        }
    }

    fun save() {
        val tn = _trackingNumber.value.trim()
        if (tn.isBlank()) {
            _errorMessage.value = "Tracking number is required"
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            val result = _trackingResult.value
            val now = System.currentTimeMillis()
            val pkg = TrackedPackage(
                id = editingPackageId ?: 0L,
                trackingNumber = tn,
                name = _name.value.trim().ifBlank { tn },
                photoUri = _photoUri.value,
                status = result?.status ?: PackageStatus.UNKNOWN,
                statusDescription = result?.statusDescription.orEmpty(),
                lastEvent = result?.events?.firstOrNull(),
                events = result?.events ?: emptyList(),
                lastUpdated = now,
                isReceived = false,
                createdAt = now,
                estimatedDeliveryTime = result?.estimatedDeliveryTime,
                daysInTransit = result?.daysInTransit,
                originCountry = result?.originCountry,
                destCountry = result?.destCountry
            )
            if (editingPackageId != null) {
                // Preserve createdAt and isReceived from existing
                val existing = repository.getPackageById(editingPackageId!!)
                val updated = pkg.copy(
                    createdAt = existing?.createdAt ?: now,
                    isReceived = existing?.isReceived ?: false
                )
                updatePackage(updated)
                _savedPackageId.value = editingPackageId
            } else {
                val id = addPackage(pkg)
                _savedPackageId.value = id
            }
            _isSaving.value = false
        }
    }

    fun clearError() { _errorMessage.value = null }
}
