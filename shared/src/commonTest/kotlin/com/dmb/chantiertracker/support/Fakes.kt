package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.core.BuildInfo
import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.local.OnboardingStore
import com.dmb.chantiertracker.data.local.TokenStorage
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectMember
import com.dmb.chantiertracker.domain.model.UpdateProjectInput
import com.dmb.chantiertracker.domain.repository.AccountRepository
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeBuildInfo(
    override val isDebug: Boolean,
    override val appVersion: String = "1.0-test",
    override val isStaging: Boolean = false,
) : BuildInfo

class FakeTokenStorage(initial: AuthTokens? = null) : TokenStorage {
    var tokens: AuthTokens? = initial
    var clearCount = 0

    override suspend fun get(): AuthTokens? = tokens
    override suspend fun save(tokens: AuthTokens) { this.tokens = tokens }
    override suspend fun clear() { tokens = null; clearCount++ }
}

class FakeOnboardingStore(initial: Boolean = false, onboardingSeen: Boolean = false) : OnboardingStore {
    var firstLoginCompleted = initial
        private set
    var onboardingSeen = onboardingSeen
        private set

    override suspend fun hasCompletedFirstLogin(): Boolean = firstLoginCompleted
    override suspend fun markFirstLoginCompleted() { firstLoginCompleted = true }
    override suspend fun hasSeenOnboarding(): Boolean = onboardingSeen
    override suspend fun markOnboardingSeen() { onboardingSeen = true }
}

class FakeAuthRepository : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    override val authState: StateFlow<AuthState> = _authState

    var error: Throwable? = null
    var firstLoginCompleted = false
    var onboardingSeen = false
    val calls = mutableListOf<String>()

    fun emitState(state: AuthState) { _authState.value = state }

    private fun record(call: String) {
        calls += call
        error?.let { throw it }
    }

    override suspend fun hasCompletedFirstLogin(): Boolean = firstLoginCompleted
    override suspend fun hasSeenOnboarding(): Boolean = onboardingSeen
    override suspend fun markOnboardingSeen() { onboardingSeen = true; calls += "markOnboardingSeen" }
    override suspend fun bootstrap() { record("bootstrap") }
    override suspend fun register(email: String, password: String, name: String) = record("register:$email:$password:$name")
    override suspend fun verifyEmail(email: String, code: String) = record("verifyEmail:$email:$code")
    override suspend fun resendCode(email: String) = record("resendCode:$email")
    override suspend fun login(email: String, password: String) {
        record("login:$email:$password")
        firstLoginCompleted = true
    }
    override suspend fun logout() = record("logout")
    override suspend fun forgotPassword(email: String) = record("forgotPassword:$email")
    override suspend fun resetPassword(email: String, code: String, newPassword: String) =
        record("resetPassword:$email:$code:$newPassword")
}

class FakeBackgroundSync : com.dmb.chantiertracker.data.sync.BackgroundSync {
    var ensurePeriodicCount = 0
        private set
    var expeditedCount = 0
        private set

    override fun ensurePeriodicSync() { ensurePeriodicCount++ }
    override fun requestExpeditedSync() { expeditedCount++ }
}

class FakeSyncer : com.dmb.chantiertracker.data.sync.Syncer {
    var requestCount = 0
        private set
    var syncCount = 0
        private set
    val syncedProjects = mutableListOf<String>()
    val syncedStages = mutableListOf<String>()
    var outcome: com.dmb.chantiertracker.data.sync.SyncOutcome =
        com.dmb.chantiertracker.data.sync.SyncOutcome.Synced
    var onSync: (suspend () -> Unit)? = null

    override fun requestSync() { requestCount++ }

    override suspend fun syncNow(): com.dmb.chantiertracker.data.sync.SyncOutcome {
        syncCount++
        onSync?.invoke()
        return outcome
    }

    override suspend fun syncProject(localId: String): com.dmb.chantiertracker.data.sync.SyncOutcome {
        syncedProjects += localId
        onSync?.invoke()
        return outcome
    }

