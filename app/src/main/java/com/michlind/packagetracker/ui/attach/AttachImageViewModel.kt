package com.michlind.packagetracker.ui.attach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.repository.PackageRepository
import com.michlind.packagetracker.domain.usecase.GetActivePackagesUseCase
import com.michlind.packagetracker.domain.usecase.GetNotYetSentPackagesUseCase
import com.michlind.packagetracker.domain.usecase.GetReceivedPackagesUseCase
import com.michlind.packagetracker.domain.usecase.UpdatePackageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AttachImageViewModel @Inject constructor(
    getActivePackages: GetActivePackagesUseCase,
    getReceivedPackages: GetReceivedPackagesUseCase,
    getNotYetSentPackages: GetNotYetSentPackagesUseCase,
    private val updatePackage: UpdatePackageUseCase,
    private val repository: PackageRepository
) : ViewModel() {

    val allPackages: StateFlow<List<TrackedPackage>> = combine(
        getNotYetSentPackages(),
        getActivePackages(),
        getReceivedPackages()
    ) { notYet, active, received -> notYet + active + received }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun attachImage(packageId: Long, imageUri: String) {
        viewModelScope.launch {
            val existing = repository.getPackageById(packageId) ?: return@launch
            updatePackage(existing.copy(photoUri = imageUri, lastUpdated = System.currentTimeMillis()))
        }
    }
}
