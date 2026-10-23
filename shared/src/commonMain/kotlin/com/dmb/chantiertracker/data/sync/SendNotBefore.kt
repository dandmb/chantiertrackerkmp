package com.dmb.chantiertracker.data.sync

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val REFUSED_CREATION_SPACING: List<Duration> = listOf(15.minutes, 1.hours, 6.hours, 24.hours)

val SEND_WITHOUT_ANSWER_SPACING: List<Duration> = REFUSED_CREATION_SPACING

val RATE_LIMIT_WAIT_WITHOUT_RETRY_AFTER: Duration = 1.minutes
val CREATION_IN_PROGRESS_WAIT_WITHOUT_RETRY_AFTER: Duration = 10.seconds

class SendNotBefore<A>(private val clock: Clock) {

    private class Wait<A>(val notBefore: Long, val stepsTaken: Int, val about: A)

    private val waits = mutableMapOf<String, Wait<A>>()

    fun mayBeSentNow(key: String): Boolean = isDue(waits[key])

    fun notBefore(key: String): Long? = waits[key]?.notBefore

    fun about(key: String): A? = waits[key]?.about

    fun waitAtLeast(key: String, delay: Duration, about: A) {
        val current = waits[key]
        val asked = clock.nowEpochMillis() + delay.inWholeMilliseconds
        waits[key] = Wait(maxOf(asked, current?.notBefore ?: asked), current?.stepsTaken ?: 0, about)
    }

    fun waitLonger(key: String, spacing: List<Duration>, about: A) {
        val stepsTaken = waits[key]?.stepsTaken ?: 0
        val delay = spacing[minOf(stepsTaken, spacing.lastIndex)]
        waits[key] = Wait(clock.nowEpochMillis() + delay.inWholeMilliseconds, stepsTaken + 1, about)
    }

    fun sendAtNextPass(key: String) {
        val current = waits[key] ?: return
        waits[key] = Wait(SEND_NOW, current.stepsTaken, current.about)
    }

    fun sendAtNextPass(concerned: (A) -> Boolean): Boolean {
        val released = waits.filterValues { !isDue(it) && concerned(it.about) }.keys
        released.forEach { sendAtNextPass(it) }
        return released.isNotEmpty()
    }

    fun forget(key: String) {
        waits.remove(key)
    }

    fun isWaiting(concerned: (A) -> Boolean): Boolean = waits.values.any { concerned(it.about) }

    fun soonestStillToCome(concerned: (A) -> Boolean): Long? =
        waits.values.filter { !isDue(it) && concerned(it.about) }.minOfOrNull { it.notBefore }

    private fun isDue(wait: Wait<A>?): Boolean = wait == null || wait.notBefore <= clock.nowEpochMillis()
}

private const val SEND_NOW = 0L
