package com.michlind.packagetracker.domain.model

// Destination carrier (last-mile courier in the recipient's country) as
// reported by Cainiao's `destCpInfo`. `phone` is a free-text blob; UI is
// responsible for surfacing any clickable phone/email/URLs it contains.
data class DestCarrierInfo(
    val name: String,
    val phone: String?,
    val url: String?,
    val email: String?
)
