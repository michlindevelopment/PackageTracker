package com.michlind.packagetracker.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class TrackingSnapshotRow(
    val id: Long,
    val trackingNumber: String
)

@Dao
interface PackageDao {

    // Tiebreaker on `id DESC`: imported packages start with lastUpdated = 0,
    // so without the tiebreaker SQLite returns them in rowid-ASC order
    // (first-scraped = oldest at top, which inverts the AliExpress listing
    // order). Higher id = more recently inserted = newer on AliExpress.
    @Query("SELECT * FROM packages WHERE isReceived = 0 AND status != 'NOT_YET_SENT' ORDER BY lastUpdated DESC, id DESC")
    fun getActivePackages(): Flow<List<PackageEntity>>

    @Query("SELECT * FROM packages WHERE isReceived = 1 ORDER BY lastUpdated DESC, id DESC")
    fun getReceivedPackages(): Flow<List<PackageEntity>>

    @Query("SELECT * FROM packages WHERE status = 'NOT_YET_SENT' AND isReceived = 0 ORDER BY createdAt DESC")
    fun getNotYetSentPackages(): Flow<List<PackageEntity>>

    @Query("SELECT * FROM packages WHERE id = :id")
    suspend fun getById(id: Long): PackageEntity?

    @Query("SELECT * FROM packages WHERE isReceived = 0 AND status != 'NOT_YET_SENT'")
    suspend fun getNonReceivedPackages(): List<PackageEntity>

    // Used by refresh paths (manual + background worker). Includes packages the
    // user marked received early but whose carrier status hasn't actually
    // reached DELIVERED yet — Cainiao may still have new events for them.
    // Excludes NOT_YET_SENT (no real TN to query) and DELIVERED (terminal).
    @Query("SELECT * FROM packages WHERE trackingNumber != '' AND status NOT IN ('NOT_YET_SENT', 'DELIVERED')")
    suspend fun getPackagesEligibleForRefresh(): List<PackageEntity>

    @Query("SELECT * FROM packages WHERE trackingNumber = :trackingNumber")
    suspend fun getByTrackingNumber(trackingNumber: String): List<PackageEntity>

    @Query("SELECT * FROM packages WHERE externalOrderId = :externalOrderId LIMIT 1")
    suspend fun getByExternalOrderId(externalOrderId: String): PackageEntity?

    @Query("SELECT externalOrderId FROM packages WHERE externalOrderId LIKE 'ali:%' AND trackingNumber != ''")
    suspend fun getAliExternalOrderIdsWithTracking(): List<String>

    @Query("SELECT id FROM packages WHERE trackingNumber = '' AND isReceived = 0")
    suspend fun getBlankTrackingPackageIds(): List<Long>

    // (id, trackingNumber) for every non-received package — used by Full
    // Sync to diff tracking numbers before/after an import and refresh
    // anything that changed.
    @Query("SELECT id, trackingNumber FROM packages WHERE isReceived = 0")
    suspend fun getNonReceivedTrackingSnapshot(): List<TrackingSnapshotRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PackageEntity): Long

    @Update
    suspend fun update(entity: PackageEntity)

    @Delete
    suspend fun delete(entity: PackageEntity)

    @Query("DELETE FROM packages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE packages SET isReceived = :isReceived, lastUpdated = :now WHERE id = :id")
    suspend fun setReceived(id: Long, isReceived: Boolean, now: Long)

    @Query("UPDATE packages SET localTrackingNumber = :tn WHERE id = :id")
    suspend fun setLocalTrackingNumber(id: Long, tn: String?)

    // Used by HomeViewModel.syncStatus to feed the SMS scanner. Returns the
    // user-supplied local-courier TNs for non-received packages so they're
    // picked up alongside the main Cainiao TNs.
    @Query("SELECT localTrackingNumber FROM packages WHERE isReceived = 0 AND localTrackingNumber IS NOT NULL AND localTrackingNumber != ''")
    suspend fun getActiveLocalTrackingNumbers(): List<String>
}
