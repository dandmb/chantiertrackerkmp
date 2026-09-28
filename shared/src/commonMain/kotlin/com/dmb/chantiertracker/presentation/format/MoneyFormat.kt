package com.dmb.chantiertracker.presentation.format

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Amount + currency, mirroring the web `formatCurrency` (`currencyDisplay: 'code'`):
 * space-grouped thousands, always two decimals, the currency code appended, and
 * the code dropped when the project has no currency set (the web null branch).
 * Locale formatting stays light: the cross-platform requirement is the content,
 * not exact glyphs.
 */
fun formatMoney(amount: Double, currency: String?): String {
    val number = formatAmount(amount, decimals = true)
    return if (currency.isNullOrBlank()) number else "$number $currency"
}

fun formatAmount(amount: Double, decimals: Boolean = false): String {
    val cents = (abs(amount) * 100).roundToLong()
    val whole = cents / 100
    val fraction = cents % 100
    val grouped = whole.toString()
        .reversed()
        .chunked(3)
        .joinToString(" ")
        .reversed()
    val body = if (decimals || fraction != 0L) {
        "$grouped,${fraction.toString().padStart(2, '0')}"
    } else {
        grouped
    }
    return if (amount < 0) "-$body" else body
}
