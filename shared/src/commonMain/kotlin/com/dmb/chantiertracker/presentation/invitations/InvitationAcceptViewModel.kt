package com.dmb.chantiertracker.presentation.invitations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.InvitationStatus
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.repository.InvitationRepository
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface InvitationAcceptStatus {
    data object Loading : InvitationAcceptStatus

    // projectLocalId is null when the freshly-accepted project hasn't
    // synced into the local store by the time refresh() returns — the
    // screen falls back to the project list rather than a broken link.
    data class Accepted(val projectName: String, val projectLocalId: String?) : InvitationAcceptStatus
    data class Ready(val projectName: String, val invitedByName: String?, val role: ProjectRole?, val working: Boolean = false) : InvitationAcceptStatus
    data object Declined : InvitationAcceptStatus
    data object InvalidLink : InvitationAcceptStatus
    data class Failed(val error: DomainException) : InvitationAcceptStatus
}

// ADR-59 — reached only when InvitationDeepLinkBridge has already confirmed
// the current user is authenticated; every field this needs (preview +
// accept + the resulting local project id) is already-existing repository
// surface, nothing reimplemented here.
class InvitationAcceptViewModel(
    private val invitationRepository: InvitationRepository,
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    private val _status = MutableStateFlow<InvitationAcceptStatus>(InvitationAcceptStatus.Loading)
    val status: StateFlow<InvitationAcceptStatus> = _status.asStateFlow()

    private var token: String? = null

    private var projectId: Long? = null

    fun load(token: String) {
        if (this.token == token) return
        this.token = token
        viewModelScope.launch {
            val preview = try {
                invitationRepository.getInvitationPreview(token)
            } catch (e: DomainException) {
                _status.value = failure(e)
                return@launch
            }
            if (preview.status != InvitationStatus.PENDING) {
                _status.value = InvitationAcceptStatus.InvalidLink
                return@launch
            }
            val mine = try {
                invitationRepository.listIncomingInvitations().firstOrNull { it.projectId == preview.projectId }
            } catch (e: DomainException) {
                null
            }
            projectId = preview.projectId
            _status.value = InvitationAcceptStatus.Ready(preview.projectName, mine?.invitedByName, mine?.role)
        }
    }

    fun join() = act { ready, token ->
        invitationRepository.acceptInvitation(token)
        projectRepository.refresh()
        val localId = projectId?.let { projectRepository.findLocalIdByServerId(it) }
        InvitationAcceptStatus.Accepted(ready.projectName, localId)
    }

    fun decline() = act { _, token ->
        invitationRepository.declineInvitation(token)
        InvitationAcceptStatus.Declined
    }

    private fun act(block: suspend (InvitationAcceptStatus.Ready, String) -> InvitationAcceptStatus) {
        val ready = _status.value as? InvitationAcceptStatus.Ready ?: return
        val current = token ?: return
        if (ready.working) return
        _status.value = ready.copy(working = true)
        viewModelScope.launch {
            _status.value = try {
                block(ready, current)
            } catch (e: DomainException) {
                failure(e)
            }
        }
    }

    private fun failure(e: DomainException) =
        if (e == DomainException.NotFound) InvitationAcceptStatus.InvalidLink else InvitationAcceptStatus.Failed(e)

    fun retry() {
        val current = token ?: return
        token = null
        _status.update { InvitationAcceptStatus.Loading }
        load(current)
    }
}
