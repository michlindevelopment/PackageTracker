package com.michlind.packagetracker.domain.model

enum class PackageStatus {
    ORDER_PLACED,
    SHIPPED,
    IN_TRANSIT,
    CUSTOMS,
    OUT_FOR_DELIVERY,
    DELIVERED,
    EXCEPTION,
    UNKNOWN;

    val displayName: String
        get() = when (this) {
            ORDER_PLACED -> "Order Placed"
            SHIPPED -> "Shipped"
            IN_TRANSIT -> "In Transit"
            CUSTOMS -> "In Customs"
            OUT_FOR_DELIVERY -> "Out for Delivery"
            DELIVERED -> "Delivered"
            EXCEPTION -> "Exception"
            UNKNOWN -> "Unknown"
        }

    val stepIndex: Int
        get() = when (this) {
            ORDER_PLACED -> 0
            SHIPPED -> 1
            IN_TRANSIT -> 2
            CUSTOMS -> 3
            OUT_FOR_DELIVERY -> 4
            DELIVERED -> 5
            EXCEPTION, UNKNOWN -> -1
        }
}
