package com.dmb.chantiertracker.presentation.invitations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.InvitationStatus
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

    fun load(token: String) {
        if (this.token == token) return
        this.token = token
        viewModelScope.launch {
            val preview = try {
                invitationRepository.getInvitationPreview(token)
            } catch (e: DomainException) {
                // An unknown/malformed token reads the same as an expired or
                // already-used one to the user — mirrors the web, which
                // shows the same "Invitation invalide" message for both.
                _status.value = if (e == DomainException.NotFound) InvitationAcceptStatus.InvalidLink else InvitationAcceptStatus.Failed(e)
                return@launch
            }
            if (preview.status != InvitationStatus.PENDING) {
                _status.value = InvitationAcceptStatus.InvalidLink
                return@launch
            }
            try {
                invitationRepository.acceptInvitation(token)
            } catch (e: DomainException) {
                _status.value = InvitationAcceptStatus.Failed(e)
                return@launch
            }
            // Best-effort: never throws (ProjectRepository.refresh's own
            // contract) — a failed pull here still leaves a genuinely
            // accepted invitation, just not synced into Room yet.
            projectRepository.refresh()
            val projectLocalId = projectRepository.findLocalIdByServerId(preview.projectId)
            _status.value = InvitationAcceptStatus.Accepted(preview.projectName, projectLocalId)
        }
    }

    fun retry() {
        val current = token ?: return
        token = null
        _status.update { InvitationAcceptStatus.Loading }
        load(current)
    }
}
