package com.dmb.chantiertracker.presentation.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.Attachment
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.ConsumptionLine
import com.dmb.chantiertracker.domain.model.DailyLogDetail
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.Material
import com.dmb.chantiertracker.domain.model.MaterialStock
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.PurchaseLine
import com.dmb.chantiertracker.domain.model.StageStatus
import com.dmb.chantiertracker.domain.model.UploadFile
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.buffered

data class AttachmentUploadUi(val stage: Stage) {
    enum class Stage { Finalizing }
}

data class DailyLogUiState(
    val isLoading: Boolean = true,
    val detail: DailyLogDetail? = null,
    val canEdit: Boolean = false,
    val isAdmin: Boolean = false,
    val currentUserId: Long? = null,
    val currency: String? = null,
    val projectLocalId: String? = null,
    val materials: List<Material> = emptyList(),
    val stock: List<MaterialStock> = emptyList(),
    val purchaseLines: List<PurchaseLine> = emptyList(),
    val consumptionLines: List<ConsumptionLine> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    // Non-null while a photo/video is being added, from the moment it's picked
    // until the new row is actually visible in `attachments` (ADR-39) — the UI
    // never shows a finished state with nothing in the list.
    val attachmentUpload: AttachmentUploadUi? = null,
    val attachmentError: DomainException? = null,
    val videoTooLong: VideoDurationCheck.TooLong? = null,
    val videoTooHeavy: VideoWeightCheck.TooHeavy? = null,
    // The project OWNER's allowance — gates the "Add a video" affordance and the duration pre-check.
    val ownerVideoLimits: OwnerVideoLimits? = null,
    // Project IN_PROGRESS and stage not COMPLETED — the only server-side gate on
    // creating a report (no date restriction, unlike a line).
    val projectAndStageActive: Boolean = false,
) {
    val isMissing: Boolean get() = !isLoading && detail == null

    // The backend lets a SUPERVISOR delete only what they sent (403 PROJECT_INSUFFICIENT_ROLE
    // otherwise); an attachment not on the server yet can only be theirs. Unknown author: hidden.
    fun canDelete(attachment: Attachment): Boolean =
        canEdit && (isAdmin || !attachment.sentToTheServer || (currentUserId != null && attachment.uploadedById == currentUserId))
    val canAddVideo: Boolean get() = VideoLimit.canAdd(ownerVideoLimits)

    // "Flag an issue" is for a member who can see the entry but can't fix it
    // themselves — mirrors the web's `!isAdmin && !canEdit && projectAndStageActive`.
    // An ADMIN or a SUPERVISOR editing today would just correct it directly.
    val canReport: Boolean get() = !isAdmin && !canEdit && projectAndStageActive
}

private data class LogAccess(
    val detail: DailyLogDetail?,
    val canEdit: Boolean,
    val isAdmin: Boolean,
    val currency: String?,
    val projectLocalId: String?,
    val ownerVideoLimits: OwnerVideoLimits? = null,
    val projectAndStageActive: Boolean = false,
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
                combine(materialRepository.observeMaterials(projectId), materialRepository.observeStock(projectId)) { m, s -> m to s.materials }
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
                            currentUserId = (authRepository.authState.value as? AuthState.Authenticated)?.user?.id,
                            currency = access.currency,
                            projectLocalId = access.projectLocalId,
                            materials = materialsStock.first,
                            stock = materialsStock.second,
                            purchaseLines = lines.first,
                            consumptionLines = lines.second,
                            attachments = lines.third,
                            ownerVideoLimits = access.ownerVideoLimits,
                            projectAndStageActive = access.projectAndStageActive,
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
                        ownerVideoLimits = project?.ownerVideoLimits(),
                        projectAndStageActive = projectAndStageActive,
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
    fun onPhotoSelected(entryLocalId: String, bytes: ByteArray, originalName: String, mimeType: String) {
        addAttachment(AttachmentUploadUi(AttachmentUploadUi.Stage.Finalizing)) {
            attachmentRepository.addAttachment(entryLocalId, bytes, originalName, mimeType)
        }
    }

    fun onVideoSelected(entryLocalId: String, video: UploadFile) {
        _state.update { it.copy(attachmentError = null, videoTooLong = null, videoTooHeavy = null) }
        val tooHeavy = VideoLimit.checkWeight(video.size()) as? VideoWeightCheck.TooHeavy
        if (tooHeavy != null) {
            _state.update { it.copy(videoTooHeavy = tooHeavy) }
            return
        }
        val limits = _state.value.ownerVideoLimits
        viewModelScope.launch {
            // Reads only a bounded prefix of the file (Mp4Duration), so it's
            // cheap enough to run inline — no full read, no ByteArray.
            val duration = runCatching {
                video.openSource().buffered().use { probeMp4DurationSeconds(it) }
            }.getOrNull()
            when (val check = VideoLimit.check(limits, duration)) {
                is VideoDurationCheck.TooLong -> _state.update { it.copy(videoTooLong = check) }
                VideoDurationCheck.Ok -> addAttachment(AttachmentUploadUi(AttachmentUploadUi.Stage.Finalizing)) {
                    attachmentRepository.addVideo(entryLocalId, video)
                }
            }
        }
    }

    // Runs [add], then keeps the indicator on `Finalizing` until the row it
    // returns is actually observable in `attachments` — the Room Flow re-emits
    // asynchronously after the write, so clearing the indicator on return left a
    // visible "done but empty" gap (ADR-39). A timeout guards against a Flow
    // that never catches up, so the user is never stuck.
    private fun addAttachment(initial: AttachmentUploadUi, add: suspend () -> Attachment) {
        _state.update { it.copy(attachmentError = null, videoTooLong = null, videoTooHeavy = null, attachmentUpload = initial) }
        viewModelScope.launch {
            try {
                val added = add()
                _state.update { it.copy(attachmentUpload = AttachmentUploadUi(AttachmentUploadUi.Stage.Finalizing)) }
                withTimeoutOrNull(FINALIZE_TIMEOUT_MS) {
                    state.first { ui -> ui.attachments.any { it.localId == added.localId } }
                }
            } catch (e: DomainException) {
                _state.update { it.copy(attachmentError = e) }
            } catch (e: Throwable) {
                _state.update { it.copy(attachmentError = DomainException.Unexpected) }
            } finally {
                _state.update { it.copy(attachmentUpload = null) }
            }
        }
    }

    fun clearAttachmentError() = _state.update { it.copy(attachmentError = null, videoTooLong = null, videoTooHeavy = null) }

    fun deleteAttachment(attachmentLocalId: String) {
        viewModelScope.launch { attachmentRepository.deleteAttachment(attachmentLocalId) }
    }

    private companion object {
        // Room's observing Flow normally catches up in well under a second; this
        // is only a safety net so a stuck Flow can't pin the indicator forever.
        const val FINALIZE_TIMEOUT_MS = 4_000L
    }
}
