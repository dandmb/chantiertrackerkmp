package com.dmb.chantiertracker.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.AdminUser
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.repository.AdminRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUsersUiState(
    val isLoading: Boolean = true,
    val error: DomainException? = null,
    val items: List<AdminUser> = emptyList(),
    // 0-based, mirrors the server's `number`.
    val page: Int = 0,
    val totalPages: Int = 0,
    val isFirst: Boolean = true,
    val isLast: Boolean = true,
) {
    val showPagination: Boolean get() = totalPages > 1
}

// SUPER_ADMIN only, online only (ADR-52) — same posture as
// ProjectHistoryViewModel/ProjectReportsViewModel: no local cache, every
// page fetched fresh. The screen is only reachable via the Administration
// tab, itself only shown to a SUPER_ADMIN account.
class AdminUsersViewModel(
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminUsersUiState())
    val state = _state.asStateFlow()

    private var fetchJob: Job? = null

    init {
        fetch(page = 0)
    }

    fun retry() = fetch(page = _state.value.page)

    fun nextPage() {
        if (!_state.value.isLast && !_state.value.isLoading) fetch(_state.value.page + 1)
    }

    fun previousPage() {
        if (!_state.value.isFirst && !_state.value.isLoading) fetch(_state.value.page - 1)
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
