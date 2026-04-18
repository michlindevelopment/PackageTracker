package com.michlind.packagetracker.domain.usecase

import com.michlind.packagetracker.domain.repository.PackageRepository
import javax.inject.Inject

class RefreshPackageUseCase @Inject constructor(
    private val repository: PackageRepository
) {
    suspend operator fun invoke(id: Long): Result<Boolean> = repository.refreshPackage(id)
}
