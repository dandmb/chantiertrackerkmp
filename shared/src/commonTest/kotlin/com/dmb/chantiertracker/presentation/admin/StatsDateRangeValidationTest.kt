package com.dmb.chantiertracker.presentation.admin

import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.admin_stats_date_range_future
import com.dmb.chantiertracker.resources.admin_stats_date_range_order
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatsDateRangeValidationTest {

    private val today = "2026-09-13"

    @Test
    fun both_blank_is_valid() {
        assertNull(validateStatsDateRange(from = "", to = "", today = today))
    }

    @Test
    fun an_end_date_in_the_future_is_rejected() {
        assertEquals(
            Res.string.admin_stats_date_range_future,
            validateStatsDateRange(from = "", to = "2026-09-14", today = today),
        )
    }

    @Test
    fun an_end_date_of_today_is_allowed() {
        assertNull(validateStatsDateRange(from = "", to = today, today = today))
    }

    @Test
    fun a_start_date_after_the_end_date_is_rejected() {
        assertEquals(
            Res.string.admin_stats_date_range_order,
            validateStatsDateRange(from = "2026-09-10", to = "2026-09-01", today = today),
        )
    }

    @Test
    fun equal_start_and_end_dates_are_allowed() {
        assertNull(validateStatsDateRange(from = "2026-09-01", to = "2026-09-01", today = today))
    }

    @Test
    fun a_start_date_alone_is_never_rejected() {
        assertNull(validateStatsDateRange(from = "2099-01-01", to = "", today = today))
    }

    @Test
    fun the_future_end_date_check_takes_priority_over_the_order_check() {
        // from > to AND to is in the future — the backend checks "to" first
        // (AdminStatsService.validateDateRange); mirrored here so the message
        // shown always matches what the server would have said.
        assertEquals(
            Res.string.admin_stats_date_range_future,
            validateStatsDateRange(from = "2026-09-20", to = "2026-09-15", today = today),
        )
    }
}
