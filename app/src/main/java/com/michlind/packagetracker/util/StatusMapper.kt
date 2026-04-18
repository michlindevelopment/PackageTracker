package com.michlind.packagetracker.util

import com.michlind.packagetracker.domain.model.PackageStatus

object StatusMapper {

    fun map(statusRaw: String?, latestActionCode: String?): PackageStatus {
        val status = statusRaw?.uppercase().orEmpty()
        return when {
            status.contains("SIGN") || status == "DELIVERED" -> PackageStatus.DELIVERED
            status == "DELIVERING" -> {
                when (latestActionCode?.uppercase()) {
                    "GSTA_INFORM_BUYER", "GTMS_STA_SIGNED" -> PackageStatus.AWAITING_PICKUP
                    else -> PackageStatus.OUT_FOR_DELIVERY
                }
            }
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
            "LH_HO_IN_SUCCESS", "LH_DEPART", "LH_ARRIVE" -> PackageStatus.IN_TRANSIT
            "LH_HO_AIRLINE", "CC_EX_START", "CC_EX_SUCCESS" -> PackageStatus.CUSTOMS_EXPORT
            "CC_HO_IN_SUCCESS", "CC_HO_OUT_SUCCESS",
            "CC_IM_START", "CC_IM_SUCCESS" -> PackageStatus.CUSTOMS_IMPORT
            "GSTA_INFORM_BUYER", "GTMS_STA_SIGNED" -> PackageStatus.AWAITING_PICKUP
            "SIGN" -> PackageStatus.DELIVERED
            else -> PackageStatus.UNKNOWN
        }
    }
}
