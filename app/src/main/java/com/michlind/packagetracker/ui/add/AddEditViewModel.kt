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
    private var existingPackage: TrackedPackage? = null

    val isEditMode: Boolean get() = editingPackageId != null

    fun loadForEdit(packageId: Long) {
        viewModelScope.launch {
            val pkg = repository.getPackageById(packageId) ?: return@launch
            editingPackageId = packageId
            existingPackage = pkg
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
            trackPackageUseCase(tn)
                .onSuccess { _trackingResult.value = it }
                .onFailure { _errorMessage.value = it.message ?: "Failed to track package" }
            _isTracking.value = false
        }
    }

    fun save() {
        val tn = _trackingNumber.value.trim()
        val nameVal = _name.value.trim()
        if (tn.isBlank() && nameVal.isBlank()) {
            _errorMessage.value = "Please enter a package name or tracking number"
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            val result = _trackingResult.value
            val now = System.currentTimeMillis()
            val existing = existingPackage
            existing?.status == PackageStatus.NOT_YET_SENT
            val hasTrackingNumber = tn.isNotBlank()

            // Determine status: prefer fresh tracking result, then preserve existing, fall back
            val resolvedStatus = result?.status
                ?: existing?.status?.takeUnless { it == PackageStatus.NOT_YET_SENT && hasTrackingNumber }
                ?: if (hasTrackingNumber) PackageStatus.UNKNOWN else PackageStatus.NOT_YET_SENT

            val pkg = TrackedPackage(
                id = editingPackageId ?: 0L,
                trackingNumber = tn,
                name = nameVal.ifBlank { tn.ifBlank { "Package" } },
                photoUri = _photoUri.value,
                status = resolvedStatus,
                statusDescription = result?.statusDescription ?: existing?.statusDescription.orEmpty(),
                lastEvent = result?.events?.firstOrNull() ?: existing?.lastEvent,
                events = result?.events ?: existing?.events ?: emptyList(),
                lastUpdated = now,
                isReceived = existing?.isReceived ?: false,
                createdAt = existing?.createdAt ?: now,
                estimatedDeliveryTime = result?.estimatedDeliveryTime ?: existing?.estimatedDeliveryTime,
                daysInTransit = result?.daysInTransit ?: existing?.daysInTransit,
                originCountry = result?.originCountry ?: existing?.originCountry,
                destCountry = result?.destCountry ?: existing?.destCountry,
                progressRate = result?.progressRate ?: existing?.progressRate,
                // Preserve user-supplied local-courier TN across edits —
                // rebuilding the model from scratch would otherwise null it.
                localTrackingNumber = existing?.localTrackingNumber
            )

            val savedId = if (editingPackageId != null) {
                updatePackage(pkg)
                editingPackageId!!
            } else {
                addPackage(pkg)
            }

            // Auto-fetch tracking data after save whenever a tracking number is present
            // and no fresh result was already attached via the manual "Track" button.
            if (hasTrackingNumber && result == null) {
                repository.refreshPackage(savedId)
            }

            _savedPackageId.value = savedId
            _isSaving.value = false
        }
    }

    fun clearError() { _errorMessage.value = null }
}
