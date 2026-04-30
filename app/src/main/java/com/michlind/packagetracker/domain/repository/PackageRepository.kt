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

    /**
     * Refresh all packages sharing [trackingNumber] with a single API call.
     * Returns a map of package id → whether its status changed.
     */
    suspend fun refreshTrackingNumber(trackingNumber: String): Result<Map<Long, Boolean>>

    suspend fun getByExternalOrderId(externalOrderId: String): TrackedPackage?

    /**
     * Returns the AliExpress orderIds (without the "ali:" prefix) of packages
     * we've already imported AND that already have a tracking number on file.
     * Used by the AliExpress import script to skip the per-order iframe lookup
     * for orders we've already enriched.
     */
    suspend fun getImportedAliOrderIdsWithTracking(): Set<String>
}
