package com.michlind.packagetracker.util

import com.michlind.packagetracker.domain.model.PackageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusMapperTest {

    /**
     * Real trace for RS1337664736Y (newest first). Israeli customs "started"
     * import clearance on the pre-filed paperwork while the parcel was still
     * in Dongguan — an hour before export clearance even began.
     */
    private val preDeclaredImport = listOf(
        "LH_HO_AIRLINE",        // Leaving from departure country/region
        "CC_EX_SUCCESS",        // Export clearance success
        "CC_EX_START",          // Export customs clearance started
        "CC_IM_START",          // Import clearance start  <- pre-declaration
        "LH_HO_IN_SUCCESS",     // Arrived at departure transport hub
        "SC_OUTBOUND_SUCCESS",
        "SC_INBOUND_SUCCESS",
        "CW_OUTBOUND",
        "CW_INBOUND",
        "CW_SIGN_IN_SUCCESS",
        "SC_OUTBOUND_SUCCESS",
        "SC_INBOUND_SUCCESS",
        "PU_PICKUP_SUCCESS"
    )

    @Test
    fun `import clearance filed before export does not mean the parcel arrived`() {
        val status = StatusMapper.deriveStatus(
            actionCodes = preDeclaredImport,
            progressRate = 0.16666667f,
            apiStatus = "DELIVERING"
        )
        assertEquals(PackageStatus.IN_FLIGHT, status)
    }

    @Test
    fun `import clearance after export counts as reaching destination customs`() {
        val landed = listOf("CC_IM_SUCCESS") + preDeclaredImport
        assertEquals(PackageStatus.CUSTOMS_IMPORT, StatusMapper.deriveStatus(landed))
    }

    @Test
    fun `line-haul scan after real import clearance reads as arriving`() {
        val moving = listOf("LAST_MILE_HO_SUCCESS", "CC_IM_SUCCESS") + preDeclaredImport
        assertEquals(PackageStatus.ARRIVING, StatusMapper.deriveStatus(moving))
    }

    @Test
    fun `import clearance is trusted when the trace has no export event`() {
        val noExport = listOf("LH_HO_AIRLINE", "CC_IM_SUCCESS", "LH_HO_IN_SUCCESS", "PU_PICKUP_SUCCESS")
        assertEquals(PackageStatus.ARRIVING, StatusMapper.deriveStatus(noExport))
    }
}
