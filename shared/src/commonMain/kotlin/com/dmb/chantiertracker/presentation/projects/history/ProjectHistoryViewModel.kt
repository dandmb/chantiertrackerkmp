package com.dmb.chantiertracker.presentation.projects.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.HistorySort
import com.dmb.chantiertracker.domain.model.ModificationHistoryItem
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.repository.HistoryRepository
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectHistoryUiState(
    val isLoading: Boolean = true,
    val error: DomainException? = null,
    val items: List<ModificationHistoryItem> = emptyList(),
    // 0-based, mirrors the server's `number`.
    val page: Int = 0,
    val totalPages: Int = 0,
    val isFirst: Boolean = true,
    val isLast: Boolean = true,
    val sort: HistorySort = HistorySort.NEWEST_FIRST,
    // The project OWNER's plan — drives the retention notice (30 j / 6 mois /
    // rien). Null until the local project row is observed.
    val ownerPlan: Plan? = null,
) {
    val showPagination: Boolean get() = totalPages > 1
}

// ADMIN-only, online only (ADR-44). No local cache: every page is fetched
// fresh from the server, so the retention window (computed server-side against
// now()) is always accurate. The screen is only reachable from an ADMIN's
// project detail; a 403 (rights lost meanwhile) is just an error state.
class ProjectHistoryViewModel(
    private val historyRepository: HistoryRepository,
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectHistoryUiState())
    val state = _state.asStateFlow()

    private var localId: String? = null
    private var fetchJob: Job? = null

    fun load(projectLocalId: String) {
        if (localId == projectLocalId) return
        localId = projectLocalId

        viewModelScope.launch {
            projectRepository.observeProject(projectLocalId).collect { detail ->
                _state.update { it.copy(ownerPlan = detail?.ownerPlan) }
            }
        }
        fetch(page = 0)
    }

    fun retry() = fetch(page = _state.value.page)

    fun nextPage() {
        if (!_state.value.isLast && !_state.value.isLoading) fetch(_state.value.page + 1)
    }

    fun previousPage() {
        if (!_state.value.isFirst && !_state.value.isLoading) fetch(_state.value.page - 1)
    }

    fun setSort(sort: HistorySort) {
        if (sort == _state.value.sort) return
        _state.update { it.copy(sort = sort) }
        fetch(page = 0)
    }

    private fun fetch(page: Int) {
        val id = localId ?: return
        fetchJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        fetchJob = viewModelScope.launch {
            try {
                val result = historyRepository.projectHistory(id, page, _state.value.sort)
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
