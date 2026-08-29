package com.michlind.packagetracker.util

import com.michlind.packagetracker.domain.model.PackageStatus

/**
 * Derives a [PackageStatus] from a Cainiao trace.
 *
 * Cainiao's top-level `status` field is too coarse to drive the UI — it reads
 * `"DELIVERING"` for the entire transit phase, including pre-shipment
 * advance-shipping notices — so status comes from the per-event `actionCode`
 * instead. Three things make that harder than a lookup table:
 *
 *  1. **Cainiao invents codes.** `COMMON_INTRANSIT`, `LAST_MILE_HO_SUCCESS`
 *     and friends appear nowhere in the Alibaba TOP enum (apiId 30120) this
 *     table was built from. An unmapped code used to fall straight through to
 *     the `progressRate` bucket, which cheerfully reported "Order Placed" for
 *     a parcel a week into its journey. [classify] therefore ends with a
 *     keyword net, and anything it infers is marked [Confidence.WEAK].
 *
 *  2. **Some codes are forecasts, not movement.** `LAST_MILE_ASN_NOTIFY` is
 *     the destination carrier being *told* a parcel is coming while the parcel
 *     is still at origin — [Confidence.ADVISORY], ignored outright.
 *     `COMMON_INTRANSIT` is worse: Cainiao uses it both for pre-shipment
 *     notices and for genuine in-transit scans, so it is [Confidence.WEAK] and
 *     counts only once a [Confidence.STRONG] code confirms the parcel shipped.
 *
 *  3. **Scans are not monotonic.** `LH_*` line-haul scans appear both before
 *     export customs (origin-hub handoff) and after import customs
 *     (destination line-haul), so a routine movement scan can make the status
 *     appear to jump backwards. Two guards: ambiguous "the parcel moved" scans
 *     are resolved by which customs milestones already appear in the trace
 *     (see [anchor]), and the answer can never fall below the furthest
 *     *milestone* already reached (see [MILESTONE_STATUSES]).
 *
 * Where the TOP reference distinguishes states this enum doesn't (LAST_MILE vs
 * OUT_FOR_DELIVERY, DUTIES_DUE vs IMPORT_CUSTOMS, RETURNED vs EXCEPTION) we
 * collapse to the nearest existing value — noted inline.
 */
object StatusMapper {

    /** How much weight one action code carries in [deriveStatus]. */
    enum class Confidence {
        /** Explicitly mapped code — trusted on its own. */
        STRONG,

        /** Inferred from the code's wording, or a code Cainiao overloads.
         *  Counts only once a STRONG code confirms the parcel shipped. */
        WEAK,

        /** Forecast/notification — never reflects where the parcel is. */
        ADVISORY
    }

    data class CodeMeaning(val status: PackageStatus, val confidence: Confidence)

