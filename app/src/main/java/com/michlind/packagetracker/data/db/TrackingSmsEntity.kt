package com.michlind.packagetracker.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cache of SMS messages whose body contains a tracked package's tracking
 * number. Populated during syncStatus() — for each tracked TN we query the
 * device SMS inbox and upsert matches here. The DetailScreen's "SMS" tab
 * reads from this table per-TN.
 *
 * Keyed independently of [PackageEntity] (no FK), so SMS rows can outlive a
 * deleted package and rejoin if the user re-adds the same TN. The unique
 * (trackingNumber, smsId) index makes re-scans idempotent.
 */
@Entity(
    tableName = "tracking_sms",
    indices = [
        Index(value = ["trackingNumber"]),
        Index(value = ["trackingNumber", "smsId"], unique = true)
    ]
)
data class TrackingSmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackingNumber: String,
    // _id from Telephony.Sms — used as the dedupe key so we don't insert
    // the same message twice on repeated scans.
    val smsId: Long,
    val sender: String,
    val body: String,
    val timestamp: Long
)
