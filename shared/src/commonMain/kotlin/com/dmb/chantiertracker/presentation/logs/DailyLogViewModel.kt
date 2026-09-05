package com.dmb.chantiertracker.presentation.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DailyLogDetail
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.StageStatus
import com.dmb.chantiertracker.domain.model.projectAdmin
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.domain.repository.DailyLogRepository
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import com.dmb.chantiertracker.domain.repository.StageRepository
import com.dmb.chantiertracker.presentation.todayIn
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.entry_summary_required_work
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

data class DailyLogUiState(
    val isLoading: Boolean = true,
    val detail: DailyLogDetail? = null,
    val canEdit: Boolean = false,
    val editingEntryLocalId: String? = null,
    val summaryError: StringResource? = null,
    val isSubmitting: Boolean = false,
) {
    val isMissing: Boolean get() = !isLoading && detail == null
}

private data class LoadedLog(val detail: DailyLogDetail?, val canEdit: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class DailyLogViewModel(
    private val dailyLogRepository: DailyLogRepository,
    private val stageRepository: StageRepository,
    private val projectRepository: ProjectRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DailyLogUiState())
    val state = _state.asStateFlow()

    private var localId: String? = null

    fun load(dailyLogLocalId: String) {
        if (localId == dailyLogLocalId) return
        localId = dailyLogLocalId

        viewModelScope.launch {
            dailyLogRepository.observeLog(dailyLogLocalId)
                .flatMapLatest { log -> writeAccessFor(log) }
                .collect { (detail, canEdit) ->
                    _state.update { it.copy(isLoading = false, detail = detail, canEdit = canEdit) }
                }
        }
        viewModelScope.launch { dailyLogRepository.refreshLog(dailyLogLocalId) }
    }

    // A SUPERVISOR may only write on a day that is "today" in the PROJECT's
    // own timezone, and only while the project is IN_PROGRESS and the stage
    // isn't COMPLETED — mirrors EntryWriteAccessService.assertCanWrite on the
    // backend. An ADMIN is never restricted.
    private fun writeAccessFor(log: DailyLogDetail?) = if (log == null) {
        flowOf(LoadedLog(null, canEdit = false))
    } else {
        stageRepository.observeStage(log.stageLocalId).flatMapLatest { stage ->
            val projectLocalId = stage?.projectLocalId
            if (projectLocalId == null) {
                flowOf(LoadedLog(log, canEdit = false))
            } else {
                combine(
                    projectRepository.observeProject(projectLocalId),
                    projectRepository.observeMembers(projectLocalId),
                ) { project, members ->
                    val currentUserId = (authRepository.authState.value as? AuthState.Authenticated)?.user?.id
                    val isAdmin = project != null && projectAdmin(project.ownerId, members, currentUserId)
                    val projectAndStageActive =
                        project?.status == ProjectStatus.IN_PROGRESS && stage.status != StageStatus.COMPLETED
                    val isToday = project != null && log.date == todayIn(project.timezone).toString()
                    LoadedLog(log, canEdit = isAdmin || (isToday && projectAndStageActive))
                }
            }
        }
    }

    fun retry() {
        localId?.let { id -> viewModelScope.launch { dailyLogRepository.refreshLog(id) } }
    }

    // Goes straight to a locally-persisted entry with a blank summary — no
    // intermediate "résumé"-only step, same as the web. The summary is filled
    // in later via startEditingSummary/saveSummary.
    fun addEntry(type: EntryType) {
        val detail = _state.value.detail ?: return
        viewModelScope.launch {
            when (type) {
                EntryType.PURCHASE -> dailyLogRepository.createPurchaseEntry(detail.stageLocalId, detail.date)
                EntryType.WORK -> dailyLogRepository.createWorkEntry(detail.stageLocalId, detail.date)
                EntryType.UNKNOWN -> Unit
            }
        }
    }

    fun startEditingSummary(entryLocalId: String) {
        _state.update { it.copy(editingEntryLocalId = entryLocalId, summaryError = null) }
    }

    fun cancelEditingSummary() {
        _state.update { it.copy(editingEntryLocalId = null, summaryError = null) }
    }

    fun saveSummary(summary: String) {
        val entryId = _state.value.editingEntryLocalId ?: return
        val entry = _state.value.detail?.entries?.firstOrNull { it.localId == entryId } ?: return

        if (entry.type == EntryType.WORK && summary.isBlank()) {
            _state.update { it.copy(summaryError = Res.string.entry_summary_required_work) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, summaryError = null) }
            dailyLogRepository.updateEntry(entryId, summary.trim())
            _state.update { it.copy(isSubmitting = false, editingEntryLocalId = null) }
        }
    }
}