    override suspend fun syncStage(stageLocalId: String): com.dmb.chantiertracker.data.sync.SyncOutcome {
        syncedStages += stageLocalId
        onSync?.invoke()
        return outcome
    }

    val syncedLogs = mutableListOf<String>()

    override suspend fun syncLog(logLocalId: String): com.dmb.chantiertracker.data.sync.SyncOutcome {
        syncedLogs += logLocalId
        onSync?.invoke()
        return outcome
    }
}

class FakeProjectRepository(
    projects: List<Project> = emptyList(),
    detail: ProjectDetail? = null,
    members: List<ProjectMember> = emptyList(),
) : ProjectRepository {

    val projectsFlow = MutableStateFlow(projects)
    val detailFlow = MutableStateFlow(detail)
    val membersFlow = MutableStateFlow(members)
    val activeProjectCountFlow = MutableStateFlow(0)

    val log = mutableListOf<String>()
    var lastCreateInput: CreateProjectInput? = null
    var lastUpdateInput: UpdateProjectInput? = null
    var refreshCount = 0
        private set
    var refreshProjectCount = 0
        private set
    var createError: Throwable? = null
    var newLocalId = "local-new"
    var onRefresh: (suspend () -> Unit)? = null

    override fun observeProjects() = projectsFlow

    override fun observeProject(localId: String) = detailFlow

    override fun observeMembers(localId: String) = membersFlow

    override fun observeActiveProjectCount(ownerId: Long) = activeProjectCountFlow

    override suspend fun createProject(input: CreateProjectInput): String {
        log += "createProject:${input.name}"
        lastCreateInput = input
        createError?.let { throw it }
        projectsFlow.value = projectsFlow.value + com.dmb.chantiertracker.domain.model.Project(
            localId = newLocalId,
            name = input.name,
            description = input.description,
            location = input.location,
            status = com.dmb.chantiertracker.domain.model.ProjectStatus.IN_PROGRESS,
            createdAt = null,
        )
        return newLocalId
    }

    override suspend fun updateProject(localId: String, input: UpdateProjectInput) {
        log += "updateProject:$localId:${input.name}"
        lastUpdateInput = input
    }

    override suspend fun deleteProject(localId: String) {
        log += "deleteProject:$localId"
    }

    override suspend fun refresh() {
        refreshCount++
        log += "refresh"
        onRefresh?.invoke()
    }

    override suspend fun refreshProject(localId: String) {
        refreshProjectCount++
        log += "refreshProject:$localId"
    }
}

class FakeStageRepository(
    stages: List<com.dmb.chantiertracker.domain.model.Stage> = emptyList(),
    detail: com.dmb.chantiertracker.domain.model.StageDetail? = null,
) : com.dmb.chantiertracker.domain.repository.StageRepository {

    val stagesFlow = MutableStateFlow(stages)
    val detailFlow = MutableStateFlow(detail)

    val log = mutableListOf<String>()
    var lastCreateInput: com.dmb.chantiertracker.domain.model.CreateStageInput? = null
    var lastUpdateInput: com.dmb.chantiertracker.domain.model.UpdateStageInput? = null
    var createError: Throwable? = null
    var newLocalId = "stage-new"
    var refreshStagesCount = 0
        private set
    var refreshStageCount = 0
        private set

    override fun observeStages(projectLocalId: String) = stagesFlow

    override fun observeStage(stageLocalId: String) = detailFlow

    override suspend fun createStage(input: com.dmb.chantiertracker.domain.model.CreateStageInput): String {
        log += "createStage:${input.name}"
        lastCreateInput = input
        createError?.let { throw it }
        return newLocalId
    }

    override suspend fun updateStage(stageLocalId: String, input: com.dmb.chantiertracker.domain.model.UpdateStageInput) {
        log += "updateStage:$stageLocalId:${input.name}"
        lastUpdateInput = input
    }

    override suspend fun deleteStage(stageLocalId: String) {
        log += "deleteStage:$stageLocalId"
    }

    override suspend fun refreshStages(projectLocalId: String) {
        refreshStagesCount++
        log += "refreshStages:$projectLocalId"
    }

    override suspend fun refreshStage(stageLocalId: String) {
        refreshStageCount++
        log += "refreshStage:$stageLocalId"
    }
}

