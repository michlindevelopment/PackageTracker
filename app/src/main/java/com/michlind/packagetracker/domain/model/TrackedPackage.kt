package com.michlind.packagetracker.domain.model

data class TrackedPackage(
    val id: Long = 0,
    val trackingNumber: String,
    val name: String,
    val photoUri: String?,
    val status: PackageStatus,
    val statusDescription: String,
    val lastEvent: TrackingEvent?,
    val events: List<TrackingEvent>,
    val lastUpdated: Long,
    val isReceived: Boolean,
    val createdAt: Long,
    val estimatedDeliveryTime: Long?,
    val daysInTransit: String?,
    val originCountry: String?,
    val destCountry: String?
)
