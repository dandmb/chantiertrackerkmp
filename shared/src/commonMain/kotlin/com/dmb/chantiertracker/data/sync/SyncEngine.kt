@file:OptIn(ExperimentalUuidApi::class)

package com.dmb.chantiertracker.data.sync

import com.dmb.chantiertracker.data.local.db.SyncedRow
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.domain.model.waitsForACorrection
import com.dmb.chantiertracker.data.local.AttachmentFileStore
import com.dmb.chantiertracker.data.local.db.AttachmentDao
import com.dmb.chantiertracker.data.local.db.AttachmentEntity
import com.dmb.chantiertracker.data.local.db.ConsumptionLineDao
import com.dmb.chantiertracker.data.local.db.ConsumptionLineEntity
import com.dmb.chantiertracker.data.local.db.DailyEntryDao
import com.dmb.chantiertracker.data.local.db.DailyEntryEntity
import com.dmb.chantiertracker.data.local.db.DailyLogDao
import com.dmb.chantiertracker.data.local.db.DailyLogEntity
import com.dmb.chantiertracker.data.local.db.InvitationDao
import com.dmb.chantiertracker.data.local.db.MaterialAdoptionDao
import com.dmb.chantiertracker.data.local.db.MaterialDao
import com.dmb.chantiertracker.data.local.db.MaterialEntity
import com.dmb.chantiertracker.data.local.db.LocalChangesDao
import com.dmb.chantiertracker.data.local.db.LocallyVersioned
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.ProjectDao
import com.dmb.chantiertracker.data.local.db.ProjectEntity
import com.dmb.chantiertracker.data.local.db.PurchaseLineDao
import com.dmb.chantiertracker.data.local.db.PurchaseLineEntity
import com.dmb.chantiertracker.data.local.db.StageDao
import com.dmb.chantiertracker.data.local.db.MaterialStockEntity
import com.dmb.chantiertracker.data.local.db.StageEntity
import com.dmb.chantiertracker.data.local.db.StockDao
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.remote.AttachmentApi
import com.dmb.chantiertracker.data.remote.ConsumptionLineApi
import com.dmb.chantiertracker.data.remote.DailyLogApi
import com.dmb.chantiertracker.data.remote.InvitationApi
import com.dmb.chantiertracker.data.remote.MaterialApi
import com.dmb.chantiertracker.data.remote.ProjectApi
import com.dmb.chantiertracker.data.remote.PurchaseLineApi
import com.dmb.chantiertracker.data.remote.StageApi
import com.dmb.chantiertracker.data.remote.StockApi
import com.dmb.chantiertracker.data.remote.apiCallReportingRefusalCode
import com.dmb.chantiertracker.data.remote.dto.AttachmentDto
import com.dmb.chantiertracker.data.remote.dto.ConsumptionLineDto
import com.dmb.chantiertracker.data.remote.dto.DailyLogSummaryDto
import com.dmb.chantiertracker.data.remote.dto.InvitationDto
import com.dmb.chantiertracker.data.remote.dto.MaterialDto
import com.dmb.chantiertracker.data.remote.dto.MaterialStockDto
import com.dmb.chantiertracker.data.remote.dto.MemberDto
import com.dmb.chantiertracker.data.remote.dto.ProjectDto
import com.dmb.chantiertracker.data.remote.dto.PurchaseLineDto
import com.dmb.chantiertracker.data.remote.dto.StageDto
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.presentation.sync.SyncState
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.dmb.chantiertracker.data.local.db.AwaitedServerVersions
import kotlinx.io.Buffer
import kotlinx.io.write
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed interface SyncOutcome {
    data object Synced : SyncOutcome
    data object Skipped : SyncOutcome
    data class Failed(val cause: DomainException) : SyncOutcome
}

/** What repositories need from the sync layer: nudge it after a local write, or await a pass. */
interface Syncer {
    fun requestSync()
    suspend fun syncNow(): SyncOutcome
    suspend fun syncProject(localId: String): SyncOutcome
    suspend fun syncStage(stageLocalId: String): SyncOutcome
    suspend fun syncLog(logLocalId: String): SyncOutcome

    /**
     * Runs [block] while no sync pass can run: one in flight finishes first, one requested meanwhile
     * starts after (ADR-69). For what must never interleave with a push — switching the token and the
     * account that owns the local data, or signing out. Never call a sync method from [block].
     */
    suspend fun <T> runExclusive(block: suspend () -> T): T

    suspend fun restoreServerVersion(target: SyncIssueTarget, localId: String): SyncOutcome
}

/**
 * The single place that talks to [ProjectApi]. Repositories read and write
 * Room only; this drains locally-pending rows to the server (push) then
 * reconciles Room with the server's state (pull). Runs on an
 * application-lifetime scope, never a screen's.
 *
 * Conflict resolution is optimistic-concurrency, no user prompt (ADR-21): a
 * pending local edit is pushed as-is unless the server's `updatedAt` has moved
 * since our last sync of that row, in which case the server version wins and the
 * local edit is dropped. Comparing the server's own timestamps (not the device
 * clock) keeps this correct regardless of server/device clock agreement.
 *
 * [start] also runs an in-process catch-up loop every [catchUpInterval]; on
 * Android/iOS the OS scheduler (via [backgroundSync]) handles catch-up while the
 * app is backgrounded, on Desktop this loop is the whole mechanism (ADR-22).
 */
