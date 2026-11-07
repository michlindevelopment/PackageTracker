package com.michlind.packagetracker.util

import java.util.Currency
import java.util.Locale

/**
 * Price helpers shared by the detail screen, the edit form and statistics.
 *
 * Prices are stored as the amount plus the currency token exactly as
 * AliExpress printed it ("₪", "US $", "€"). [canonical] collapses the
 * different spellings of one currency to a single code so totals can be
 * grouped; the raw token is kept so nothing is lost.
 */
object Money {

    // Spellings seen on order cards, lower-cased with spaces removed.
    private val CODE_BY_TOKEN = mapOf(
        "₪" to "ILS", "ils" to "ILS", "nis" to "ILS",
        "$" to "USD", "us$" to "USD", "usd" to "USD",
        "€" to "EUR", "eur" to "EUR",
        "£" to "GBP", "gbp" to "GBP"
    )

    private val SYMBOL_BY_CODE = mapOf(
        "ILS" to "₪", "USD" to "$", "EUR" to "€", "GBP" to "£"
    )

    /** One code per currency ("₪", "ILS" and "NIS" are all "ILS"); "" if unknown. */
    fun canonical(token: String?): String {
        val t = token.orEmpty().trim()
        if (t.isEmpty()) return ""
        val key = t.lowercase(Locale.ROOT).replace(" ", "").replace(" ", "")
        return CODE_BY_TOKEN[key] ?: t.uppercase(Locale.ROOT)
    }

    /** Display symbol for a currency token, e.g. "₪"; an unknown one is shown as its code. */
    fun symbolFor(token: String?): String {
        val code = canonical(token)
        return SYMBOL_BY_CODE[code] ?: code
    }

    /** "₪1,234.50", or "CHF 12.00" when the currency has no symbol. */
    fun format(amount: Double, token: String?): String {
        val symbol = symbolFor(token)
        val number = String.format(Locale.getDefault(), "%,.2f", amount)
        return when {
            symbol.isEmpty() -> number
            symbol.last().isLetter() -> "$symbol $number"
            else -> "$symbol$number"
        }
    }

    /** Short form for chart labels: "950", "1.2k", "15k". */
    fun formatCompact(amount: Double): String = when {
        amount >= 10_000 -> "${(amount / 1000).toInt()}k"
        amount >= 1_000 -> String.format(Locale.US, "%.1fk", amount / 1000)
        amount >= 100 -> "${amount.toInt()}"
        else -> String.format(Locale.US, "%.0f", amount)
    }

    /** Amount in plain editable form, always with two decimals and a dot: "108.40". */
    fun plain(amount: Double): String = String.format(Locale.US, "%.2f", amount)

    /**
     * Reads a user-typed amount. Accepts "108.40", "108,40", "1,234.50" and
     * "1.234,50": whichever of dot or comma comes last is the decimal mark,
     * and a lone comma or dot followed by exactly three digits is thousands.
     * Returns null for blank or unparseable input.
     */
    fun parseAmount(text: String): Double? {
        val t = text.trim().replace(" ", "").replace(" ", "")
        if (t.isEmpty() || t.any { !it.isDigit() && it != '.' && it != ',' }) return null
        val lastDot = t.lastIndexOf('.')
        val lastComma = t.lastIndexOf(',')
        val decimalAt = when {
            lastDot >= 0 && lastComma >= 0 -> maxOf(lastDot, lastComma)
            lastComma >= 0 -> if (t.length - lastComma - 1 == 3) -1 else lastComma
            lastDot >= 0 -> if (t.length - lastDot - 1 == 3 && t.count { it == '.' } > 1) -1 else lastDot
            else -> -1
        }
        val digits = if (decimalAt < 0) {
            t.filter { it.isDigit() }
        } else {
            t.substring(0, decimalAt).filter { it.isDigit() } + "." +
                t.substring(decimalAt + 1).filter { it.isDigit() }
        }
        return digits.toDoubleOrNull()?.takeIf { it >= 0.0 }
    }

    /** Currency to assume for a hand-typed price: the phone's own, else USD. */
    fun defaultCurrencyCode(): String =
        runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrDefault("USD")
}
