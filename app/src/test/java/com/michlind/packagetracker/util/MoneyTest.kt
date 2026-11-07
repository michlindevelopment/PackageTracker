package com.michlind.packagetracker.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

class MoneyTest {

    private lateinit var originalLocale: Locale

    // format() follows the default locale; pin it so the expectations hold on any machine.
    @Before
    fun pinLocale() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun differentSpellingsOfOneCurrencyShareACode() {
        assertEquals("ILS", Money.canonical("₪"))
        assertEquals("ILS", Money.canonical("ILS"))
        assertEquals("ILS", Money.canonical("nis"))
        assertEquals("USD", Money.canonical("US $"))
        assertEquals("USD", Money.canonical("$"))
        assertEquals("USD", Money.canonical("usd"))
        assertEquals("EUR", Money.canonical("€"))
    }

    @Test
    fun unknownCurrencyKeepsItsOwnCode() {
        assertEquals("CHF", Money.canonical("chf"))
        assertEquals("", Money.canonical("  "))
        assertEquals("", Money.canonical(null))
    }

    @Test
    fun symbolsFollowTheCode() {
        assertEquals("₪", Money.symbolFor("ILS"))
        assertEquals("$", Money.symbolFor("US $"))
        assertEquals("CHF", Money.symbolFor("CHF"))
    }

    @Test
    fun formatsWithSymbolOrCode() {
        assertEquals("₪108.40", Money.format(108.4, "₪"))
        assertEquals("$1,234.50", Money.format(1234.5, "US $"))
        assertEquals("CHF 12.00", Money.format(12.0, "CHF"))
        assertEquals("5.00", Money.format(5.0, null))
    }

    @Test
    fun compactFormatForChartLabels() {
        assertEquals("42", Money.formatCompact(42.0))
        assertEquals("950", Money.formatCompact(950.0))
        assertEquals("1.2k", Money.formatCompact(1234.0))
        assertEquals("15k", Money.formatCompact(15_000.0))
    }

    @Test
    fun plainAlwaysHasTwoDecimalsAndADot() {
        assertEquals("108.40", Money.plain(108.4))
        assertEquals("12.00", Money.plain(12.0))
    }

    @Test
    fun parsesTypedAmounts() {
        assertEquals(108.4, Money.parseAmount("108.40")!!, 0.0001)
        assertEquals(108.4, Money.parseAmount("108,40")!!, 0.0001)
        assertEquals(1234.5, Money.parseAmount("1,234.50")!!, 0.0001)
        assertEquals(1234.5, Money.parseAmount("1.234,50")!!, 0.0001)
        assertEquals(1234.0, Money.parseAmount("1,234")!!, 0.0001)
        assertEquals(12.0, Money.parseAmount("12")!!, 0.0001)
        assertEquals(12.0, Money.parseAmount(" 12 ")!!, 0.0001)
    }

    @Test
    fun rejectsBlankAndNonNumericInput() {
        assertNull(Money.parseAmount(""))
        assertNull(Money.parseAmount("   "))
        assertNull(Money.parseAmount("abc"))
        assertNull(Money.parseAmount("12 usd"))
    }
}
