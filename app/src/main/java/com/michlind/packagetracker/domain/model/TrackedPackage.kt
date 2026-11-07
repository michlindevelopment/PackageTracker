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
    val destCountry: String?,
    val externalOrderId: String? = null,
    // Cainiao's `processInfo.progressRate` — 0.0..1.0 across the four
    // origin → destination-country → destination-city → delivered checkpoints.
    val progressRate: Float? = null,
    val destCarrier: DestCarrierInfo? = null,
    // User-supplied tracking number for the destination courier. Lives on
    // the package (not DestCarrierInfo) because it's manually entered and
    // we want it preserved across Cainiao refreshes that rebuild destCarrier
    // from API data. Used only as an additional needle for SMS scanning.
    val localTrackingNumber: String? = null,
    // Last raw Cainiao API response, serialized JSON. Surfaced only by the
    // hidden debug screen reachable via 5-tap on the Shipping Progress card.
    val rawApiJson: String? = null,
    // Number of items in the order. Greater than 1 for an AliExpress order
    // that bundles several products under one card.
    val itemCount: Int = 1,
    // Local copies of every item thumbnail in the order, in display order.
    // May be shorter than [itemCount] when some downloads failed.
    val itemPhotoUris: List<String> = emptyList(),
    // When the package moved to Received (epoch ms), used to keep the
    // Received tab ordered "last received on top". 0 = unknown, which sorts
    // below every known time.
    val receivedAt: Long = 0L,
    // What the order cost. [priceCurrency] is the token AliExpress printed
    // ("₪", "US $") or a currency code for hand-typed prices.
    val priceAmount: Double? = null,
    val priceCurrency: String? = null,
    // Seller's store, read from the AliExpress order card.
    val storeName: String? = null,
    // Product page on AliExpress, when the import saw exactly one product on
    // the order card. See [aliLink] for what the app actually opens.
    val aliUrl: String? = null
) {
    val isMultiItem: Boolean get() = itemCount > 1

    // Where "Open in AliExpress" goes: the product page when we know it, else
    // the order page built from the AliExpress order id (so every imported
    // package has a link even before a re-sync). Null for packages that never
    // came from AliExpress.
    val aliLink: String?
        get() = aliUrl?.takeIf { it.isNotBlank() }
            ?: externalOrderId
                ?.takeIf { it.startsWith("ali:") }
                ?.removePrefix("ali:")
                ?.takeIf { it.isNotBlank() }
                ?.let { "https://www.aliexpress.com/p/order/detail.html?orderId=$it" }

    // Every image file this package owns, for cleanup on delete.
    val allPhotoUris: List<String>
        get() = (listOfNotNull(photoUri) + itemPhotoUris).distinct()
}
