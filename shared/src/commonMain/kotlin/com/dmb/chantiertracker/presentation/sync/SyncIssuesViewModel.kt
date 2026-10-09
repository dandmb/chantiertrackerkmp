package com.dmb.chantiertracker.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.SyncIssueItem
import com.dmb.chantiertracker.domain.repository.RetryOutcome
import com.dmb.chantiertracker.domain.repository.SyncIssueRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SyncIssueDayGroup(val dailyLogLocalId: String, val date: String, val items: List<SyncIssueItem>)

data class SyncIssueStageGroup(
    val stageLocalId: String,
    val stageName: String,
    val items: List<SyncIssueItem>,
    val days: List<SyncIssueDayGroup>,
)

data class SyncIssueProjectGroup(
    val projectLocalId: String,
    val projectName: String,
    val items: List<SyncIssueItem>,
    val stages: List<SyncIssueStageGroup>,
)

data class SyncIssuesUiState(
    val isLoading: Boolean = true,
    val projects: List<SyncIssueProjectGroup> = emptyList(),
    val total: Int = 0,
    val retryingKey: String? = null,
    val notice: RetryOutcome? = null,
) {
    val isEmpty: Boolean get() = !isLoading && total == 0
}

fun groupSyncIssues(items: List<SyncIssueItem>): List<SyncIssueProjectGroup> =
    items.groupBy { it.projectLocalId }
        .map { (projectLocalId, ofProject) ->
            SyncIssueProjectGroup(
                projectLocalId = projectLocalId,
                projectName = ofProject.first().projectName,
                items = ofProject.filter { it.stageLocalId == null }.inDisplayOrder(),
                stages = ofProject.filter { it.stageLocalId != null }
                    .groupBy { it.stageLocalId!! }
                    .map { (stageLocalId, ofStage) ->
                        SyncIssueStageGroup(
                            stageLocalId = stageLocalId,
                            stageName = ofStage.first().stageName.orEmpty(),
                            items = ofStage.filter { it.dailyLogLocalId == null }.inDisplayOrder(),
                            days = ofStage.filter { it.dailyLogLocalId != null }
                                .groupBy { it.dailyLogLocalId!! }
                                .map { (dailyLogLocalId, ofDay) ->
                                    SyncIssueDayGroup(dailyLogLocalId, ofDay.first().date.orEmpty(), ofDay.inDisplayOrder())
                                }
                                .sortedWith(compareByDescending<SyncIssueDayGroup> { it.date }.thenBy { it.dailyLogLocalId }),
                        )
                    }
                    .sortedWith(compareBy({ it.stageName.lowercase() }, { it.stageLocalId })),
            )
        }
        .sortedWith(compareBy({ it.projectName.lowercase() }, { it.projectLocalId }))

private fun List<SyncIssueItem>.inDisplayOrder(): List<SyncIssueItem> =
    sortedWith(compareBy({ it.entryType?.ordinal ?: -1 }, { it.target.ordinal }, { it.label.orEmpty().lowercase() }, { it.localId }))

class SyncIssuesViewModel(private val repository: SyncIssueRepository) : ViewModel() {

    private val _state = MutableStateFlow(SyncIssuesUiState())
    val state: StateFlow<SyncIssuesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeIssues().collect { items ->
                _state.update { it.copy(isLoading = false, projects = groupSyncIssues(items), total = items.size) }
            }
        }
    }

    fun retry(item: SyncIssueItem) {
        if (_state.value.retryingKey != null) return
        _state.update { it.copy(retryingKey = item.key, notice = null) }
        viewModelScope.launch {
            val outcome = try {
                repository.retry(item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                RetryOutcome.NOT_SENT
            }
            _state.update { it.copy(retryingKey = null, notice = outcome) }
        }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }
}

class SyncIssueCountViewModel(repository: SyncIssueRepository) : ViewModel() {
    val count: StateFlow<Int> = repository.observeIssueCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
