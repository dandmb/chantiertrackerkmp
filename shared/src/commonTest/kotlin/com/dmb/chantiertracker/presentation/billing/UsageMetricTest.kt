package com.dmb.chantiertracker.presentation.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UsageMetricTest {

    @Test
    fun progress_is_null_when_the_limit_is_unlimited() {
        assertNull(usageProgress(used = 4, limit = null))
    }

    @Test
    fun progress_is_the_used_over_limit_ratio() {
        assertEquals(0.5f, usageProgress(used = 1, limit = 2))
        assertEquals(1f, usageProgress(used = 3, limit = 3))
    }

    @Test
    fun progress_is_capped_at_1_even_over_the_limit() {
        assertEquals(1f, usageProgress(used = 9, limit = 3))
    }

    @Test
    fun a_zero_or_negative_limit_is_treated_as_no_progress_to_show() {
        assertNull(usageProgress(used = 0, limit = 0))
    }

    @Test
    fun at_limit_is_true_only_once_used_reaches_the_limit() {
        assertFalse(usageAtLimit(used = 2, limit = 3))
        assertTrue(usageAtLimit(used = 3, limit = 3))
        assertTrue(usageAtLimit(used = 4, limit = 3))
    }

    @Test
    fun at_limit_is_always_false_when_unlimited() {
        assertFalse(usageAtLimit(used = 1_000, limit = null))
    }
}
