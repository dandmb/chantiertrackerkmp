package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.InvitationDao
import com.dmb.chantiertracker.data.local.db.InvitationEntity
import com.dmb.chantiertracker.data.local.db.ProjectDao
import com.dmb.chantiertracker.data.remote.InvitationApi
import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.data.remote.apiCallReportingRefusalCode
import com.dmb.chantiertracker.data.remote.dto.CreateInvitationRequestDto
import com.dmb.chantiertracker.data.remote.dto.InvitationDetailsDto
import com.dmb.chantiertracker.data.remote.dto.PendingInvitationDto
import com.dmb.chantiertracker.data.remote.OnlineCreationKeys
import com.dmb.chantiertracker.data.sync.Syncer
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.IncomingInvitation
import com.dmb.chantiertracker.domain.model.Invitation
import com.dmb.chantiertracker.domain.model.InvitationPreview
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

    private val creationKeys = OnlineCreationKeys()

    override fun observeInvitations(projectLocalId: String): Flow<List<Invitation>> =
        dao.observeForProject(projectLocalId).map { rows -> rows.map(InvitationEntity::toInvitation) }

    override suspend fun invite(projectLocalId: String, email: String) {
        val serverId = projectDao.findByLocalId(projectLocalId)?.serverId ?: throw DomainException.NotFound
        val request = CreateInvitationRequestDto(email = email, role = ProjectRole.SUPERVISOR.name)
        try {
            creationKeys.send(serverId to request) { key -> api.create(serverId, request, key) }
        } catch (e: DomainException.PlanLimitReached) {
            throw DomainException.CollaboratorLimitReached
        }
        syncer.syncProject(projectLocalId)
    }

    override suspend fun replacePendingInvitation(projectLocalId: String, email: String) {
        syncer.syncProject(projectLocalId)
        val pending = dao.findForProject(projectLocalId).firstOrNull { it.email.equals(email, ignoreCase = true) && it.status.equals("PENDING", ignoreCase = true) }
            ?: throw DomainException.NotFound
        apiCall { api.cancel(pending.id) }
        invite(projectLocalId, email)
    }

    override suspend fun cancelInvitation(projectLocalId: String, invitationId: Long) {
        apiCall { api.cancel(invitationId) }
        syncer.syncProject(projectLocalId)
    }

    override suspend fun removeMember(projectLocalId: String, userId: Long) {
        val serverId = projectDao.findByLocalId(projectLocalId)?.serverId ?: throw DomainException.NotFound
        var refusalCode: String? = null
        try {
            apiCallReportingRefusalCode({ refusalCode = it }) { api.removeMember(serverId, userId) }
        } catch (e: DomainException) {
            when (refusalCode) {
                PROJECT_MEMBER_NOT_FOUND_CODE -> Unit
                LAST_ADMIN_REMOVAL_CODE -> throw DomainException.LastAdminRemoval
                else -> throw e
            }
        }
        projectDao.deleteMember(projectLocalId, userId)
        syncer.syncProject(projectLocalId)
    }

    override suspend fun listIncomingInvitations(): List<IncomingInvitation> =
        apiCall { api.listMine() }.map(PendingInvitationDto::toIncomingInvitation)

    override suspend fun getInvitationPreview(token: String): InvitationPreview =
        apiCall { api.getDetails(token) }.toInvitationPreview()

    override suspend fun acceptInvitation(token: String) {
        acceptingWithinTheQuota { api.accept(token) }
        // Caller re-pulls the project list so the newly joined project shows up.
    }

    override suspend fun acceptIncomingInvitation(id: Long) {
        try {
            acceptingWithinTheQuota { api.acceptMine(id) }
        } catch (e: DomainException.NotFound) {
            throw DomainException.InvitationGone
        }
    }

    override suspend fun declineIncomingInvitation(id: Long) {
        try {
            apiCall { api.declineMine(id) }
        } catch (e: DomainException.NotFound) {
            throw DomainException.InvitationGone
        }
    }

    private suspend fun acceptingWithinTheQuota(accept: suspend () -> Unit) {
        try {
            apiCall { accept() }
        } catch (e: DomainException.PlanLimitReached) {
            throw DomainException.CollaboratorLimitReached
        }
    }
}

private fun PendingInvitationDto.toIncomingInvitation() = IncomingInvitation(
    id = id,
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
    status = status.toInvitationStatus(),
)

private fun InvitationDetailsDto.toInvitationPreview() = InvitationPreview(
    projectId = projectId,
    projectName = projectName,
    status = status.toInvitationStatus(),
)

private fun String.toInvitationStatus(): InvitationStatus = when (uppercase()) {
    "PENDING" -> InvitationStatus.PENDING
    "ACCEPTED" -> InvitationStatus.ACCEPTED
    "EXPIRED" -> InvitationStatus.EXPIRED
    "DECLINED" -> InvitationStatus.DECLINED
    else -> InvitationStatus.UNKNOWN
}

private const val PROJECT_MEMBER_NOT_FOUND_CODE = "PROJECT_MEMBER_NOT_FOUND"
private const val LAST_ADMIN_REMOVAL_CODE = "LAST_ADMIN_REMOVAL"
