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
