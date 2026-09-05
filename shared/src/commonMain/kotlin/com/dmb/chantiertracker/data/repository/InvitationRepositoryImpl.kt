package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.InvitationDao
import com.dmb.chantiertracker.data.local.db.InvitationEntity
import com.dmb.chantiertracker.data.local.db.ProjectDao
import com.dmb.chantiertracker.data.remote.InvitationApi
import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.data.remote.dto.CreateInvitationRequestDto
import com.dmb.chantiertracker.data.remote.dto.PendingInvitationDto
import com.dmb.chantiertracker.data.sync.Syncer
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.IncomingInvitation
import com.dmb.chantiertracker.domain.model.Invitation
import com.dmb.chantiertracker.domain.model.InvitationStatus
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.repository.InvitationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// The one repository that talks to an Api directly (via apiCall) instead of
// going through the SyncEngine — a deliberate, documented exception (ADR-32):
// creating/cancelling an invitation can't be a queued offline write (token
// minted server-side, email sent on create). Reads stay a pull-through Room
// cache filled by SyncEngine.pullInvitations. After a mutation the project is
// re-pulled so the local cache catches up.
class InvitationRepositoryImpl(
    private val dao: InvitationDao,
    private val api: InvitationApi,
    private val projectDao: ProjectDao,
    private val syncer: Syncer,
) : InvitationRepository {

    override fun observeInvitations(projectLocalId: String): Flow<List<Invitation>> =
        dao.observeForProject(projectLocalId).map { rows -> rows.map(InvitationEntity::toInvitation) }

    override suspend fun invite(projectLocalId: String, email: String) {
        val serverId = projectDao.findByLocalId(projectLocalId)?.serverId ?: throw DomainException.NotFound
        apiCall { api.create(serverId, CreateInvitationRequestDto(email = email, role = ProjectRole.SUPERVISOR.name)) }
        syncer.syncProject(projectLocalId)
    }

    override suspend fun cancelInvitation(projectLocalId: String, invitationId: Long) {
        apiCall { api.cancel(invitationId) }
        syncer.syncProject(projectLocalId)
    }

    override suspend fun listIncomingInvitations(): List<IncomingInvitation> =
        apiCall { api.listMine() }.map(PendingInvitationDto::toIncomingInvitation)

    override suspend fun acceptInvitation(token: String) {
        apiCall { api.accept(token) }
        // Caller re-pulls the project list so the newly joined project shows up.
    }
}

private fun PendingInvitationDto.toIncomingInvitation() = IncomingInvitation(
    token = token,
    projectId = projectId,
    projectName = projectName,
    role = when (role.uppercase()) {
        "ADMIN" -> ProjectRole.ADMIN
        "SUPERVISOR" -> ProjectRole.SUPERVISOR
        else -> ProjectRole.UNKNOWN
    },
    invitedByName = invitedByName,
    createdAt = createdAt,
    expiresAt = expiresAt,
)

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
