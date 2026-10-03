package com.dmb.chantiertracker.domain.model

import com.dmb.chantiertracker.support.founderOnFreeEntitlements
import com.dmb.chantiertracker.support.ownerEntitlements
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProjectOwnerEntitlementsTest {

    private fun detail(ownerPlan: Plan?, entitlements: ProjectOwnerEntitlements? = null) = ProjectDetail(
        localId = "p1", name = "Villa", description = null, location = null,
        currency = "EUR", timezone = "Europe/Paris", status = ProjectStatus.IN_PROGRESS,
        ownerId = 1L, ownerPlan = ownerPlan, ownerEntitlements = entitlements,
    )

    private val founderOnFree = founderOnFreeEntitlements

    // The whole point of ADR-66: the plan alone says FREE, the server says otherwise.
    @Test
    fun a_founder_owner_on_the_free_plan_can_export_and_keeps_six_months_of_history() {
        val project = detail(Plan.FREE, founderOnFree)

        assertEquals(true, project.ownerCanExportPdf())
        assertEquals(180, project.ownerMaxHistoryDays())
    }

    @Test
    fun the_server_answer_wins_over_the_static_plan_mirror_in_both_directions() {
        val stricterThanThePlanSuggests = ownerEntitlements(isFounder = false, canExportPdf = false, maxHistoryDays = 30)

        val project = detail(Plan.SEMI_FLEX, stricterThanThePlanSuggests)

        assertEquals(false, project.ownerCanExportPdf())
        assertEquals(30, project.ownerMaxHistoryDays())
    }

    @Test
    fun a_null_history_window_sent_by_the_server_means_unlimited_not_a_fallback_on_the_plan() {
        val unlimited = ownerEntitlements(isFounder = false, canExportPdf = true, maxHistoryDays = null)

        assertNull(detail(Plan.FREE, unlimited).ownerMaxHistoryDays())
    }

    @Test
    fun without_server_entitlements_the_plan_mirror_is_the_fallback() {
        assertEquals(false, detail(Plan.FREE).ownerCanExportPdf())
        assertEquals(30, detail(Plan.FREE).ownerMaxHistoryDays())
        assertEquals(true, detail(Plan.SEMI_FLEX).ownerCanExportPdf())
        assertEquals(180, detail(Plan.SEMI_FLEX).ownerMaxHistoryDays())
        assertEquals(true, detail(Plan.LIBERTE).ownerCanExportPdf())
        assertNull(detail(Plan.LIBERTE).ownerMaxHistoryDays())
    }

    @Test
    fun nothing_is_known_before_the_first_detail_pull() {
        assertNull(detail(ownerPlan = null).ownerCanExportPdf())
        assertNull(detail(ownerPlan = null).ownerMaxHistoryDays())
        assertNull(detail(ownerPlan = null).ownerMaxSupervisorsPerProject(), "no cap known: the invite form fails open")
        assertEquals(0, detail(ownerPlan = null).ownerMaxVideos(), "no video affordance until something is known")
        assertNull(detail(ownerPlan = null).ownerMaxVideoDurationSeconds())
    }

    // Lot 2 — the three limits a founder on FREE was still denied.
    @Test
    fun a_founder_owner_on_the_free_plan_gets_videos_and_three_supervisors() {
        val project = detail(Plan.FREE, founderOnFreeEntitlements)

        assertEquals(5, project.ownerMaxVideos())
        assertEquals(120, project.ownerMaxVideoDurationSeconds())
        assertEquals(3, project.ownerMaxSupervisorsPerProject())
    }

    @Test
    fun without_server_entitlements_video_and_supervisor_limits_fall_back_on_the_plan() {
        assertEquals(0, detail(Plan.FREE).ownerMaxVideos())
        assertEquals(0, detail(Plan.FREE).ownerMaxVideoDurationSeconds())
        assertEquals(1, detail(Plan.FREE).ownerMaxSupervisorsPerProject())
        assertEquals(5, detail(Plan.SEMI_FLEX).ownerMaxVideos())
        assertEquals(120, detail(Plan.SEMI_FLEX).ownerMaxVideoDurationSeconds())
        assertEquals(3, detail(Plan.SEMI_FLEX).ownerMaxSupervisorsPerProject())
        assertEquals(20, detail(Plan.LIBERTE).ownerMaxVideos())
        assertEquals(300, detail(Plan.LIBERTE).ownerMaxVideoDurationSeconds())
        assertNull(detail(Plan.LIBERTE).ownerMaxSupervisorsPerProject())
    }

    @Test
    fun an_unlimited_supervisor_cap_sent_by_the_server_is_not_a_fallback_on_the_plan() {
        val unlimited = ownerEntitlements(maxSupervisorsPerProject = null)

        assertNull(detail(Plan.FREE, unlimited).ownerMaxSupervisorsPerProject())
    }

    @Test
    fun the_server_video_allowance_wins_over_the_plan_mirror() {
        val noVideoDespiteThePlan = ownerEntitlements(maxVideos = 0, maxVideoDurationSeconds = 0)

        assertEquals(0, detail(Plan.LIBERTE, noVideoDespiteThePlan).ownerMaxVideos())
        assertEquals(0, detail(Plan.LIBERTE, noVideoDespiteThePlan).ownerMaxVideoDurationSeconds())
    }
}
