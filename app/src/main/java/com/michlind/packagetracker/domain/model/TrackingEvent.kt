package com.michlind.packagetracker.domain.model

data class TrackingEvent(
    val time: Long,
    val timeStr: String,
    val description: String,
    val standardDescription: String,
    val actionCode: String,
    val groupDescription: String?,
    // Cainiao group icons (per-event) — used by TimelineItem to render a
    // node-specific marker instead of the generic colored dot. Null when
    // the event has no group association on the carrier side; the UI then
    // falls back to the legacy circle.
    val groupCurrentIconUrl: String? = null,
    val groupHistoryIconUrl: String? = null
)
