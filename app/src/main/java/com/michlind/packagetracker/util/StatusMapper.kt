package com.michlind.packagetracker.util

import com.michlind.packagetracker.domain.model.PackageStatus

object StatusMapper {

    // Fallback threshold for `progressRate` when `progressPointList` info isn't
    // available. The 4-point list (origin → dest country → dest city → delivered)
    // hits ~0.75 only once the destination-city checkpoint is lit.
    private const val LAST_MILE_PROGRESS_THRESHOLD = 0.75f

    /**
     * Maps Cainiao API response to a [PackageStatus].
     *
     * The top-level `status` field is unreliable — Cainiao uses `"DELIVERING"`
     * for the entire in-transit phase, not just last-mile. So we prefer the
     * latest action code (authoritative). When the action code is unknown,
     * we fall back to the top-level status, guarded for the `"DELIVERING"`
     * case by either:
     *   - `progressPointList` lit-count (preferred — works for both 3- and
     *     4-point variants: last-mile when ≥ total - 1 points are lit), or
     *   - the legacy `progressRate >= 0.75` heuristic when point info is
     *     missing.
     */
    fun map(
        statusRaw: String?,
        latestActionCode: String?,
        progressRate: Float? = null,
        progressPointsLit: Int = 0,
        progressPointsTotal: Int = 0
    ): PackageStatus {
        val byAction = mapActionCode(latestActionCode)
        if (byAction != PackageStatus.UNKNOWN) return byAction

        val status = statusRaw?.uppercase().orEmpty()
        return when {
            status.contains("SIGN") || status == "DELIVERED" -> PackageStatus.DELIVERED
            status == "DELIVERING" ->
                if (isAtLastMile(progressRate, progressPointsLit, progressPointsTotal)) PackageStatus.OUT_FOR_DELIVERY
                else PackageStatus.IN_TRANSIT
            status.contains("CUSTOMS") -> PackageStatus.CUSTOMS
            status.contains("DEPARTURE") || status == "IN_TRANSIT" -> PackageStatus.IN_TRANSIT
            status.contains("ACCEPT") || status.contains("GTMS") -> PackageStatus.SHIPPED
            status == "SELLER_PREPARING" || status == "PENDING_PICKUP" -> PackageStatus.ORDER_PLACED
            status.contains("FAILED") || status.contains("RETURN") || status.contains("LOST") || status.contains("EXCEPTION") -> PackageStatus.EXCEPTION
            else -> PackageStatus.UNKNOWN
        }
    }

    // "Last mile" = the destination-side hop. With Cainiao's point lists this
    // is when all but one checkpoint are lit (the unlit one being "Delivered"):
    //   - 4-point: ≥3 lit (origin + dest country + dest city)
    //   - 3-point: ≥2 lit (origin + dest country)
    // If point info isn't available we fall back to the progressRate heuristic
    // — coarser, but better than nothing.
    private fun isAtLastMile(
        progressRate: Float?,
        progressPointsLit: Int,
        progressPointsTotal: Int
    ): Boolean = when {
        progressPointsTotal > 0 -> progressPointsLit >= progressPointsTotal - 1
        progressRate != null -> progressRate >= LAST_MILE_PROGRESS_THRESHOLD
        else -> false
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

            // Local delivery: handed off to last-mile carrier, then on the
            // truck. GTMS_ACCEPT = parcel received by local delivery company;
            // GTMS_DO_DEPART = driver departed depot with the parcel.
            "GTMS_ACCEPT", "GTMS_DO_DEPART" -> PackageStatus.OUT_FOR_DELIVERY

            // Package available at a pickup point
            "GSTA_INFORM_BUYER", "GTMS_STA_SIGNED" -> PackageStatus.AWAITING_PICKUP

            "SIGN" -> PackageStatus.DELIVERED

            else -> PackageStatus.UNKNOWN
        }
    }
}
