package com.michlind.packagetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackingSmsDao {

    /**
     * Reactive list of SMS hits for a tracking number, newest first.
     * The DetailScreen "SMS" tab observes this so it updates live as
     * syncStatus() lands new matches.
     */
    @Query(
        "SELECT * FROM tracking_sms WHERE trackingNumber = :trackingNumber " +
            "ORDER BY timestamp DESC"
    )
    fun observeForTrackingNumber(trackingNumber: String): Flow<List<TrackingSmsEntity>>

    /**
     * Same as [observeForTrackingNumber] but unions hits for any of the
     * supplied tracking numbers, deduping rows that match more than one TN
     * by their primary key. Used when a package has both a Cainiao TN and a
     * user-supplied local-courier TN.
     */
    @Query(
        "SELECT * FROM tracking_sms WHERE trackingNumber IN (:trackingNumbers) " +
            "GROUP BY id ORDER BY timestamp DESC"
    )
    fun observeForTrackingNumbers(trackingNumbers: List<String>): Flow<List<TrackingSmsEntity>>

    /**
     * Upsert. The unique (trackingNumber, smsId) index makes re-scans
     * idempotent — re-inserting the same message just refreshes the row.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<TrackingSmsEntity>)

    @Query("DELETE FROM tracking_sms WHERE trackingNumber = :trackingNumber")
    suspend fun deleteForTrackingNumber(trackingNumber: String)
}
