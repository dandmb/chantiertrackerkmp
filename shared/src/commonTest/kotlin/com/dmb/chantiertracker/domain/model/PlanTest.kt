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

    @Test
    fun a_founder_on_the_free_plan_reads_the_history_window_the_server_combined() {
        val usage = PlanUsage(Plan.FREE, projectsLimit = 3, isFounder = true, historyDaysLimit = 180, historyDaysLimitKnown = true)

        assertEquals(180, usage.maxHistoryDays())
    }

    @Test
    fun an_unlimited_history_window_sent_by_the_server_is_not_replaced_by_the_plan_mirror() {
        val usage = PlanUsage(Plan.FREE, projectsLimit = 1, historyDaysLimit = null, historyDaysLimitKnown = true)

        assertNull(usage.maxHistoryDays())
    }

    @Test
    fun without_a_history_window_from_the_server_the_plan_mirror_is_the_fallback() {
        assertEquals(30, PlanUsage(Plan.FREE, projectsLimit = 1).maxHistoryDays())
        assertEquals(180, PlanUsage(Plan.SEMI_FLEX, projectsLimit = 3).maxHistoryDays())
        assertNull(PlanUsage(Plan.LIBERTE, projectsLimit = null).maxHistoryDays())
    }

    @Test
    fun pdf_export_is_a_paid_tier_capability_of_the_plan_alone() {
        assertEquals(false, Plan.FREE.canExportPdf())
        assertEquals(true, Plan.SEMI_FLEX.canExportPdf())
        assertEquals(true, Plan.LIBERTE.canExportPdf())
        assertEquals(false, Plan.UNKNOWN.canExportPdf())
    }
}
