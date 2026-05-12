package com.michlind.packagetracker.data.api

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

/**
 * Synthesizes Cainiao-shaped responses for the test mode toggled in Settings.
 * Each call picks a random "current stage" and builds a plausible trace ending
 * at that stage, with consistent `processInfo.progressRate` and
 * `progressPointList` flags. Used by `PackageRepositoryImpl.trackPackage` when
 * `MockTrackingPreferenceRepository.enabled` is true.
 */
object MockCainiaoResponseGenerator {

    private data class Stage(val actionCode: String, val desc: String, val nodeDesc: String)

    // Ordered earliest → latest along the typical China → Israel route. The
    // generator picks an index and produces a trace from stage 0 up through it.
    private val stages = listOf(
        Stage("GWMS_ACCEPT",         "Order received successfully",         "Store processing the order"),
        Stage("GWMS_OUTBOUND",       "Leave the warehouse",                 "Store processing the order"),
        Stage("PU_PICKUP_SUCCESS",   "Accepted by carrier",                 "In transit"),
        Stage("SC_INBOUND_SUCCESS",  "Inbound in sorting center",           "In transit"),
        Stage("SC_OUTBOUND_SUCCESS", "Outbound in sorting center",          "In transit"),
        Stage("LH_HO_IN_SUCCESS",    "Arrived at departure transport hub",  "In transit"),
        Stage("LH_HO_AIRLINE",       "Leaving from departure country/region", "In transit"),
        Stage("LH_DEPART",           "Left from departure country/region",  "In transit"),
        Stage("LH_ARRIVE",           "Arrived at linehaul office",          "In transit"),
        Stage("CC_IM_START",         "Import clearance start",              "At customs"),
        Stage("CC_IM_SUCCESS",       "Import customs clearance complete",   "At customs"),
        Stage("GTMS_ACCEPT",         "Received by local delivery company",  "In transit"),
        Stage("GTMS_DO_DEPART",      "Out for delivery",                    "Out for delivery"),
        Stage("SIGN",                "Signed",                              "Delivered"),
    )

    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT+8")
    }

    fun generate(trackingNumber: String): CainiaoResponse {
        // Mix the TN into the seed but also include the clock so successive
        // refreshes of the same TN can return different states — handy for
        // watching the UI react to status changes.
        val rng = Random(trackingNumber.hashCode().toLong() xor System.nanoTime())
        // Stop short of the final "SIGN" stage — the mock should never auto-
        // mark a package as delivered (which would also auto-flip it to
        // received). Limit picks to 0..lastIndex-1.
        val targetIdx = rng.nextInt(stages.size - 1)

        val now = System.currentTimeMillis()
        // Spread events backwards from "now" with a small jittered gap so the
        // trace looks like real, sparse carrier updates.
        val gapMs = 6L * 60L * 60L * 1000L  // ~6 hours
        val events = (0..targetIdx).reversed().mapIndexed { i, idx ->
            val t = now - i * (gapMs + rng.nextLong(0, gapMs))
            val stage = stages[idx]
            CainiaoTraceEvent(
                time = t,
                timeStr = timeFmt.format(Date(t)),
                desc = stage.desc,
                standerdDesc = stage.desc,
                descTitle = "Carrier note:",
                timeZone = "GMT+8",
                actionCode = stage.actionCode,
                group = TraceGroup(
                    nodeCode = null,
                    nodeDesc = stage.nodeDesc,
                    currentIconUrl = null,
                    historyIconUrl = null
                )
            )
        }

        val (statusStr, statusDesc, progressRate) = when {
            // SIGN stage is excluded above; we never produce DELIVERED.
            targetIdx >= 12                   -> Triple("DELIVERING", "Delivering", 0.85f) // out for delivery
            targetIdx >= 11                   -> Triple("DELIVERING", "Delivering", 0.7f)  // with local courier
            targetIdx >= 8                    -> Triple("DELIVERING", "Delivering", 0.5f)  // arrived destination country
            targetIdx >= 6                    -> Triple("DELIVERING", "Delivering", 0.3f)  // departed origin
            else                              -> Triple("DELIVERING", "Delivering", 0.1f)  // origin processing
        }

        // Randomly pick a 3-point or 4-point list so we exercise both shapes
        // of `progressPointList` the mapper has to handle.
        val pointList = if (rng.nextBoolean()) {
            listOf(
                ProgressPoint("Mainland China",    light = true),
                ProgressPoint("Israel",            light = progressRate >= 0.5f),
                ProgressPoint("Destination city",  light = progressRate >= 0.8f),
                ProgressPoint("Delivered",         light = progressRate >= 1.0f),
            )
        } else {
            listOf(
                ProgressPoint("Mainland China", light = true),
                ProgressPoint("Israel",         light = progressRate >= 0.5f),
                ProgressPoint("Delivered",      light = progressRate >= 1.0f),
            )
        }

        return CainiaoResponse(
            module = listOf(
                CainiaoPackageData(
                    mailNo = trackingNumber,
                    status = statusStr,
                    statusDesc = statusDesc,
                    detailList = events,
                    latestTrace = events.firstOrNull(),
                    daysNumber = "${(targetIdx + 1)}\tday(s)",
                    originCountry = "Mainland China",
                    destCountry = "Israel",
                    estimatedDeliveryTime = now + 5L * 24 * 60 * 60 * 1000,
                    processInfo = ProcessInfo(
                        progressStatus = "NORMAL",
                        progressRate = progressRate,
                        progressPointList = pointList
                    ),
                    logisticsAlert = null
                )
            ),
            success = true
        )
    }
}
