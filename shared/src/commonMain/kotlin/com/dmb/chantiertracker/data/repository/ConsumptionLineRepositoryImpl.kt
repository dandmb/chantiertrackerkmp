@file:OptIn(ExperimentalUuidApi::class)

package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.ConsumptionLineDao
import com.dmb.chantiertracker.data.local.db.ConsumptionLineEntity
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.Clock
import com.dmb.chantiertracker.data.sync.SystemClock
import com.dmb.chantiertracker.data.sync.Syncer
import com.dmb.chantiertracker.domain.model.ConsumptionLine
import com.dmb.chantiertracker.domain.model.CreateConsumptionLineInput
import com.dmb.chantiertracker.domain.model.UpdateConsumptionLineInput
import com.dmb.chantiertracker.domain.repository.ConsumptionLineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ConsumptionLineRepositoryImpl(
    private val dao: ConsumptionLineDao,
    private val syncer: Syncer,
    private val scope: AppCoroutineScope,
    private val clock: Clock = SystemClock,
    private val newLocalId: () -> String = { Uuid.random().toString() },
) : ConsumptionLineRepository {

    override fun observeLines(entryLocalId: String): Flow<List<ConsumptionLine>> =
        dao.observeLinesForEntry(entryLocalId).map { rows -> rows.map(ConsumptionLineEntity::toConsumptionLine) }

    override suspend fun createLine(entryLocalId: String, input: CreateConsumptionLineInput): String {
        val localId = newLocalId()
        val now = clock.nowEpochMillis()
        dao.upsert(
            ConsumptionLineEntity(
                localId = localId,
                serverId = null,
                entryLocalId = entryLocalId,
                materialLocalId = input.materialLocalId,
                quantity = input.quantity,
                createdAt = null,
                syncStatus = SyncStatus.PENDING,
                pendingOp = PendingOp.CREATE,
                locallyModifiedAt = now,
                lastSyncedAt = null,
                remoteUpdatedAt = null,
                lastSyncError = null,
            ),
        )
        syncer.requestSync()
        return localId
    }

    override suspend fun updateLine(lineLocalId: String, input: UpdateConsumptionLineInput) {
        val existing = dao.findByLocalId(lineLocalId) ?: return
        dao.upsert(
            existing.copy(
                quantity = input.quantity,
                syncStatus = SyncStatus.PENDING,
                pendingOp = if (existing.pendingOp == PendingOp.CREATE) PendingOp.CREATE else PendingOp.UPDATE,
                locallyModifiedAt = clock.nowEpochMillis(),
                lastSyncError = null,
            ),
        )
        syncer.requestSync()
    }

    override suspend fun deleteLine(lineLocalId: String) {
        val existing = dao.findByLocalId(lineLocalId) ?: return
        if (existing.serverId == null) {
            dao.deleteByLocalId(lineLocalId)
            return
        }
        dao.upsert(
            existing.copy(
                syncStatus = SyncStatus.PENDING,
                pendingOp = PendingOp.DELETE,
                locallyModifiedAt = clock.nowEpochMillis(),
                lastSyncError = null,
            ),
        )
        syncer.requestSync()
    }
}

internal fun ConsumptionLineEntity.toConsumptionLine(): ConsumptionLine = ConsumptionLine(
    localId = localId,
    entryLocalId = entryLocalId,
    materialLocalId = materialLocalId,
    quantity = quantity,
)