class FakeDailyLogRepository(
    logs: List<com.dmb.chantiertracker.domain.model.DailyLog> = emptyList(),
    detail: com.dmb.chantiertracker.domain.model.DailyLogDetail? = null,
) : com.dmb.chantiertracker.domain.repository.DailyLogRepository {

    val logsFlow = MutableStateFlow(logs)
    val detailFlow = MutableStateFlow(detail)
    val entryFlow = MutableStateFlow<com.dmb.chantiertracker.domain.model.DailyEntry?>(null)

    val log = mutableListOf<String>()
    var createdPurchaseDayId = "log-new"
    var createdWorkDayId = "log-new"
    var lastUpdatedSummary: String? = null
    var refreshLogsCount = 0
        private set
    var refreshLogCount = 0
        private set

    override fun observeLogs(stageLocalId: String) = logsFlow

    override fun observeLog(logLocalId: String) = detailFlow

    override fun observeEntry(entryLocalId: String) = entryFlow

    override suspend fun createPurchaseEntry(stageLocalId: String, date: String): String {
        log += "createPurchaseEntry:$stageLocalId:$date"
        return createdPurchaseDayId
    }

    override suspend fun createWorkEntry(stageLocalId: String, date: String): String {
        log += "createWorkEntry:$stageLocalId:$date"
        return createdWorkDayId
    }

    override suspend fun updateEntry(entryLocalId: String, summary: String) {
        log += "updateEntry:$entryLocalId:$summary"
        lastUpdatedSummary = summary
    }

    override suspend fun refreshLogs(stageLocalId: String) {
        refreshLogsCount++
        log += "refreshLogs:$stageLocalId"
    }

    override suspend fun refreshLog(logLocalId: String) {
        refreshLogCount++
        log += "refreshLog:$logLocalId"
    }
}

class FakeMaterialRepository(
    materials: List<com.dmb.chantiertracker.domain.model.Material> = emptyList(),
    stock: List<com.dmb.chantiertracker.domain.model.MaterialStock> = emptyList(),
) : com.dmb.chantiertracker.domain.repository.MaterialRepository {

    val materialsFlow = MutableStateFlow(materials)
    val stockFlow = MutableStateFlow(stock)
    val log = mutableListOf<String>()

    /** Material returned by [createMaterial]; defaults to echoing the requested name/unit under a fixed id. */
    var createdMaterial: ((projectLocalId: String, name: String, unit: String) -> com.dmb.chantiertracker.domain.model.Material)? = null

    override fun observeMaterials(projectLocalId: String) = materialsFlow

    override fun observeStock(projectLocalId: String) = stockFlow

    override suspend fun createMaterial(projectLocalId: String, name: String, unit: String): com.dmb.chantiertracker.domain.model.Material {
        log += "createMaterial:$projectLocalId:$name:$unit"
        val material = createdMaterial?.invoke(projectLocalId, name, unit)
            ?: com.dmb.chantiertracker.domain.model.Material("material-new", projectLocalId, name, unit)
        materialsFlow.value = materialsFlow.value + material
        return material
    }
}

