package com.michlind.packagetracker.domain.usecase

import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.repository.PackageRepository
import javax.inject.Inject

class UpdatePackageUseCase @Inject constructor(
    private val repository: PackageRepository
) {
    suspend operator fun invoke(pkg: TrackedPackage) = repository.updatePackage(pkg)
}
