@file:OptIn(ExperimentalUuidApi::class)

package com.dmb.chantiertracker.data.sync

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.ProjectDao
import com.dmb.chantiertracker.data.local.db.ProjectEntity
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.remote.ProjectApi
import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.presentation.sync.SyncState
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed interface SyncOutcome {
    data object Synced : SyncOutcome
    data object Skipped : SyncOutcome
    data class Failed(val cause: DomainException) : SyncOutcome
}

/** What repositories need from the sync layer: nudge it after a local write, or await a full pass. */
interface Syncer {
    fun requestSync()
    suspend fun syncNow(): SyncOutcome
}

/**
 * The single place that talks to [ProjectApi]. Repositories read and write
 * Room only; this drains locally-pending rows to the server (push) then
 * reconciles Room with the server's state (pull). Runs on an
 * application-lifetime scope, never a screen's.
 *
 * Conflict resolution is last-write-wins by timestamp: whichever of the local
 * last-edit time and the server `updatedAt` is more recent wins, with no user
 * prompt (ADR-20).
 */
class SyncEngine(
    private val dao: ProjectDao,
    private val api: ProjectApi,
    private val connectivity: ConnectivityObserver,
    private val syncState: SyncStateHolder,
    private val scope: CoroutineScope,
    private val clock: Clock = SystemClock,
    private val newLocalId: () -> String = { Uuid.random().toString() },
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
    }

    override fun requestSync() {
        scope.launch { syncNow() }
    }

    override suspend fun syncNow(): SyncOutcome = mutex.withLock { runSync() }

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

    private suspend fun pushPending() {
        for (entity in dao.findPending()) {
            when (entity.pendingOp) {
                PendingOp.CREATE -> pushCreate(entity)
                PendingOp.UPDATE -> pushUpdate(entity)
                PendingOp.DELETE -> pushDelete(entity)
                PendingOp.NONE -> Unit
            }
        }
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

        val remoteMillis = parseServerTimestampMillis(remote.updatedAt)
        if (remoteMillis != null && remoteMillis > entity.locallyModifiedAt) {
            // Server copy is newer → it wins, the local edit is dropped.
            dao.upsert(remote.toSyncedEntity(localId = entity.localId, syncedAt = clock.nowEpochMillis(), previous = entity))
            return
        }

        val updated = apiCall { api.update(serverId, entity.toUpdateRequest()) }
        dao.upsert(updated.toSyncedEntity(localId = entity.localId, syncedAt = clock.nowEpochMillis(), previous = entity))
    }

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

                else -> {
                    val remoteMillis = parseServerTimestampMillis(dto.updatedAt)
                    if (remoteMillis != null && remoteMillis > local.locallyModifiedAt) {
                        dao.upsert(dto.toSyncedEntity(localId = local.localId, syncedAt = syncedAt, previous = local))
                    }
                    // else: local edit is newer → keep it pending, it wins on the next push.
                }
            }
        }

        val remoteIds = remote.map { it.id }.toSet()
        locals
            .filter { it.serverId != null && it.serverId !in remoteIds }
            .filter { it.syncStatus == SyncStatus.SYNCED && it.pendingOp == PendingOp.NONE }
            .forEach { dao.deleteByLocalId(it.localId) }
    }
}
