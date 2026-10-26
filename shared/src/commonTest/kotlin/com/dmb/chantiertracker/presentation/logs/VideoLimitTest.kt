package com.dmb.chantiertracker.presentation.logs

import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectOwnerEntitlements
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.support.founderOnFreeEntitlements
import com.dmb.chantiertracker.support.ownerEntitlements
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoLimitTest {

    private fun limitsOf(ownerPlan: Plan?, entitlements: ProjectOwnerEntitlements? = null) = ProjectDetail(
        localId = "p1", name = "Villa", description = null, location = null,
        currency = "EUR", timezone = "Europe/Paris", status = ProjectStatus.IN_PROGRESS,
        ownerId = 1L, ownerPlan = ownerPlan, ownerEntitlements = entitlements,
    ).ownerVideoLimits()

    // ---- fallback on the plan alone (backend that sends no entitlements)

    @Test
    fun the_add_video_affordance_is_off_for_free_and_unknown_plans() {
        assertFalse(VideoLimit.canAdd(limitsOf(Plan.FREE)))
        assertFalse(VideoLimit.canAdd(limitsOf(Plan.UNKNOWN)))
        assertFalse(VideoLimit.canAdd(limitsOf(null)))
        assertFalse(VideoLimit.canAdd(null), "no project observed yet")
        assertTrue(VideoLimit.canAdd(limitsOf(Plan.SEMI_FLEX)))
        assertTrue(VideoLimit.canAdd(limitsOf(Plan.LIBERTE)))
    }

    @Test
    fun a_clip_within_the_plan_limit_passes() {
        assertIs<VideoDurationCheck.Ok>(VideoLimit.check(limitsOf(Plan.SEMI_FLEX), durationSeconds = 90.0))
        assertIs<VideoDurationCheck.Ok>(VideoLimit.check(limitsOf(Plan.LIBERTE), durationSeconds = 299.0))
    }

    @Test
    fun a_clip_over_the_semi_flex_limit_is_flagged_with_an_upgrade_hint() {
        val result = VideoLimit.check(limitsOf(Plan.SEMI_FLEX), durationSeconds = 125.0)
        assertIs<VideoDurationCheck.TooLong>(result)
        assertEquals("2 min 05 s", result.actual)
        assertEquals("2 min 00 s", result.limit)
        assertTrue(result.hasUpgrade)
    }

    @Test
    fun a_clip_over_the_liberte_limit_is_flagged_without_an_upgrade_hint() {
        val result = VideoLimit.check(limitsOf(Plan.LIBERTE), durationSeconds = 400.0)
        assertIs<VideoDurationCheck.TooLong>(result)
        assertFalse(result.hasUpgrade, "LIBERTE is the top tier — no 'upgrade' wording")
    }

    @Test
    fun the_duration_is_rounded_up_before_comparison_like_ffprobe() {
        // 119.4 s → ffprobe counts it as 120 s → exactly at the SEMI_FLEX limit → OK.
        assertIs<VideoDurationCheck.Ok>(VideoLimit.check(limitsOf(Plan.SEMI_FLEX), durationSeconds = 119.4))
        assertIs<VideoDurationCheck.TooLong>(VideoLimit.check(limitsOf(Plan.SEMI_FLEX), durationSeconds = 120.1))
    }

    @Test
    fun an_unprobed_duration_or_unknown_plan_passes_the_pre_check() {
        assertIs<VideoDurationCheck.Ok>(VideoLimit.check(limitsOf(Plan.SEMI_FLEX), durationSeconds = null))
        assertIs<VideoDurationCheck.Ok>(VideoLimit.check(limitsOf(null), durationSeconds = 9999.0))
        assertIs<VideoDurationCheck.Ok>(VideoLimit.check(null, durationSeconds = 9999.0))
        assertNull(limitsOf(null).maxDurationSeconds)
    }

    // ---- ADR-66 lot 2 — the owner's allowance as the backend combined it

    @Test
    fun a_founder_owner_on_the_free_plan_gets_the_add_video_affordance() {
        val limits = limitsOf(Plan.FREE, founderOnFreeEntitlements)

        assertTrue(VideoLimit.canAdd(limits))
        assertEquals(5, limits.maxVideos)
        assertEquals(120, limits.maxDurationSeconds)
    }

    @Test
    fun a_founder_owner_on_the_free_plan_is_pre_checked_against_two_minutes() {
        val limits = limitsOf(Plan.FREE, founderOnFreeEntitlements)

        assertIs<VideoDurationCheck.Ok>(VideoLimit.check(limits, durationSeconds = 119.4))
        val tooLong = VideoLimit.check(limits, durationSeconds = 125.0)
        assertIs<VideoDurationCheck.TooLong>(tooLong)
        assertEquals("2 min 00 s", tooLong.limit)
        assertTrue(tooLong.hasUpgrade, "a paid tier above still allows longer clips")
    }

    @Test
    fun the_server_allowance_wins_over_what_the_plan_alone_would_allow() {
        val noVideo = limitsOf(Plan.SEMI_FLEX, ownerEntitlements(maxVideos = 0, maxVideoDurationSeconds = 0))

        assertFalse(VideoLimit.canAdd(noVideo))
    }

    @Test
    fun a_founder_owner_who_upgraded_to_liberte_gets_the_liberte_duration_without_upgrade_wording() {
        val limits = limitsOf(
            Plan.LIBERTE,
            ownerEntitlements(isFounder = true, canExportPdf = true, maxHistoryDays = null, maxVideos = 20,
                maxVideoDurationSeconds = 300, maxSupervisorsPerProject = null),
        )

        assertIs<VideoDurationCheck.Ok>(VideoLimit.check(limits, durationSeconds = 299.0))
        val tooLong = VideoLimit.check(limits, durationSeconds = 301.0)
        assertIs<VideoDurationCheck.TooLong>(tooLong)
        assertFalse(tooLong.hasUpgrade)
    }

    @Test
    fun duration_formatting_matches_the_backend() {
        assertEquals("45 s", VideoLimit.formatDuration(45))
        assertEquals("1 min 05 s", VideoLimit.formatDuration(65))
        assertEquals("2 min 00 s", VideoLimit.formatDuration(120))
    }
}
