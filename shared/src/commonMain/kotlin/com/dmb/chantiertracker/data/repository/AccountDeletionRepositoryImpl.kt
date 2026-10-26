package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.AuthStateHolder
import com.dmb.chantiertracker.data.local.TokenStorage
import com.dmb.chantiertracker.data.remote.AccountApi
import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.data.remote.apiCallReportingRefusalCode
import com.dmb.chantiertracker.data.remote.dto.AccountDeletionPreviewDto
import com.dmb.chantiertracker.data.session.LocalDataEraser
import com.dmb.chantiertracker.data.session.LocalDataOwnerStore
import com.dmb.chantiertracker.data.session.SessionNotice
import com.dmb.chantiertracker.data.session.SessionNotices
import com.dmb.chantiertracker.data.sync.Syncer
import com.dmb.chantiertracker.domain.model.AccountDeletionPreview
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DeletionBlocker
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.MemberLosingAccess
import com.dmb.chantiertracker.domain.model.OwnedProjectToDelete
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.model.ProjectToLeave
import com.dmb.chantiertracker.domain.repository.AccountDeletionRepository

private const val INVALID_CURRENT_PASSWORD_CODE = "INVALID_CURRENT_PASSWORD"

class AccountDeletionRepositoryImpl(
    private val api: AccountApi,
    private val tokenStorage: TokenStorage,
    private val authStateHolder: AuthStateHolder,
    private val syncer: Syncer,
    private val eraser: LocalDataEraser,
    private val ownerStore: LocalDataOwnerStore,
    private val notices: SessionNotices,
) : AccountDeletionRepository {

    override suspend fun preview(): AccountDeletionPreview = apiCall { api.deletionPreview() }.toPreview()

    // Under the engine's lock: no pass runs, the video being sent stops, and nothing of the account
    // is left behind on the device once the server has answered 204 — Room (and with it every
    // pending write), the files, the exports, the session and the recorded owner.
    override suspend fun deleteAccount(password: String) {
        syncer.runExclusive {
            var code: String? = null
            try {
                apiCallReportingRefusalCode({ code = it }) { api.deleteMe(password) }
            } catch (e: DomainException) {
                throw e.readAs(code)
            }
            tokenStorage.clear()
            eraser.eraseAll()
            ownerStore.forget()
        }
        notices.publish(SessionNotice.AccountDeleted)
        authStateHolder.update(AuthState.Unauthenticated)
    }

    private fun DomainException.readAs(code: String?): DomainException {
        if (code == INVALID_CURRENT_PASSWORD_CODE) return DomainException.InvalidCurrentPassword
        val blocker = DeletionBlocker.entries.firstOrNull { it.name == code }
        return if (blocker != null) DomainException.AccountDeletionBlocked(blocker) else this
    }
}

private fun String.toRole(): ProjectRole = ProjectRole.entries.firstOrNull { it.name == this } ?: ProjectRole.UNKNOWN

private fun AccountDeletionPreviewDto.toPreview() = AccountDeletionPreview(
    canDelete = canDelete,
    blockers = blockers.mapNotNull { name -> DeletionBlocker.entries.firstOrNull { it.name == name } },
    founder = founder,
    ownedProjects = ownedProjects.map { project ->
        OwnedProjectToDelete(project.name, project.otherMembers.map { MemberLosingAccess(it.name, it.role.toRole()) })
    },
    memberships = memberships.map { ProjectToLeave(it.projectName, it.role.toRole()) },
    pendingInvitations = pendingInvitations,
)
