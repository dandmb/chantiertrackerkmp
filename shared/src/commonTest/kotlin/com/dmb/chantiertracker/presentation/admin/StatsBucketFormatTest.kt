package com.dmb.chantiertracker.presentation.admin

import com.dmb.chantiertracker.domain.model.Granularity
import kotlin.test.Test
import kotlin.test.assertEquals

class StatsBucketFormatTest {

    @Test
    fun day_granularity_shows_day_and_month() {
        assertEquals("05/09", formatStatsBucket("2026-09-05", Granularity.DAY))
    }

    @Test
    fun month_granularity_shows_month_and_year() {
        assertEquals("03/2026", formatStatsBucket("2026-03-01", Granularity.MONTH))
    }

    @Test
    fun year_granularity_shows_only_the_year() {
        assertEquals("2026", formatStatsBucket("2026-01-01", Granularity.YEAR))
    }

    @Test
    fun a_malformed_bucket_is_returned_as_is() {
        assertEquals("2026-09", formatStatsBucket("2026-09", Granularity.MONTH))
        assertEquals("", formatStatsBucket("", Granularity.DAY))
    }
}
