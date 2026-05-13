package com.michlind.packagetracker.domain.repository

import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.model.TrackingResult
import kotlinx.coroutines.flow.Flow

interface PackageRepository {
    fun getActivePackages(): Flow<List<TrackedPackage>>
    fun getReceivedPackages(): Flow<List<TrackedPackage>>
    fun getNotYetSentPackages(): Flow<List<TrackedPackage>>
    suspend fun getPackageById(id: Long): TrackedPackage?

    /**
     * Synchronous, non-blocking lookup from the in-memory snapshot that is
     * kept warm by the active/received/not-yet-sent flow collectors. Returns
     * null if we haven't seen this id yet in any list emission (e.g.
     * deep-linked from a notification before the home list ever loaded).
     *
     * Used to render Detail immediately when the user taps a card —
     * sidesteps the Room round-trip during the slide animation.
     */
    fun peekById(id: Long): TrackedPackage?
    suspend fun getNonReceivedPackages(): List<TrackedPackage>
    suspend fun getPackagesEligibleForRefresh(): List<TrackedPackage>
    suspend fun addPackage(pkg: TrackedPackage): Long
    suspend fun updatePackage(pkg: TrackedPackage)
    suspend fun deletePackage(id: Long)
    suspend fun markAsReceived(id: Long, isReceived: Boolean)

    /**
     * Set (or clear, with null/blank) the user-supplied local-courier
     * tracking number. Used only as an extra needle for SMS scanning.
     */
    suspend fun setLocalTrackingNumber(id: Long, trackingNumber: String?)

    /**
     * Snapshot of non-blank local-courier TNs across non-received packages.
     * Used by syncStatus to feed the SMS scanner alongside main TNs.
     */
    suspend fun getActiveLocalTrackingNumbers(): List<String>
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

    /**
     * IDs of non-received packages whose tracking number is blank. Used to
     * snapshot which packages had no tracking before a background AliExpress
     * import runs, so afterwards we can refresh only the ones that just got
     * a tracking number.
     */
    suspend fun getBlankTrackingPackageIds(): List<Long>

    /**
     * Map of id → current tracking number for every non-received package.
     * Used by Full Sync: snapshot before, diff after, refresh any package
     * whose tracking number changed (blank → non-blank OR
     * non-blank → different non-blank).
     */
    suspend fun getNonReceivedTrackingSnapshot(): Map<Long, String>

    /**
     * Any one existing package that already shares this tracking number, or
     * null if none. Used when adding a new package that's joining a
     * multi-group, so it can inherit the group's tracking events instead
     * of starting with an empty timeline.
     */
    suspend fun getFirstByTrackingNumber(trackingNumber: String): TrackedPackage?
}