class FakePurchaseLineRepository(
    lines: List<com.dmb.chantiertracker.domain.model.PurchaseLine> = emptyList(),
) : com.dmb.chantiertracker.domain.repository.PurchaseLineRepository {

    val linesFlow = MutableStateFlow(lines)
    val log = mutableListOf<String>()
    var newLocalId = "purchase-line-new"

    override fun observeLines(entryLocalId: String) = linesFlow

    override suspend fun createLine(entryLocalId: String, input: com.dmb.chantiertracker.domain.model.CreatePurchaseLineInput): String {
        log += "createLine:$entryLocalId:${input.materialLocalId}:${input.quantity}:${input.unitPrice}:${input.supplier}"
        return newLocalId
    }

    override suspend fun updateLine(lineLocalId: String, input: com.dmb.chantiertracker.domain.model.UpdatePurchaseLineInput) {
        log += "updateLine:$lineLocalId:${input.quantity}:${input.unitPrice}:${input.supplier}"
    }

    override suspend fun deleteLine(lineLocalId: String) {
        log += "deleteLine:$lineLocalId"
    }
}

class FakeConsumptionLineRepository(
    lines: List<com.dmb.chantiertracker.domain.model.ConsumptionLine> = emptyList(),
) : com.dmb.chantiertracker.domain.repository.ConsumptionLineRepository {

    val linesFlow = MutableStateFlow(lines)
    val log = mutableListOf<String>()
    var newLocalId = "consumption-line-new"

    override fun observeLines(entryLocalId: String) = linesFlow

    override suspend fun createLine(entryLocalId: String, input: com.dmb.chantiertracker.domain.model.CreateConsumptionLineInput): String {
        log += "createLine:$entryLocalId:${input.materialLocalId}:${input.quantity}"
        return newLocalId
    }

    override suspend fun updateLine(lineLocalId: String, input: com.dmb.chantiertracker.domain.model.UpdateConsumptionLineInput) {
        log += "updateLine:$lineLocalId:${input.quantity}"
    }

    override suspend fun deleteLine(lineLocalId: String) {
        log += "deleteLine:$lineLocalId"
    }
}

class FakeAttachmentRepository(
    attachments: List<com.dmb.chantiertracker.domain.model.Attachment> = emptyList(),
) : com.dmb.chantiertracker.domain.repository.AttachmentRepository {

    val attachmentsFlow = MutableStateFlow(attachments)
    val log = mutableListOf<String>()
    var newLocalId = "attachment-new"

    // When true, a freshly added row is NOT pushed to observeAttachments right
    // away — it waits for emitDeferredRows(). Models Room's asynchronous
    // invalidation: dao.upsert() returns before the observing Flow re-emits.
    var deferListEmission = false
    private val deferred = mutableListOf<com.dmb.chantiertracker.domain.model.Attachment>()

    fun emitDeferredRows() {
        if (deferred.isEmpty()) return
        attachmentsFlow.value = attachmentsFlow.value + deferred
        deferred.clear()
    }

    private fun publish(row: com.dmb.chantiertracker.domain.model.Attachment) {
        if (deferListEmission) deferred += row else attachmentsFlow.value = attachmentsFlow.value + row
    }

    override fun observeAttachments(entryLocalId: String) = attachmentsFlow

    override suspend fun addAttachment(entryLocalId: String, bytes: ByteArray, originalName: String, mimeType: String): com.dmb.chantiertracker.domain.model.Attachment {
        log += "addAttachment:$entryLocalId:$originalName:$mimeType:${bytes.size}"
        val row = com.dmb.chantiertracker.domain.model.Attachment(
            localId = newLocalId,
            entryLocalId = entryLocalId,
            localPath = "fake-attachments/$newLocalId.jpg",
            originalName = originalName,
            mimeType = mimeType,
            sizeBytes = bytes.size.toLong(),
            uploadedAt = 0L,
        )
        publish(row)
        return row
    }

    var uploadVideoError: com.dmb.chantiertracker.domain.model.DomainException? = null
    var uploadVideoProgressSteps: List<Float> = listOf(0.5f, 1f)

    override suspend fun uploadVideo(
        entryLocalId: String,
        video: com.dmb.chantiertracker.domain.model.UploadFile,
        onProgress: (Float) -> Unit,
    ): com.dmb.chantiertracker.domain.model.Attachment {
        log += "uploadVideo:$entryLocalId:${video.name}:${video.mimeType}:${video.size()}"
        uploadVideoProgressSteps.forEach(onProgress)
        uploadVideoError?.let { throw it }
        val stored = com.dmb.chantiertracker.domain.model.Attachment(
            localId = newLocalId,
            entryLocalId = entryLocalId,
            localPath = "fake-attachments/$newLocalId.mp4",
            originalName = video.name,
            mimeType = "video/mp4",
            sizeBytes = 1_024L,
            durationSeconds = 12,
            uploadedAt = 0L,
        )
        publish(stored)
        return stored
    }

    override suspend fun deleteAttachment(attachmentLocalId: String) {
        log += "deleteAttachment:$attachmentLocalId"
    }
}

