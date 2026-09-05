package com.dmb.chantiertracker.presentation.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.Attachment
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.ConsumptionLine
import com.dmb.chantiertracker.domain.model.CreateConsumptionLineInput
import com.dmb.chantiertracker.domain.model.CreatePurchaseLineInput
import com.dmb.chantiertracker.domain.model.DailyLogDetail
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.Material
import com.dmb.chantiertracker.domain.model.MaterialStock
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.PurchaseLine
import com.dmb.chantiertracker.domain.model.StageStatus
import com.dmb.chantiertracker.domain.model.UpdateConsumptionLineInput
import com.dmb.chantiertracker.domain.model.UpdatePurchaseLineInput
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
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.entry_summary_required_work
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
    val isAdmin: Boolean = false,
    val currency: String? = null,
    val materials: List<Material> = emptyList(),
    val stock: List<MaterialStock> = emptyList(),
    val purchaseLines: List<PurchaseLine> = emptyList(),
    val consumptionLines: List<ConsumptionLine> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val editingEntryLocalId: String? = null,
    val summaryError: StringResource? = null,
    val isSubmitting: Boolean = false,
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
    private var projectLocalId: String? = null

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
                    projectLocalId = access.projectLocalId
                    _state.update {
                        it.copy(
                            isLoading = false,
                            detail = access.detail,
                            canEdit = access.canEdit,
                            isAdmin = access.isAdmin,
                            currency = access.currency,
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
    // an ADMIN regardless of date (see DailyLogScreen — `isAdmin`, not
    // `canEdit`, gates the delete buttons), mirroring the backend's general
    // "suppression réservée à l'ADMIN" rule.
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

    // ─── materials referential ──────────────────────────────────────────────

    /** Creates the material (or reuses an existing one with the same name) for the project currently loaded. */
    suspend fun createMaterial(name: String, unit: String): Material? {
        val projectId = projectLocalId ?: return null
        return materialRepository.createMaterial(projectId, name.trim(), unit.trim())
    }

    // ─── purchase lines ──────────────────────────────────────────────────────

    suspend fun createPurchaseLine(entryLocalId: String, materialLocalId: String, quantity: Double, unitPrice: Double, supplier: String?) {
        purchaseLineRepository.createLine(entryLocalId, CreatePurchaseLineInput(materialLocalId, quantity, unitPrice, supplier))
    }

    suspend fun updatePurchaseLine(lineLocalId: String, quantity: Double, unitPrice: Double, supplier: String?) {
        purchaseLineRepository.updateLine(lineLocalId, UpdatePurchaseLineInput(quantity, unitPrice, supplier))
    }

    fun deletePurchaseLine(lineLocalId: String) {
        viewModelScope.launch { purchaseLineRepository.deleteLine(lineLocalId) }
    }

    // ─── consumption lines (stock-limited) ──────────────────────────────────

    /** Returns `false` without writing anything if [quantity] would exceed the material's available stock. */
    suspend fun createConsumptionLine(entryLocalId: String, materialLocalId: String, quantity: Double): Boolean {
        val ceiling = availableCeiling(_state.value.stock, materialLocalId, editingLineQuantity = null)
        if (quantity > ceiling) return false
        consumptionLineRepository.createLine(entryLocalId, CreateConsumptionLineInput(materialLocalId, quantity))
        return true
    }

    /** Same stock guard as [createConsumptionLine], but the line's own current quantity is given back before checking the ceiling. */
    suspend fun updateConsumptionLine(lineLocalId: String, materialLocalId: String, quantity: Double): Boolean {
        val currentQuantity = _state.value.consumptionLines.firstOrNull { it.localId == lineLocalId }?.quantity
        val ceiling = availableCeiling(_state.value.stock, materialLocalId, editingLineQuantity = currentQuantity)
        if (quantity > ceiling) return false
        consumptionLineRepository.updateLine(lineLocalId, UpdateConsumptionLineInput(quantity))
        return true
    }

    fun deleteConsumptionLine(lineLocalId: String) {
        viewModelScope.launch { consumptionLineRepository.deleteLine(lineLocalId) }
    }

    // ─── attachments (justificatifs — PURCHASE entry only) ──────────────────
    // Deleting one follows canEdit, not isAdmin: unlike a purchase/consumption
    // line, a mis-attached photo has no effect on stock or budget, so the
    // backend doesn't reserve its deletion to an ADMIN (see
    // EntryWriteAccessService / CONTEXTE.md "Suppression").

    suspend fun addAttachment(entryLocalId: String, bytes: ByteArray, originalName: String, mimeType: String) {
        attachmentRepository.addAttachment(entryLocalId, bytes, originalName, mimeType)
    }

    fun deleteAttachment(attachmentLocalId: String) {
        viewModelScope.launch { attachmentRepository.deleteAttachment(attachmentLocalId) }
    }
}
