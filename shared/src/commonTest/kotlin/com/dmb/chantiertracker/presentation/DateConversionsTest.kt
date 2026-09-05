package com.dmb.chantiertracker.presentation

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DateConversionsTest {

    @Test
    fun iso_string_round_trips_through_utc_millis() {
        val date = LocalDate(2026, 9, 4)
        val millis = date.toUtcMillis()
        assertEquals(date, utcMillisToLocalDate(millis))
        assertEquals("2026-09-04", utcMillisToLocalDate(millis).toString())
    }

    @Test
    fun utc_millis_is_midnight_utc_of_that_day() {
        // 2026-01-01T00:00:00Z
        assertEquals(1_767_225_600_000L, LocalDate(2026, 1, 1).toUtcMillis())
    }

    @Test
    fun parse_iso_date_accepts_valid_and_rejects_the_rest() {
        assertEquals(LocalDate(2026, 12, 31), parseIsoDateOrNull(" 2026-12-31 "))
        assertNull(parseIsoDateOrNull(""))
        assertNull(parseIsoDateOrNull("31/12/2026"))
        assertNull(parseIsoDateOrNull("2026-13-01"))
    }
}
