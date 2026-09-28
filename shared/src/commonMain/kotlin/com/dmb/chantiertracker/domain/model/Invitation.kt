package com.dmb.chantiertracker.domain.model

enum class InvitationStatus { PENDING, ACCEPTED, EXPIRED, UNKNOWN }

/**
 * A pending (or past) invitation to join a project as a member. Read-through
 * cache only — an invitation is minted server-side (secure token) and its
 * creation sends an email, so it is never created offline (see ADR-32).
 */
data class Invitation(
    val id: Long,
    val projectLocalId: String,
    val email: String,
    val role: ProjectRole,
    val invitedById: Long?,
    val createdAt: String?,
    val expiresAt: String?,
    val status: InvitationStatus,
)

/**
 * Public preview of an invitation by its token (ADR-59 App Links) — just
 * enough to gate and label the accept attempt (`status`, so an already-used
 * link isn't re-submitted; `projectName`, to greet the user by name before
 * the project itself has synced locally; `projectId`, the **server** id, to
 * resolve the local one afterwards via `ProjectRepository.
 * findLocalIdByServerId`). Never used for the logged-out cases (missing
 * account, needs login) — those fall back to the browser instead.
 */
data class InvitationPreview(
    val projectId: Long,
    val projectName: String,
    val status: InvitationStatus,
)
