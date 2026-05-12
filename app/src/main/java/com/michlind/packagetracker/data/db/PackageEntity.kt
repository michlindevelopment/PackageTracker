package com.michlind.packagetracker.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "packages",
    indices = [Index(value = ["externalOrderId"], unique = false)]
)
data class PackageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackingNumber: String,
    val name: String,
    val photoUri: String?,
    val status: String,
    val statusDescription: String,
    val lastEventDescription: String,
    val lastEventTime: Long,
    val lastUpdated: Long,
    val isReceived: Boolean,
    val createdAt: Long,
    val eventsJson: String,
    val estimatedDeliveryTime: Long?,
    val daysInTransit: String?,
    val originCountry: String?,
    val destCountry: String?,
    val externalOrderId: String? = null,
    // Cainiao's `processInfo.progressRate` — 0.0..1.0 across the four
    // origin → destination-country → destination-city → delivered checkpoints.
    val progressRate: Float? = null
)
