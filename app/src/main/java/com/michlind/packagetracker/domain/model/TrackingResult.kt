package com.michlind.packagetracker.domain.model

data class TrackingResult(
    val status: PackageStatus,
    val statusDescription: String,
    val events: List<TrackingEvent>,
    val estimatedDeliveryTime: Long?,
    val daysInTransit: String?,
    val originCountry: String?,
    val destCountry: String?
)
