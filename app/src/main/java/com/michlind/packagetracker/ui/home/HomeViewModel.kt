package com.michlind.packagetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michlind.packagetracker.domain.model.PackageStatus
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.usecase.AddPackageUseCase
import com.michlind.packagetracker.domain.usecase.DeletePackageUseCase
import com.michlind.packagetracker.domain.usecase.GetActivePackagesUseCase
import com.michlind.packagetracker.domain.usecase.GetNotYetSentPackagesUseCase
import com.michlind.packagetracker.domain.usecase.GetReceivedPackagesUseCase
import com.michlind.packagetracker.domain.usecase.MarkAsReceivedUseCase
import com.michlind.packagetracker.domain.usecase.RefreshPackageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PackageGroup(
    val trackingNumber: String,
    val packages: List<TrackedPackage>,
    val status: PackageStatus,
    val lastUpdated: Long
) {
    val isMultiple: Boolean get() = packages.size > 1
    val displayName: String
        get() = if (isMultiple) "Multi-package (${packages.size} items)"
                else packages.first().name.ifBlank { trackingNumber.ifBlank { "Package" } }
}

// "Order date" = earliest tracking event time for the package (when the order
// was first recorded by the carrier). Falls back to createdAt when no events.
private fun TrackedPackage.orderDate(): Long =
    events.minOfOrNull { it.time } ?: createdAt

private fun List<TrackedPackage>.toGroups(): List<PackageGroup> =
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
        .sortedByDescending { group -> group.packages.maxOf { it.orderDate() } }

@HiltViewModel
class HomeViewModel @Inject constructor(
    getActivePackages: GetActivePackagesUseCase,
    getReceivedPackages: GetReceivedPackagesUseCase,
    getNotYetSentPackages: GetNotYetSentPackagesUseCase,
    private val deletePackage: DeletePackageUseCase,
    private val addPackage: AddPackageUseCase,
    private val markAsReceived: MarkAsReceivedUseCase,
    private val refreshPackage: RefreshPackageUseCase
) : ViewModel() {

    val activeGroups: StateFlow<List<PackageGroup>> = getActivePackages()
        .map { it.toGroups() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val receivedGroups: StateFlow<List<PackageGroup>> = getReceivedPackages()
        .map { it.toGroups() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notYetSentPackages: StateFlow<List<TrackedPackage>> = getNotYetSentPackages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var lastDeletedPackages: List<TrackedPackage>? = null

    fun deleteGroup(group: PackageGroup) {
        lastDeletedPackages = group.packages
        viewModelScope.launch {
            group.packages.forEach { deletePackage(it.id) }
        }
    }

    fun delete(pkg: TrackedPackage) {
        lastDeletedPackages = listOf(pkg)
        viewModelScope.launch { deletePackage(pkg.id) }
    }

    fun undoDelete() {
        val pkgs = lastDeletedPackages ?: return
        viewModelScope.launch {
            pkgs.forEach { addPackage(it) }
            lastDeletedPackages = null
        }
    }

    fun toggleReceived(id: Long, isReceived: Boolean) {
        viewModelScope.launch { markAsReceived(id, isReceived) }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                activeGroups.value.flatMap { it.packages }.forEach { pkg ->
                    refreshPackage(pkg.id)
                }
            } catch (_: Exception) {
                _errorMessage.value = "Failed to refresh packages"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun clearError() { _errorMessage.value = null }
}
