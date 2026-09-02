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
 * (`2026-09-02T10:30:00`), read here as UTC. Conflict resolution never compares
 * this to the device clock — it only compares two server-issued `updatedAt`
 * values to each other (see [SyncEngine] / ADR-21), so a constant server-zone
 * offset cancels out. Still used to order projects by `createdAt` and to store
 * `remoteUpdatedAt`.
 */
fun parseServerTimestampMillis(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw.trim().removeSuffix("Z")
    return runCatching {
        LocalDateTime.parse(trimmed).toInstant(TimeZone.UTC).toEpochMilliseconds()
    }.getOrNull()
}