class SyncEngine(
    private val dao: ProjectDao,
    private val api: ProjectApi,
    private val stageDao: StageDao,
    private val stageApi: StageApi,
    private val materialDao: MaterialDao,
    private val materialAdoptionDao: MaterialAdoptionDao,
    private val materialApi: MaterialApi,
    private val dailyLogDao: DailyLogDao,
    private val dailyEntryDao: DailyEntryDao,
    private val dailyLogApi: DailyLogApi,
    private val purchaseLineDao: PurchaseLineDao,
    private val purchaseLineApi: PurchaseLineApi,
    private val consumptionLineDao: ConsumptionLineDao,
    private val consumptionLineApi: ConsumptionLineApi,
    private val attachmentDao: AttachmentDao,
    private val attachmentApi: AttachmentApi,
    private val attachmentFileStore: AttachmentFileStore,
    private val invitationDao: InvitationDao,
    private val invitationApi: InvitationApi,
    private val stockApi: StockApi,
    private val stockDao: StockDao,
    private val connectivity: ConnectivityObserver,
    private val awaitedServerVersions: AwaitedServerVersions,
    private val syncState: SyncStateHolder,
    private val scope: CoroutineScope,
    private val clock: Clock = SystemClock,
    private val newLocalId: () -> String = { Uuid.random().toString() },
    private val backgroundSync: BackgroundSync = NoOpBackgroundSync,
    private val catchUpInterval: Duration = 15.minutes,
    private val log: (String) -> Unit = ::println,
) : Syncer {

    private val mutex = Mutex()
    private val neverLoadedStocksTouchedThisPass = mutableSetOf<String>()
    private val stocksReadIncompletelyThisPass = mutableSetOf<String>()
    private var refusalCode: String? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            connectivity.online.collect { online ->
                if (online) syncNow() else syncState.update(SyncState.Offline)
            }
        }
        scope.launch {
            while (isActive) {
                delay(catchUpInterval)
                syncNow()
            }
        }
    }

    override fun requestSync() {
        scope.launch { syncNowOrDeferToOs() }
    }

    /** One in-process pass; if it can't finish (offline / server error), hand the queue to the OS scheduler. */
    internal suspend fun syncNowOrDeferToOs(): SyncOutcome =
        syncNow().also { if (it != SyncOutcome.Synced) backgroundSync.requestExpeditedSync() }

    override suspend fun syncNow(): SyncOutcome = onePass { runSync() }

    override suspend fun syncProject(localId: String): SyncOutcome = onePass { runProjectSync(localId) }

    override suspend fun syncStage(stageLocalId: String): SyncOutcome = onePass { runStageSync(stageLocalId) }

    override suspend fun syncLog(logLocalId: String): SyncOutcome = onePass { runLogSync(logLocalId) }

    private suspend fun onePass(pass: suspend () -> SyncOutcome): SyncOutcome = mutex.withLock {
        stocksReadIncompletelyThisPass.clear()
        pass()
    }

    override suspend fun <T> runExclusive(block: suspend () -> T): T = mutex.withLock { block() }

    override suspend fun restoreServerVersion(target: SyncIssueTarget, localId: String): SyncOutcome = mutex.withLock {
        if (!connectivity.isOnline()) return@withLock SyncOutcome.Skipped
        try {
            if (writeServerVersion(target, localId)) SyncOutcome.Synced else SyncOutcome.Failed(DomainException.NotFound)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DomainException) {
            SyncOutcome.Failed(e)
        } catch (e: Throwable) {
            SyncOutcome.Failed(DomainException.Unexpected)
        }
    }

    private suspend fun writeServerVersion(target: SyncIssueTarget, localId: String): Boolean {
        val syncedAt = clock.nowEpochMillis()
        try {
            when (target) {
                SyncIssueTarget.PROJECT -> restoreProject(localId, syncedAt)
                SyncIssueTarget.STAGE -> restoreStage(localId, syncedAt)
                SyncIssueTarget.MATERIAL -> restoreMaterial(localId, syncedAt)
                SyncIssueTarget.ENTRY -> restoreEntry(localId, syncedAt)
                SyncIssueTarget.PURCHASE_LINE -> restorePurchaseLine(localId, syncedAt)
                SyncIssueTarget.CONSUMPTION_LINE -> restoreConsumptionLine(localId, syncedAt)
                SyncIssueTarget.ATTACHMENT -> Unit
            }
        } catch (e: DomainException.NotFound) {
            rowGoneOnServer(target, localId)
            return false
        }
        return true
    }

    private suspend fun refreshRowsAwaitingTheirServerVersion() {
        for (row in awaitedServerVersions.rowsAwaitingServerVersion()) {
            try {
                writeServerVersion(row.target, row.localId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: DomainException) {
                if (e.isServerRejection()) awaitedServerVersions.stopAwaitingServerVersion(row.target, row.localId)
            }
        }
    }

    private suspend fun rowGoneOnServer(target: SyncIssueTarget, localId: String) {
        when (target) {
            SyncIssueTarget.PROJECT -> dao.findByLocalId(localId)?.let { row ->
                if (row.holdsARefusedUpdate()) dao.writeIfUnchanged(row.copy(lastSyncError = SyncError.DELETED_ON_SERVER, serverErrorCode = null)) else if (row.awaitsItsServerVersion()) projectGoneOnServer(row)
            }
            SyncIssueTarget.STAGE -> stageDao.findByLocalId(localId)?.let { row ->
                if (row.holdsARefusedUpdate()) stageDao.writeIfUnchanged(row.copy(lastSyncError = SyncError.DELETED_ON_SERVER, serverErrorCode = null)) else if (row.awaitsItsServerVersion()) stageGoneOnServer(row)
            }
            SyncIssueTarget.ENTRY -> dailyEntryDao.findByLocalId(localId)?.let { row ->
                if (row.holdsARefusedUpdate()) dailyEntryDao.writeIfUnchanged(row.copy(lastSyncError = SyncError.DELETED_ON_SERVER, serverErrorCode = null)) else if (row.awaitsItsServerVersion()) entryGoneOnServer(row)
            }
            SyncIssueTarget.MATERIAL -> materialDao.findByLocalId(localId)?.takeIf { it.holdsARefusedUpdate() }
                ?.let { materialDao.writeIfUnchanged(it.copy(lastSyncError = SyncError.DELETED_ON_SERVER, serverErrorCode = null)) }
            SyncIssueTarget.PURCHASE_LINE -> purchaseLineDao.findByLocalId(localId)?.takeIf { it.holdsARefusedUpdate() }
                ?.let { purchaseLineDao.writeIfUnchanged(it.copy(lastSyncError = SyncError.DELETED_ON_SERVER, serverErrorCode = null)) }
            SyncIssueTarget.CONSUMPTION_LINE -> consumptionLineDao.findByLocalId(localId)?.takeIf { it.holdsARefusedUpdate() }
                ?.let { consumptionLineDao.writeIfUnchanged(it.copy(lastSyncError = SyncError.DELETED_ON_SERVER, serverErrorCode = null)) }
            SyncIssueTarget.ATTACHMENT -> Unit
        }
        awaitedServerVersions.stopAwaitingServerVersion(target, localId)
    }

    private suspend fun <E : LocallyVersioned<E>> LocalChangesDao<E>.storeCreated(created: E, removeFromServer: suspend () -> Unit, keepingTheEdit: (E) -> E) {
        if (writeIfUnchanged(created)) return
        if (keepLocalChange(created.localId, keepingTheEdit)) return
        try {
            serverCall { removeFromServer() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: DomainException) {
            Unit
        }
    }

    private fun SyncedRow.waitsForACorrection(): Boolean = syncIssue()?.waitsForACorrection == true

    private fun SyncedRow.holdsARefusedUpdate(): Boolean =
        syncStatus == SyncStatus.CONFLICTED && pendingOp == PendingOp.UPDATE && serverId != null && lastSyncError != SyncError.DELETED_ON_SERVER

    private fun SyncedRow.awaitsItsServerVersion(): Boolean =
        syncStatus == SyncStatus.SYNCED && pendingOp == PendingOp.NONE && serverId != null && awaitsServerVersion

    private fun SyncedRow.canTakeItsServerVersion(): Boolean = holdsARefusedUpdate() || awaitsItsServerVersion()

    private fun <T> PagedRead<T>.itemOrGone(matches: (T) -> Boolean): T =
        items.firstOrNull(matches) ?: throw if (isComplete) DomainException.NotFound else DomainException.Unexpected

    private suspend fun restoreProject(localId: String, syncedAt: Long) {
        val local = dao.findByLocalId(localId)?.takeIf { it.canTakeItsServerVersion() } ?: return
        val remote = serverCall { api.get(local.serverId!!) }
        dao.writeIfUnchanged(remote.toSyncedEntity(localId = localId, syncedAt = syncedAt, previous = local))
    }

    private suspend fun restoreStage(localId: String, syncedAt: Long) {
        val local = stageDao.findByLocalId(localId)?.takeIf { it.canTakeItsServerVersion() } ?: return
        val remote = serverCall { stageApi.get(local.serverId!!) }
        stageDao.writeIfUnchanged(remote.toSyncedEntity(localId, local.projectLocalId, syncedAt, local))
    }

    private suspend fun restoreMaterial(localId: String, syncedAt: Long) {
        val local = materialDao.findByLocalId(localId)?.takeIf { it.canTakeItsServerVersion() } ?: return
        val projectServerId = dao.findByLocalId(local.projectLocalId)?.serverId ?: throw DomainException.NotFound
        val remote = readProjectMaterials(projectServerId).itemOrGone { it.id == local.serverId }
        materialDao.writeIfUnchanged(remote.toSyncedEntity(localId, local.projectLocalId, syncedAt, local))
    }

    private suspend fun restoreEntry(localId: String, syncedAt: Long) {
        val local = dailyEntryDao.findByLocalId(localId)?.takeIf { it.canTakeItsServerVersion() } ?: return
        val logServerId = dailyLogDao.findByLocalId(local.dailyLogLocalId)?.serverId ?: throw DomainException.NotFound
        val remote = serverCall { dailyLogApi.getLog(logServerId) }.entries.firstOrNull { it.id == local.serverId } ?: throw DomainException.NotFound
        dailyEntryDao.writeIfUnchanged(remote.toSyncedEntity(localId, local.dailyLogLocalId, syncedAt, local))
    }

    private suspend fun restorePurchaseLine(localId: String, syncedAt: Long) {
        val local = purchaseLineDao.findByLocalId(localId)?.takeIf { it.canTakeItsServerVersion() } ?: return
        val entryServerId = dailyEntryDao.findByLocalId(local.entryLocalId)?.serverId ?: throw DomainException.NotFound
        val read = readAllPages(PurchaseLineDto::id) { page, size -> serverCall { purchaseLineApi.list(entryServerId, page, size) } }
        val remote = read.itemOrGone { it.id == local.serverId }
        val materialLocalId = materialDao.findByServerId(remote.materialId)?.localId ?: local.materialLocalId
        purchaseLineDao.writeIfUnchanged(remote.toSyncedEntity(localId, local.entryLocalId, materialLocalId, syncedAt, local))
    }

    private suspend fun restoreConsumptionLine(localId: String, syncedAt: Long) {
        val local = consumptionLineDao.findByLocalId(localId)?.takeIf { it.canTakeItsServerVersion() } ?: return
        val entryServerId = dailyEntryDao.findByLocalId(local.entryLocalId)?.serverId ?: throw DomainException.NotFound
        val read = readAllPages(ConsumptionLineDto::id) { page, size -> serverCall { consumptionLineApi.list(entryServerId, page, size) } }
        val remote = read.itemOrGone { it.id == local.serverId }
        val materialLocalId = materialDao.findByServerId(remote.materialId)?.localId ?: local.materialLocalId
        consumptionLineDao.writeIfUnchanged(remote.toSyncedEntity(localId, local.entryLocalId, materialLocalId, syncedAt, local))
    }

    private suspend fun runSync(): SyncOutcome {
        if (!connectivity.isOnline()) {
            syncState.update(SyncState.Offline)
            return SyncOutcome.Skipped
        }
        syncState.update(SyncState.Syncing)
        return try {
            pushPending()
            refreshRowsAwaitingTheirServerVersion()
            pullAll()
            refreshStocksMarkedForRefresh()
            syncState.update(SyncState.Idle)
            SyncOutcome.Synced
        } catch (e: CancellationException) {
            throw e
        } catch (e: DomainException) {
            syncState.update(SyncState.Error(e))
            SyncOutcome.Failed(e)
        } catch (e: Throwable) {
            syncState.update(SyncState.Error(DomainException.Unexpected))
            SyncOutcome.Failed(DomainException.Unexpected)
        }
    }

    private suspend fun runProjectSync(localId: String): SyncOutcome {
        if (!connectivity.isOnline()) {
            syncState.update(SyncState.Offline)
            return SyncOutcome.Skipped
        }
        return try {
            pushPending()
            val serverId = dao.findByLocalId(localId)?.serverId ?: return SyncOutcome.Skipped
            pullProject(serverId, localId)
            refreshStocksMarkedForRefresh()
            SyncOutcome.Synced
        } catch (e: CancellationException) {
            throw e
        } catch (e: DomainException) {
            SyncOutcome.Failed(e)
        } catch (e: Throwable) {
            SyncOutcome.Failed(DomainException.Unexpected)
        }
    }

    private suspend fun pullProject(serverId: Long, localId: String) {
        val detail = try {
            serverCall { api.get(serverId) }
        } catch (e: DomainException.NotFound) {
            dao.findByLocalId(localId)?.let { projectGoneOnServer(it) }
            return
        }
        val local = dao.findByLocalId(localId)
        if (local != null && local.pendingOp == PendingOp.NONE) {
            dao.writeIfUnchanged(detail.toSyncedEntity(localId = localId, syncedAt = clock.nowEpochMillis(), previous = local))
        }
        pullMembers(serverId, localId)
        pullInvitations(serverId, localId)
        pullStages(serverId, localId)
        pullMaterials(serverId, localId)
        refreshStock(serverId, localId)
        // Log *summaries* only (existence + server id per day); a day's entries,
        // lines and photos are pulled by syncLog when its screen opens, so
        // syncProject stays proportional to what the project detail shows.
        for (stage in stageDao.findForProject(localId)) {
            if (stage.lastSyncError == SyncError.DELETED_ON_SERVER) continue
            stage.serverId?.let { pullLogSummaries(it, stage.localId) }
        }
    }

    private suspend fun runStageSync(stageLocalId: String): SyncOutcome {
        if (!connectivity.isOnline()) {
            syncState.update(SyncState.Offline)
            return SyncOutcome.Skipped
        }
        return try {
            pushPending()
            val serverId = stageDao.findByLocalId(stageLocalId)?.serverId ?: return SyncOutcome.Skipped
            val dto = try {
                serverCall { stageApi.get(serverId) }
            } catch (e: DomainException.NotFound) {
                stageDao.findByLocalId(stageLocalId)?.let { stageGoneOnServer(it) }
                return SyncOutcome.Synced
            }
            val local = stageDao.findByLocalId(stageLocalId)
            if (local != null && local.pendingOp == PendingOp.NONE) {
                stageDao.writeIfUnchanged(
                    dto.toSyncedEntity(
                        localId = stageLocalId,
                        projectLocalId = local.projectLocalId,
                        syncedAt = clock.nowEpochMillis(),
                        previous = local,
                    ),
                )
            }
            val projectServerId = local?.projectLocalId?.let { dao.findByLocalId(it)?.serverId }
            if (projectServerId != null) {
                pullMaterials(projectServerId, local.projectLocalId)
            }
            pullLogSummaries(serverId, stageLocalId)
            refreshStocksMarkedForRefresh()
            SyncOutcome.Synced
        } catch (e: CancellationException) {
            throw e
        } catch (e: DomainException) {
            SyncOutcome.Failed(e)
        } catch (e: Throwable) {
            SyncOutcome.Failed(DomainException.Unexpected)
        }
    }

    private suspend fun runLogSync(logLocalId: String): SyncOutcome {
        if (!connectivity.isOnline()) {
            syncState.update(SyncState.Offline)
            return SyncOutcome.Skipped
        }
        return try {
            pushPending()
            val log = dailyLogDao.findByLocalId(logLocalId) ?: return SyncOutcome.Skipped
            val logServerId = log.serverId ?: return SyncOutcome.Skipped
            val detail = try {
                serverCall { dailyLogApi.getLog(logServerId) }
            } catch (e: DomainException.NotFound) {
                // A day is never deleted through its own endpoint; a 404 here just
                // means it has no server-side entries left. Keep the local row.
                return SyncOutcome.Synced
            }
            pullEntries(detail.entries, logLocalId)
            for (entry in dailyEntryDao.findForLog(logLocalId)) {
                if (entry.lastSyncError == SyncError.DELETED_ON_SERVER) continue
                val entryServerId = entry.serverId ?: continue
                when (entry.type.uppercase()) {
                    "PURCHASE" -> {
                        pullPurchaseLines(entryServerId, entry.localId)
                        pullAttachments(entryServerId, entry.localId)
                    }
                    "WORK" -> pullConsumptionLines(entryServerId, entry.localId)
                }
            }
            refreshStockOfDay(log)
            refreshStocksMarkedForRefresh()
            SyncOutcome.Synced
        } catch (e: CancellationException) {
            throw e
        } catch (e: DomainException) {
            SyncOutcome.Failed(e)
        } catch (e: Throwable) {
            SyncOutcome.Failed(DomainException.Unexpected)
        }
    }

    private suspend fun pullStages(projectServerId: Long, projectLocalId: String) {
        val read = readAllPages(StageDto::id) { page, size -> serverCall { stageApi.list(projectServerId, page, size) } }
        val remote = read.items
        val locals = stageDao.findForProject(projectLocalId)
        val byServerId = locals.mapNotNull { local -> local.serverId?.let { it to local } }.toMap()
        val syncedAt = clock.nowEpochMillis()

        for (dto in remote) {
            val local = byServerId[dto.id]
            when {
                local == null ->
                    stageDao.insertNew(dto.toSyncedEntity(newLocalId(), projectLocalId, syncedAt))

                local.pendingOp == PendingOp.NONE ->
                    stageDao.writeIfUnchanged(dto.toSyncedEntity(local.localId, projectLocalId, syncedAt, local))

                // else: a pending local edit — no server `updatedAt` to arbitrate,
                // so it survives and wins on its next push (last-writer-wins).
                else -> Unit
            }
        }

        if (skipsRemovalAfter(read, "stages of project $projectServerId")) return
        val remoteIds = remote.map { it.id }.toSet()
        locals
            .filter { it.serverId != null && it.serverId !in remoteIds }
            .filter { it.syncStatus == SyncStatus.SYNCED && it.pendingOp == PendingOp.NONE }
            .forEach { stageGoneOnServer(it) }
    }

    private suspend fun pullMembers(serverId: Long, localId: String) {
        val read = readAllPages(MemberDto::userId) { page, size -> serverCall { api.members(serverId, page, size) } }
        if (!skipsRemovalAfter(read, "members of project $serverId")) dao.clearMembers(localId)
        if (read.items.isNotEmpty()) {
            dao.upsertMembers(read.items.map { it.toEntity(localId) })
        }
    }

    // ADMIN-only server-side. A non-admin (SUPERVISOR) gets 403 — clear the
    // local cache so a demoted user stops seeing stale invitations; any other
    // error is transient, leave the cache as-is.
    private suspend fun pullInvitations(serverId: Long, localId: String) {
        val read = try {
            readAllPages(InvitationDto::id) { page, size -> serverCall { invitationApi.list(serverId, page, size) } }
        } catch (e: DomainException.Forbidden) {
            invitationDao.clearForProject(localId)
            return
        } catch (e: DomainException.NotFound) {
            invitationDao.clearForProject(localId)
            return
        }
        if (!skipsRemovalAfter(read, "invitations of project $serverId")) invitationDao.clearForProject(localId)
        if (read.items.isNotEmpty()) {
            invitationDao.upsertAll(read.items.map { it.toEntity(localId) })
        }
    }

    private suspend fun pushPending() {
        // Projects first: a pending stage's parent project may still be local-only,
        // and it needs a server id before the stage (its child) can be created.
        for (entity in dao.findPending().filterNot { it.waitsForACorrection() }) {
            when (entity.pendingOp) {
                PendingOp.CREATE -> pushCreate(entity)
                PendingOp.UPDATE -> pushUpdate(entity)
                PendingOp.DELETE -> pushDelete(entity)
                PendingOp.NONE -> Unit
            }
        }
        for (stage in stageDao.findPending().filterNot { it.waitsForACorrection() }) {
            when (stage.pendingOp) {
                PendingOp.CREATE -> pushStageCreate(stage)
                PendingOp.UPDATE -> pushStageUpdate(stage)
                PendingOp.DELETE -> pushStageDelete(stage)
                PendingOp.NONE -> Unit
            }
        }
        // Then the daily-log hierarchy, deepest-last: materials & entries (each
        // needs only its parent's server id), then lines (need entry + material
        // server ids), then photos (need the entry's server id). A row whose
        // parent isn't on the server yet stays PENDING and the next pass, after
        // the parent pushes, picks it up — same as pushStageCreate.
        for (material in materialDao.findPending().filterNot { it.waitsForACorrection() }) {
            when (material.pendingOp) {
                PendingOp.CREATE -> pushMaterialCreate(material)
                PendingOp.UPDATE -> pushMaterialUpdate(material)
                PendingOp.DELETE, PendingOp.NONE -> Unit
            }
        }
        for (entry in dailyEntryDao.findPending().filterNot { it.waitsForACorrection() }) {
            when (entry.pendingOp) {
                PendingOp.CREATE -> pushEntryCreate(entry)
                PendingOp.UPDATE -> pushEntryUpdate(entry)
                PendingOp.DELETE -> pushEntryDelete(entry)
                PendingOp.NONE -> Unit
            }
        }
        for (line in purchaseLineDao.findPending().filterNot { it.waitsForACorrection() }) {
            when (line.pendingOp) {
                PendingOp.CREATE -> pushPurchaseLineCreate(line)
                PendingOp.UPDATE -> pushPurchaseLineUpdate(line)
                PendingOp.DELETE -> pushPurchaseLineDelete(line)
                PendingOp.NONE -> Unit
            }
        }
        for (line in consumptionLineDao.findPending().filterNot { it.waitsForACorrection() }) {
            when (line.pendingOp) {
                PendingOp.CREATE -> pushConsumptionLineCreate(line)
                PendingOp.UPDATE -> pushConsumptionLineUpdate(line)
                PendingOp.DELETE -> pushConsumptionLineDelete(line)
                PendingOp.NONE -> Unit
            }
        }
        for (attachment in attachmentDao.findPending()) {
            when (attachment.pendingOp) {
                PendingOp.CREATE -> pushAttachmentCreate(attachment)
                PendingOp.DELETE -> pushAttachmentDelete(attachment)
                PendingOp.UPDATE, PendingOp.NONE -> Unit
            }
        }
    }

    // A definitive "the server refused this write" — mark the row CONFLICTED and
    // move on, rather than retrying forever. Covers plan limits, validation,
    // 403s (inactive project/stage, non-admin), duplicate entry / insufficient
    // stock (both 409), and entry-type / attachment-type mismatch (400).
    private suspend fun <T> serverCall(block: suspend () -> T): T {
        refusalCode = null
        return apiCallReportingRefusalCode({ refusalCode = it }, block)
    }

    private fun DomainException.isFileRefusal(): Boolean =
        this is DomainException.FileTooLarge || refusalCode in FILE_REFUSAL_CODES

    private fun DomainException.isServerRejection(): Boolean =
        this is DomainException.Forbidden ||
            this is DomainException.Validation ||
            this is DomainException.InvalidCode ||
            this is DomainException.EmailAlreadyUsed ||
            this is DomainException.PlanLimitReached ||
            this is DomainException.DuplicateMaterial ||
            this is DomainException.FileTooLarge

    private enum class RemoteDelete { DELETED, ALREADY_GONE, REJECTED }

    // ─── rows gone on the server (ADR-70) ───────────────────────────────────

    private fun isUnsent(syncStatus: SyncStatus, pendingOp: PendingOp): Boolean =
        syncStatus != SyncStatus.SYNCED && pendingOp != PendingOp.DELETE

    private suspend fun projectGoneOnServer(project: ProjectEntity) {
        if (!keepProjectGoneOnServer(project)) dao.deleteIfUnchanged(project)
    }

    private suspend fun stageGoneOnServer(stage: StageEntity) {
        if (!keepStageGoneOnServer(stage)) stageDao.deleteIfUnchanged(stage)
    }

    private suspend fun entryGoneOnServer(entry: DailyEntryEntity) {
        if (!keepEntryGoneOnServer(entry)) dailyEntryDao.deleteIfUnchanged(entry)
    }

    private suspend fun keepProjectGoneOnServer(project: ProjectEntity): Boolean {
        var unsentBelow = false
        for (stage in stageDao.findForProject(project.localId)) {
            if (keepStageGoneOnServer(stage)) unsentBelow = true
        }
        for (material in materialDao.findForProject(project.localId)) {
            if (isUnsent(material.syncStatus, material.pendingOp)) {
                materialDao.writeIfUnchanged(material.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER))
                unsentBelow = true
            }
        }
        val kept = unsentBelow || isUnsent(project.syncStatus, project.pendingOp)
        if (kept) dao.writeIfUnchanged(project.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER))
        return kept
    }

    private suspend fun keepStageGoneOnServer(stage: StageEntity): Boolean {
        var unsentBelow = false
        for (log in dailyLogDao.findForStage(stage.localId)) {
            for (entry in dailyEntryDao.findForLog(log.localId)) {
                if (keepEntryGoneOnServer(entry)) unsentBelow = true
            }
        }
        val kept = unsentBelow || isUnsent(stage.syncStatus, stage.pendingOp)
        if (kept) stageDao.writeIfUnchanged(stage.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER))
        return kept
    }

    private suspend fun keepEntryGoneOnServer(entry: DailyEntryEntity): Boolean {
        var unsentBelow = false
        for (line in purchaseLineDao.findForEntry(entry.localId)) {
            if (isUnsent(line.syncStatus, line.pendingOp)) {
                purchaseLineDao.writeIfUnchanged(line.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER))
                unsentBelow = true
            }
        }
        for (line in consumptionLineDao.findForEntry(entry.localId)) {
            if (isUnsent(line.syncStatus, line.pendingOp)) {
                consumptionLineDao.writeIfUnchanged(line.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER))
                unsentBelow = true
            }
        }
        for (attachment in attachmentDao.findForEntry(entry.localId)) {
            if (isUnsent(attachment.syncStatus, attachment.pendingOp)) {
                attachmentDao.writeIfUnchanged(attachment.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER))
                unsentBelow = true
            }
        }
        val kept = unsentBelow || isUnsent(entry.syncStatus, entry.pendingOp)
        if (kept) dailyEntryDao.writeIfUnchanged(entry.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER))
        return kept
    }

    // A refused delete (403 after a demotion, 409 when removing a line would
    // break stock…) must not stay PENDING/DELETE: it would be retried forever and,
    // rethrown, abort every push queued behind it. NotFound = already gone.
    // Restoring the row is enough for entries, stages and projects too: the local
    // delete only ever tombstones the parent row (its children are never touched —
    // Room's ON DELETE CASCADE fires only when the tombstone is finally removed),
    // so a refusal leaves the whole subtree intact and the pull that follows in the
    // same pass reconciles it with the server (or drops it if access was lost).
    private suspend fun deleteOnServer(call: suspend () -> Unit): RemoteDelete =
        try {
            serverCall { call() }
            RemoteDelete.DELETED
        } catch (e: DomainException.NotFound) {
            RemoteDelete.ALREADY_GONE
        } catch (e: DomainException) {
            if (e.isServerRejection()) RemoteDelete.REJECTED else throw e
        }

    // ─── materials ──────────────────────────────────────────────────────────

    private suspend fun pushMaterialCreate(material: MaterialEntity) {
        val projectServerId = dao.findByLocalId(material.projectLocalId)?.serverId ?: return
        val created = try {
            serverCall { materialApi.create(projectServerId, material.toCreateRequest()) }
        } catch (e: DomainException.NotFound) {
            materialDao.writeIfUnchanged(material.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER))
            return
        } catch (e: DomainException.DuplicateMaterial) {
            when (adoptServerMaterialOfSameName(material, projectServerId)) {
                MaterialAdoption.ADOPTED, MaterialAdoption.NAMESAKE_NOT_READ_YET -> Unit
                MaterialAdoption.NO_NAMESAKE ->
                    materialDao.writeIfUnchanged(material.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED, serverErrorCode = refusalCode))
            }
            return
        } catch (e: DomainException) {
            if (e.isServerRejection()) {
                materialDao.writeIfUnchanged(material.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED, serverErrorCode = refusalCode))
                return
            }
            throw e
        }
        materialDao.writeIfUnchanged(created.toSyncedEntity(material.localId, material.projectLocalId, clock.nowEpochMillis(), material))
    }

    private enum class MaterialAdoption { ADOPTED, NO_NAMESAKE, NAMESAKE_NOT_READ_YET }

    private suspend fun readProjectMaterials(projectServerId: Long): PagedRead<MaterialDto> =
        readAllPages(MaterialDto::id) { page, size -> serverCall { materialApi.list(projectServerId, page, size) } }
            .also { logIncomplete(it, "materials of project $projectServerId") }

    private suspend fun adoptServerMaterialOfSameName(material: MaterialEntity, projectServerId: Long): MaterialAdoption {
        val read = readProjectMaterials(projectServerId)
        val remote = read.items
        val match = remote.firstOrNull { it.name == material.name }
            ?: remote.firstOrNull { it.name.equals(material.name, ignoreCase = true) }
            ?: return if (read.isComplete) MaterialAdoption.NO_NAMESAKE else MaterialAdoption.NAMESAKE_NOT_READ_YET
        val syncedAt = clock.nowEpochMillis()
        val alreadyLocal = materialDao.findByServerId(match.id)
        if (alreadyLocal == null) {
            materialDao.writeIfUnchanged(match.toSyncedEntity(material.localId, material.projectLocalId, syncedAt, material))
        } else {
            materialAdoptionDao.mergeInto(material.localId, match.toSyncedEntity(alreadyLocal.localId, material.projectLocalId, syncedAt, alreadyLocal))
        }
        return MaterialAdoption.ADOPTED
    }

    private suspend fun pushMaterialUpdate(material: MaterialEntity) {
        val serverId = material.serverId ?: return pushMaterialCreate(material)
        val updated = try {
            serverCall { materialApi.update(serverId, material.toUpdateRequest()) }
        } catch (e: DomainException.NotFound) {
            materialDao.writeIfUnchanged(material.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER))
            return
        } catch (e: DomainException) {
            if (e.isServerRejection()) {
                materialDao.writeIfUnchanged(material.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.UPDATE_REFUSED, serverErrorCode = refusalCode))
                return
            }
            throw e
        }
        materialDao.writeIfUnchanged(updated.toSyncedEntity(material.localId, material.projectLocalId, clock.nowEpochMillis(), material))
    }

    // ─── daily entries (and, implicitly, their daily log) ───────────────────

    private suspend fun pushEntryCreate(entry: DailyEntryEntity) {
        val log = dailyLogDao.findByLocalId(entry.dailyLogLocalId) ?: return
        val stage = stageDao.findByLocalId(log.stageLocalId) ?: return
        val stageServerId = stage.serverId ?: return
        val body = entry.toEntryRequest()
        val dto = try {
            when (entry.type.uppercase()) {
                "PURCHASE" -> serverCall { dailyLogApi.createPurchaseEntry(stageServerId, log.date, body) }
                "WORK" -> serverCall { dailyLogApi.createWorkEntry(stageServerId, log.date, body) }
                else -> return
            }
        } catch (e: DomainException.NotFound) {
            keepEntryGoneOnServer(entry)
            return
        } catch (e: DomainException) {
            if (e.isServerRejection()) {
                dailyEntryDao.writeIfUnchanged(entry.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED, serverErrorCode = refusalCode))
                return
            }
            throw e
        }
        val now = clock.nowEpochMillis()
        val synced = dto.toSyncedEntity(entry.localId, entry.dailyLogLocalId, now, entry)
        dailyEntryDao.storeCreated(synced, removeFromServer = { dailyLogApi.deleteEntry(dto.id) }) { edited ->
            edited.copy(serverId = synced.serverId, pendingOp = PendingOp.UPDATE, remoteUpdatedAt = synced.remoteUpdatedAt, createdById = synced.createdById, createdAt = synced.createdAt)
        }
        // The parent day's server id is learned here, never through its own
        // endpoint (it has none — ADR-27).
        if (log.serverId == null) {
            dailyLogDao.upsert(log.copy(serverId = dto.dailyLogId, lastSyncedAt = now))
        }
    }

    private suspend fun pushEntryUpdate(entry: DailyEntryEntity) {
        val serverId = entry.serverId ?: return pushEntryCreate(entry)
        val updated = try {
            serverCall { dailyLogApi.updateEntry(serverId, entry.toEntryRequest()) }
        } catch (e: DomainException.NotFound) {
            entryGoneOnServer(entry)
            return
        } catch (e: DomainException) {
            if (e.isServerRejection()) {
                dailyEntryDao.writeIfUnchanged(entry.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.UPDATE_REFUSED, serverErrorCode = refusalCode))
                return
            }
            throw e
        }
        dailyEntryDao.writeIfUnchanged(updated.toSyncedEntity(entry.localId, entry.dailyLogLocalId, clock.nowEpochMillis(), entry))
    }

    private suspend fun pushEntryDelete(entry: DailyEntryEntity) {
        val serverId = entry.serverId
        if (serverId != null && deleteOnServer { dailyLogApi.deleteEntry(serverId) } == RemoteDelete.REJECTED) {
            dailyEntryDao.writeIfUnchanged(entry.copy(syncStatus = SyncStatus.SYNCED, pendingOp = PendingOp.NONE, lastSyncError = SyncError.REJECTED, serverErrorCode = refusalCode))
            return
        }
        projectLocalIdOfDay(entry.dailyLogLocalId)?.let { stockDao.markNeedsRefresh(it) }
        dailyEntryDao.deleteIfUnchanged(entry)
    }

    // ─── purchase lines ─────────────────────────────────────────────────────

    private suspend fun pushPurchaseLineCreate(line: PurchaseLineEntity) {
        val entryServerId = dailyEntryDao.findByLocalId(line.entryLocalId)?.serverId ?: return
        val material = materialDao.findByLocalId(line.materialLocalId) ?: return
        val materialServerId = material.serverId ?: return
        val created = try {
            serverCall { purchaseLineApi.create(entryServerId, line.toCreateRequest(materialServerId)) }
        } catch (e: DomainException.NotFound) {
            purchaseLineDao.writeIfUnchanged(line.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER))
            return
        } catch (e: DomainException) {
            if (e.isServerRejection()) {
                purchaseLineDao.writeIfUnchanged(line.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED, serverErrorCode = refusalCode))
                return
            }
            throw e
        }
        val synced = created.toSyncedEntity(line.localId, line.entryLocalId, line.materialLocalId, clock.nowEpochMillis(), line)
        if (stockDao.recordPurchaseLineSynced(material.projectLocalId, materialServerId, synced, previousServerQuantity = null)) {
            loadStockAtEndOfPassIfNeverLoaded(material.projectLocalId)
            return
        }
        purchaseLineDao.storeCreated(synced, removeFromServer = { purchaseLineApi.delete(created.id) }) { edited ->
            edited.copy(serverId = synced.serverId, pendingOp = PendingOp.UPDATE, remoteUpdatedAt = synced.remoteUpdatedAt, serverQuantity = synced.serverQuantity)
        }
        reloadStockAtEndOfPass(material.projectLocalId)
    }

    private suspend fun pushPurchaseLineUpdate(line: PurchaseLineEntity) {
        val serverId = line.serverId ?: return pushPurchaseLineCreate(line)
        val updated = try {
            serverCall { purchaseLineApi.update(serverId, line.toUpdateRequest()) }
        } catch (e: DomainException.NotFound) {
            purchaseLineDao.writeIfUnchanged(line.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER))
            return
        } catch (e: DomainException) {
            if (e.isServerRejection()) {
                purchaseLineDao.writeIfUnchanged(line.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.UPDATE_REFUSED, serverErrorCode = refusalCode))
                return
            }
            throw e
        }
        val synced = updated.toSyncedEntity(line.localId, line.entryLocalId, line.materialLocalId, clock.nowEpochMillis(), line)
        val material = materialDao.findByLocalId(line.materialLocalId)
        val materialServerId = material?.serverId
        val stored = if (material == null || materialServerId == null) {
            purchaseLineDao.writeIfUnchanged(synced)
        } else {
            stockDao.recordPurchaseLineSynced(material.projectLocalId, materialServerId, synced, previousServerQuantity = line.serverQuantity ?: synced.quantity)
        }
        if (!stored) purchaseLineDao.keepLocalChange(line.localId) { edited -> edited.copy(serverQuantity = synced.serverQuantity, remoteUpdatedAt = synced.remoteUpdatedAt) }
        if (material != null) reloadStockAtEndOfPass(material.projectLocalId)
    }

    private suspend fun pushPurchaseLineDelete(line: PurchaseLineEntity) {
        val serverId = line.serverId
        if (serverId == null) {
            purchaseLineDao.deleteIfUnchanged(line)
            return
        }
        val outcome = deleteOnServer { purchaseLineApi.delete(serverId) }
        if (outcome == RemoteDelete.REJECTED) {
            purchaseLineDao.writeIfUnchanged(
                line.copy(
                    syncStatus = SyncStatus.SYNCED,
                    pendingOp = PendingOp.NONE,
                    lastSyncError = SyncError.REJECTED,
                    serverErrorCode = refusalCode,
                    serverQuantity = line.serverQuantity ?: line.quantity,
                ),
            )
            return
        }
        val material = materialDao.findByLocalId(line.materialLocalId)
        val materialServerId = material?.serverId
        if (material == null || materialServerId == null) {
            purchaseLineDao.deleteIfUnchanged(line)
            return
        }
        if (outcome == RemoteDelete.DELETED) {
            stockDao.recordPurchaseLineDeleted(material.projectLocalId, materialServerId, line, line.serverQuantity ?: line.quantity)
        } else {
            purchaseLineDao.deleteIfUnchanged(line)
        }
        reloadStockAtEndOfPass(material.projectLocalId)
    }

    // ─── consumption lines ──────────────────────────────────────────────────

    private suspend fun pushConsumptionLineCreate(line: ConsumptionLineEntity) {
        val entryServerId = dailyEntryDao.findByLocalId(line.entryLocalId)?.serverId ?: return
        val material = materialDao.findByLocalId(line.materialLocalId) ?: return
        val materialServerId = material.serverId ?: return
        val created = try {
            serverCall { consumptionLineApi.create(entryServerId, line.toCreateRequest(materialServerId)) }
        } catch (e: DomainException.NotFound) {
            consumptionLineDao.writeIfUnchanged(line.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER))
            return
        } catch (e: DomainException) {
            if (e.isServerRejection()) {
                consumptionLineDao.writeIfUnchanged(line.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED, serverErrorCode = refusalCode))
                return
            }
            throw e
        }
        val synced = created.toSyncedEntity(line.localId, line.entryLocalId, line.materialLocalId, clock.nowEpochMillis(), line)
        if (stockDao.recordConsumptionLineSynced(material.projectLocalId, materialServerId, synced, previousServerQuantity = null)) {
            loadStockAtEndOfPassIfNeverLoaded(material.projectLocalId)
            return
        }
        consumptionLineDao.storeCreated(synced, removeFromServer = { consumptionLineApi.delete(created.id) }) { edited ->
            edited.copy(serverId = synced.serverId, pendingOp = PendingOp.UPDATE, remoteUpdatedAt = synced.remoteUpdatedAt, serverQuantity = synced.serverQuantity)
        }
        reloadStockAtEndOfPass(material.projectLocalId)
    }

    private suspend fun pushConsumptionLineUpdate(line: ConsumptionLineEntity) {
        val serverId = line.serverId ?: return pushConsumptionLineCreate(line)
        val updated = try {
            serverCall { consumptionLineApi.update(serverId, line.toUpdateRequest()) }
        } catch (e: DomainException.NotFound) {
            consumptionLineDao.writeIfUnchanged(line.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER))
            return
        } catch (e: DomainException) {
            if (e.isServerRejection()) {
                consumptionLineDao.writeIfUnchanged(line.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.UPDATE_REFUSED, serverErrorCode = refusalCode))
                return
            }
            throw e
        }
        val synced = updated.toSyncedEntity(line.localId, line.entryLocalId, line.materialLocalId, clock.nowEpochMillis(), line)
        val material = materialDao.findByLocalId(line.materialLocalId)
        val materialServerId = material?.serverId
        val stored = if (material == null || materialServerId == null) {
            consumptionLineDao.writeIfUnchanged(synced)
        } else {
            stockDao.recordConsumptionLineSynced(material.projectLocalId, materialServerId, synced, previousServerQuantity = line.serverQuantity ?: synced.quantity)
        }
        if (!stored) consumptionLineDao.keepLocalChange(line.localId) { edited -> edited.copy(serverQuantity = synced.serverQuantity, remoteUpdatedAt = synced.remoteUpdatedAt) }
        if (material != null) reloadStockAtEndOfPass(material.projectLocalId)
    }

    private suspend fun pushConsumptionLineDelete(line: ConsumptionLineEntity) {
        val serverId = line.serverId
        if (serverId == null) {
            consumptionLineDao.deleteIfUnchanged(line)
            return
        }
        val outcome = deleteOnServer { consumptionLineApi.delete(serverId) }
        if (outcome == RemoteDelete.REJECTED) {
            consumptionLineDao.writeIfUnchanged(
                line.copy(
                    syncStatus = SyncStatus.SYNCED,
                    pendingOp = PendingOp.NONE,
                    lastSyncError = SyncError.REJECTED,
                    serverErrorCode = refusalCode,
                    serverQuantity = line.serverQuantity ?: line.quantity,
                ),
            )
            return
        }
        val material = materialDao.findByLocalId(line.materialLocalId)
        val materialServerId = material?.serverId
        if (material == null || materialServerId == null) {
            consumptionLineDao.deleteIfUnchanged(line)
            return
        }
        if (outcome == RemoteDelete.DELETED) {
            stockDao.recordConsumptionLineDeleted(material.projectLocalId, materialServerId, line, line.serverQuantity ?: line.quantity)
        } else {
            consumptionLineDao.deleteIfUnchanged(line)
        }
        reloadStockAtEndOfPass(material.projectLocalId)
    }

    // ─── attachments (photos) ───────────────────────────────────────────────

    private suspend fun pushAttachmentCreate(attachment: AttachmentEntity) {
        val entryServerId = dailyEntryDao.findByLocalId(attachment.entryLocalId)?.serverId ?: return
        val bytes = try {
            attachmentFileStore.readBytes(attachment.localPath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // The local copy is gone — nothing left to upload.
            attachmentDao.writeIfUnchanged(attachment.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.FILE_REFUSED, serverErrorCode = LOCAL_FILE_MISSING_CODE))
            return
        }
        val dto = try {
            serverCall {
                attachmentApi.upload(
                    entryId = entryServerId,
                    contentLength = bytes.size.toLong(),
                    fileName = attachment.originalName,
                    mimeType = attachment.mimeType,
                    openSource = { Buffer().apply { write(bytes) } },
                )
            }
        } catch (e: DomainException.NotFound) {
            attachmentDao.writeIfUnchanged(attachment.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.DELETED_ON_SERVER))
            return
        } catch (e: DomainException) {
            if (e.isFileRefusal()) {
                attachmentDao.writeIfUnchanged(
                    attachment.copy(
                        syncStatus = SyncStatus.CONFLICTED,
                        lastSyncError = SyncError.FILE_REFUSED,
                        serverErrorCode = refusalCode ?: ATTACHMENT_TOO_LARGE_CODE,
                    ),
                )
                return
            }
            if (e.isServerRejection() || e is DomainException.Unexpected) {
                attachmentDao.writeIfUnchanged(attachment.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED, serverErrorCode = refusalCode))
                return
            }
            throw e
        }
        val synced = dto.toSyncedEntity(attachment.localId, attachment.entryLocalId, attachment.localPath, clock.nowEpochMillis(), attachment)
        attachmentDao.storeCreated(synced, removeFromServer = { attachmentApi.delete(dto.id) }) { edited -> edited.copy(serverId = synced.serverId) }
    }

    private suspend fun pushAttachmentDelete(attachment: AttachmentEntity) {
        val serverId = attachment.serverId
        if (serverId != null && deleteOnServer { attachmentApi.delete(serverId) } == RemoteDelete.REJECTED) {
            restoreAttachmentRefusedByServer(attachment, serverId)
            return
        }
        // The local file was already freed by AttachmentRepositoryImpl at delete time (ADR-29).
        attachmentDao.deleteIfUnchanged(attachment)
    }

    // The local file was freed at delete time (ADR-29), so bringing the row back
    // means re-downloading its bytes — the same call the pull uses for a file that
    // first appeared on the server.
    private suspend fun restoreAttachmentRefusedByServer(attachment: AttachmentEntity, serverId: Long) {
        val bytes = try {
            serverCall { attachmentApi.download(serverId) }
        } catch (e: DomainException.NotFound) {
            attachmentDao.deleteIfUnchanged(attachment)
            return
        }
        val path = attachmentFileStore.save(bytes, attachment.originalName)
        attachmentDao.writeIfUnchanged(
            attachment.copy(
                localPath = path,
                syncStatus = SyncStatus.SYNCED,
                pendingOp = PendingOp.NONE,
                lastSyncError = SyncError.REJECTED,
                serverErrorCode = refusalCode,
            ),
        )
    }

    // ─── pull: materials ────────────────────────────────────────────────────

    private suspend fun pullMaterials(projectServerId: Long, projectLocalId: String) {
        val remote = readProjectMaterials(projectServerId).items
        val syncedAt = clock.nowEpochMillis()
        for (dto in remote) storeServerMaterial(dto, projectLocalId, syncedAt)
        // No removal pass — the backend never deletes a material (PATCH-only).
    }

    private suspend fun storeServerMaterial(dto: MaterialDto, projectLocalId: String, syncedAt: Long) {
        val byServerId = materialDao.findByServerId(dto.id)
        val sameName = materialDao.findByProjectAndNameExactly(projectLocalId, dto.name)
        when {
            sameName == null || sameName.localId == byServerId?.localId -> when {
                byServerId == null -> materialDao.insertNew(dto.toSyncedEntity(newLocalId(), projectLocalId, syncedAt))
                byServerId.pendingOp == PendingOp.NONE -> materialDao.writeIfUnchanged(dto.toSyncedEntity(byServerId.localId, projectLocalId, syncedAt, byServerId))
                // else: a pending local rename — it wins on its next push (LWW).
                else -> Unit
            }
            sameName.serverId != null -> Unit
            byServerId == null -> materialDao.writeIfUnchanged(dto.toSyncedEntity(sameName.localId, projectLocalId, syncedAt, sameName))
            else -> materialAdoptionDao.mergeInto(sameName.localId, dto.toSyncedEntity(byServerId.localId, projectLocalId, syncedAt, byServerId))
        }
    }

    // ─── pull: stock counters (ADR-71) ──────────────────────────────────────

    private suspend fun refreshStock(projectServerId: Long, projectLocalId: String) {
        val read = try {
            readAllPages(MaterialStockDto::materialId) { page, size -> serverCall { stockApi.list(projectServerId, page, size) } }
        } catch (e: DomainException.NotFound) {
            return
        } catch (e: DomainException.Forbidden) {
            return
        }
        if (skipsRemovalAfter(read, "stock of project $projectServerId")) {
            stockDao.markNeedsRefresh(projectLocalId)
            stocksReadIncompletelyThisPass += projectLocalId
            return
        }
        val counters = read.items.map { MaterialStockEntity(projectLocalId, it.materialId, it.quantityIn, it.quantityOut) }
        stockDao.replaceCounters(projectLocalId, counters, clock.nowEpochMillis())
    }

    private suspend fun refreshStockOfDay(log: DailyLogEntity) {
        val stage = stageDao.findByLocalId(log.stageLocalId) ?: return
        val project = dao.findByLocalId(stage.projectLocalId) ?: return
        val projectServerId = project.serverId ?: return
        if (project.lastSyncError == SyncError.DELETED_ON_SERVER) return
        pullMaterials(projectServerId, project.localId)
        refreshStock(projectServerId, project.localId)
    }

    private suspend fun reloadStockAtEndOfPass(projectLocalId: String) {
        stockDao.markNeedsRefresh(projectLocalId)
        loadStockAtEndOfPassIfNeverLoaded(projectLocalId)
    }

    private suspend fun loadStockAtEndOfPassIfNeverLoaded(projectLocalId: String) {
        if (stockDao.findSnapshot(projectLocalId) == null) neverLoadedStocksTouchedThisPass += projectLocalId
    }

    private suspend fun refreshStocksMarkedForRefresh() {
        val projectLocalIds = stockDao.findProjectsNeedingRefresh() + neverLoadedStocksTouchedThisPass
        neverLoadedStocksTouchedThisPass.clear()
        for (projectLocalId in projectLocalIds.distinct() - stocksReadIncompletelyThisPass) {
            val project = dao.findByLocalId(projectLocalId) ?: continue
            val projectServerId = project.serverId ?: continue
            if (project.lastSyncError == SyncError.DELETED_ON_SERVER) continue
            refreshStock(projectServerId, projectLocalId)
        }
    }

    private suspend fun projectLocalIdOfDay(dailyLogLocalId: String): String? {
        val log = dailyLogDao.findByLocalId(dailyLogLocalId) ?: return null
        return stageDao.findByLocalId(log.stageLocalId)?.projectLocalId
    }

    // ─── pull: daily log summaries (one stage) ──────────────────────────────

    private suspend fun pullLogSummaries(stageServerId: Long, stageLocalId: String) {
        val read = readAllPages(DailyLogSummaryDto::id) { page, size -> serverCall { dailyLogApi.listLogs(stageServerId, page, size) } }
        logIncomplete(read, "day logs of stage $stageServerId")
        val remote = read.items
        val locals = dailyLogDao.findForStage(stageLocalId)
        val byDate = locals.associateBy { it.date }
        val syncedAt = clock.nowEpochMillis()

        for (dto in remote) {
            val local = byDate[dto.date]
            when {
                local == null ->
                    dailyLogDao.upsert(
                        DailyLogEntity(
                            localId = newLocalId(),
                            serverId = dto.id,
                            stageLocalId = stageLocalId,
                            date = dto.date,
                            locallyCreatedAt = syncedAt,
                            lastSyncedAt = syncedAt,
                        ),
                    )
                local.serverId == null ->
                    dailyLogDao.upsert(local.copy(serverId = dto.id, lastSyncedAt = syncedAt))
                else -> Unit
            }
        }
        // Days are never removed on pull — they carry no pendingOp and a
        // local-only day (with unsynced entries) must not vanish.
    }

    // ─── pull: entries (one day) ────────────────────────────────────────────

    private suspend fun pullEntries(remote: List<com.dmb.chantiertracker.data.remote.dto.DailyEntryDto>, logLocalId: String) {
        val locals = dailyEntryDao.findForLog(logLocalId)
        val byServerId = locals.mapNotNull { e -> e.serverId?.let { it to e } }.toMap()
        val byType = locals.associateBy { it.type.uppercase() }
        val syncedAt = clock.nowEpochMillis()

        for (dto in remote) {
            val existing = byServerId[dto.id] ?: byType[dto.type.uppercase()]
            when {
                existing == null -> dailyEntryDao.insertNew(dto.toSyncedEntity(newLocalId(), logLocalId, syncedAt))
                existing.pendingOp == PendingOp.NONE -> dailyEntryDao.writeIfUnchanged(dto.toSyncedEntity(existing.localId, logLocalId, syncedAt, existing))
                else -> Unit
            }
        }

        val remoteIds = remote.map { it.id }.toSet()
        locals
            .filter { it.serverId != null && it.serverId !in remoteIds }
            .filter { it.syncStatus == SyncStatus.SYNCED && it.pendingOp == PendingOp.NONE }
            .forEach { entryGoneOnServer(it) }
    }

    // ─── pull: lines (one entry) ────────────────────────────────────────────

    private suspend fun pullPurchaseLines(entryServerId: Long, entryLocalId: String) {
        val read = readAllPages(PurchaseLineDto::id) { page, size -> serverCall { purchaseLineApi.list(entryServerId, page, size) } }
        val remote = read.items
        val locals = purchaseLineDao.findForEntry(entryLocalId)
        val byServerId = locals.mapNotNull { l -> l.serverId?.let { it to l } }.toMap()
        val syncedAt = clock.nowEpochMillis()

        for (dto in remote) {
            val materialLocalId = materialDao.findByServerId(dto.materialId)?.localId ?: continue
            val local = byServerId[dto.id]
            when {
                local == null -> purchaseLineDao.insertNew(dto.toSyncedEntity(newLocalId(), entryLocalId, materialLocalId, syncedAt))
                local.pendingOp == PendingOp.NONE -> purchaseLineDao.writeIfUnchanged(dto.toSyncedEntity(local.localId, entryLocalId, materialLocalId, syncedAt, local))
                else -> Unit
            }
        }

        if (skipsRemovalAfter(read, "purchase lines of entry $entryServerId")) return
        val remoteIds = remote.map { it.id }.toSet()
        locals
            .filter { it.serverId != null && it.serverId !in remoteIds }
            .filter { it.syncStatus == SyncStatus.SYNCED && it.pendingOp == PendingOp.NONE }
            .forEach { purchaseLineDao.deleteIfUnchanged(it) }
    }

    private suspend fun pullConsumptionLines(entryServerId: Long, entryLocalId: String) {
        val read = readAllPages(ConsumptionLineDto::id) { page, size -> serverCall { consumptionLineApi.list(entryServerId, page, size) } }
        val remote = read.items
        val locals = consumptionLineDao.findForEntry(entryLocalId)
        val byServerId = locals.mapNotNull { l -> l.serverId?.let { it to l } }.toMap()
        val syncedAt = clock.nowEpochMillis()

        for (dto in remote) {
            val materialLocalId = materialDao.findByServerId(dto.materialId)?.localId ?: continue
            val local = byServerId[dto.id]
            when {
                local == null -> consumptionLineDao.insertNew(dto.toSyncedEntity(newLocalId(), entryLocalId, materialLocalId, syncedAt))
                local.pendingOp == PendingOp.NONE -> consumptionLineDao.writeIfUnchanged(dto.toSyncedEntity(local.localId, entryLocalId, materialLocalId, syncedAt, local))
                else -> Unit
            }
        }

        if (skipsRemovalAfter(read, "consumption lines of entry $entryServerId")) return
        val remoteIds = remote.map { it.id }.toSet()
        locals
            .filter { it.serverId != null && it.serverId !in remoteIds }
            .filter { it.syncStatus == SyncStatus.SYNCED && it.pendingOp == PendingOp.NONE }
            .forEach { consumptionLineDao.deleteIfUnchanged(it) }
    }

    // ─── pull: attachments (one entry) ──────────────────────────────────────

    private fun defaultAttachmentName(mimeType: String?): String =
        if (mimeType?.startsWith("video/") == true) "video.mp4" else "photo.jpg"

    private suspend fun pullAttachments(entryServerId: Long, entryLocalId: String) {
        val read = readAllPages(AttachmentDto::id) { page, size -> serverCall { attachmentApi.list(entryServerId, page, size) } }
        val remote = read.items
        val locals = attachmentDao.findForEntry(entryLocalId)
        val knownServerIds = locals.mapNotNull { it.serverId }.toSet()
        val syncedAt = clock.nowEpochMillis()

        for (dto in remote) {
            if (dto.id in knownServerIds) continue
            // A photo or video that first appeared on the server (web, another
            // device) — download it once and keep a local copy, same as one
            // added here. Videos are already transcoded server-side (ADR-35).
            val bytes = serverCall { attachmentApi.download(dto.id) }
            val path = attachmentFileStore.save(bytes, dto.originalName ?: defaultAttachmentName(dto.mimeType))
            attachmentDao.insertNew(dto.toSyncedEntity(newLocalId(), entryLocalId, path, syncedAt))
        }

        if (skipsRemovalAfter(read, "attachments of entry $entryServerId")) return
        val remoteIds = remote.map { it.id }.toSet()
        locals
            .filter { it.serverId != null && it.serverId !in remoteIds }
            .filter { it.syncStatus == SyncStatus.SYNCED && it.pendingOp == PendingOp.NONE }
            .forEach {
                attachmentFileStore.delete(it.localPath)
                attachmentDao.deleteIfUnchanged(it)
            }
    }

    private suspend fun pushStageCreate(stage: StageEntity) {
        // Parent project not on the server yet → leave the stage PENDING; the next
        // pass (after the project pushes) picks it up. Not an error.
        val projectServerId = dao.findByLocalId(stage.projectLocalId)?.serverId ?: return
        val created = try {
            serverCall { stageApi.create(projectServerId, stage.toCreateRequest()) }
        } catch (e: DomainException.NotFound) {
            keepStageGoneOnServer(stage)
            return
        } catch (e: DomainException.Forbidden) {
            stageDao.writeIfUnchanged(stage.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED, serverErrorCode = refusalCode))
            return
        } catch (e: DomainException.Validation) {
            stageDao.writeIfUnchanged(stage.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED, serverErrorCode = refusalCode))
            return
        }
        val synced = created.toSyncedEntity(
            localId = stage.localId,
            projectLocalId = stage.projectLocalId,
            syncedAt = clock.nowEpochMillis(),
            previous = stage,
        )
        stageDao.storeCreated(synced, removeFromServer = { stageApi.delete(created.id) }) { edited ->
            edited.copy(serverId = synced.serverId, pendingOp = PendingOp.UPDATE, remoteUpdatedAt = synced.remoteUpdatedAt)
        }
    }

    private suspend fun pushStageUpdate(stage: StageEntity) {
        val serverId = stage.serverId ?: return pushStageCreate(stage)

        try {
            serverCall { stageApi.get(serverId) }
        } catch (e: DomainException.NotFound) {
            stageGoneOnServer(stage)
            return
        }

        val updated = try {
            serverCall { stageApi.update(serverId, stage.toUpdateRequest()) }
        } catch (e: DomainException) {
            if (!e.isServerRejection()) throw e
            stageDao.writeIfUnchanged(stage.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.UPDATE_REFUSED, serverErrorCode = refusalCode))
            return
        }
        stageDao.writeIfUnchanged(
            updated.toSyncedEntity(
                localId = stage.localId,
                projectLocalId = stage.projectLocalId,
                syncedAt = clock.nowEpochMillis(),
                previous = stage,
            ),
        )
    }

    private suspend fun pushStageDelete(stage: StageEntity) {
        val serverId = stage.serverId
        if (serverId != null && deleteOnServer { stageApi.delete(serverId) } == RemoteDelete.REJECTED) {
            stageDao.writeIfUnchanged(stage.copy(syncStatus = SyncStatus.SYNCED, pendingOp = PendingOp.NONE, lastSyncError = SyncError.REJECTED, serverErrorCode = refusalCode))
            return
        }
        stockDao.markNeedsRefresh(stage.projectLocalId)
        stageDao.deleteIfUnchanged(stage)
    }

    private suspend fun pushCreate(entity: ProjectEntity) {
        val created = try {
            serverCall { api.create(entity.toCreateRequest()) }
        } catch (e: DomainException.PlanLimitReached) {
            dao.writeIfUnchanged(entity.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.PLAN_LIMIT, serverErrorCode = refusalCode))
            return
        } catch (e: DomainException.Validation) {
            dao.writeIfUnchanged(entity.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED, serverErrorCode = refusalCode))
            return
        }
        val synced = created.toSyncedEntity(localId = entity.localId, syncedAt = clock.nowEpochMillis(), previous = entity)
        dao.storeCreated(synced, removeFromServer = { api.delete(created.id) }) { edited ->
            edited.copy(serverId = synced.serverId, pendingOp = PendingOp.UPDATE, remoteUpdatedAt = synced.remoteUpdatedAt, ownerId = synced.ownerId, createdAt = synced.createdAt)
        }
    }

    private suspend fun pushUpdate(entity: ProjectEntity) {
        val serverId = entity.serverId ?: return pushCreate(entity)

        val remote = try {
            serverCall { api.get(serverId) }
        } catch (e: DomainException.NotFound) {
            projectGoneOnServer(entity)
            return
        }

        if (serverChangedSinceLastSync(remote.updatedAt, entity)) {
            // A concurrent edit landed on the server since we last synced this row,
            // so our local edit is based on a stale copy → the server version wins.
            dao.writeIfUnchanged(remote.toSyncedEntity(localId = entity.localId, syncedAt = clock.nowEpochMillis(), previous = entity))
            return
        }

        val updated = try {
            serverCall { api.update(serverId, entity.toUpdateRequest()) }
        } catch (e: DomainException) {
            if (!e.isServerRejection()) throw e
            dao.writeIfUnchanged(entity.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.UPDATE_REFUSED, serverErrorCode = refusalCode))
            return
        }
        val synced = updated.toSyncedEntity(localId = entity.localId, syncedAt = clock.nowEpochMillis(), previous = entity)
        if (!dao.writeIfUnchanged(synced)) dao.keepLocalChange(entity.localId) { edited -> edited.copy(remoteUpdatedAt = synced.remoteUpdatedAt) }
    }

    /**
     * True when the server's `updatedAt` for this row differs from the value we
     * recorded at our last successful sync. Both sides are server-generated, so
     * this comparison needs no agreement between the server clock and the
     * device clock — unlike a wall-clock last-write-wins (see ADR-21).
     */
    private fun serverChangedSinceLastSync(serverUpdatedAt: String?, local: ProjectEntity): Boolean =
        parseServerTimestampMillis(serverUpdatedAt) != local.remoteUpdatedAt

    private suspend fun pushDelete(entity: ProjectEntity) {
        val serverId = entity.serverId
        if (serverId != null && deleteOnServer { api.delete(serverId) } == RemoteDelete.REJECTED) {
            dao.writeIfUnchanged(entity.copy(syncStatus = SyncStatus.SYNCED, pendingOp = PendingOp.NONE, lastSyncError = SyncError.REJECTED, serverErrorCode = refusalCode))
            return
        }
        dao.deleteIfUnchanged(entity)
    }

    private suspend fun pullAll() {
        val read = readAllPages(ProjectDto::id) { page, size -> serverCall { api.list(page, size) } }
        val remote = read.items
        val locals = dao.findAll()
        val byServerId = locals.mapNotNull { local -> local.serverId?.let { it to local } }.toMap()
        val syncedAt = clock.nowEpochMillis()

        for (dto in remote) {
            val local = byServerId[dto.id]
            when {
                local == null ->
                    dao.insertNew(dto.toSyncedEntity(localId = newLocalId(), syncedAt = syncedAt))

                local.pendingOp == PendingOp.NONE ->
                    dao.writeIfUnchanged(dto.toSyncedEntity(localId = local.localId, syncedAt = syncedAt, previous = local))

                serverChangedSinceLastSync(dto.updatedAt, local) ->
                    // The server row moved on since our last sync → it wins over the pending local edit.
                    dao.writeIfUnchanged(dto.toSyncedEntity(localId = local.localId, syncedAt = syncedAt, previous = local))

                // else: server unchanged since our last sync → keep the local edit pending, it pushes cleanly.
                else -> Unit
            }
        }

        if (skipsRemovalAfter(read, "projects")) return
        val remoteIds = remote.map { it.id }.toSet()
        locals
            .filter { it.serverId != null && it.serverId !in remoteIds }
            .filter { it.syncStatus == SyncStatus.SYNCED && it.pendingOp == PendingOp.NONE }
            .forEach { projectGoneOnServer(it) }
    }

    private fun skipsRemovalAfter(read: PagedRead<*>, listName: String): Boolean {
        val reason = read.incompleteReason ?: return false
        log("sync: incomplete read of $listName ($reason), ${read.items.size} read, local removal skipped")
        return true
    }

    private fun logIncomplete(read: PagedRead<*>, listName: String) {
        val reason = read.incompleteReason ?: return
        log("sync: incomplete read of $listName ($reason), ${read.items.size} read")
    }
}

private const val ATTACHMENT_TOO_LARGE_CODE = "ATTACHMENT_TOO_LARGE"
private const val LOCAL_FILE_MISSING_CODE = "LOCAL_FILE_MISSING"
private val FILE_REFUSAL_CODES = setOf(
    ATTACHMENT_TOO_LARGE_CODE,
    "INVALID_ATTACHMENT_TYPE",
    "INVALID_ATTACHMENT_NAME",
    "UNSUPPORTED_IMAGE",
    "UNREADABLE_VIDEO",
    "VIDEO_TOO_LONG",
)