class FakeInvitationRepository(
    invitations: List<com.dmb.chantiertracker.domain.model.Invitation> = emptyList(),
) : com.dmb.chantiertracker.domain.repository.InvitationRepository {

    val invitationsFlow = MutableStateFlow(invitations)

    val invited = mutableListOf<Pair<String, String>>()
    val cancelled = mutableListOf<Pair<String, Long>>()
    var inviteError: com.dmb.chantiertracker.domain.model.DomainException? = null
    var cancelError: com.dmb.chantiertracker.domain.model.DomainException? = null

    override fun observeInvitations(projectLocalId: String) = invitationsFlow

    override suspend fun invite(projectLocalId: String, email: String) {
        inviteError?.let { throw it }
        invited += projectLocalId to email
    }

    override suspend fun cancelInvitation(projectLocalId: String, invitationId: Long) {
        cancelError?.let { throw it }
        cancelled += projectLocalId to invitationId
    }

    var incoming: List<com.dmb.chantiertracker.domain.model.IncomingInvitation> = emptyList()
    var listIncomingError: com.dmb.chantiertracker.domain.model.DomainException? = null
    var acceptError: com.dmb.chantiertracker.domain.model.DomainException? = null
    var declineError: com.dmb.chantiertracker.domain.model.DomainException? = null
    val accepted = mutableListOf<String>()
    val declined = mutableListOf<String>()

    override suspend fun listIncomingInvitations(): List<com.dmb.chantiertracker.domain.model.IncomingInvitation> {
        listIncomingError?.let { throw it }
        return incoming
    }

    override suspend fun acceptInvitation(token: String) {
        acceptError?.let { throw it }
        accepted += token
        incoming = incoming.filterNot { it.token == token }
    }

    override suspend fun declineInvitation(token: String) {
        declineError?.let { throw it }
        declined += token
        incoming = incoming.filterNot { it.token == token }
    }
}

class FakeHistoryRepository(
    private val pages: List<com.dmb.chantiertracker.domain.model.HistoryPage> = emptyList(),
) : com.dmb.chantiertracker.domain.repository.HistoryRepository {

    // Every call is recorded so tests can assert the exact (page, sort) asked for.
    val calls = mutableListOf<Triple<String, Int, com.dmb.chantiertracker.domain.model.HistorySort>>()
    var error: com.dmb.chantiertracker.domain.model.DomainException? = null
    var onCall: (suspend () -> Unit)? = null

    override suspend fun projectHistory(
        projectLocalId: String,
        page: Int,
        sort: com.dmb.chantiertracker.domain.model.HistorySort,
    ): com.dmb.chantiertracker.domain.model.HistoryPage {
        calls += Triple(projectLocalId, page, sort)
        onCall?.invoke()
        error?.let { throw it }
        return pages.getOrNull(page)
            ?: com.dmb.chantiertracker.domain.model.HistoryPage(
                items = emptyList(), page = page, totalPages = pages.size,
                isFirst = page == 0, isLast = page >= pages.size - 1, totalElements = 0,
            )
    }
}

