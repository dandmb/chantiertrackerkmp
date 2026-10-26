package com.dmb.chantiertracker.domain.model

enum class DeletionBlocker { LAST_SUPER_ADMIN, SUBSCRIPTION_STILL_RENEWING }

data class AccountDeletionPreview(
    val canDelete: Boolean,
    val blockers: List<DeletionBlocker>,
    val founder: Boolean,
    val ownedProjects: List<OwnedProjectToDelete>,
    val memberships: List<ProjectToLeave>,
    val pendingInvitations: Long,
)

data class OwnedProjectToDelete(val name: String, val otherMembers: List<MemberLosingAccess>)

data class MemberLosingAccess(val name: String, val role: ProjectRole)

data class ProjectToLeave(val name: String, val role: ProjectRole)
