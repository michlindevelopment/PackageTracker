package com.michlind.packagetracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackedPackageTest {

    private fun pkg(
        externalOrderId: String? = null,
        aliUrl: String? = null
    ) = TrackedPackage(
        trackingNumber = "LP123",
        name = "Test",
        photoUri = null,
        status = PackageStatus.IN_TRANSIT,
        statusDescription = "",
        lastEvent = null,
        events = emptyList(),
        lastUpdated = 0L,
        isReceived = false,
        createdAt = 0L,
        estimatedDeliveryTime = null,
        daysInTransit = null,
        originCountry = null,
        destCountry = null,
        externalOrderId = externalOrderId,
        aliUrl = aliUrl
    )

    @Test
    fun productPageWinsWhenKnown() {
        val link = pkg(
            externalOrderId = "ali:1123772285666282",
            aliUrl = "https://www.aliexpress.com/item/1005006123456789.html"
        ).aliLink
        assertEquals("https://www.aliexpress.com/item/1005006123456789.html", link)
    }

    @Test
    fun orderPageIsBuiltFromTheOrderId() {
        assertEquals(
            "https://www.aliexpress.com/p/order/detail.html?orderId=1123772285666282",
            pkg(externalOrderId = "ali:1123772285666282").aliLink
        )
    }

    @Test
    fun blankProductUrlFallsBackToTheOrderPage() {
        assertEquals(
            "https://www.aliexpress.com/p/order/detail.html?orderId=42",
            pkg(externalOrderId = "ali:42", aliUrl = "  ").aliLink
        )
    }

    @Test
    fun anEditedPackageKeepsItsSavedLink() {
        // Saving in the edit screen clears externalOrderId but keeps aliUrl.
        assertEquals(
            "https://www.aliexpress.com/p/order/detail.html?orderId=7",
            pkg(aliUrl = "https://www.aliexpress.com/p/order/detail.html?orderId=7").aliLink
        )
    }

    @Test
    fun noLinkForPackagesThatNeverCameFromAliExpress() {
        assertNull(pkg().aliLink)
        assertNull(pkg(externalOrderId = "manual:5").aliLink)
        assertNull(pkg(externalOrderId = "ali:").aliLink)
    }
}
