package com.dmb.chantiertracker.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.AdminUser
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.repository.AdminRepository
import com.dmb.chantiertracker.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class AdminUserActionMessage {
    data class PasswordResetSent(val email: String) : AdminUserActionMessage()
    data class ActivationResent(val email: String) : AdminUserActionMessage()
}

data class AdminUsersUiState(
    val isLoading: Boolean = true,
    val error: DomainException? = null,
    val items: List<AdminUser> = emptyList(),
    // 0-based, mirrors the server's `number`.
    val page: Int = 0,
    val totalPages: Int = 0,
    val isFirst: Boolean = true,
    val isLast: Boolean = true,
    // Read once from AuthState.Authenticated — never null once the screen is
    // actually reachable (SUPER_ADMIN only). Drives the self-delete guard.
    val currentUserId: Long? = null,
    val processingIds: Set<Long> = emptySet(),
    val actionError: DomainException? = null,
    val actionMessage: AdminUserActionMessage? = null,
) {
    val showPagination: Boolean get() = totalPages > 1
}

// SUPER_ADMIN only, online only (ADR-52) — same posture as
// ProjectHistoryViewModel/ProjectReportsViewModel: no local cache, every
// page fetched fresh. The screen is only reachable via the Administration
// tab, itself only shown to a SUPER_ADMIN account. No init{}-driven fetch —
// load() is called from the screen's LaunchedEffect(Unit), which reruns
// every time the screen re-enters composition (e.g. returning from
// AdminCreateUserScreen), so a freshly created user is visible without a
// manual refresh — same idiom as ProjectHistoryViewModel.load(id).
class AdminUsersViewModel(
    private val adminRepository: AdminRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val currentUserId = (authRepository.authState.value as? AuthState.Authenticated)?.user?.id

    private val _state = MutableStateFlow(AdminUsersUiState(currentUserId = currentUserId))
    val state = _state.asStateFlow()

    private var fetchJob: Job? = null

    fun load() = fetch(page = 0)

    fun retry() = fetch(page = _state.value.page)

    fun nextPage() {
        if (!_state.value.isLast && !_state.value.isLoading) fetch(_state.value.page + 1)
    }

    fun previousPage() {
        if (!_state.value.isFirst && !_state.value.isLoading) fetch(_state.value.page - 1)
    }

    fun clearActionError() = _state.update { it.copy(actionError = null) }
    fun clearActionMessage() = _state.update { it.copy(actionMessage = null) }

    fun updateName(id: Long, name: String) {
        if (id in _state.value.processingIds) return
        _state.update { it.copy(processingIds = it.processingIds + id, actionError = null) }
        viewModelScope.launch {
            try {
                val updated = adminRepository.updateUserName(id, name)
                _state.update { s ->
                    s.copy(
                        processingIds = s.processingIds - id,
                        items = s.items.map { if (it.id == id) updated else it },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DomainException) {
                _state.update { it.copy(processingIds = it.processingIds - id, actionError = e) }
            } catch (e: Throwable) {
                _state.update { it.copy(processingIds = it.processingIds - id, actionError = DomainException.Unexpected) }
            }
        }
    }

    fun resetPassword(id: Long, email: String) {
        if (id in _state.value.processingIds) return
        _state.update { it.copy(processingIds = it.processingIds + id, actionError = null) }
        viewModelScope.launch {
            try {
                adminRepository.resetPassword(id)
                _state.update {
                    it.copy(
                        processingIds = it.processingIds - id,
                        actionMessage = AdminUserActionMessage.PasswordResetSent(email),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DomainException) {
                _state.update { it.copy(processingIds = it.processingIds - id, actionError = e) }
            } catch (e: Throwable) {
                _state.update { it.copy(processingIds = it.processingIds - id, actionError = DomainException.Unexpected) }
            }
        }
    }

    fun resendActivation(id: Long, email: String) {
        if (id in _state.value.processingIds) return
        _state.update { it.copy(processingIds = it.processingIds + id, actionError = null) }
        viewModelScope.launch {
            try {
                adminRepository.resendActivation(id)
                _state.update {
                    it.copy(
                        processingIds = it.processingIds - id,
                        actionMessage = AdminUserActionMessage.ActivationResent(email),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DomainException) {
                _state.update { it.copy(processingIds = it.processingIds - id, actionError = e) }
            } catch (e: Throwable) {
                _state.update { it.copy(processingIds = it.processingIds - id, actionError = DomainException.Unexpected) }
            }
        }
    }

    // No server-side guard exists against a SUPER_ADMIN deleting their own
    // account (verified against AdminUserService.deleteUser) — the UI
    // disables the menu item for the current user's own row, and this is the
    // defense-in-depth backstop, same posture as CreateProjectViewModel's
    // isSuperAdmin guard.
    fun deleteUser(id: Long) {
        if (id == currentUserId) return
        if (id in _state.value.processingIds) return
        _state.update { it.copy(processingIds = it.processingIds + id, actionError = null) }
        viewModelScope.launch {
            try {
                adminRepository.deleteUser(id)
                _state.update { it.copy(processingIds = it.processingIds - id, items = it.items.filterNot { u -> u.id == id }) }
                // Deleting the last row of a page beyond the first leaves an
                // empty page stranded — step back one, same UX as most
                // paginated delete flows.
                val current = _state.value
                if (current.items.isEmpty() && current.page > 0) fetch(current.page - 1)
            } catch (e: CancellationException) {
                throw e
            } catch (e: DomainException) {
                _state.update { it.copy(processingIds = it.processingIds - id, actionError = e) }
            } catch (e: Throwable) {
                _state.update { it.copy(processingIds = it.processingIds - id, actionError = DomainException.Unexpected) }
            }
        }
    }

    private fun fetch(page: Int) {
        fetchJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        fetchJob = viewModelScope.launch {
            try {
                val result = adminRepository.listUsers(page)
                _state.update {
                    it.copy(
                        isLoading = false,
                        items = result.items,
                        page = result.page,
                        totalPages = result.totalPages,
                        isFirst = result.isFirst,
                        isLast = result.isLast,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DomainException) {
                _state.update { it.copy(isLoading = false, error = e) }
            } catch (e: Throwable) {
                _state.update { it.copy(isLoading = false, error = DomainException.Unexpected) }
            }
        }
    }
}
