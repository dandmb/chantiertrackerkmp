package com.dmb.chantiertracker.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.SyncIssueAction
import com.dmb.chantiertracker.domain.model.SyncIssueItem
import com.dmb.chantiertracker.domain.model.actions
import com.dmb.chantiertracker.domain.model.countsToReview
import com.dmb.chantiertracker.domain.model.removesLocalDataWhenAcknowledged
import com.dmb.chantiertracker.domain.model.serverValueKnownLocally
import com.dmb.chantiertracker.domain.repository.RevertOutcome
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
    val listed: Int = 0,
    val retryingKey: String? = null,
    val notice: RetryOutcome? = null,
    val isOnline: Boolean = true,
    val busyKey: String? = null,
    val confirmation: SyncIssueConfirmation? = null,
    val actionNotice: SyncIssueActionNotice? = null,
) {
    val isEmpty: Boolean get() = !isLoading && listed == 0

    fun canRevert(item: SyncIssueItem): Boolean = isOnline || item.serverValueKnownLocally

    val isBusy: Boolean get() = retryingKey != null || busyKey != null
}

enum class SyncIssueActionNotice { DISCARDED, ACKNOWLEDGED, REVERTED, REVERT_NEEDS_CONNECTION, REVERT_FAILED }

data class SyncIssueConfirmation(val item: SyncIssueItem, val action: SyncIssueAction, val linkedCount: Int)

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
            repository.observeOnline().collect { online -> _state.update { it.copy(isOnline = online) } }
        }
        viewModelScope.launch {
            repository.observeIssues().collect { items ->
                _state.update {
                    it.copy(isLoading = false, projects = groupSyncIssues(items), total = items.count { item -> item.issue.countsToReview }, listed = items.size)
                }
            }
        }
    }

    fun retry(item: SyncIssueItem) {
        if (_state.value.isBusy) return
        _state.update { it.copy(retryingKey = item.key, notice = null, actionNotice = null) }
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

    fun dismissNotice() = _state.update { it.copy(notice = null, actionNotice = null) }

    fun discard(item: SyncIssueItem) {
        if (SyncIssueAction.DISCARD !in item.actions || _state.value.isBusy) return
        viewModelScope.launch {
            val linked = repository.linkedCount(item)
            _state.update { it.copy(confirmation = SyncIssueConfirmation(item, SyncIssueAction.DISCARD, linked)) }
        }
    }

    fun acknowledge(item: SyncIssueItem) {
        if (SyncIssueAction.ACKNOWLEDGE !in item.actions || _state.value.isBusy) return
        if (!item.removesLocalDataWhenAcknowledged) return perform(item, SyncIssueActionNotice.ACKNOWLEDGED) { repository.acknowledge(item) }
        viewModelScope.launch {
            val linked = repository.linkedCount(item)
            if (linked > 0) {
                _state.update { it.copy(confirmation = SyncIssueConfirmation(item, SyncIssueAction.ACKNOWLEDGE, linked)) }
            } else {
                perform(item, SyncIssueActionNotice.ACKNOWLEDGED) { repository.acknowledge(item) }
            }
        }
    }

    fun confirm() {
        val confirmation = _state.value.confirmation ?: return
        _state.update { it.copy(confirmation = null) }
        when (confirmation.action) {
            SyncIssueAction.DISCARD -> perform(confirmation.item, SyncIssueActionNotice.DISCARDED) { repository.discard(confirmation.item) }
            SyncIssueAction.ACKNOWLEDGE -> perform(confirmation.item, SyncIssueActionNotice.ACKNOWLEDGED) { repository.acknowledge(confirmation.item) }
            SyncIssueAction.FIX, SyncIssueAction.RETRY, SyncIssueAction.REVERT -> Unit
        }
    }

    fun dismissConfirmation() = _state.update { it.copy(confirmation = null) }

    fun revert(item: SyncIssueItem) {
        if (SyncIssueAction.REVERT !in item.actions || _state.value.isBusy) return
        if (!_state.value.canRevert(item)) {
            _state.update { it.copy(notice = null, actionNotice = SyncIssueActionNotice.REVERT_NEEDS_CONNECTION) }
            return
        }
        perform(item, onSuccess = null) {
            when (repository.revert(item)) {
                RevertOutcome.RESTORED -> SyncIssueActionNotice.REVERTED
                RevertOutcome.NEEDS_CONNECTION -> SyncIssueActionNotice.REVERT_NEEDS_CONNECTION
                RevertOutcome.FAILED -> SyncIssueActionNotice.REVERT_FAILED
            }
        }
    }

    private fun perform(item: SyncIssueItem, onSuccess: SyncIssueActionNotice?, action: suspend () -> Any?) {
        if (_state.value.isBusy) return
        _state.update { it.copy(busyKey = item.key, notice = null, actionNotice = null) }
        viewModelScope.launch {
            val notice = try {
                val result = action()
                onSuccess ?: result as? SyncIssueActionNotice
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
            _state.update { it.copy(busyKey = null, actionNotice = notice) }
        }
    }
}

class SyncIssueCountViewModel(repository: SyncIssueRepository) : ViewModel() {
    val count: StateFlow<Int> = repository.observeIssueCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
