package com.michlind.packagetracker.domain.model

/**
 * One SMS message whose body mentioned a tracking number, surfaced on the
 * DetailScreen "SMS" tab. Mirrored from [com.michlind.packagetracker.data.db.TrackingSmsEntity].
 */
data class TrackingSms(
    val id: Long,
    val trackingNumber: String,
    val smsId: Long,
    val sender: String,
    val body: String,
    val timestamp: Long
)