    // ─────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Derives the package's current status from its full trace.
     *
     * @param actionCodes every event's `actionCode`, **newest first**, exactly
     *   as Cainiao sent them — advisory filtering happens here, not upstream.
     * @param progressRate `processInfo.progressRate` (0..1), used only when no
     *   usable action code is present.
     * @param apiStatus the top-level `status` field, consulted only for the
     *   one thing it says unambiguously (see [isDeliveredApiStatus]).
     */
    fun deriveStatus(
        actionCodes: List<String>,
        progressRate: Float? = null,
        apiStatus: String? = null
    ): PackageStatus {
        val meanings = actionCodes
            .map { classify(it) }
            .filter { it.confidence != Confidence.ADVISORY }

        // WEAK signals (COMMON_INTRANSIT, keyword-net guesses) only count once
        // something we actually recognise says the parcel left the seller.
        // Without that corroboration a pre-shipment notice reads as movement.
        val corroborated = meanings.any {
            it.confidence == Confidence.STRONG &&
                it.status.stepIndex >= PackageStatus.SHIPPED.stepIndex
        }
        val statuses = (if (corroborated) meanings else meanings.filter {
            it.confidence == Confidence.STRONG
        }).map { it.status } // still newest-first

        // ── Terminal states, decided before any floor logic ───────────────
        // Both indices are the *newest* occurrence (the list is newest-first),
        // so comparing them tells us whether a return/failure superseded the
        // sign-for or the other way round.
        val deliveredIdx = statuses.indexOf(PackageStatus.DELIVERED)
        val exceptionIdx = statuses.indexOf(PackageStatus.EXCEPTION)
        if (deliveredIdx >= 0 && (exceptionIdx < 0 || exceptionIdx > deliveredIdx)) {
            return PackageStatus.DELIVERED
        }
        // Only index 0: a failed delivery attempt IS the current state, but an
        // older failure that later scans moved past must not stick.
        if (exceptionIdx == 0) return PackageStatus.EXCEPTION

        // Cainiao sometimes reports the sign-for in the summary field before a
        // signed trace event shows up. That single value is unambiguous; the
        // rest of the field is not, and is ignored.
        if (isDeliveredApiStatus(apiStatus)) return PackageStatus.DELIVERED

        // ── Where the trace says we are ───────────────────────────────────
        val sawImport = statuses.contains(PackageStatus.CUSTOMS_IMPORT)
        val sawExport = statuses.contains(PackageStatus.CUSTOMS_EXPORT)
        val latest = statuses.firstOrNull()
        val base = latest?.let { anchor(it, sawImport, sawExport) } ?: mapByProgress(progressRate)
        // The newest scan told us nothing by name, so `base` is an inference
        // from customs anchors or progressRate rather than a reading.
        val baseIsGuess = latest == null || latest == PackageStatus.UNKNOWN

        // ── Never report earlier than a milestone we already passed ───────
        // Ambiguous "moving" statuses are deliberately not milestones: an
        // origin-hub LH_* scan precedes export customs, so counting it would
        // hide the later "Export Customs" state.
        val floor = statuses.filter { it in MILESTONE_STATUSES }.maxByOrNull { it.stepIndex }
            ?: return base
        // On a tie the newest scan wins — "Awaiting Pickup" is more useful
        // than the "Local Courier" milestone behind it — unless `base` is only
        // a guess, in which case the milestone we actually recorded is better.
        val baseWins =
            if (baseIsGuess) base.stepIndex > floor.stepIndex
            else base.stepIndex >= floor.stepIndex
        return if (baseWins) base else floor
    }

    /** Convenience for callers that only need the status of a single code. */
    fun mapActionCode(actionCode: String?): PackageStatus = classify(actionCode).status

    /** True for codes that are forecasts rather than real package movement. */
    fun isAdvisory(actionCode: String?): Boolean =
        classify(actionCode).confidence == Confidence.ADVISORY

