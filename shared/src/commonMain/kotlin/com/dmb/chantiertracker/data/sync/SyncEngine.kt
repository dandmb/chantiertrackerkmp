@file:OptIn(ExperimentalUuidApi::class)

package com.dmb.chantiertracker.data.sync

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
import com.dmb.chantiertracker.data.local.db.MaterialDao
import com.dmb.chantiertracker.data.local.db.MaterialEntity
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.ProjectDao
import com.dmb.chantiertracker.data.local.db.ProjectEntity
import com.dmb.chantiertracker.data.local.db.PurchaseLineDao
import com.dmb.chantiertracker.data.local.db.PurchaseLineEntity
import com.dmb.chantiertracker.data.local.db.StageDao
import com.dmb.chantiertracker.data.local.db.StageEntity
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.remote.AttachmentApi
import com.dmb.chantiertracker.data.remote.ConsumptionLineApi
import com.dmb.chantiertracker.data.remote.DailyLogApi
import com.dmb.chantiertracker.data.remote.InvitationApi
import com.dmb.chantiertracker.data.remote.MaterialApi
import com.dmb.chantiertracker.data.remote.ProjectApi
import com.dmb.chantiertracker.data.remote.PurchaseLineApi
import com.dmb.chantiertracker.data.remote.StageApi
import com.dmb.chantiertracker.data.remote.apiCall
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
    private val connectivity: ConnectivityObserver,
    private val syncState: SyncStateHolder,
    private val scope: CoroutineScope,
    private val clock: Clock = SystemClock,
    private val newLocalId: () -> String = { Uuid.random().toString() },
    private val backgroundSync: BackgroundSync = NoOpBackgroundSync,
    private val catchUpInterval: Duration = 15.minutes,
) : Syncer {

    private val mutex = Mutex()
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

    override suspend fun syncNow(): SyncOutcome = mutex.withLock { runSync() }

    override suspend fun syncProject(localId: String): SyncOutcome = mutex.withLock { runProjectSync(localId) }

    override suspend fun syncStage(stageLocalId: String): SyncOutcome = mutex.withLock { runStageSync(stageLocalId) }

    override suspend fun syncLog(logLocalId: String): SyncOutcome = mutex.withLock { runLogSync(logLocalId) }

    private suspend fun runSync(): SyncOutcome {
        if (!connectivity.isOnline()) {
            syncState.update(SyncState.Offline)
            return SyncOutcome.Skipped
        }
        syncState.update(SyncState.Syncing)
        return try {
            pushPending()
            pullAll()
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
            apiCall { api.get(serverId) }
        } catch (e: DomainException.NotFound) {
            dao.deleteByLocalId(localId)
            return
        }
        val local = dao.findByLocalId(localId)
        if (local != null && local.pendingOp == PendingOp.NONE) {
            dao.upsert(detail.toSyncedEntity(localId = localId, syncedAt = clock.nowEpochMillis(), previous = local))
        }
        pullMembers(serverId, localId)
        pullInvitations(serverId, localId)
        pullStages(serverId, localId)
        pullMaterials(serverId, localId)
        // Log *summaries* only (existence + server id per day); a day's entries,
        // lines and photos are pulled by syncLog when its screen opens, so
        // syncProject stays proportional to what the project detail shows.
        for (stage in stageDao.findForProject(localId)) {
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
                apiCall { stageApi.get(serverId) }
            } catch (e: DomainException.NotFound) {
                stageDao.deleteByLocalId(stageLocalId)
                return SyncOutcome.Synced
            }
            val local = stageDao.findByLocalId(stageLocalId)
            if (local != null && local.pendingOp == PendingOp.NONE) {
                stageDao.upsert(
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
                apiCall { dailyLogApi.getLog(logServerId) }
            } catch (e: DomainException.NotFound) {
                // A day is never deleted through its own endpoint; a 404 here just
                // means it has no server-side entries left. Keep the local row.
                return SyncOutcome.Synced
            }
            pullEntries(detail.entries, logLocalId)
            for (entry in dailyEntryDao.findForLog(logLocalId)) {
                val entryServerId = entry.serverId ?: continue
                when (entry.type.uppercase()) {
                    "PURCHASE" -> {
                        pullPurchaseLines(entryServerId, entry.localId)
                        pullAttachments(entryServerId, entry.localId)
                    }
                    "WORK" -> pullConsumptionLines(entryServerId, entry.localId)
                }
            }
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
        val remote = apiCall { stageApi.list(projectServerId) }.content
        val locals = stageDao.findForProject(projectLocalId)
        val byServerId = locals.mapNotNull { local -> local.serverId?.let { it to local } }.toMap()
        val syncedAt = clock.nowEpochMillis()

        for (dto in remote) {
            val local = byServerId[dto.id]
            when {
                local == null ->
                    stageDao.upsert(dto.toSyncedEntity(newLocalId(), projectLocalId, syncedAt))

                local.pendingOp == PendingOp.NONE ->
                    stageDao.upsert(dto.toSyncedEntity(local.localId, projectLocalId, syncedAt, local))

                // else: a pending local edit — no server `updatedAt` to arbitrate,
                // so it survives and wins on its next push (last-writer-wins).
                else -> Unit
            }
        }

        val remoteIds = remote.map { it.id }.toSet()
        locals
            .filter { it.serverId != null && it.serverId !in remoteIds }
            .filter { it.syncStatus == SyncStatus.SYNCED && it.pendingOp == PendingOp.NONE }
            .forEach { stageDao.deleteByLocalId(it.localId) }
    }

    private suspend fun pullMembers(serverId: Long, localId: String) {
        val members = apiCall { api.members(serverId) }.content
        dao.clearMembers(localId)
        if (members.isNotEmpty()) {
            dao.upsertMembers(members.map { it.toEntity(localId) })
        }
    }

    // ADMIN-only server-side. A non-admin (SUPERVISOR) gets 403 — clear the
    // local cache so a demoted user stops seeing stale invitations; any other
    // error is transient, leave the cache as-is.
    private suspend fun pullInvitations(serverId: Long, localId: String) {
        val invitations = try {
            apiCall { invitationApi.list(serverId) }.content
        } catch (e: DomainException.Forbidden) {
            invitationDao.clearForProject(localId)
            return
        } catch (e: DomainException.NotFound) {
            invitationDao.clearForProject(localId)
            return
        }
        invitationDao.clearForProject(localId)
        if (invitations.isNotEmpty()) {
            invitationDao.upsertAll(invitations.map { it.toEntity(localId) })
        }
    }

    private suspend fun pushPending() {
        // Projects first: a pending stage's parent project may still be local-only,
        // and it needs a server id before the stage (its child) can be created.
        for (entity in dao.findPending()) {
            when (entity.pendingOp) {
                PendingOp.CREATE -> pushCreate(entity)
                PendingOp.UPDATE -> pushUpdate(entity)
                PendingOp.DELETE -> pushDelete(entity)
                PendingOp.NONE -> Unit
            }
        }
        for (stage in stageDao.findPending()) {
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
        for (material in materialDao.findPending()) {
            when (material.pendingOp) {
                PendingOp.CREATE -> pushMaterialCreate(material)
                PendingOp.UPDATE -> pushMaterialUpdate(material)
                PendingOp.DELETE, PendingOp.NONE -> Unit
            }
        }
        for (entry in dailyEntryDao.findPending()) {
            when (entry.pendingOp) {
                PendingOp.CREATE -> pushEntryCreate(entry)
                PendingOp.UPDATE -> pushEntryUpdate(entry)
                PendingOp.DELETE -> pushEntryDelete(entry)
                PendingOp.NONE -> Unit
            }
        }
        for (line in purchaseLineDao.findPending()) {
            when (line.pendingOp) {
                PendingOp.CREATE -> pushPurchaseLineCreate(line)
                PendingOp.UPDATE -> pushPurchaseLineUpdate(line)
                PendingOp.DELETE -> pushPurchaseLineDelete(line)
                PendingOp.NONE -> Unit
            }
        }
        for (line in consumptionLineDao.findPending()) {
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
    private fun DomainException.isServerRejection(): Boolean =
        this is DomainException.Forbidden ||
            this is DomainException.Validation ||
            this is DomainException.InvalidCode ||
            this is DomainException.EmailAlreadyUsed ||
            this is DomainException.PlanLimitReached

    // ─── materials ──────────────────────────────────────────────────────────

    private suspend fun pushMaterialCreate(material: MaterialEntity) {
        val projectServerId = dao.findByLocalId(material.projectLocalId)?.serverId ?: return
        val created = try {
            apiCall { materialApi.create(projectServerId, material.toCreateRequest()) }
        } catch (e: DomainException) {
            if (e.isServerRejection()) {
                materialDao.upsert(material.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED))
                return
            }
            throw e
        }
        materialDao.upsert(created.toSyncedEntity(material.localId, material.projectLocalId, clock.nowEpochMillis(), material))
    }

    private suspend fun pushMaterialUpdate(material: MaterialEntity) {
        val serverId = material.serverId ?: return pushMaterialCreate(material)
        val updated = try {
            apiCall { materialApi.update(serverId, material.toUpdateRequest()) }
        } catch (e: DomainException.NotFound) {
            return
        } catch (e: DomainException) {
            if (e.isServerRejection()) {
                materialDao.upsert(material.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED))
                return
            }
            throw e
        }
        materialDao.upsert(updated.toSyncedEntity(material.localId, material.projectLocalId, clock.nowEpochMillis(), material))
    }

    // ─── daily entries (and, implicitly, their daily log) ───────────────────

    private suspend fun pushEntryCreate(entry: DailyEntryEntity) {
        val log = dailyLogDao.findByLocalId(entry.dailyLogLocalId) ?: return
        val stage = stageDao.findByLocalId(log.stageLocalId) ?: return
        val stageServerId = stage.serverId ?: return
        val body = entry.toEntryRequest()
        val dto = try {
            when (entry.type.uppercase()) {
                "PURCHASE" -> apiCall { dailyLogApi.createPurchaseEntry(stageServerId, log.date, body) }
                "WORK" -> apiCall { dailyLogApi.createWorkEntry(stageServerId, log.date, body) }
                else -> return
            }
        } catch (e: DomainException) {
            if (e.isServerRejection()) {
                dailyEntryDao.upsert(entry.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED))
                return
            }
            throw e
        }
        val now = clock.nowEpochMillis()
        dailyEntryDao.upsert(dto.toSyncedEntity(entry.localId, entry.dailyLogLocalId, now, entry))
        // The parent day's server id is learned here, never through its own
        // endpoint (it has none — ADR-27).
        if (log.serverId == null) {
            dailyLogDao.upsert(log.copy(serverId = dto.dailyLogId, lastSyncedAt = now))
        }
    }

    private suspend fun pushEntryUpdate(entry: DailyEntryEntity) {
        val serverId = entry.serverId ?: return pushEntryCreate(entry)
        val updated = try {
            apiCall { dailyLogApi.updateEntry(serverId, entry.toEntryRequest()) }
        } catch (e: DomainException.NotFound) {
            dailyEntryDao.deleteByLocalId(entry.localId)
            return
        } catch (e: DomainException) {
            if (e.isServerRejection()) {
                dailyEntryDao.upsert(entry.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED))
                return
            }
            throw e
        }
        dailyEntryDao.upsert(updated.toSyncedEntity(entry.localId, entry.dailyLogLocalId, clock.nowEpochMillis(), entry))
    }

    private suspend fun pushEntryDelete(entry: DailyEntryEntity) {
        val serverId = entry.serverId
        if (serverId != null) {
            try {
                apiCall { dailyLogApi.deleteEntry(serverId) }
            } catch (e: DomainException.NotFound) {
                // Already gone — nothing more to do.
            }
        }
        dailyEntryDao.deleteByLocalId(entry.localId)
    }

    // ─── purchase lines ─────────────────────────────────────────────────────

    private suspend fun pushPurchaseLineCreate(line: PurchaseLineEntity) {
        val entryServerId = dailyEntryDao.findByLocalId(line.entryLocalId)?.serverId ?: return
        val materialServerId = materialDao.findByLocalId(line.materialLocalId)?.serverId ?: return
        val created = try {
            apiCall { purchaseLineApi.create(entryServerId, line.toCreateRequest(materialServerId)) }
        } catch (e: DomainException) {
            if (e.isServerRejection()) {
                purchaseLineDao.upsert(line.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED))
                return
            }
            throw e
        }
        purchaseLineDao.upsert(created.toSyncedEntity(line.localId, line.entryLocalId, line.materialLocalId, clock.nowEpochMillis(), line))
    }

    private suspend fun pushPurchaseLineUpdate(line: PurchaseLineEntity) {
        val serverId = line.serverId ?: return pushPurchaseLineCreate(line)
        val updated = try {
            apiCall { purchaseLineApi.update(serverId, line.toUpdateRequest()) }
        } catch (e: DomainException.NotFound) {
            purchaseLineDao.deleteByLocalId(line.localId)
            return
        } catch (e: DomainException) {
            if (e.isServerRejection()) {
                purchaseLineDao.upsert(line.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED))
                return
            }
            throw e
        }
        purchaseLineDao.upsert(updated.toSyncedEntity(line.localId, line.entryLocalId, line.materialLocalId, clock.nowEpochMillis(), line))
    }

    private suspend fun pushPurchaseLineDelete(line: PurchaseLineEntity) {
        val serverId = line.serverId
        if (serverId != null) {
            try {
                apiCall { purchaseLineApi.delete(serverId) }
            } catch (e: DomainException.NotFound) {
            }
        }
        purchaseLineDao.deleteByLocalId(line.localId)
    }

    // ─── consumption lines ──────────────────────────────────────────────────

    private suspend fun pushConsumptionLineCreate(line: ConsumptionLineEntity) {
        val entryServerId = dailyEntryDao.findByLocalId(line.entryLocalId)?.serverId ?: return
        val materialServerId = materialDao.findByLocalId(line.materialLocalId)?.serverId ?: return
        val created = try {
            apiCall { consumptionLineApi.create(entryServerId, line.toCreateRequest(materialServerId)) }
        } catch (e: DomainException) {
            if (e.isServerRejection()) {
                consumptionLineDao.upsert(line.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED))
                return
            }
            throw e
        }
        consumptionLineDao.upsert(created.toSyncedEntity(line.localId, line.entryLocalId, line.materialLocalId, clock.nowEpochMillis(), line))
    }

    private suspend fun pushConsumptionLineUpdate(line: ConsumptionLineEntity) {
        val serverId = line.serverId ?: return pushConsumptionLineCreate(line)
        val updated = try {
            apiCall { consumptionLineApi.update(serverId, line.toUpdateRequest()) }
        } catch (e: DomainException.NotFound) {
            consumptionLineDao.deleteByLocalId(line.localId)
            return
        } catch (e: DomainException) {
            if (e.isServerRejection()) {
                consumptionLineDao.upsert(line.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED))
                return
            }
            throw e
        }
        consumptionLineDao.upsert(updated.toSyncedEntity(line.localId, line.entryLocalId, line.materialLocalId, clock.nowEpochMillis(), line))
    }

    private suspend fun pushConsumptionLineDelete(line: ConsumptionLineEntity) {
        val serverId = line.serverId
        if (serverId != null) {
            try {
                apiCall { consumptionLineApi.delete(serverId) }
            } catch (e: DomainException.NotFound) {
            }
        }
        consumptionLineDao.deleteByLocalId(line.localId)
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
            attachmentDao.upsert(attachment.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED))
            return
        }
        val dto = try {
            apiCall { attachmentApi.upload(entryServerId, bytes, attachment.originalName, attachment.mimeType) }
        } catch (e: DomainException) {
            if (e.isServerRejection() || e is DomainException.Unexpected) {
                attachmentDao.upsert(attachment.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED))
                return
            }
            throw e
        }
        attachmentDao.upsert(dto.toSyncedEntity(attachment.localId, attachment.entryLocalId, attachment.localPath, clock.nowEpochMillis(), attachment))
    }

    private suspend fun pushAttachmentDelete(attachment: AttachmentEntity) {
        val serverId = attachment.serverId
        if (serverId != null) {
            try {
                apiCall { attachmentApi.delete(serverId) }
            } catch (e: DomainException.NotFound) {
            }
        }
        // The local file was already freed by AttachmentRepositoryImpl at delete time (ADR-29).
        attachmentDao.deleteByLocalId(attachment.localId)
    }

    // ─── pull: materials ────────────────────────────────────────────────────

    private suspend fun pullMaterials(projectServerId: Long, projectLocalId: String) {
        val remote = apiCall { materialApi.list(projectServerId) }.content
        val locals = materialDao.findForProject(projectLocalId)
        val byServerId = locals.mapNotNull { local -> local.serverId?.let { it to local } }.toMap()
        val syncedAt = clock.nowEpochMillis()

        for (dto in remote) {
            val local = byServerId[dto.id]
            when {
                local == null -> materialDao.upsert(dto.toSyncedEntity(newLocalId(), projectLocalId, syncedAt))
                local.pendingOp == PendingOp.NONE -> materialDao.upsert(dto.toSyncedEntity(local.localId, projectLocalId, syncedAt, local))
                // else: a pending local rename — it wins on its next push (LWW).
                else -> Unit
            }
        }
        // No removal pass — the backend never deletes a material (PATCH-only).
    }

    // ─── pull: daily log summaries (one stage) ──────────────────────────────

    private suspend fun pullLogSummaries(stageServerId: Long, stageLocalId: String) {
        val remote = apiCall { dailyLogApi.listLogs(stageServerId) }.content
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
                existing == null -> dailyEntryDao.upsert(dto.toSyncedEntity(newLocalId(), logLocalId, syncedAt))
                existing.pendingOp == PendingOp.NONE -> dailyEntryDao.upsert(dto.toSyncedEntity(existing.localId, logLocalId, syncedAt, existing))
                else -> Unit
            }
        }

        val remoteIds = remote.map { it.id }.toSet()
        locals
            .filter { it.serverId != null && it.serverId !in remoteIds }
            .filter { it.syncStatus == SyncStatus.SYNCED && it.pendingOp == PendingOp.NONE }
            .forEach { dailyEntryDao.deleteByLocalId(it.localId) }
    }

    // ─── pull: lines (one entry) ────────────────────────────────────────────

    private suspend fun pullPurchaseLines(entryServerId: Long, entryLocalId: String) {
        val remote = apiCall { purchaseLineApi.list(entryServerId) }.content
        val locals = purchaseLineDao.findForEntry(entryLocalId)
        val byServerId = locals.mapNotNull { l -> l.serverId?.let { it to l } }.toMap()
        val syncedAt = clock.nowEpochMillis()

        for (dto in remote) {
            val materialLocalId = materialDao.findByServerId(dto.materialId)?.localId ?: continue
            val local = byServerId[dto.id]
            when {
                local == null -> purchaseLineDao.upsert(dto.toSyncedEntity(newLocalId(), entryLocalId, materialLocalId, syncedAt))
                local.pendingOp == PendingOp.NONE -> purchaseLineDao.upsert(dto.toSyncedEntity(local.localId, entryLocalId, materialLocalId, syncedAt, local))
                else -> Unit
            }
        }

        val remoteIds = remote.map { it.id }.toSet()
        locals
            .filter { it.serverId != null && it.serverId !in remoteIds }
            .filter { it.syncStatus == SyncStatus.SYNCED && it.pendingOp == PendingOp.NONE }
            .forEach { purchaseLineDao.deleteByLocalId(it.localId) }
    }

    private suspend fun pullConsumptionLines(entryServerId: Long, entryLocalId: String) {
        val remote = apiCall { consumptionLineApi.list(entryServerId) }.content
        val locals = consumptionLineDao.findForEntry(entryLocalId)
        val byServerId = locals.mapNotNull { l -> l.serverId?.let { it to l } }.toMap()
        val syncedAt = clock.nowEpochMillis()

        for (dto in remote) {
            val materialLocalId = materialDao.findByServerId(dto.materialId)?.localId ?: continue
            val local = byServerId[dto.id]
            when {
                local == null -> consumptionLineDao.upsert(dto.toSyncedEntity(newLocalId(), entryLocalId, materialLocalId, syncedAt))
                local.pendingOp == PendingOp.NONE -> consumptionLineDao.upsert(dto.toSyncedEntity(local.localId, entryLocalId, materialLocalId, syncedAt, local))
                else -> Unit
            }
        }

        val remoteIds = remote.map { it.id }.toSet()
        locals
            .filter { it.serverId != null && it.serverId !in remoteIds }
            .filter { it.syncStatus == SyncStatus.SYNCED && it.pendingOp == PendingOp.NONE }
            .forEach { consumptionLineDao.deleteByLocalId(it.localId) }
    }

    // ─── pull: attachments (one entry) ──────────────────────────────────────

    private fun defaultAttachmentName(mimeType: String?): String =
        if (mimeType?.startsWith("video/") == true) "video.mp4" else "photo.jpg"

    private suspend fun pullAttachments(entryServerId: Long, entryLocalId: String) {
        val remote = apiCall { attachmentApi.list(entryServerId) }.content
        val locals = attachmentDao.findForEntry(entryLocalId)
        val knownServerIds = locals.mapNotNull { it.serverId }.toSet()
        val syncedAt = clock.nowEpochMillis()

        for (dto in remote) {
            if (dto.id in knownServerIds) continue
            // A photo or video that first appeared on the server (web, another
            // device) — download it once and keep a local copy, same as one
            // added here. Videos are already transcoded server-side (ADR-35).
            val bytes = apiCall { attachmentApi.download(dto.id) }
            val path = attachmentFileStore.save(bytes, dto.originalName ?: defaultAttachmentName(dto.mimeType))
            attachmentDao.upsert(dto.toSyncedEntity(newLocalId(), entryLocalId, path, syncedAt))
        }

        val remoteIds = remote.map { it.id }.toSet()
        locals
            .filter { it.serverId != null && it.serverId !in remoteIds }
            .filter { it.syncStatus == SyncStatus.SYNCED && it.pendingOp == PendingOp.NONE }
            .forEach {
                attachmentFileStore.delete(it.localPath)
                attachmentDao.deleteByLocalId(it.localId)
            }
    }

    private suspend fun pushStageCreate(stage: StageEntity) {
        // Parent project not on the server yet → leave the stage PENDING; the next
        // pass (after the project pushes) picks it up. Not an error.
        val projectServerId = dao.findByLocalId(stage.projectLocalId)?.serverId ?: return
        val created = try {
            apiCall { stageApi.create(projectServerId, stage.toCreateRequest()) }
        } catch (e: DomainException.Forbidden) {
            stageDao.upsert(stage.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED))
            return
        } catch (e: DomainException.Validation) {
            stageDao.upsert(stage.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED))
            return
        }
        stageDao.upsert(
            created.toSyncedEntity(
                localId = stage.localId,
                projectLocalId = stage.projectLocalId,
                syncedAt = clock.nowEpochMillis(),
                previous = stage,
            ),
        )
    }

    private suspend fun pushStageUpdate(stage: StageEntity) {
        val serverId = stage.serverId ?: return pushStageCreate(stage)

        try {
            apiCall { stageApi.get(serverId) }
        } catch (e: DomainException.NotFound) {
            stageDao.deleteByLocalId(stage.localId)
            return
        }

        val updated = try {
            apiCall { stageApi.update(serverId, stage.toUpdateRequest()) }
        } catch (e: DomainException.Forbidden) {
            stageDao.upsert(stage.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED))
            return
        } catch (e: DomainException.Validation) {
            stageDao.upsert(stage.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED))
            return
        }
        stageDao.upsert(
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
        if (serverId != null) {
            try {
                apiCall { stageApi.delete(serverId) }
            } catch (e: DomainException.NotFound) {
                // Already gone on the server — nothing more to do.
            }
        }
        stageDao.deleteByLocalId(stage.localId)
    }

    private suspend fun pushCreate(entity: ProjectEntity) {
        val created = try {
            apiCall { api.create(entity.toCreateRequest()) }
        } catch (e: DomainException.PlanLimitReached) {
            dao.upsert(entity.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.PLAN_LIMIT))
            return
        } catch (e: DomainException.Validation) {
            dao.upsert(entity.copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = SyncError.REJECTED))
            return
        }
        dao.upsert(created.toSyncedEntity(localId = entity.localId, syncedAt = clock.nowEpochMillis(), previous = entity))
    }

    private suspend fun pushUpdate(entity: ProjectEntity) {
        val serverId = entity.serverId ?: return pushCreate(entity)

        val remote = try {
            apiCall { api.get(serverId) }
        } catch (e: DomainException.NotFound) {
            dao.deleteByLocalId(entity.localId)
            return
        }

        if (serverChangedSinceLastSync(remote.updatedAt, entity)) {
            // A concurrent edit landed on the server since we last synced this row,
            // so our local edit is based on a stale copy → the server version wins.
            dao.upsert(remote.toSyncedEntity(localId = entity.localId, syncedAt = clock.nowEpochMillis(), previous = entity))
            return
        }

        val updated = apiCall { api.update(serverId, entity.toUpdateRequest()) }
        dao.upsert(updated.toSyncedEntity(localId = entity.localId, syncedAt = clock.nowEpochMillis(), previous = entity))
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
        if (serverId != null) {
            try {
                apiCall { api.delete(serverId) }
            } catch (e: DomainException.NotFound) {
                // Already gone on the server — nothing more to do.
            }
        }
        dao.deleteByLocalId(entity.localId)
    }

    private suspend fun pullAll() {
        val remote = apiCall { api.list() }.content
        val locals = dao.findAll()
        val byServerId = locals.mapNotNull { local -> local.serverId?.let { it to local } }.toMap()
        val syncedAt = clock.nowEpochMillis()

        for (dto in remote) {
            val local = byServerId[dto.id]
            when {
                local == null ->
                    dao.upsert(dto.toSyncedEntity(localId = newLocalId(), syncedAt = syncedAt))

                local.pendingOp == PendingOp.NONE ->
                    dao.upsert(dto.toSyncedEntity(localId = local.localId, syncedAt = syncedAt, previous = local))

                serverChangedSinceLastSync(dto.updatedAt, local) ->
                    // The server row moved on since our last sync → it wins over the pending local edit.
                    dao.upsert(dto.toSyncedEntity(localId = local.localId, syncedAt = syncedAt, previous = local))

                // else: server unchanged since our last sync → keep the local edit pending, it pushes cleanly.
                else -> Unit
            }
        }

        val remoteIds = remote.map { it.id }.toSet()
        locals
            .filter { it.serverId != null && it.serverId !in remoteIds }
            .filter { it.syncStatus == SyncStatus.SYNCED && it.pendingOp == PendingOp.NONE }
            .forEach { dao.deleteByLocalId(it.localId) }
    }
}
