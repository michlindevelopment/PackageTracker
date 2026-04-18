package com.michlind.packagetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.usecase.AddPackageUseCase
import com.michlind.packagetracker.domain.usecase.DeletePackageUseCase
import com.michlind.packagetracker.domain.usecase.GetActivePackagesUseCase
import com.michlind.packagetracker.domain.usecase.GetReceivedPackagesUseCase
import com.michlind.packagetracker.domain.usecase.MarkAsReceivedUseCase
import com.michlind.packagetracker.domain.usecase.RefreshPackageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    getActivePackages: GetActivePackagesUseCase,
    getReceivedPackages: GetReceivedPackagesUseCase,
    private val deletePackage: DeletePackageUseCase,
    private val addPackage: AddPackageUseCase,
    private val markAsReceived: MarkAsReceivedUseCase,
    private val refreshPackage: RefreshPackageUseCase
) : ViewModel() {

    val activePackages: StateFlow<List<TrackedPackage>> = getActivePackages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val receivedPackages: StateFlow<List<TrackedPackage>> = getReceivedPackages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var lastDeletedPackage: TrackedPackage? = null

    fun delete(pkg: TrackedPackage) {
        lastDeletedPackage = pkg
        viewModelScope.launch {
            deletePackage(pkg.id)
        }
    }

    fun undoDelete() {
        val pkg = lastDeletedPackage ?: return
        viewModelScope.launch {
            addPackage(pkg)
            lastDeletedPackage = null
        }
    }

    fun toggleReceived(id: Long, isReceived: Boolean) {
        viewModelScope.launch {
            markAsReceived(id, isReceived)
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                activePackages.value.forEach { pkg ->
                    refreshPackage(pkg.id)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to refresh packages"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun clearError() { _errorMessage.value = null }
}
