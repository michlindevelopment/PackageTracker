package com.michlind.packagetracker.ui.preview

import com.michlind.packagetracker.domain.model.DestCarrierInfo
import com.michlind.packagetracker.domain.model.PackageStatus
import com.michlind.packagetracker.domain.model.TrackedPackage
import com.michlind.packagetracker.domain.model.TrackingEvent

private val now: Long = 1_730_000_000_000L

fun sampleEvent(
    description: String = "Leaving from destination country/region",
    timeStr: String = "Apr 24, 2026 14:32",
    actionCode: String = "SC_INBOUND_SUCCESS",
    timeOffsetMs: Long = 0
): TrackingEvent = TrackingEvent(
    time = now - timeOffsetMs,
    timeStr = timeStr,
    description = description,
    standardDescription = description,
    actionCode = actionCode,
    groupDescription = null
)

fun samplePackage(
    id: Long = 1L,
    name: String = "AliExpress Headphones",
    trackingNumber: String = "CNG00811858377424",
    status: PackageStatus = PackageStatus.IN_TRANSIT,
    daysInTransit: String? = "12 days",
    photoUri: String? = null,
    lastEvent: TrackingEvent? = sampleEvent(),
    destCarrier: DestCarrierInfo? = null
): TrackedPackage = TrackedPackage(
    id = id,
    trackingNumber = trackingNumber,
    name = name,
    photoUri = photoUri,
    status = status,
    statusDescription = status.displayName,
    lastEvent = lastEvent,
    events = listOfNotNull(lastEvent),
    lastUpdated = now,
    isReceived = false,
    createdAt = now - 1_000_000_000L,
    estimatedDeliveryTime = now + 5_000_000_000L,
    daysInTransit = daysInTransit,
    originCountry = "Mainland China",
    destCountry = "Israel",
    destCarrier = destCarrier
)
