package com.michlind.packagetracker.domain.model

data class AliOrderImport(
    val orderId: String,
    val name: String,
    val imageUrl: String?,
    val trackingNumber: String?,
    val orderCreatedAt: Long,
    // Visible AliExpress card status, e.g. "Awaiting shipment",
    // "Awaiting delivery", "Order completed". Used to decide whether to mark
    // the package as Not Yet Sent or already received on import.
    val statusText: String? = null
)

enum class ImportResult { ADDED, UPGRADED, SKIPPED, FAILED }
