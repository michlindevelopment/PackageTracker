package com.michlind.packagetracker.util

import com.michlind.packagetracker.domain.model.PackageStatus

object StatusMapper {

    /**
     * Maps a Cainiao `actionCode` to a [PackageStatus]. No fallback to the
     * top-level `status` field — that field is too coarse (Cainiao uses
     * `"DELIVERING"` for the entire transit phase, including pre-shipment
     * advance-shipping notices).
     *
     * When the action code is unknown but Cainiao gave us a `progressRate`,
     * we bucket the rate across the six in-flight statuses as a coarse
     * fallback (see [mapByProgress]). [PackageStatus.DELIVERED] is never
     * inferred from progress — that requires an explicit sign-for event.
     *
     * Buckets follow the Alibaba TOP API reference (apiId 30120). Where the
     * reference distinguishes states our enum doesn't (e.g. LAST_MILE vs
     * OUT_FOR_DELIVERY, DUTIES_DUE vs IMPORT_CUSTOMS, RETURNED vs EXCEPTION),
     * we collapse to the nearest existing enum value — noted inline.
     */
    fun map(actionCode: String?, progressRate: Float? = null): PackageStatus {
        val byAction = mapActionCode(actionCode)
        if (byAction != PackageStatus.UNKNOWN) return byAction
        return mapByProgress(progressRate)
    }

    /**
     * Coarse fallback used when [mapActionCode] returns [PackageStatus.UNKNOWN].
     * Splits `[0, 1]` into six equal buckets — one per in-flight status, in
     * pipeline order. Examples (matches the spec the user gave):
     *   - `0.50` → [PackageStatus.IN_TRANSIT]
     *   - `0.98` → [PackageStatus.OUT_FOR_DELIVERY] ("Local Courier")
     *
     * Returns [PackageStatus.UNKNOWN] if the rate is null or negative —
     * better to admit we don't know than to fabricate a stage.
     */
    private fun mapByProgress(progressRate: Float?): PackageStatus {
        val rate = progressRate ?: return PackageStatus.UNKNOWN
        if (rate < 0f) return PackageStatus.UNKNOWN
        val sixth = 1f / 6f
        return when {
            rate < 1 * sixth -> PackageStatus.ORDER_PLACED      // [0,    16.7%)
            rate < 2 * sixth -> PackageStatus.SHIPPED           // [16.7, 33.3%)
            rate < 3 * sixth -> PackageStatus.CUSTOMS_EXPORT    // [33.3, 50%)
            rate < 4 * sixth -> PackageStatus.IN_TRANSIT        // [50,   66.7%)
            rate < 5 * sixth -> PackageStatus.CUSTOMS_IMPORT    // [66.7, 83.3%)
            else              -> PackageStatus.OUT_FOR_DELIVERY  // [83.3, 100%]
        }
    }

    fun mapActionCode(actionCode: String?): PackageStatus {
        val code = actionCode?.uppercase().orEmpty()
        if (code.isEmpty()) return PackageStatus.UNKNOWN

        // ── Failures first ──────────────────────────────────────────────
        // Suffix check has to win over prefix checks below, otherwise
        // `PU_PICKUP_FAILURE` would slip into the PU_* "shipped" bucket.
        if (code.endsWith("_FAILURE") || code.endsWith("_FAIL") ||
            code == "FAILED" || code == "REJECT" || code == "GWMS_EXCEPTION"
        ) return PackageStatus.EXCEPTION

        // Returns — no dedicated RETURNED status yet; surface as EXCEPTION
        // so the user actually notices something went wrong.
        if (code.startsWith("RT_") || code == "RETURNED") return PackageStatus.EXCEPTION

        // ── Terminal: delivered ─────────────────────────────────────────
        when (code) {
            "GTMS_SIGNED", "GTMS_STA_SIGNED", "SIGNED",
            "GSTA_SIGN", "GSTA_BUYER_SIGN", "STA_SIGN" -> return PackageStatus.DELIVERED
        }

        // ── Awaiting pickup at locker / pickup point ────────────────────
        when (code) {
            "GTMS_WAIT_SELF_PICK", "GSTA_INBOUND", "GSTAHUB_INBOUND" ->
                return PackageStatus.AWAITING_PICKUP
        }

        // ── Out for delivery / last-mile ────────────────────────────────
        // The reference splits these into LAST_MILE (with local courier /
        // at delivery station) and OUT_FOR_DELIVERY (on the truck right
        // now). Our enum has one bucket — OUT_FOR_DELIVERY ("Local
        // Courier") — so both collapse to it.
        when (code) {
            "GTMS_DELIVERING", "SENT_SCAN", "GTMS_RE_DELIVERING",
            "GTMS_ACCEPT" -> return PackageStatus.OUT_FOR_DELIVERY
        }
        if (code.startsWith("GTMS_SC_") ||
            code.startsWith("GTMS_DO_") ||
            code.startsWith("GTMS_STATION_")
        ) return PackageStatus.OUT_FOR_DELIVERY

        // ── Import customs (destination) ────────────────────────────────
        // DUTIES_DUE (CUS_TAX) doesn't have its own enum value; folded into
        // CUSTOMS_IMPORT since it's still a customs hold, just user-actionable.
        if (code == "CUS_TAX") return PackageStatus.CUSTOMS_IMPORT
        if (code.startsWith("CC_IM_") ||
            code.startsWith("CC_HO_") ||
            code.startsWith("CIQ_") ||
            code.startsWith("CUS_")
        ) return PackageStatus.CUSTOMS_IMPORT

        // ── In transit (line-haul, transit hubs, transit-country clearance) ─
        if (code.startsWith("LH_") ||
            code.startsWith("TD_") ||
            code.startsWith("CC_TRANS_")
        ) return PackageStatus.IN_TRANSIT

        // ── Export customs (origin) ─────────────────────────────────────
        if (code.startsWith("CC_EX_")) return PackageStatus.CUSTOMS_EXPORT

        // ── Origin processing — picked up, sorting, in warehouse ────────
        // GWMS_ACCEPT is excluded (it's the order-accepted event, not
        // physical processing).
        if (code.startsWith("PU_") ||
            code.startsWith("SC_") ||
            (code.startsWith("GWMS_") && code != "GWMS_ACCEPT")
        ) return PackageStatus.SHIPPED

        // ── Order placed / consignment declared ─────────────────────────
        when (code) {
            "CONSIGN", "OM_CONSIGN", "GTMS_ASN",
            "GWMS_ACCEPT" -> return PackageStatus.ORDER_PLACED
        }

        // GTMS_STA_* (address reroutes), GTMS_OTHER / GSTA_OTHER, GOT /
        // DEPARTURE / ARRIVAL / GTMS_RELABEL, and every non-official
        // opcode (CW_*, COMMON_*, LAST_MILE_ASN_NOTIFY, POSTMAN_POST, …)
        // intentionally fall through to UNKNOWN — `map()` then leans on
        // `progressRate` for the coarse status bucket.
        return PackageStatus.UNKNOWN
    }
}
