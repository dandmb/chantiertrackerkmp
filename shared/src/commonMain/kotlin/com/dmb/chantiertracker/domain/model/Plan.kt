package com.dmb.chantiertracker.domain.model

enum class Plan { FREE, SEMI_FLEX, LIBERTE, UNKNOWN }

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
