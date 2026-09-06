package com.dmb.chantiertracker.domain.model

enum class Plan { FREE, SEMI_FLEX, LIBERTE, UNKNOWN }

/**
 * Per-project supervisor cap, mirroring the backend `PlanLimitService`
 * (FREE 1 / SEMI_FLEX 3). `null` = no cap: LIBERTE, or plan not yet known
 * (`UNKNOWN`) — the caller then lets the server be the arbiter (fail open,
 * same stance as the project-creation check, ADR-25 / ADR-33).
 */
fun Plan.maxSupervisorsPerProject(): Int? = when (this) {
    Plan.FREE -> 1
    Plan.SEMI_FLEX -> 3
    Plan.LIBERTE, Plan.UNKNOWN -> null
}

/**
 * Last-known plan + the active-project cap for it (`projectsLimit == null` means
 * unlimited — LIBERTE — or that the limit isn't known). Persisted locally so the
 * project-creation check works offline (ADR-25).
 */
data class PlanUsage(
    val plan: Plan,
    val projectsLimit: Int?,
) {
    fun isAtProjectLimit(activeProjectCount: Int): Boolean =
        projectsLimit != null && activeProjectCount >= projectsLimit
}