class FakeReportRepository(
    private val pages: List<com.dmb.chantiertracker.domain.model.ReportPage> = emptyList(),
) : com.dmb.chantiertracker.domain.repository.ReportRepository {

    val createdReports = mutableListOf<Pair<String, String>>()
    val listCalls = mutableListOf<Triple<String, Int, com.dmb.chantiertracker.domain.model.ReportSort>>()
    val processedIds = mutableListOf<Long>()

    var createError: com.dmb.chantiertracker.domain.model.DomainException? = null
    var listError: com.dmb.chantiertracker.domain.model.DomainException? = null
    var processError: com.dmb.chantiertracker.domain.model.DomainException? = null

    override suspend fun createReport(entryLocalId: String, message: String) {
        createdReports += entryLocalId to message
        createError?.let { throw it }
    }

    override suspend fun projectReports(
        projectLocalId: String,
        page: Int,
        sort: com.dmb.chantiertracker.domain.model.ReportSort,
    ): com.dmb.chantiertracker.domain.model.ReportPage {
        listCalls += Triple(projectLocalId, page, sort)
        listError?.let { throw it }
        return pages.getOrNull(page)
            ?: com.dmb.chantiertracker.domain.model.ReportPage(
                items = emptyList(), page = page, totalPages = pages.size,
                isFirst = page == 0, isLast = page >= pages.size - 1, totalElements = 0,
            )
    }

    override suspend fun markProcessed(reportId: Long): com.dmb.chantiertracker.domain.model.Report {
        processedIds += reportId
        processError?.let { throw it }
        val existing = pages.flatMap { it.items }.firstOrNull { it.id == reportId }
        return existing?.copy(
            status = com.dmb.chantiertracker.domain.model.ReportStatus.PROCESSED,
            processedAt = "2026-09-07T12:00:00",
        ) ?: com.dmb.chantiertracker.domain.model.Report(
            id = reportId, entryId = 0, entryType = com.dmb.chantiertracker.domain.model.EntryType.UNKNOWN,
            entryDate = "2026-09-01", authorName = null, message = "", createdAt = "2026-09-01T08:00:00",
            status = com.dmb.chantiertracker.domain.model.ReportStatus.PROCESSED, processedAt = "2026-09-07T12:00:00",
        )
    }
}

class FakeExportRepository : com.dmb.chantiertracker.domain.repository.ExportRepository {
    val calls = mutableListOf<String>()
    var error: com.dmb.chantiertracker.domain.model.DomainException? = null
    var result = com.dmb.chantiertracker.domain.model.ExportedPdf(
        path = "/cache/exports/chantier-villa-2026-09-08.pdf",
        fileName = "chantier-villa-2026-09-08.pdf",
    )
    var onCall: (suspend () -> Unit)? = null

    override suspend fun exportProjectPdf(projectLocalId: String): com.dmb.chantiertracker.domain.model.ExportedPdf {
        calls += projectLocalId
        onCall?.invoke()
        error?.let { throw it }
        return result
    }
}

class FakeExportFileStore : com.dmb.chantiertracker.data.local.ExportFileStore {
    val saved = mutableListOf<Pair<String, Int>>()

    override suspend fun save(bytes: ByteArray, fileName: String): String {
        saved += fileName to bytes.size
        return "/cache/exports/$fileName"
    }
}

class FakePdfSharer : com.dmb.chantiertracker.presentation.projects.export.PdfSharer {
    val shared = mutableListOf<String>()
    var error: Throwable? = null

    override suspend fun share(path: String) {
        shared += path
        error?.let { throw it }
    }
}

class FakeAccountRepository(
    planUsage: com.dmb.chantiertracker.domain.model.PlanUsage? = null,
) : AccountRepository {
    val planUsageFlow = MutableStateFlow(planUsage)
    var refreshCount = 0
        private set
    /** Set to have refreshPlanUsage() also push a value into the flow (mimics a fetch). */
    var refreshResult: com.dmb.chantiertracker.domain.model.PlanUsage? = null

    override fun observePlanUsage() = planUsageFlow

    override suspend fun refreshPlanUsage() {
        refreshCount++
        refreshResult?.let { planUsageFlow.value = it }
    }
}
