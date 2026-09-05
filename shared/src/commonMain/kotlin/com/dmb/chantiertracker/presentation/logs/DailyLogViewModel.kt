package com.dmb.chantiertracker.presentation.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.Attachment
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.ConsumptionLine
import com.dmb.chantiertracker.domain.model.DailyLogDetail
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.Material
import com.dmb.chantiertracker.domain.model.MaterialStock
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.PurchaseLine
import com.dmb.chantiertracker.domain.model.StageStatus
import com.dmb.chantiertracker.domain.model.projectAdmin
import com.dmb.chantiertracker.domain.repository.AttachmentRepository
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.domain.repository.ConsumptionLineRepository
import com.dmb.chantiertracker.domain.repository.DailyLogRepository
import com.dmb.chantiertracker.domain.repository.MaterialRepository
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import com.dmb.chantiertracker.domain.repository.PurchaseLineRepository
import com.dmb.chantiertracker.domain.repository.StageRepository
import com.dmb.chantiertracker.presentation.todayIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DailyLogUiState(
    val isLoading: Boolean = true,
    val detail: DailyLogDetail? = null,
    val canEdit: Boolean = false,
    val isAdmin: Boolean = false,
    val currency: String? = null,
    val projectLocalId: String? = null,
    val materials: List<Material> = emptyList(),
    val stock: List<MaterialStock> = emptyList(),
    val purchaseLines: List<PurchaseLine> = emptyList(),
    val consumptionLines: List<ConsumptionLine> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
) {
    val isMissing: Boolean get() = !isLoading && detail == null
}

private data class LogAccess(
    val detail: DailyLogDetail?,
    val canEdit: Boolean,
    val isAdmin: Boolean,
    val currency: String?,
    val projectLocalId: String?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DailyLogViewModel(
    private val dailyLogRepository: DailyLogRepository,
    private val stageRepository: StageRepository,
    private val projectRepository: ProjectRepository,
    private val authRepository: AuthRepository,
    private val materialRepository: MaterialRepository,
    private val purchaseLineRepository: PurchaseLineRepository,
    private val consumptionLineRepository: ConsumptionLineRepository,
    private val attachmentRepository: AttachmentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DailyLogUiState())
    val state = _state.asStateFlow()

    private var localId: String? = null

    fun load(dailyLogLocalId: String) {
        if (localId == dailyLogLocalId) return
        localId = dailyLogLocalId

        val accessFlow = dailyLogRepository.observeLog(dailyLogLocalId).flatMapLatest(::writeAccessFor)

        val materialsAndStockFlow = accessFlow.flatMapLatest { access ->
            val projectId = access.projectLocalId
            if (projectId == null) {
                flowOf(emptyList<Material>() to emptyList<MaterialStock>())
            } else {
                combine(materialRepository.observeMaterials(projectId), materialRepository.observeStock(projectId)) { m, s -> m to s }
            }
        }

        val linesFlow = accessFlow.flatMapLatest { access ->
            val purchaseEntryId = access.detail?.entries?.firstOrNull { it.type == EntryType.PURCHASE }?.localId
            val workEntryId = access.detail?.entries?.firstOrNull { it.type == EntryType.WORK }?.localId
            combine(
                purchaseEntryId?.let(purchaseLineRepository::observeLines) ?: flowOf(emptyList()),
                workEntryId?.let(consumptionLineRepository::observeLines) ?: flowOf(emptyList()),
                purchaseEntryId?.let(attachmentRepository::observeAttachments) ?: flowOf(emptyList()),
            ) { p, c, a -> Triple(p, c, a) }
        }

        viewModelScope.launch {
            combine(accessFlow, materialsAndStockFlow, linesFlow) { access, materialsStock, lines -> Triple(access, materialsStock, lines) }
                .collect { (access, materialsStock, lines) ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            detail = access.detail,
                            canEdit = access.canEdit,
                            isAdmin = access.isAdmin,
                            currency = access.currency,
                            projectLocalId = access.projectLocalId,
                            materials = materialsStock.first,
                            stock = materialsStock.second,
                            purchaseLines = lines.first,
                            consumptionLines = lines.second,
                            attachments = lines.third,
                        )
                    }
                }
        }
        viewModelScope.launch { dailyLogRepository.refreshLog(dailyLogLocalId) }
    }

    // A SUPERVISOR may only write on a day that is "today" in the PROJECT's
    // own timezone, and only while the project is IN_PROGRESS and the stage
    // isn't COMPLETED — mirrors EntryWriteAccessService.assertCanWrite on the
    // backend. An ADMIN is never restricted. Deleting a line is reserved to
    // an ADMIN regardless of date (`isAdmin`, not `canEdit`, gates the delete
    // buttons), mirroring the backend's "suppression réservée à l'ADMIN" rule.
    private fun writeAccessFor(log: DailyLogDetail?): Flow<LogAccess> = if (log == null) {
        flowOf(LogAccess(null, canEdit = false, isAdmin = false, currency = null, projectLocalId = null))
    } else {
        stageRepository.observeStage(log.stageLocalId).flatMapLatest { stage ->
            val projectId = stage?.projectLocalId
            if (projectId == null) {
                flowOf(LogAccess(log, canEdit = false, isAdmin = false, currency = null, projectLocalId = null))
            } else {
                combine(
                    projectRepository.observeProject(projectId),
                    projectRepository.observeMembers(projectId),
                ) { project, members ->
                    val currentUserId = (authRepository.authState.value as? AuthState.Authenticated)?.user?.id
                    val isAdmin = project != null && projectAdmin(project.ownerId, members, currentUserId)
                    val projectAndStageActive =
                        project?.status == ProjectStatus.IN_PROGRESS && stage.status != StageStatus.COMPLETED
                    val isToday = project != null && log.date == todayIn(project.timezone).toString()
                    LogAccess(
                        detail = log,
                        canEdit = isAdmin || (isToday && projectAndStageActive),
                        isAdmin = isAdmin,
                        currency = project?.currency,
                        projectLocalId = projectId,
                    )
                }
            }
        }
    }

    fun retry() {
        localId?.let { id -> viewModelScope.launch { dailyLogRepository.refreshLog(id) } }
    }

    // Creates a locally-persisted entry with a blank summary — no intermediate
    // "résumé"-only step, same as the web. The summary is filled in afterwards
    // on the dedicated EntrySummaryScreen.
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

    fun deletePurchaseLine(lineLocalId: String) {
        viewModelScope.launch { purchaseLineRepository.deleteLine(lineLocalId) }
    }

    fun deleteConsumptionLine(lineLocalId: String) {
        viewModelScope.launch { consumptionLineRepository.deleteLine(lineLocalId) }
    }

    // Deleting a photo follows canEdit, not isAdmin: unlike a purchase/
    // consumption line, a mis-attached photo has no effect on stock or budget,
    // so the backend doesn't reserve its deletion to an ADMIN.
    suspend fun addAttachment(entryLocalId: String, bytes: ByteArray, originalName: String, mimeType: String) {
        attachmentRepository.addAttachment(entryLocalId, bytes, originalName, mimeType)
    }

    fun deleteAttachment(attachmentLocalId: String) {
        viewModelScope.launch { attachmentRepository.deleteAttachment(attachmentLocalId) }
    }
}
