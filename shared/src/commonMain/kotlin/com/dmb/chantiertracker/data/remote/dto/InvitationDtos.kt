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
