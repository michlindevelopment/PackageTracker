package com.michlind.packagetracker.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.repository.PackageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: PackageRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun clearQuery() {
        _query.value = ""
    }

    // The repository exposes three disjoint list flows rather than one
    // "getAll" — combine them so search covers active, received, and
    // not-yet-sent packages alike. Dedupe by id in case a package ever
    // shows up in more than one list.
    private val allPackages: StateFlow<List<TrackedPackage>> = combine(
        repository.getActivePackages(),
        repository.getReceivedPackages(),
        repository.getNotYetSentPackages()
    ) { active, received, notYetSent ->
        (active + received + notYetSent).distinctBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Blank query intentionally yields an empty list (a hint is shown in the
    // UI) instead of dumping the whole database. Otherwise: case-insensitive
    // substring match on name OR tracking number.
    val results: StateFlow<List<TrackedPackage>> = combine(
        _query,
        allPackages
    ) { q, all ->
        val needle = q.trim()
        if (needle.isBlank()) {
            emptyList()
        } else {
            all.filter {
                it.name.contains(needle, ignoreCase = true) ||
                    it.trackingNumber.contains(needle, ignoreCase = true)
            }.distinctBy { it.id }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
