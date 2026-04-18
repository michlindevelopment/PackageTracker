package com.michlind.packagetracker.domain.usecase

import com.michlind.packagetracker.domain.model.TrackingResult
import com.michlind.packagetracker.domain.repository.PackageRepository
import javax.inject.Inject

class TrackPackageUseCase @Inject constructor(
    private val repository: PackageRepository
) {
    suspend operator fun invoke(trackingNumber: String): Result<TrackingResult> =
        repository.trackPackage(trackingNumber)
}
