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

/** What repositories need from the sync layer: nudge it after a local write, or await a pass. */
interface Syncer {
    fun requestSync()
    suspend fun syncNow(): SyncOutcome
    suspend fun syncProject(localId: String): SyncOutcome
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

    override suspend fun syncProject(localId: String): SyncOutcome = mutex.withLock { runProjectSync(localId) }

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
    }

    private suspend fun pullMembers(serverId: Long, localId: String) {
        val members = apiCall { api.members(serverId) }.content
        dao.clearMembers(localId)
        if (members.isNotEmpty()) {
            dao.upsertMembers(members.map { it.toEntity(localId) })
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
