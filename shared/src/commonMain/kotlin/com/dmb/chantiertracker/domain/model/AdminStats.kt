package com.dmb.chantiertracker.domain.model

// Mirrors the backend `Granularity` enum exactly (DAY/MONTH/YEAR) — same
// tolerant-mapping posture as GlobalRole/Plan elsewhere in this file, even
// though the UI only ever offers these three values (no UNKNOWN needed:
// this is chosen by the user, never parsed from a server response).
enum class Granularity { DAY, MONTH, YEAR }

// bucket: the backend's LocalDate for this point, as a raw ISO string
// (yyyy-MM-dd) — same convention as every other date in this domain, never
// parsed here. The first day of the bucket period regardless of
// granularity (e.g. "2026-03-01" for March under MONTH) — formatted as-is
// in the presentation layer, the active granularity gives it its meaning.
data class StatsPoint(val bucket: String, val count: Long)

data class AdminStats(
    val totalUsers: Long,
    val totalProjects: Long,
    val registrations: List<StatsPoint>,
    val projectsCreated: List<StatsPoint>,
)
