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

/**
 * Today's date in the given IANA timezone — the project's own, never the
 * device's or the server's. Mirrors the web's `today(timezone)`: day-boundary
 * rules (what a supervisor can still edit) are defined by the project's
 * timezone, since a remote owner and an on-site supervisor are typically in
 * different zones. Falls back to the device's zone if [timezoneId] doesn't
 * resolve (corrupt/unknown data should never crash the check).
 */
fun todayIn(timezoneId: String): LocalDate {
    val zone = runCatching { TimeZone.of(timezoneId) }.getOrDefault(TimeZone.currentSystemDefault())
    return Clock.System.todayIn(zone)
}
