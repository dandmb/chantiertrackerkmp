package com.dmb.chantiertracker.data.sync

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

fun interface Clock {
    fun nowEpochMillis(): Long
}

val SystemClock = Clock { kotlin.time.Clock.System.now().toEpochMilliseconds() }

/**
 * The backend serialises timestamps as a zoneless ISO `LocalDateTime`
 * (`2026-09-02T10:30:00`). Last-write-wins compares these against the
 * device's own epoch-millis clock, so both sides are read as UTC — the
 * comparison is only as accurate as the server/device clock agreement,
 * which is acceptable for our deliberately simple LWW rule (see ADR-20).
 */
fun parseServerTimestampMillis(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw.trim().removeSuffix("Z")
    return runCatching {
        LocalDateTime.parse(trimmed).toInstant(TimeZone.UTC).toEpochMilliseconds()
    }.getOrNull()
}
