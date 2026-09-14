package com.dmb.chantiertracker.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlanTest {

    @Test
    fun max_history_days_mirrors_the_backend_plan_limit_service() {
        assertEquals(30, Plan.FREE.maxHistoryDays())
        assertEquals(180, Plan.SEMI_FLEX.maxHistoryDays())
        assertNull(Plan.LIBERTE.maxHistoryDays())
        assertNull(Plan.UNKNOWN.maxHistoryDays())
    }
}
