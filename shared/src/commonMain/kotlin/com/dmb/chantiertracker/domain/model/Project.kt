package com.dmb.chantiertracker.domain.model

data class Project(
    val localId: String,
    val name: String,
    val description: String?,
    val location: String?,
    val status: ProjectStatus,
    val createdAt: String? = null,
    val syncIssue: SyncIssue? = null,
)

enum class ProjectStatus { IN_PROGRESS, SUSPENDED, COMPLETED, UNKNOWN }

data class ProjectDetail(
    val localId: String,
    val name: String,
    val description: String?,
    val location: String?,
    val currency: String,
    val timezone: String,
    val status: ProjectStatus,
    val ownerId: Long?,
    // The project owner's plan (never the caller's) — gates the supervisor
    // limit, mirrors the backend. Null until the first detail pull (ADR-33).
    val ownerPlan: Plan? = null,
    // Null until a founder-aware backend has answered a detail pull — callers
    // then fall back on the static per-plan mirrors (the owner*() accessors below).
    val ownerEntitlements: ProjectOwnerEntitlements? = null,
)

/**
 * What the project owner's account allows, **already combined by the backend**
 * (plan + founder status, `PlanLimitService`) — never recomputed here.
 * `maxHistoryDays` / `maxSupervisorsPerProject` = `null` means unlimited;
 * `maxVideos` / `maxVideoDurationSeconds` are never unlimited, `0` = no video.
 */
data class ProjectOwnerEntitlements(
    val isFounder: Boolean,
    val canExportPdf: Boolean,
    val maxHistoryDays: Int?,
    val maxVideos: Int,
    val maxVideoDurationSeconds: Int,
    val maxSupervisorsPerProject: Int?,
)

/** `null` = not known yet (no detail pull so far) — the export section stays hidden. */
fun ProjectDetail.ownerCanExportPdf(): Boolean? =
    ownerEntitlements?.canExportPdf ?: ownerPlan?.canExportPdf()

/** `null` = unlimited **or** not known yet — either way there is no retention notice to show. */
fun ProjectDetail.ownerMaxHistoryDays(): Int? {
    val entitlements = ownerEntitlements ?: return ownerPlan?.maxHistoryDays()
    return entitlements.maxHistoryDays
}

/** `null` = no cap, or not known yet — the invite form then lets the server decide (ADR-33). */
fun ProjectDetail.ownerMaxSupervisorsPerProject(): Int? {
    val entitlements = ownerEntitlements ?: return ownerPlan?.maxSupervisorsPerProject()
    return entitlements.maxSupervisorsPerProject
}

/** `0` = videos are off for this project, which is also the answer while nothing is known yet. */
fun ProjectDetail.ownerMaxVideos(): Int =
    ownerEntitlements?.maxVideos ?: (ownerPlan ?: Plan.UNKNOWN).maxVideos()

/** `null` = not known yet — the duration pre-check is then skipped, the server decides. */
fun ProjectDetail.ownerMaxVideoDurationSeconds(): Int? =
    ownerEntitlements?.maxVideoDurationSeconds ?: ownerPlan?.maxVideoDurationSeconds()

data class ProjectMember(
    val userId: Long,
    val name: String,
    val email: String,
    val role: ProjectRole,
)

enum class ProjectRole { ADMIN, SUPERVISOR, UNKNOWN }

data class CreateProjectInput(
    val name: String,
    val description: String?,
    val location: String?,
    val currency: String?,
    val timezone: String,
)

data class UpdateProjectInput(
    val name: String,
    val description: String?,
    val location: String?,
    val currency: String,
    val timezone: String,
    val status: ProjectStatus,
)
