package com.michlind.packagetracker.util

import com.michlind.packagetracker.domain.model.PackageStatus

object StatusMapper {

    /**
     * Maps the top-level status string from the Cainiao API to a [PackageStatus].
     * Falls back to action code inspection for finer granularity.
     */
    fun map(statusRaw: String?, latestActionCode: String?): PackageStatus {
        val status = statusRaw?.uppercase().orEmpty()
        return when {
            status.contains("SIGN") || status == "DELIVERED" -> PackageStatus.DELIVERED
            status == "DELIVERING" -> PackageStatus.OUT_FOR_DELIVERY
            status.contains("CUSTOMS") -> PackageStatus.CUSTOMS
            status.contains("DEPARTURE") || status == "IN_TRANSIT" -> PackageStatus.IN_TRANSIT
            status.contains("ACCEPT") || status.contains("GTMS") -> PackageStatus.SHIPPED
            status.contains("FAILED") || status.contains("RETURN") || status.contains("LOST") || status.contains("EXCEPTION") -> PackageStatus.EXCEPTION
            else -> mapActionCode(latestActionCode)
        }
    }

    fun mapActionCode(actionCode: String?): PackageStatus {
        return when (actionCode?.uppercase().orEmpty()) {
            "GWMS_ACCEPT", "GWMS_PACKAGE", "GWMS_OUTBOUND" -> PackageStatus.ORDER_PLACED
            "GTMS_ACCEPT", "CW_INBOUND", "CW_SIGN_IN_SUCCESS", "CW_OUTBOUND" -> PackageStatus.SHIPPED
            "SC_INBOUND_SUCCESS", "SC_OUTBOUND_SUCCESS",
            "LH_HO_IN_SUCCESS", "LH_HO_AIRLINE", "LH_DEPART" -> PackageStatus.IN_TRANSIT
            "CC_EX_START", "CC_EX_SUCCESS", "CC_IM_START", "CC_IM_SUCCESS" -> PackageStatus.CUSTOMS
            "SIGN" -> PackageStatus.DELIVERED
            else -> PackageStatus.UNKNOWN
        }
    }
}
