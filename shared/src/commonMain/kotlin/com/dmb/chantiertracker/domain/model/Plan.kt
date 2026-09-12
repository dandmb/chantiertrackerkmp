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
 * Total videos per account, mirroring the backend `PlanLimitService.maxVideos`
 * exactly. **Never null** — unlike photos/projects, LIBERTE still caps at 20;
 * `0` (FREE) means the feature is off entirely. `UNKNOWN` → `0` (fail closed:
 * the "Add a video" button just won't show until we know the plan; the server
 * re-asserts on upload anyway).
 */
fun Plan.maxVideos(): Int = when (this) {
    Plan.FREE, Plan.UNKNOWN -> 0
    Plan.SEMI_FLEX -> 5
    Plan.LIBERTE -> 20
}

/**
 * Max duration of a single video, in seconds — mirrors
 * `PlanLimitService.maxVideoDurationSeconds`. `0` (FREE / `UNKNOWN`) = no video.
 */
fun Plan.maxVideoDurationSeconds(): Int = when (this) {
    Plan.FREE, Plan.UNKNOWN -> 0
    Plan.SEMI_FLEX -> 120
    Plan.LIBERTE -> 300
}

/** Whether a higher paid tier exists above this plan — mirrors `PlanLimitService.hasUpgrade`. */
fun Plan.hasUpgrade(): Boolean = this != Plan.LIBERTE

/**
 * Retention window for the project history, in days — mirrors
 * `PlanLimitService.maxHistoryDays`. Not part of `GET /users/me/plan-usage`
 * (it bounds a *read*, not a write, so `PlanUsageService` never needed it) —
 * a static mirror, same family as `maxSupervisorsPerProject()`. `null` =
 * unlimited (LIBERTE) or plan not yet known (`UNKNOWN`, fail open like the
 * other static mirrors).
 */
fun Plan.maxHistoryDays(): Int? = when (this) {
    Plan.FREE -> 30
    Plan.SEMI_FLEX -> 180
    Plan.LIBERTE, Plan.UNKNOWN -> null
}

/**
 * Last-known plan + usage counters from `GET /users/me/plan-usage` (ADR-25,
 * extended ADR-49 for the billing screen). Persisted locally (`plan_usage`,
 * one row) so both the project-creation gate and the billing screen work
 * offline with the last known values. `*Limit` fields `null` = unlimited
 * (LIBERTE) or not yet known; `videosLimit`/`videoDurationLimitSeconds`
 * mirror the backend's own non-null contract (`0` = feature off, never
 * "unlimited") but default to `0` here too when a locally cached row predates
 * this extension (ADR-49) and hasn't been refreshed yet.
 */
data class PlanUsage(
    val plan: Plan,
    val projectsLimit: Int?,
    val projectsUsed: Int = 0,
    val photosUsed: Int = 0,
    val photosLimit: Int? = null,
    val videosUsed: Int = 0,
    val videosLimit: Int = 0,
    val videoDurationLimitSeconds: Int = 0,
    val supervisorsUsed: Int = 0,
    val supervisorsLimit: Int? = null,
    val planExpiresAt: String? = null,
    val hasStripeCustomer: Boolean = false,
) {
    fun isAtProjectLimit(activeProjectCount: Int): Boolean =
        projectsLimit != null && activeProjectCount >= projectsLimit
}
