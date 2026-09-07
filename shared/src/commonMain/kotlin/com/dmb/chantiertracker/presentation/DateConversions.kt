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

/**
 * The one display format for **every** date shown in the app: `JJ-MM-AAAA`
 * (day-month-year). The API and Room always store ISO `yyyy-MM-dd`; this is the
 * only place a date turns human-readable. An unparseable value is returned
 * as-is rather than hidden.
 */
fun formatIsoDate(iso: String): String {
    // The stored form is always `yyyy-MM-dd`; parse the string directly so this
    // never depends on a kotlinx-datetime field name.
    val parts = iso.trim().split("-")
    if (parts.size != 3 || parts.any { it.isEmpty() }) return iso
    val (year, month, day) = parts
    return "$day-$month-$year"
}

/**
 * A server `LocalDateTime` string (`yyyy-MM-ddTHH:mm:ss[.SSS]`, zoneless server
 * zone) shown as `JJ-MM-AAAA · HH:MM`. Parsed as a string like [formatIsoDate]
 * — never via a datetime library, so it can't depend on a field name or a
 * zone, and the separator is locale-neutral. Unparseable input degrades to the
 * date alone, or is returned as-is.
 */
fun formatIsoDateTime(iso: String): String {
    val trimmed = iso.trim()
    val tIndex = trimmed.indexOf('T')
    val datePart = formatIsoDate(if (tIndex < 0) trimmed else trimmed.substring(0, tIndex))
    if (tIndex < 0) return datePart
    val time = trimmed.substring(tIndex + 1).split(":")
    if (time.size < 2 || time[0].length > 2 || time[1].isEmpty()) return datePart
    return "$datePart · ${time[0].padStart(2, '0')}:${time[1].take(2).padStart(2, '0')}"
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
