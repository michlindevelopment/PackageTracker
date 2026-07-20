package com.michlind.packagetracker.domain.model

enum class PackageStatus {
    NOT_YET_SENT,
    ORDER_PLACED,
    SHIPPED,
    IN_TRANSIT,
    IN_FLIGHT,
    CUSTOMS_EXPORT,
    CUSTOMS_IMPORT,
    CUSTOMS,
    ARRIVING,
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
            IN_FLIGHT        -> "In Flight"
            CUSTOMS_EXPORT   -> "Export Customs"
            CUSTOMS_IMPORT   -> "Import Customs"
            CUSTOMS          -> "In Customs"
            ARRIVING         -> "Arriving"
            OUT_FOR_DELIVERY -> "Local Courier"
            AWAITING_PICKUP  -> "Awaiting Pickup"
            DELIVERED        -> "Delivered"
            EXCEPTION        -> "Exception"
            UNKNOWN          -> "Unknown"
        }

    // Used by the progress stepper (0-6); -1 = no progress shown.
    // The stepper's step 3 dot is labelled "In Flight" and step 5 "Delivery";
    // IN_TRANSIT (customs-less fallback) and IN_FLIGHT share step 3, while
    // ARRIVING / OUT_FOR_DELIVERY / AWAITING_PICKUP share step 5.
    val stepIndex: Int
        get() = when (this) {
            NOT_YET_SENT     -> -1
            ORDER_PLACED     -> 0
            SHIPPED          -> 1
            CUSTOMS_EXPORT   -> 2
            IN_TRANSIT       -> 3
            IN_FLIGHT        -> 3
            CUSTOMS          -> 3
            CUSTOMS_IMPORT   -> 4
            ARRIVING         -> 5
            OUT_FOR_DELIVERY -> 5
            AWAITING_PICKUP  -> 5
            DELIVERED        -> 6
            EXCEPTION, UNKNOWN -> -1
        }
}
