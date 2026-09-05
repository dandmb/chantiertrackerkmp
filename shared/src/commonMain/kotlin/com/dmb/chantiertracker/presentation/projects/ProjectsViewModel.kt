package com.dmb.chantiertracker.presentation.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.IncomingInvitation
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.model.ProjectSort
import com.dmb.chantiertracker.domain.model.applySort
import com.dmb.chantiertracker.domain.repository.InvitationRepository
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val isLoading: Boolean = true,
    val projects: List<Project> = emptyList(),
    val isRefreshing: Boolean = false,
    val incomingInvitations: List<IncomingInvitation> = emptyList(),
    val acceptingTokens: Set<String> = emptySet(),
    val invitationError: DomainException? = null,
) {
    val isEmpty: Boolean get() = !isLoading && projects.isEmpty()
}

class ProjectsViewModel(
    private val projectRepository: ProjectRepository,
    private val invitationRepository: InvitationRepository,
    private val sortHolder: ProjectSortHolder,
) : ViewModel() {

    private data class InvitationsPart(
        val items: List<IncomingInvitation> = emptyList(),
        val acceptingTokens: Set<String> = emptySet(),
        val error: DomainException? = null,
    )

    private val _state = MutableStateFlow(ProjectsUiState())
    val state: StateFlow<ProjectsUiState> = _state.asStateFlow()

    val sort: StateFlow<ProjectSort> = sortHolder.sort

    private val _isRefreshing = MutableStateFlow(false)
    private val _invitations = MutableStateFlow(InvitationsPart())

    init {
        viewModelScope.launch {
            combine(
                projectRepository.observeProjects(),
                sortHolder.sort,
                _isRefreshing,
                _invitations,
            ) { projects, sort, isRefreshing, invitations ->
                ProjectsUiState(
                    isLoading = false,
                    projects = projects.applySort(sort),
                    isRefreshing = isRefreshing,
                    incomingInvitations = invitations.items,
                    acceptingTokens = invitations.acceptingTokens,
                    invitationError = invitations.error,
                )
            }.collect { _state.value = it }
        }
    }

    /**
     * Called on each screen entry (also right after login, when the list first
     * mounts): a silent background pull of the projects, plus a refresh of the
     * current user's pending invitations. The list itself always comes from the
     * local store.
     */
    fun onEnter() {
        viewModelScope.launch { projectRepository.refresh() }
        refreshIncomingInvitations()
    }

    /** Pull-to-refresh: the same background pull, but with a visible indicator until it settles. */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                projectRepository.refresh()
            } finally {
                _isRefreshing.value = false
            }
            refreshIncomingInvitations()
        }
    }

    fun acceptInvitation(token: String) {
        if (token in _invitations.value.acceptingTokens) return
        _invitations.update { it.copy(acceptingTokens = it.acceptingTokens + token, error = null) }
        viewModelScope.launch {
            try {
                invitationRepository.acceptInvitation(token)
                // Drop it now; the re-fetch below confirms and catches any siblings.
                _invitations.update {
                    it.copy(
                        items = it.items.filterNot { invitation -> invitation.token == token },
                        acceptingTokens = it.acceptingTokens - token,
                    )
                }
                projectRepository.refresh() // the newly joined project shows up in the list
                refreshIncomingInvitations()
            } catch (e: CancellationException) {
                throw e
            } catch (e: DomainException) {
                _invitations.update { it.copy(acceptingTokens = it.acceptingTokens - token, error = e) }
            } catch (e: Throwable) {
                _invitations.update {
                    it.copy(acceptingTokens = it.acceptingTokens - token, error = DomainException.Unexpected)
                }
            }
        }
    }

    fun clearInvitationError() = _invitations.update { it.copy(error = null) }

    private fun refreshIncomingInvitations() {
        viewModelScope.launch {
            val fresh = try {
                invitationRepository.listIncomingInvitations()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return@launch // best-effort — keep whatever we already show
            }
            _invitations.update { it.copy(items = fresh) }
        }
    }
}
