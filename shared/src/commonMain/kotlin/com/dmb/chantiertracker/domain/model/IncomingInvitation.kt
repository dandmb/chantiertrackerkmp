package com.dmb.chantiertracker.domain.model

/**
 * A pending invitation addressed to the **current user**, from
 * `GET /users/me/invitations`. Lets the app prompt "you were invited to
 * project X" however the user signed in — not only when they open the
 * tokenised link from the email. Online-only, held in the ViewModel (ADR-34)
 * — unlike a project's own invitation list (ADR-32) it is not cached in Room.
 */
data class IncomingInvitation(
    val token: String,
    val projectId: Long,
    val projectName: String,
    val role: ProjectRole,
    val invitedByName: String?,
    val createdAt: String?,
    val expiresAt: String?,
)
