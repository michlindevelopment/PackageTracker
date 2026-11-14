package com.michlind.packagetracker.util

import com.michlind.packagetracker.domain.model.PackageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    /**
     * Real trace for RS1352838076Y as it stood on Nov 14 2026, newest first.
     * Import clearance "started" at 08:15, seven hours before the first export
     * event. With no export event in the trace yet, the old mapper had nothing
     * to compare it against and showed "Import Customs" for the whole gap.
     */
    private val preDeclaredBeforeAnyExport = listOf(
        "CC_IM_START",              // Import clearance start  <- pre-declaration
        "LH_HO_IN_SUCCESS",         // Arrived at departure transport hub
        "SC_OUTBOUND_SUCCESS",
        "SC_TRANS_INBOUND_SUCCESS",
        "SC_TRANS_OUTBOUND_SUCCESS",
        "SC_INBOUND_SUCCESS",
        "PU_PICKUP_SUCCESS"
    )

    @Test
    fun `import clearance start before any export event does not mean the parcel arrived`() {
        val status = StatusMapper.deriveStatus(
            actionCodes = preDeclaredBeforeAnyExport,
            progressRate = 0.16666667f,
            apiStatus = "CLEAR_CUSTOMS"
        )
        assertEquals(PackageStatus.IN_TRANSIT, status)
    }

    @Test
    fun `export clearance following an import pre-declaration reads as export customs`() {
        val status = StatusMapper.deriveStatus(
            actionCodes = listOf("CC_EX_SUCCESS", "CC_EX_START") + preDeclaredBeforeAnyExport,
            progressRate = 0.16666667f,
            apiStatus = "CLEAR_CUSTOMS"
        )
        assertEquals(PackageStatus.CUSTOMS_EXPORT, status)
    }

    @Test
    fun `import clearance start alone never raises status above shipped`() {
        val status = StatusMapper.deriveStatus(listOf("CC_IM_START", "PU_PICKUP_SUCCESS"))
        assertEquals(PackageStatus.SHIPPED, status)
    }

    @Test
    fun `only the import clearance start is advisory`() {
        assertTrue(StatusMapper.isAdvisory("CC_IM_START"))
        assertFalse(StatusMapper.isAdvisory("CC_IM_SUCCESS"))
        assertFalse(StatusMapper.isAdvisory("CC_HO_IN_SUCCESS"))
        assertFalse(StatusMapper.isAdvisory("CC_EX_START"))
    }

    @Test
    fun `arrival at destination customs still reads as import customs after export`() {
        // Real destination-side events from RS1337664736Y.
        val arrived = listOf("CC_HO_IN_SUCCESS", "LH_ARRIVE", "LH_DEPART", "LH_HO_AIRLINE") +
            preDeclaredImport
        assertEquals(PackageStatus.CUSTOMS_IMPORT, StatusMapper.deriveStatus(arrived))
    }
}
