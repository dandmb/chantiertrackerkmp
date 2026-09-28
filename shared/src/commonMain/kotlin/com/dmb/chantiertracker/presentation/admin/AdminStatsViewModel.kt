package com.dmb.chantiertracker.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.AdminStats
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Granularity
import com.dmb.chantiertracker.domain.repository.AdminRepository
import com.dmb.chantiertracker.presentation.todayInSystemZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

data class AdminStatsUiState(
    val isLoading: Boolean = true,
    val error: DomainException? = null,
    val stats: AdminStats? = null,
    val granularity: Granularity = Granularity.MONTH,
    // yyyy-MM-dd or blank — blank means "let the backend pick its own
    // default", never pre-filled client-side (mirrors the web).
    val from: String = "",
    val to: String = "",
    val dateRangeError: StringResource? = null,
)

// SUPER_ADMIN only, online only (ADR-52) — same posture as
// AdminUsersViewModel: no local cache, every change refetches fresh. load()
// drives the very first fetch (called from LaunchedEffect(Unit), so a
// re-entry into the screen also refreshes); setGranularity is driven from
// MainScreen's TopAppBar control the same way ProjectReportsViewModel.setSort
// is driven from ReportSortControl.
class AdminStatsViewModel(
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminStatsUiState())
    val state = _state.asStateFlow()

    private var fetchJob: Job? = null

    fun load() = fetch()
    fun retry() = fetch()

    fun setGranularity(value: Granularity) {
        if (value == _state.value.granularity) return
        _state.update { it.copy(granularity = value) }
        fetch()
    }

    fun onFromChange(value: String) {
        _state.update { it.copy(from = value) }
        fetch()
    }

    fun onToChange(value: String) {
        _state.update { it.copy(to = value) }
        fetch()
    }

    private fun fetch() {
        fetchJob?.cancel()
        val current = _state.value
        val rangeError = validateStatsDateRange(current.from, current.to, todayInSystemZone().toString())
        if (rangeError != null) {
            _state.update { it.copy(dateRangeError = rangeError, isLoading = false, error = null) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null, dateRangeError = null) }
        fetchJob = viewModelScope.launch {
            try {
                val stats = adminRepository.getStats(
                    granularity = current.granularity,
                    from = current.from.ifBlank { null },
                    to = current.to.ifBlank { null },
                )
                _state.update { it.copy(isLoading = false, stats = stats) }
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
