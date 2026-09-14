package com.dmb.chantiertracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class InvitationDto(
    val id: Long,
    val projectId: Long,
    val email: String,
    val role: String,
    val invitedById: Long? = null,
    val createdAt: String? = null,
    val expiresAt: String? = null,
    val status: String,
)

@Serializable
data class CreateInvitationRequestDto(
    val email: String,
    val role: String,
)

/** One item of `GET /users/me/invitations` — a bare JSON array, not a page. */
@Serializable
data class PendingInvitationDto(
    val token: String,
    val projectId: Long,
    val projectName: String,
    val role: String,
    val invitedByName: String? = null,
    val createdAt: String? = null,
    val expiresAt: String? = null,
)

/**
 * `GET /invitations/{token}` — public, unauthenticated (ADR-59: App Links
 * can deliver a token before the app knows whether the user is logged in).
 * `email`/`accountExists` exist server-side for the web's logged-out flows
 * (login/register prompts) but aren't consumed here — the mobile App Links
 * handler only ever calls this once already authenticated, falling back to
 * the browser (which does use them) otherwise.
 */
@Serializable
data class InvitationDetailsDto(
    val projectId: Long,
    val projectName: String,
    val email: String,
    val status: String,
    val accountExists: Boolean,
)
