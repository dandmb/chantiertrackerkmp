package com.dmb.chantiertracker.presentation.projects.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Invitation
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectMember
import com.dmb.chantiertracker.domain.model.ownerMaxSupervisorsPerProject
import com.dmb.chantiertracker.domain.repository.InvitationRepository
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.invite_member_already_member
import com.dmb.chantiertracker.resources.invite_member_self
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import com.dmb.chantiertracker.presentation.auth.validateEmail
import com.dmb.chantiertracker.presentation.projects.SupervisorLimit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

data class InviteMemberUiState(
    val email: String = "",
    val emailError: StringResource? = null,
    val formError: DomainException? = null,
    val isSubmitting: Boolean = false,
    val invited: Boolean = false,
    val atSupervisorLimit: Boolean = false,
) {
    val canSubmit: Boolean get() = emailError == null && !isSubmitting && !atSupervisorLimit
}

// The invited role is always SUPERVISOR (same as the web — an ADMIN can only
// hand out the SUPERVISOR role from here). Sending is online only (ADR-32):
// the limit is pre-checked from local data (ADR-33) but the server stays the
// final arbiter.
class InviteMemberViewModel(
    private val projectRepository: ProjectRepository,
    private val invitationRepository: InvitationRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(InviteMemberUiState())
    val state = _state.asStateFlow()

    private var localId: String? = null
    private var memberEmails: Set<String> = emptySet()

    fun load(projectLocalId: String) {
        if (localId == projectLocalId) return
        localId = projectLocalId

        viewModelScope.launch {
            combine(
                projectRepository.observeProject(projectLocalId),
                projectRepository.observeMembers(projectLocalId),
                invitationRepository.observeInvitations(projectLocalId),
            ) { detail, members, invitations ->
                memberEmails = members.map { it.email.normalizedAddress() }.toSet()
                atLimit(detail, members, invitations)
            }
                .collect { reached ->
                    _state.update { it.copy(atSupervisorLimit = reached, emailError = it.emailError ?: knownAddressError(it.email)) }
                }
        }
        // Pull the detail so the owner's cap (the limit's basis) is as fresh as possible.
        viewModelScope.launch { projectRepository.refreshProject(projectLocalId) }
    }

    fun onEmailChange(value: String) =
        _state.update { it.copy(email = value, emailError = knownAddressError(value), formError = null) }

    private fun knownAddressError(value: String): StringResource? {
        val address = value.normalizedAddress()
        if (address.isEmpty()) return null
        val ownEmail = (authRepository.authState.value as? AuthState.Authenticated)?.user?.email?.normalizedAddress()
        return when {
            address == ownEmail -> Res.string.invite_member_self
            address in memberEmails -> Res.string.invite_member_already_member
            else -> null
        }
    }

    private fun String.normalizedAddress(): String = trim().lowercase()

    fun submit() {
        val id = localId ?: return
        val current = _state.value
        if (current.atSupervisorLimit || current.isSubmitting) return

        val email = current.email.trim()
        val emailError = validateEmail(email) ?: knownAddressError(email)
        if (emailError != null) {
            _state.update { it.copy(emailError = emailError, formError = null) }
            return
        }

        send { invitationRepository.invite(id, email) }
    }

    fun cancelExistingAndInvite() {
        val id = localId ?: return
        val current = _state.value
        if (current.isSubmitting || current.formError != DomainException.InvitationAlreadyPending) return
        send { invitationRepository.replacePendingInvitation(id, current.email.trim()) }
    }

    private fun send(invitation: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, formError = null) }
            try {
                invitation()
                _state.update { it.copy(isSubmitting = false, invited = true) }
            } catch (e: DomainException.CannotInviteSelf) {
                _state.update { it.copy(isSubmitting = false, emailError = Res.string.invite_member_self) }
            } catch (e: DomainException.AlreadyProjectMember) {
                _state.update { it.copy(isSubmitting = false, emailError = Res.string.invite_member_already_member) }
            } catch (e: DomainException) {
                _state.update { it.copy(isSubmitting = false, formError = e) }
            } catch (e: Throwable) {
                _state.update { it.copy(isSubmitting = false, formError = DomainException.Unexpected) }
            }
        }
    }

    private fun atLimit(
        detail: ProjectDetail?,
        members: List<ProjectMember>,
        invitations: List<Invitation>,
    ): Boolean = SupervisorLimit.isReached(detail?.ownerMaxSupervisorsPerProject(), detail?.ownerId, members, invitations)
}
