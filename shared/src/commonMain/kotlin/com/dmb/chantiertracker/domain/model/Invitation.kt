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
