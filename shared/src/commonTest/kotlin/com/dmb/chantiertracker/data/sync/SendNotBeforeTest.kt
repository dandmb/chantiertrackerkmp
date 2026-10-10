package com.dmb.chantiertracker.data.sync

import com.dmb.chantiertracker.support.MutableClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SendNotBeforeTest {

    private val start = 1_000_000L
    private val clock = MutableClock(start)
    private val waits = SendNotBefore<String>(clock)

    private fun timeOf(key: String): Long = assertNotNull(waits.notBefore(key), "$key was told to wait")

    private fun after(delay: Duration) = clock.set(clock.nowEpochMillis() + delay.inWholeMilliseconds)

    @Test
    fun the_spacing_of_a_refused_creation_is_15_minutes_1_hour_6_hours_then_once_a_day() {
        assertEquals(listOf(15.minutes, 1.hours, 6.hours, 24.hours), REFUSED_CREATION_SPACING)
    }

    @Test
    fun a_row_nobody_asked_to_wait_may_be_sent_now() {
        assertTrue(waits.mayBeSentNow("STAGE:a"))
        assertEquals(null, waits.notBefore("STAGE:a"))
    }

    @Test
    fun a_row_told_to_wait_is_not_sent_before_its_time_and_is_sent_from_that_time_on() {
        waits.waitLonger("STAGE:a", REFUSED_CREATION_SPACING, about = "a")

        assertEquals(start + 15.minutes.inWholeMilliseconds, waits.notBefore("STAGE:a"))
        assertFalse(waits.mayBeSentNow("STAGE:a"), "just refused")
        after(15.minutes - 1.milliseconds)
        assertFalse(waits.mayBeSentNow("STAGE:a"), "one millisecond before its time")
        after(1.milliseconds)
        assertTrue(waits.mayBeSentNow("STAGE:a"), "at its time")
        after(3.hours)
        assertTrue(waits.mayBeSentNow("STAGE:a"), "and any time after")
        assertTrue(waits.mayBeSentNow("STAGE:b"), "another row never waited")
    }

    @Test
    fun each_new_wait_of_the_same_row_is_longer_until_the_last_step_which_repeats() {
        val asked = (1..7).map {
            val before = clock.nowEpochMillis()
            waits.waitLonger("STAGE:a", REFUSED_CREATION_SPACING, about = "a")
            val delay = (timeOf("STAGE:a") - before).milliseconds
            after(delay)
            delay
        }

        assertEquals(listOf(15.minutes, 1.hours, 6.hours, 24.hours, 24.hours, 24.hours, 24.hours), asked)
    }

    @Test
    fun each_row_has_its_own_progression() {
        repeat(3) { waits.waitLonger("STAGE:a", REFUSED_CREATION_SPACING, about = "a") }
        waits.waitLonger("STAGE:b", REFUSED_CREATION_SPACING, about = "b")

        assertEquals(listOf(6.hours, 15.minutes), listOf("STAGE:a", "STAGE:b").map { (timeOf(it) - start).milliseconds })
    }

    @Test
    fun a_row_asked_to_leave_at_the_next_pass_leaves_whatever_its_time_and_keeps_its_progression() {
        waits.waitLonger("STAGE:a", REFUSED_CREATION_SPACING, about = "a")

        waits.sendAtNextPass("STAGE:a")

        assertTrue(waits.mayBeSentNow("STAGE:a"), "asked to leave now")
        waits.waitLonger("STAGE:a", REFUSED_CREATION_SPACING, about = "a")
        assertEquals(1.hours, (timeOf("STAGE:a") - start).milliseconds, "refused again: the second step, not the first one again")
    }

    @Test
    fun asking_a_row_that_does_not_wait_to_leave_changes_nothing() {
        waits.sendAtNextPass("STAGE:a")

        assertTrue(waits.mayBeSentNow("STAGE:a"))
        waits.waitLonger("STAGE:a", REFUSED_CREATION_SPACING, about = "a")
        assertEquals(15.minutes, (timeOf("STAGE:a") - start).milliseconds)
    }

    @Test
    fun only_the_rows_a_change_concerns_are_asked_to_leave() {
        waits.waitLonger("STAGE:a", REFUSED_CREATION_SPACING, about = "project 1")
        waits.waitLonger("STAGE:b", REFUSED_CREATION_SPACING, about = "project 2")
        waits.waitLonger("ENTRY:c", REFUSED_CREATION_SPACING, about = "project 1")

        val someLeft = waits.sendAtNextPass { it == "project 1" }

        assertTrue(someLeft)
        assertEquals(listOf(true, false, true), listOf("STAGE:a", "STAGE:b", "ENTRY:c").map { waits.mayBeSentNow(it) })
    }

    @Test
    fun a_change_that_concerns_no_waiting_row_reports_that_nothing_leaves() {
        waits.waitLonger("STAGE:a", REFUSED_CREATION_SPACING, about = "project 1")

        assertFalse(waits.sendAtNextPass { it == "project 9" }, "no row about this project")
        after(15.minutes)
        assertFalse(waits.sendAtNextPass { it == "project 1" }, "this row is already due: nothing more to release")
        assertTrue(waits.mayBeSentNow("STAGE:a"))
    }

    @Test
    fun a_forgotten_row_starts_again_from_the_first_step() {
        repeat(3) { waits.waitLonger("STAGE:a", REFUSED_CREATION_SPACING, about = "a") }

        waits.forget("STAGE:a")

        assertTrue(waits.mayBeSentNow("STAGE:a"))
        assertEquals(null, waits.notBefore("STAGE:a"))
        assertFalse(waits.isWaiting { it == "a" })
        waits.waitLonger("STAGE:a", REFUSED_CREATION_SPACING, about = "a")
        assertEquals(15.minutes, (timeOf("STAGE:a") - start).milliseconds)
    }

    @Test
    fun a_row_can_be_told_to_wait_a_given_time_as_a_server_does_with_retry_after() {
        waits.waitAtLeast("ATTACHMENT:p", 90.seconds, about = "upload")

        assertFalse(waits.mayBeSentNow("ATTACHMENT:p"))
        after(90.seconds - 1.milliseconds)
        assertFalse(waits.mayBeSentNow("ATTACHMENT:p"))
        after(1.milliseconds)
        assertTrue(waits.mayBeSentNow("ATTACHMENT:p"))
    }

    @Test
    fun a_given_wait_never_shortens_a_longer_one_and_does_not_count_as_a_step() {
        waits.waitLonger("STAGE:a", REFUSED_CREATION_SPACING, about = "a")

        waits.waitAtLeast("STAGE:a", 30.seconds, about = "a")

        assertEquals(15.minutes, (timeOf("STAGE:a") - start).milliseconds, "the longer wait stays")
        waits.waitAtLeast("STAGE:a", 2.hours, about = "a")
        assertEquals(2.hours, (timeOf("STAGE:a") - start).milliseconds, "a longer one replaces it")
        waits.waitLonger("STAGE:a", REFUSED_CREATION_SPACING, about = "a")
        assertEquals(1.hours, (timeOf("STAGE:a") - start).milliseconds, "second refusal: second step, the given waits were not counted")
    }

    @Test
    fun it_tells_whether_a_row_of_a_kind_is_waiting() {
        assertFalse(waits.isWaiting { it == "plan" })

        waits.waitLonger("PROJECT:a", REFUSED_CREATION_SPACING, about = "plan")

        assertTrue(waits.isWaiting { it == "plan" })
        assertFalse(waits.isWaiting { it == "stock" })
        after(2.hours)
        assertTrue(waits.isWaiting { it == "plan" }, "still remembered once due, until it is sent or forgotten")
    }
}