    /**
     * Maps one Cainiao `actionCode` to a status plus how much we trust it.
     * Order matters throughout — see the comments on each block.
     */
    fun classify(actionCode: String?): CodeMeaning {
        val code = actionCode?.trim()?.uppercase().orEmpty()
        if (code.isEmpty()) return WEAK_UNKNOWN

        // ── Forecasts, before anything else can act on them ─────────────
        // Destination carrier notified a parcel is coming; the parcel itself
        // is typically still at origin, so this must never move status.
        if (code in ADVISORY_CODES) return ADVISORY_MEANING

        // ── Failures & cancellations ────────────────────────────────────
        // Suffix checks must win over the prefix checks below — otherwise
        // GWMS_REJECT slips into the GWMS_* "shipped" bucket and
        // PU_PICKUP_FAILED into the PU_* one.
        if (FAILURE_SUFFIXES.any { code.endsWith(it) } || code in FAILURE_CODES) {
            return STRONG_EXCEPTION
        }

        // Returns — no dedicated RETURNED status yet; surface as EXCEPTION
        // so the user actually notices something went wrong.
        if (code.startsWith("RT_") || code == "RETURNED") return STRONG_EXCEPTION

        // ── Terminal: delivered — recipient/buyer actually signed ───────
        // NB: GTMS_STA_SIGNED is NOT here — that is the station signing the
        // parcel IN (arrival), handled as AWAITING_PICKUP below.
        if (code in DELIVERED_CODES) return strong(PackageStatus.DELIVERED)

        // ── Awaiting pickup at locker / pickup point ────────────────────
        // GTMS_STA_SIGNED is the *station* signing the parcel IN (arrived at
        // the pickup station) — not the customer collecting it.
        // GSTA_INFORM_BUYER (buyer notified to collect) and POSTMAN_POST
        // (deposited in a parcel locker) likewise mean the parcel is waiting
        // for the customer. Note GSTA_INBOUND (the pickup point itself)
        // belongs here, but GSTAHUB_INBOUND (an upstream distribution hub)
        // does not — see OUT_FOR_DELIVERY below.
        if (code in AWAITING_PICKUP_CODES) return strong(PackageStatus.AWAITING_PICKUP)

        // ── Order placed / consignment declared ─────────────────────────
        // Ahead of the generic GTMS_*/GWMS_* prefixes below, which would
        // otherwise swallow GTMS_ASN and GWMS_ACCEPT.
        if (code in ORDER_PLACED_CODES) return strong(PackageStatus.ORDER_PLACED)

        // ── Out for delivery / last-mile ────────────────────────────────
        // GSTAHUB_INBOUND = parcel at a self-pickup *distribution hub* — still
        // moving through the local network toward the pickup point (the
        // GSTA-network analogue of a destination sorting centre), so it's
        // last-mile, not yet customer-collectable.
        if (code in OUT_FOR_DELIVERY_CODES) return strong(PackageStatus.OUT_FOR_DELIVERY)
        // GTMS is the destination last-mile system; every remaining GTMS_*
        // scan happens after the parcel reached the delivery carrier.
        if (code.startsWith("GTMS_")) return strong(PackageStatus.OUT_FOR_DELIVERY)

        // ── Import customs (destination) ────────────────────────────────
        // CUS_TAX is DUTIES_DUE in the TOP reference; we have no separate
        // status for it, and it only happens at import.
        if (code == "CUS_TAX") return strong(PackageStatus.CUSTOMS_IMPORT)
        if (code.startsWith("CC_IM_") ||
            code.startsWith("CC_HO_") ||
            code.startsWith("CIQ_") ||
            code.startsWith("CUS_")
        ) return strong(PackageStatus.CUSTOMS_IMPORT)

        // ── Export customs (origin) ─────────────────────────────────────
        if (code.startsWith("CC_EX_")) return strong(PackageStatus.CUSTOMS_EXPORT)

        // ── In transit (line-haul, transit hubs, transit-country clearance) ─
        if (code.startsWith("LH_") ||
            code.startsWith("TD_") ||
            code.startsWith("CC_TRANS_")
        ) return strong(PackageStatus.IN_TRANSIT)

        // LAST_MILE_* is Cainiao's handover to the destination partner. It is
        // NOT last-mile in the delivery sense: LAST_MILE_HO_SUCCESS fires while
        // the parcel is still at origin waiting for a flight. Treat it as an
        // ambiguous movement scan, exactly like LH_* — [anchor] promotes it
        // once a customs milestone says the parcel crossed the border.
        if (code.startsWith("LAST_MILE_")) return strong(PackageStatus.IN_TRANSIT)

        // Any other customs scan we can't place on a side of the border.
        if (code.startsWith("CC_")) return strong(PackageStatus.CUSTOMS)

        // ── Origin processing — picked up, sorting, in warehouse ────────
        if (code.startsWith("PU_") ||
            code.startsWith("SC_") ||
            code.startsWith("GWMS_") ||
            code.startsWith("WMS_")
        ) return strong(PackageStatus.SHIPPED)

        // Cainiao overloads this one: it marks genuine in-transit scans *and*
        // pre-shipment notices ("Pre-Shipment Info Sent To …"). WEAK, so it
        // only counts once a recognised code confirms the parcel shipped.
        if (code == "COMMON_INTRANSIT") return weak(PackageStatus.IN_TRANSIT)

        // ── Last resort: read the code's own words ──────────────────────
        val guess = keywordGuess(code)
        return if (guess == PackageStatus.UNKNOWN) WEAK_UNKNOWN else weak(guess)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Resolves the newest usable scan into a displayable status.
     *
     * Returns `null` when the scan tells us nothing and there is no customs
     * milestone to place it against — the caller then falls back to
     * `progressRate`.
     */
    private fun anchor(
        latest: PackageStatus,
        sawImport: Boolean,
        sawExport: Boolean
    ): PackageStatus? = when (latest) {
        // Ambiguous "the parcel moved" scans. Cainiao emits LH_*/LAST_MILE_*
        // on both the origin-hub handoff and the destination line-haul, so
        // resolve by which customs milestones already happened:
        //   import seen -> in destination country; export seen -> mid-air.
        PackageStatus.IN_TRANSIT, PackageStatus.IN_FLIGHT -> when {
            sawImport -> PackageStatus.ARRIVING
            sawExport -> PackageStatus.IN_FLIGHT
            else -> PackageStatus.IN_TRANSIT
        }
        // A customs scan we couldn't attribute to a side of the border; an
        // earlier explicit one usually can.
        PackageStatus.CUSTOMS -> when {
            sawImport -> PackageStatus.CUSTOMS_IMPORT
            sawExport -> PackageStatus.CUSTOMS_EXPORT
            else -> PackageStatus.CUSTOMS
        }
        PackageStatus.UNKNOWN -> when {
            sawImport -> PackageStatus.ARRIVING
            sawExport -> PackageStatus.IN_FLIGHT
            else -> null
        }
        // Everything else is an unambiguous milestone — trust it as-is.
        else -> latest
    }

    /**
     * Coarse fallback used only when the trace yields nothing usable.
     * Splits `[0, 1]` into six equal buckets, one per in-flight status, in
     * pipeline order:
     *   - `0.50` → [PackageStatus.IN_TRANSIT]
     *   - `0.98` → [PackageStatus.OUT_FOR_DELIVERY] ("Local Courier")
     *
     * [PackageStatus.DELIVERED] is never inferred from progress — that needs
     * an explicit sign-for event. Returns [PackageStatus.UNKNOWN] for a null
     * or negative rate: better to admit we don't know than fabricate a stage.
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
            else -> PackageStatus.OUT_FOR_DELIVERY              // [83.3, 100%]
        }
    }

    /**
     * Infers a status from an unrecognised code's wording. Deliberately cannot
     * produce [PackageStatus.DELIVERED] — that stays gated behind an explicit
     * sign-for code, because DELIVERED also auto-files the package as received.
     */
    private fun keywordGuess(code: String): PackageStatus = when {
        code.contains("OUT_FOR_DELIVERY") ||
            code.contains("DELIVERING") ||
            code.contains("DISPATCH") -> PackageStatus.OUT_FOR_DELIVERY

        code.contains("CUSTOM") || code.contains("CLEARANCE") -> PackageStatus.CUSTOMS

        code.contains("INTRANSIT") ||
            code.contains("IN_TRANSIT") ||
            code.contains("TRANSPORT") ||
            code.contains("LINEHAUL") ||
            code.contains("DEPART") ||
            code.contains("ARRIV") ||
            code.contains("FLIGHT") -> PackageStatus.IN_TRANSIT

        code.contains("PICKUP") ||
            code.contains("PICK_UP") ||
            code.contains("COLLECT") ||
            code.contains("INBOUND") ||
            code.contains("OUTBOUND") ||
            code.contains("SORT") ||
            code.contains("WAREHOUSE") ||
            code.contains("ACCEPT") -> PackageStatus.SHIPPED

        else -> PackageStatus.UNKNOWN
    }

    /** The one thing Cainiao's coarse top-level `status` states unambiguously. */
    private fun isDeliveredApiStatus(apiStatus: String?): Boolean {
        val value = apiStatus?.trim()?.uppercase() ?: return false
        return value in DELIVERED_API_STATUSES
    }

    // ─────────────────────────────────────────────────────────────────────
    // Tables
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Statuses that act as monotonic signposts: once a trace contains one, the
     * derived status may never fall behind it. Ambiguous movement statuses
     * (IN_TRANSIT / IN_FLIGHT / CUSTOMS) are excluded on purpose — an
     * origin-hub `LH_*` scan precedes export customs, so treating it as a
     * milestone would mask the later "Export Customs" state.
     */
    private val MILESTONE_STATUSES = setOf(
        PackageStatus.ORDER_PLACED,
        PackageStatus.SHIPPED,
        PackageStatus.CUSTOMS_EXPORT,
        PackageStatus.CUSTOMS_IMPORT,
        PackageStatus.ARRIVING,
        PackageStatus.OUT_FOR_DELIVERY,
        PackageStatus.AWAITING_PICKUP,
        PackageStatus.DELIVERED
    )

    private val ADVISORY_CODES = setOf(
        // Destination carrier told a parcel is coming, while the parcel is
        // typically still sitting at origin.
        "LAST_MILE_ASN_NOTIFY"
    )

    private val FAILURE_SUFFIXES = listOf(
        "_FAILURE", "_FAILED", "_FAIL",
        "_REJECTED", "_REJECT",
        "_CANCELLED", "_CANCELED", "_CANCEL",
        "_EXCEPTION", "_ABNORMAL"
    )

    private val FAILURE_CODES = setOf(
        "FAILED", "REJECT", "REJECTED", "CANCELLED", "CANCELED",
        // A failure event despite the missing suffix: the post office could
        // not collect the parcel.
        "LH_POST_COLLECTION"
    )

    private val DELIVERED_CODES = setOf(
        "GTMS_SIGNED", "SIGNED", "SIGN", "DELIVERED",
        "GSTA_SIGN", "GSTA_SIGNED", "GSTA_BUYER_SIGN", "STA_SIGN"
    )

    private val AWAITING_PICKUP_CODES = setOf(
        "GTMS_WAIT_SELF_PICK", "GSTA_INBOUND", "GTMS_STA_SIGNED",
        "GSTA_INFORM_BUYER", "POSTMAN_POST"
    )

    private val OUT_FOR_DELIVERY_CODES = setOf(
        "GTMS_DELIVERING", "SENT_SCAN", "GTMS_RE_DELIVERING",
        "GTMS_ACCEPT", "GSTAHUB_INBOUND"
    )

    private val ORDER_PLACED_CODES = setOf(
        "CONSIGN", "OM_CONSIGN", "GTMS_ASN", "GWMS_ACCEPT"
    )

    private val DELIVERED_API_STATUSES = setOf("SIGN", "SIGNED", "DELIVERED")

    private val ADVISORY_MEANING = CodeMeaning(PackageStatus.UNKNOWN, Confidence.ADVISORY)
    private val WEAK_UNKNOWN = CodeMeaning(PackageStatus.UNKNOWN, Confidence.WEAK)
    private val STRONG_EXCEPTION = CodeMeaning(PackageStatus.EXCEPTION, Confidence.STRONG)

    private fun strong(status: PackageStatus) = CodeMeaning(status, Confidence.STRONG)
    private fun weak(status: PackageStatus) = CodeMeaning(status, Confidence.WEAK)
}
