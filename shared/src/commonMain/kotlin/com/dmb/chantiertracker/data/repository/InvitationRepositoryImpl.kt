package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.InvitationDao
import com.dmb.chantiertracker.data.local.db.InvitationEntity
import com.dmb.chantiertracker.domain.model.Invitation
import com.dmb.chantiertracker.domain.model.InvitationStatus
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.repository.InvitationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Read-only for now (Étape 1): the list is a pull-through cache filled by
// SyncEngine.pullInvitations, exactly like project_members. Creating and
// cancelling invitations are online-only actions (ADR-32) — added in Étape 2.
class InvitationRepositoryImpl(
    private val dao: InvitationDao,
) : InvitationRepository {

    override fun observeInvitations(projectLocalId: String): Flow<List<Invitation>> =
        dao.observeForProject(projectLocalId).map { rows -> rows.map(InvitationEntity::toInvitation) }
}

internal fun InvitationEntity.toInvitation(): Invitation = Invitation(
    id = id,
    projectLocalId = projectLocalId,
    email = email,
    role = when (role.uppercase()) {
        "ADMIN" -> ProjectRole.ADMIN
        "SUPERVISOR" -> ProjectRole.SUPERVISOR
        else -> ProjectRole.UNKNOWN
    },
    invitedById = invitedById,
    createdAt = createdAt,
    expiresAt = expiresAt,
    status = when (status.uppercase()) {
        "PENDING" -> InvitationStatus.PENDING
        "ACCEPTED" -> InvitationStatus.ACCEPTED
        "EXPIRED" -> InvitationStatus.EXPIRED
        else -> InvitationStatus.UNKNOWN
    },
)
