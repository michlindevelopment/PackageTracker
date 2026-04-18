package com.michlind.packagetracker.domain.usecase

import com.michlind.packagetracker.domain.repository.PackageRepository
import javax.inject.Inject

class MarkAsReceivedUseCase @Inject constructor(
    private val repository: PackageRepository
) {
    suspend operator fun invoke(id: Long, isReceived: Boolean) =
        repository.markAsReceived(id, isReceived)
}
