package com.michlind.packagetracker.util

import com.michlind.packagetracker.domain.model.PackageStatus

object StatusMapper {

    // Cainiao's `progressRate` runs 0.0 → 1.0 across the four progress points
    // (origin country → destination country → destination city → delivered).
    // We only believe a top-level `"DELIVERING"` once the destination-city
    // checkpoint is reached; below that the parcel is still in transit.
    private const val LAST_MILE_PROGRESS_THRESHOLD = 0.75f

    /**
     * Maps Cainiao API response to a [PackageStatus].
     *
     * The top-level `status` field is unreliable — Cainiao uses `"DELIVERING"`
     * for the entire in-transit phase, not just last-mile. So we prefer the
     * latest action code (authoritative), and only fall back to the top-level
     * status — guarded by `progressRate` for the `"DELIVERING"` case — when
     * the action code is unknown.
     */
    fun map(statusRaw: String?, latestActionCode: String?, progressRate: Float? = null): PackageStatus {
        val byAction = mapActionCode(latestActionCode)
        if (byAction != PackageStatus.UNKNOWN) return byAction

        val status = statusRaw?.uppercase().orEmpty()
        return when {
            status.contains("SIGN") || status == "DELIVERED" -> PackageStatus.DELIVERED
            status == "DELIVERING" ->
                if ((progressRate ?: 0f) >= LAST_MILE_PROGRESS_THRESHOLD) PackageStatus.OUT_FOR_DELIVERY
                else PackageStatus.IN_TRANSIT
            status.contains("CUSTOMS") -> PackageStatus.CUSTOMS
            status.contains("DEPARTURE") || status == "IN_TRANSIT" -> PackageStatus.IN_TRANSIT
            status.contains("ACCEPT") || status.contains("GTMS") -> PackageStatus.SHIPPED
            status == "SELLER_PREPARING" || status == "PENDING_PICKUP" -> PackageStatus.ORDER_PLACED
            status.contains("FAILED") || status.contains("RETURN") || status.contains("LOST") || status.contains("EXCEPTION") -> PackageStatus.EXCEPTION
            else -> PackageStatus.UNKNOWN
        }
    }

    fun mapActionCode(actionCode: String?): PackageStatus {
        return when (actionCode?.uppercase().orEmpty()) {
            // Warehouse — order recorded, not yet picked up
            "GWMS_ACCEPT", "GWMS_PACKAGE", "GWMS_OUTBOUND" -> PackageStatus.ORDER_PLACED

            // Carrier has the package, domestic processing in the ORIGIN country
            // (pickup, sorting centers, arrival at departure transport hub) —
            // still before export customs.
            "PU_PICKUP_SUCCESS",
            "SC_INBOUND_SUCCESS", "SC_OUTBOUND_SUCCESS",
            // SC_TRANS_* are intermediate transit sorting centers on the
            // domestic origin leg — same kind of event as SC_INBOUND/OUTBOUND,
            // just at a hub between the first and last sorting center.
            "SC_TRANS_INBOUND_SUCCESS", "SC_TRANS_OUTBOUND_SUCCESS",
            "LH_HO_IN_SUCCESS",
            "CW_INBOUND", "CW_SIGN_IN_SUCCESS", "CW_OUTBOUND" -> PackageStatus.SHIPPED

            "SC_INBOUND_FAILURE" -> PackageStatus.EXCEPTION

            // Export customs — handing over to airline / customs clearance in origin country
            "LH_HO_AIRLINE", "CC_EX_START", "CC_EX_SUCCESS" -> PackageStatus.CUSTOMS_EXPORT

            // International transit — flying / arrived in destination country hub
            "LH_DEPART", "LH_ARRIVE" -> PackageStatus.IN_TRANSIT

            // Import customs in destination country
            "CC_HO_IN_SUCCESS", "CC_HO_OUT_SUCCESS",
            "CC_IM_START", "CC_IM_SUCCESS" -> PackageStatus.CUSTOMS_IMPORT

            // Local delivery handed off to last-mile carrier
            "GTMS_ACCEPT" -> PackageStatus.OUT_FOR_DELIVERY

            // Package available at a pickup point
            "GSTA_INFORM_BUYER", "GTMS_STA_SIGNED" -> PackageStatus.AWAITING_PICKUP

            "SIGN" -> PackageStatus.DELIVERED

            else -> PackageStatus.UNKNOWN
        }
    }
}
