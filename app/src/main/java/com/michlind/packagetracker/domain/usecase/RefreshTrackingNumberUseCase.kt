package com.michlind.packagetracker.domain.usecase

import com.michlind.packagetracker.domain.repository.PackageRepository
import javax.inject.Inject

class RefreshTrackingNumberUseCase @Inject constructor(
    private val repository: PackageRepository
) {
    suspend operator fun invoke(trackingNumber: String): Result<Map<Long, Boolean>> =
        repository.refreshTrackingNumber(trackingNumber)
}
