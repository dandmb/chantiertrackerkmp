package com.dmb.chantiertracker.presentation.invitations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.InvitationStatus
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.repository.AuthRepository
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
    data object WrongAccount : InvitationAcceptStatus
    data object InvalidLink : InvitationAcceptStatus
    data class Failed(val error: DomainException) : InvitationAcceptStatus
}

class InvitationAcceptViewModel(
    private val invitationRepository: InvitationRepository,
    private val projectRepository: ProjectRepository,
    private val invitationLinks: InvitationDeepLinkDispatcher,
    private val authRepository: AuthRepository,
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
            projectId = preview.projectId
            _status.value = InvitationAcceptStatus.Ready(preview.projectName, preview.invitedByName, preview.role)
        }
    }

    fun join() = act { ready, token ->
        invitationRepository.acceptInvitation(token)
        projectRepository.refresh()
        val localId = projectId?.let { projectRepository.findLocalIdByServerId(it) }
        invitationLinks.release(token)
        InvitationAcceptStatus.Accepted(ready.projectName, localId)
    }

    fun decline() = act { _, token ->
        invitationRepository.declineInvitation(token)
        invitationLinks.release(token)
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
            } catch (e: DomainException.InvalidCredentials) {
                ready
            } catch (e: DomainException) {
                failure(e)
            }
        }
    }

    private fun failure(e: DomainException) = when (e) {
        DomainException.NotFound -> InvitationAcceptStatus.InvalidLink
        DomainException.InvitationForAnotherAccount -> InvitationAcceptStatus.WrongAccount
        else -> InvitationAcceptStatus.Failed(e)
    }.also { if (it.settles()) token?.let(invitationLinks::release) }

    private fun InvitationAcceptStatus.settles() =
        this is InvitationAcceptStatus.Accepted || this == InvitationAcceptStatus.Declined || this == InvitationAcceptStatus.InvalidLink

    override fun onCleared() {
        if (authRepository.authState.value is AuthState.Authenticated) token?.let(invitationLinks::release)
    }

    fun retry() {
        val current = token ?: return
        token = null
        _status.update { InvitationAcceptStatus.Loading }
        load(current)
    }
}
