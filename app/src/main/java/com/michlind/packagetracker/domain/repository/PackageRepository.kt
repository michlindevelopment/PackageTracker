package com.michlind.packagetracker.domain.repository

import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.model.TrackingResult
import kotlinx.coroutines.flow.Flow

interface PackageRepository {
    fun getActivePackages(): Flow<List<TrackedPackage>>
    fun getReceivedPackages(): Flow<List<TrackedPackage>>
    fun getNotYetSentPackages(): Flow<List<TrackedPackage>>
    suspend fun getPackageById(id: Long): TrackedPackage?
    suspend fun getNonReceivedPackages(): List<TrackedPackage>
    suspend fun addPackage(pkg: TrackedPackage): Long
    suspend fun updatePackage(pkg: TrackedPackage)
    suspend fun deletePackage(id: Long)
    suspend fun markAsReceived(id: Long, isReceived: Boolean)
    suspend fun trackPackage(trackingNumber: String): Result<TrackingResult>
    suspend fun refreshPackage(id: Long): Result<Boolean>
}
