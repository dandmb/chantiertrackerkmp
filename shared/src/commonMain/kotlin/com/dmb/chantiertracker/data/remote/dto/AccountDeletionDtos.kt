package com.dmb.chantiertracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AccountDeletionPreviewDto(
    val canDelete: Boolean,
    val blockers: List<String> = emptyList(),
    val founder: Boolean = false,
    val ownedProjects: List<OwnedProjectDto> = emptyList(),
    val memberships: List<MembershipDto> = emptyList(),
    val pendingInvitations: Long = 0,
) {
    @Serializable
    data class OwnedProjectDto(val id: Long, val name: String, val otherMembers: List<OtherMemberDto> = emptyList())

    @Serializable
    data class OtherMemberDto(val name: String, val role: String)

    @Serializable
    data class MembershipDto(val projectId: Long, val projectName: String, val role: String)
}

@Serializable
data class DeleteAccountRequestDto(val password: String)
