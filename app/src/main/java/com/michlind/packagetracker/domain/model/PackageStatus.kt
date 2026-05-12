package com.michlind.packagetracker.domain.model

enum class PackageStatus {
    NOT_YET_SENT,
    ORDER_PLACED,
    SHIPPED,
    IN_TRANSIT,
    CUSTOMS_EXPORT,
    CUSTOMS_IMPORT,
    CUSTOMS,
    OUT_FOR_DELIVERY,
    AWAITING_PICKUP,
    DELIVERED,
    EXCEPTION,
    UNKNOWN;

    val displayName: String
        get() = when (this) {
            NOT_YET_SENT     -> "To Ship"
            ORDER_PLACED     -> "Order Placed"
            SHIPPED          -> "Shipped"
            IN_TRANSIT       -> "In Transit"
            CUSTOMS_EXPORT   -> "Export Customs"
            CUSTOMS_IMPORT   -> "Import Customs"
            CUSTOMS          -> "In Customs"
            OUT_FOR_DELIVERY -> "Local Courier"
            AWAITING_PICKUP  -> "Awaiting Pickup"
            DELIVERED        -> "Delivered"
            EXCEPTION        -> "Exception"
            UNKNOWN          -> "Unknown"
        }

    // Used by the progress stepper (0-6); -1 = no progress shown
    val stepIndex: Int
        get() = when (this) {
            NOT_YET_SENT     -> -1
            ORDER_PLACED     -> 0
            SHIPPED          -> 1
            CUSTOMS_EXPORT   -> 2
            IN_TRANSIT       -> 3
            CUSTOMS          -> 3
            CUSTOMS_IMPORT   -> 4
            OUT_FOR_DELIVERY -> 5
            AWAITING_PICKUP  -> 5
            DELIVERED        -> 6
            EXCEPTION, UNKNOWN -> -1
        }
}
