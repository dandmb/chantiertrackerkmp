package com.dmb.chantiertracker.presentation.format

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormatTest {

    @Test
    fun money_groups_thousands_forces_two_decimals_and_appends_the_currency_code() {
        assertEquals("18 000,00 EUR", formatMoney(18000.0, "EUR"))
        assertEquals("1 234 567,50 XAF", formatMoney(1234567.5, "XAF"))
        assertEquals("0,00 USD", formatMoney(0.0, "USD"))
    }

    @Test
    fun money_without_a_currency_drops_the_code_like_the_web_null_branch() {
        assertEquals("18 000,00", formatMoney(18000.0, null))
        assertEquals("18 000,00", formatMoney(18000.0, "  "))
    }

    @Test
    fun bare_amount_shows_decimals_only_when_present() {
        assertEquals("18 000", formatAmount(18000.0))
        assertEquals("18 000,50", formatAmount(18000.5))
        assertEquals("-1 200,00", formatAmount(-1200.0, decimals = true))
    }
}
