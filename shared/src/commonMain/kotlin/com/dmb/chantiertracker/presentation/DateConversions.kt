package com.dmb.chantiertracker.presentation

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Bridges between the app's ISO date strings (`yyyy-MM-dd`, what the API and Room
 * store) and the Material 3 date picker, which works in UTC-midnight epoch millis.
 */

fun LocalDate.toUtcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

fun utcMillisToLocalDate(millis: Long): LocalDate =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date

fun parseIsoDateOrNull(value: String): LocalDate? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    return runCatching { LocalDate.parse(trimmed) }.getOrNull()
}

fun todayInSystemZone(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
