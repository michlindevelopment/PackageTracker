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
     * Customs-anchored status derivation. [actionCodes] must be newest-first,
     * with advisory codes already removed by the caller.
     *
     * The problem this solves: Cainiao emits `LH_*` (line-haul) scans both
     * BEFORE export customs (origin-hub handoff) and AFTER import customs
     * (line-haul to the destination). [mapActionCode] collapses all of them to
     * [PackageStatus.IN_TRANSIT] (stepper position 3), so a normal movement
     * scan arriving after import customs (position 4) makes the status appear
     * to jump BACKWARD to "In Transit".
     *
     * Fix: treat the export/import customs events as monotonic signposts. When
     * the latest meaningful scan is an ambiguous "moving" event, resolve it by
     * which customs milestones already appear in the history:
     *   - import customs seen  -> [PackageStatus.ARRIVING]  (in destination country)
     *   - else export customs  -> [PackageStatus.IN_FLIGHT] (international leg)
     *   - else                 -> [PackageStatus.IN_TRANSIT] (no customs to anchor;
     *                             safe — nothing for it to look "behind")
     *
     * Unambiguous latest scans (delivered, out-for-delivery, awaiting pickup,
     * exception, a customs scan itself, shipped/order-placed) are trusted as-is.
     */
    fun mapFromHistory(actionCodes: List<String>, progressRate: Float? = null): PackageStatus {
        val base = mapActionCode(actionCodes.firstOrNull())

        // Latest scan is already an unambiguous milestone — trust it directly.
        when (base) {
            PackageStatus.DELIVERED, PackageStatus.AWAITING_PICKUP,
            PackageStatus.OUT_FOR_DELIVERY, PackageStatus.EXCEPTION,
            PackageStatus.CUSTOMS_IMPORT, PackageStatus.CUSTOMS_EXPORT,
            PackageStatus.SHIPPED, PackageStatus.ORDER_PLACED -> return base
            else -> Unit // IN_TRANSIT / CUSTOMS / UNKNOWN → resolve by context below
        }

        val sawImport = actionCodes.any { mapActionCode(it) == PackageStatus.CUSTOMS_IMPORT }
        val sawExport = actionCodes.any { mapActionCode(it) == PackageStatus.CUSTOMS_EXPORT }
        return when {
            sawImport -> PackageStatus.ARRIVING
            sawExport -> PackageStatus.IN_FLIGHT
            base == PackageStatus.IN_TRANSIT -> PackageStatus.IN_TRANSIT
            else -> mapByProgress(progressRate) // UNKNOWN/CUSTOMS with no anchors
        }
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

        // ── Failures & cancellations first ──────────────────────────────
        // Suffix checks must win over the prefix checks below — otherwise
        // GWMS_REJECT / GWMS_CANCEL slip into the GWMS_* "shipped" bucket.
        // LH_POST_COLLECTION is a failure event despite the missing suffix.
        if (code.endsWith("_FAILURE") || code.endsWith("_FAIL") ||
            code.endsWith("_REJECT") || code.endsWith("_CANCEL") ||
            code == "FAILED" || code == "REJECT" || code == "GWMS_EXCEPTION" ||
            code == "LH_POST_COLLECTION"
        ) return PackageStatus.EXCEPTION

        // Returns — no dedicated RETURNED status yet; surface as EXCEPTION
        // so the user actually notices something went wrong.
        if (code.startsWith("RT_") || code == "RETURNED") return PackageStatus.EXCEPTION

        // ── Terminal: delivered — recipient/buyer actually signed ───────
        // NB: GTMS_STA_SIGNED is NOT here — that is the station signing the
        // parcel IN (arrival), handled as AWAITING_PICKUP below.
        when (code) {
            "GTMS_SIGNED", "SIGNED", "GSTA_SIGN",
            "GSTA_BUYER_SIGN", "STA_SIGN" -> return PackageStatus.DELIVERED
        }

        // ── Awaiting pickup at locker / pickup point ────────────────────
        // GTMS_STA_SIGNED is the *station* signing the parcel IN (arrived
        // at the pickup station) — not the customer collecting it.
        // GSTA_INFORM_BUYER (buyer notified to collect) and POSTMAN_POST
        // (deposited in a parcel locker) likewise mean the parcel is
        // waiting for the customer to pick it up. Note GSTA_INBOUND (the
        // pickup point itself) belongs here, but GSTAHUB_INBOUND (an
        // upstream distribution hub) does not — see OUT_FOR_DELIVERY below.
        when (code) {
            "GTMS_WAIT_SELF_PICK", "GSTA_INBOUND", "GTMS_STA_SIGNED",
            "GSTA_INFORM_BUYER", "POSTMAN_POST" ->
                return PackageStatus.AWAITING_PICKUP
        }

        // ── Out for delivery / last-mile ────────────────────────────────
        // GSTAHUB_INBOUND = parcel at a self-pickup *distribution hub* —
        // still moving through the local network toward the pickup point
        // (the GSTA-network analogue of a destination sorting centre), so
        // it's last-mile, not yet customer-collectable.
        when (code) {
            "GTMS_DELIVERING", "SENT_SCAN", "GTMS_RE_DELIVERING",
            "GTMS_ACCEPT", "GSTAHUB_INBOUND" -> return PackageStatus.OUT_FOR_DELIVERY
        }
        if (code.startsWith("GTMS_SC_") ||
            code.startsWith("GTMS_DO_") ||
            code.startsWith("GTMS_STATION_")
        ) return PackageStatus.OUT_FOR_DELIVERY

        // ── Import customs (destination) ────────────────────────────────
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
        if (code.startsWith("PU_") ||
            code.startsWith("SC_") ||
            (code.startsWith("GWMS_") && code != "GWMS_ACCEPT")
        ) return PackageStatus.SHIPPED

        // ── Order placed / consignment declared ─────────────────────────
        when (code) {
            "CONSIGN", "OM_CONSIGN", "GTMS_ASN",
            "GWMS_ACCEPT" -> return PackageStatus.ORDER_PLACED
        }

        return PackageStatus.UNKNOWN
    }
}
