package com.michlind.packagetracker.domain.model

data class TrackingEvent(
    val time: Long,
    val timeStr: String,
    val description: String,
    val standardDescription: String,
    val actionCode: String,
    val groupDescription: String?
)
