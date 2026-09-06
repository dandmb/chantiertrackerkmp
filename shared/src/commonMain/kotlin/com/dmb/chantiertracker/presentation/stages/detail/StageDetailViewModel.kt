package com.dmb.chantiertracker.presentation.stages.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DailyLog
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.StageDetail
import com.dmb.chantiertracker.domain.model.StageStatus
import com.dmb.chantiertracker.domain.model.projectAdmin
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.domain.repository.DailyLogRepository
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import com.dmb.chantiertracker.domain.repository.StageRepository
import com.dmb.chantiertracker.presentation.todayIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

data class StageDetailUiState(
    val isLoading: Boolean = true,
    val detail: StageDetail? = null,
    val currency: String? = null,
    val logs: List<DailyLog> = emptyList(),
    val canAddToday: Boolean = false,
    val todayDate: String? = null,
) {
    val isMissing: Boolean get() = !isLoading && detail == null
    val todayLogLocalId: String? get() = logs.firstOrNull { it.date == todayDate }?.localId
}

private data class StageContext(
    val detail: StageDetail?,
    val currency: String?,
    val canAddToday: Boolean,
    val todayDate: String?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StageDetailViewModel(
    private val stageRepository: StageRepository,
    private val projectRepository: ProjectRepository,
    private val dailyLogRepository: DailyLogRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StageDetailUiState())
    val state = _state.asStateFlow()

    private var localId: String? = null

    fun load(stageLocalId: String) {
        if (localId == stageLocalId) return
        localId = stageLocalId

        viewModelScope.launch {
            combine(
                stageRepository.observeStage(stageLocalId).flatMapLatest(::contextFor),
                dailyLogRepository.observeLogs(stageLocalId),
            ) { context, logs ->
                StageDetailUiState(
                    isLoading = false,
                    detail = context.detail,
                    currency = context.currency,
                    logs = logs,
                    canAddToday = context.canAddToday,
                    todayDate = context.todayDate,
                )
            }.collect { _state.value = it }
        }
        viewModelScope.launch { stageRepository.refreshStage(stageLocalId) }
        viewModelScope.launch { dailyLogRepository.refreshLogs(stageLocalId) }
    }

    // Same rule as EntryWriteAccessService.assertProjectAndStageActive: an
    // ADMIN can always add today's entry; a SUPERVISOR only while the project
    // is IN_PROGRESS and the stage isn't COMPLETED. "Today" itself is
    // resolved in the PROJECT's timezone (see DailyLogViewModel).
    private fun contextFor(detail: StageDetail?): Flow<StageContext> {
        if (detail == null) return flowOf(StageContext(null, null, canAddToday = false, todayDate = null))
        return combine(
            projectRepository.observeProject(detail.projectLocalId),
            projectRepository.observeMembers(detail.projectLocalId),
        ) { project, members ->
            val currentUserId = (authRepository.authState.value as? AuthState.Authenticated)?.user?.id
            val isAdmin = project != null && projectAdmin(project.ownerId, members, currentUserId)
            val projectAndStageActive = project?.status == ProjectStatus.IN_PROGRESS && detail.status != StageStatus.COMPLETED
            StageContext(
                detail = detail,
                currency = project?.currency,
                canAddToday = isAdmin || projectAndStageActive,
                todayDate = project?.let { todayIn(it.timezone).toString() },
            )
        }
    }

    fun retry() {
        val id = localId ?: return
        viewModelScope.launch { stageRepository.refreshStage(id) }
        viewModelScope.launch { dailyLogRepository.refreshLogs(id) }
    }

    /** Creates (or reuses) today's entry of [type] and returns the day's local id to navigate to, or null if not ready yet. */
    suspend fun addTodayEntry(type: EntryType): String? {
        val stageId = localId ?: return null
        val date = _state.value.todayDate ?: return null
        return when (type) {
            EntryType.PURCHASE -> dailyLogRepository.createPurchaseEntry(stageId, date)
            EntryType.WORK -> dailyLogRepository.createWorkEntry(stageId, date)
            EntryType.UNKNOWN -> null
        }
    }
}
