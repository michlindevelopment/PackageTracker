package com.michlind.packagetracker.domain.usecase

import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.repository.PackageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetActivePackagesUseCase @Inject constructor(
    private val repository: PackageRepository
) {
    operator fun invoke(): Flow<List<TrackedPackage>> = repository.getActivePackages()
}

class GetReceivedPackagesUseCase @Inject constructor(
    private val repository: PackageRepository
) {
    operator fun invoke(): Flow<List<TrackedPackage>> = repository.getReceivedPackages()
}
