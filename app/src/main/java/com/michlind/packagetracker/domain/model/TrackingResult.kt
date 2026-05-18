package com.michlind.packagetracker.domain.model

data class TrackingResult(
    val status: PackageStatus,
    val statusDescription: String,
    val events: List<TrackingEvent>,
    val estimatedDeliveryTime: Long?,
    val daysInTransit: String?,
    val originCountry: String?,
    val destCountry: String?,
    // Cainiao's `processInfo.progressRate` — 0.0..1.0 across the four
    // origin → destination-country → destination-city → delivered checkpoints.
    val progressRate: Float?,
    val destCarrier: DestCarrierInfo? = null,
    // Serialized Cainiao response — captured for the hidden debug screen
    // only. Re-emitted into PackageEntity.rawApiJson on every refresh.
    val rawApiJson: String? = null
)
