package com.michlind.packagetracker.domain.model

data class AliOrderImport(
    val orderId: String,
    val name: String,
    val imageUrl: String?,
    val trackingNumber: String?,
    val orderCreatedAt: Long
)

enum class ImportResult { ADDED, UPGRADED, SKIPPED, FAILED }
