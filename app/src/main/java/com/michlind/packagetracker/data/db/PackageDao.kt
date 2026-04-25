package com.michlind.packagetracker.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PackageDao {

    @Query("SELECT * FROM packages WHERE isReceived = 0 AND status != 'NOT_YET_SENT' ORDER BY lastUpdated DESC")
    fun getActivePackages(): Flow<List<PackageEntity>>

    @Query("SELECT * FROM packages WHERE isReceived = 1 ORDER BY lastUpdated DESC")
    fun getReceivedPackages(): Flow<List<PackageEntity>>

    @Query("SELECT * FROM packages WHERE status = 'NOT_YET_SENT' ORDER BY createdAt DESC")
    fun getNotYetSentPackages(): Flow<List<PackageEntity>>

    @Query("SELECT * FROM packages WHERE id = :id")
    suspend fun getById(id: Long): PackageEntity?

    @Query("SELECT * FROM packages WHERE isReceived = 0 AND status != 'NOT_YET_SENT'")
    suspend fun getNonReceivedPackages(): List<PackageEntity>

    @Query("SELECT * FROM packages WHERE trackingNumber = :trackingNumber")
    suspend fun getByTrackingNumber(trackingNumber: String): List<PackageEntity>

    @Query("SELECT * FROM packages WHERE externalOrderId = :externalOrderId LIMIT 1")
    suspend fun getByExternalOrderId(externalOrderId: String): PackageEntity?

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
}
