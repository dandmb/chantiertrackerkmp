package com.dmb.chantiertracker.presentation.logs

import com.dmb.chantiertracker.domain.model.Plan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VideoLimitTest {

    @Test
    fun the_add_video_affordance_is_off_for_free_and_unknown_plans() {
        assertFalse(VideoLimit.canAdd(Plan.FREE))
        assertFalse(VideoLimit.canAdd(Plan.UNKNOWN))
        assertFalse(VideoLimit.canAdd(null))
        assertTrue(VideoLimit.canAdd(Plan.SEMI_FLEX))
        assertTrue(VideoLimit.canAdd(Plan.LIBERTE))
    }

    @Test
    fun a_clip_within_the_plan_limit_passes() {
        assertIs<VideoDurationCheck.Ok>(VideoLimit.check(Plan.SEMI_FLEX, durationSeconds = 90.0))
        assertIs<VideoDurationCheck.Ok>(VideoLimit.check(Plan.LIBERTE, durationSeconds = 299.0))
    }

    @Test
    fun a_clip_over_the_semi_flex_limit_is_flagged_with_an_upgrade_hint() {
        val result = VideoLimit.check(Plan.SEMI_FLEX, durationSeconds = 125.0)
        assertIs<VideoDurationCheck.TooLong>(result)
        assertEquals("2 min 05 s", result.actual)
        assertEquals("2 min 00 s", result.limit)
        assertTrue(result.hasUpgrade)
    }

    @Test
    fun a_clip_over_the_liberte_limit_is_flagged_without_an_upgrade_hint() {
        val result = VideoLimit.check(Plan.LIBERTE, durationSeconds = 400.0)
        assertIs<VideoDurationCheck.TooLong>(result)
        assertFalse(result.hasUpgrade, "LIBERTE is the top tier — no 'upgrade' wording")
    }

    @Test
    fun the_duration_is_rounded_up_before_comparison_like_ffprobe() {
        // 119.4 s → ffprobe counts it as 120 s → exactly at the SEMI_FLEX limit → OK.
        assertIs<VideoDurationCheck.Ok>(VideoLimit.check(Plan.SEMI_FLEX, durationSeconds = 119.4))
        assertIs<VideoDurationCheck.TooLong>(VideoLimit.check(Plan.SEMI_FLEX, durationSeconds = 120.1))
    }

    @Test
    fun an_unprobed_duration_or_unknown_plan_passes_the_pre_check() {
        assertIs<VideoDurationCheck.Ok>(VideoLimit.check(Plan.SEMI_FLEX, durationSeconds = null))
        assertIs<VideoDurationCheck.Ok>(VideoLimit.check(null, durationSeconds = 9999.0))
    }

    @Test
    fun duration_formatting_matches_the_backend() {
        assertEquals("45 s", VideoLimit.formatDuration(45))
        assertEquals("1 min 05 s", VideoLimit.formatDuration(65))
        assertEquals("2 min 00 s", VideoLimit.formatDuration(120))
    }
}
