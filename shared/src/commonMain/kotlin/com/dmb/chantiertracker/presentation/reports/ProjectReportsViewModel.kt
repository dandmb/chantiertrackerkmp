package com.dmb.chantiertracker.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Report
import com.dmb.chantiertracker.domain.model.ReportSort
import com.dmb.chantiertracker.domain.repository.ReportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectReportsUiState(
    val isLoading: Boolean = true,
    val error: DomainException? = null,
    val items: List<Report> = emptyList(),
    // 0-based, mirrors the server's `number`.
    val page: Int = 0,
    val totalPages: Int = 0,
    val isFirst: Boolean = true,
    val isLast: Boolean = true,
    val sort: ReportSort = ReportSort.NEWEST_FIRST,
    // Reports currently being marked processed — one row's button spins without
    // blocking the others.
    val processingIds: Set<Long> = emptySet(),
    val processError: DomainException? = null,
) {
    val showPagination: Boolean get() = totalPages > 1
}

// ADMIN-only, online only (ADR-47). No local cache, no SyncEngine: same posture
// as ProjectHistoryViewModel. Every page is fetched fresh. The sort lives in the
// TopAppBar (MainScreen) and comes in as the `sort` param of the screen, relayed
// here via setSort. `markProcessed` refreshes the affected row in place from the
// server's response — no full re-fetch.
class ProjectReportsViewModel(
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectReportsUiState())
    val state = _state.asStateFlow()

    private var localId: String? = null
    private var fetchJob: Job? = null

    fun load(projectLocalId: String) {
        if (localId == projectLocalId) return
        localId = projectLocalId
        fetch(page = 0)
    }

    fun retry() = fetch(page = _state.value.page)

    fun setSort(sort: ReportSort) {
        if (sort == _state.value.sort) return
        _state.update { it.copy(sort = sort) }
        fetch(page = 0)
    }

    fun nextPage() {
        if (!_state.value.isLast && !_state.value.isLoading) fetch(_state.value.page + 1)
    }

    fun previousPage() {
        if (!_state.value.isFirst && !_state.value.isLoading) fetch(_state.value.page - 1)
    }

    fun markProcessed(reportId: Long) {
        if (reportId in _state.value.processingIds) return
        _state.update { it.copy(processingIds = it.processingIds + reportId, processError = null) }
        viewModelScope.launch {
            try {
                val updated = reportRepository.markProcessed(reportId)
                _state.update { s ->
                    s.copy(
                        processingIds = s.processingIds - reportId,
                        items = s.items.map { if (it.id == reportId) updated else it },
                    )
                }
            } catch (e: DomainException) {
                _state.update { it.copy(processingIds = it.processingIds - reportId, processError = e) }
            } catch (e: Throwable) {
                _state.update { it.copy(processingIds = it.processingIds - reportId, processError = DomainException.Unexpected) }
            }
        }
    }

    fun clearProcessError() = _state.update { it.copy(processError = null) }

    private fun fetch(page: Int) {
        val id = localId ?: return
        fetchJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        fetchJob = viewModelScope.launch {
            try {
                val result = reportRepository.projectReports(id, page, _state.value.sort)
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
