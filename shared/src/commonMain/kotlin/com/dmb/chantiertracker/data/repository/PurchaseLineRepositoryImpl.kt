@file:OptIn(ExperimentalUuidApi::class)

package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.PurchaseLineDao
import com.dmb.chantiertracker.data.local.db.PurchaseLineEntity
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.Clock
import com.dmb.chantiertracker.data.sync.SystemClock
import com.dmb.chantiertracker.data.sync.Syncer
import com.dmb.chantiertracker.domain.model.CreatePurchaseLineInput
import com.dmb.chantiertracker.domain.model.PurchaseLine
import com.dmb.chantiertracker.domain.model.UpdatePurchaseLineInput
import com.dmb.chantiertracker.domain.repository.PurchaseLineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class PurchaseLineRepositoryImpl(
    private val dao: PurchaseLineDao,
    private val syncer: Syncer,
    private val scope: AppCoroutineScope,
    private val clock: Clock = SystemClock,
    private val newLocalId: () -> String = { Uuid.random().toString() },
) : PurchaseLineRepository {

    override fun observeLines(entryLocalId: String): Flow<List<PurchaseLine>> =
        dao.observeLinesForEntry(entryLocalId).map { rows -> rows.map(PurchaseLineEntity::toPurchaseLine) }

    override suspend fun createLine(entryLocalId: String, input: CreatePurchaseLineInput): String {
        val localId = newLocalId()
        val now = clock.nowEpochMillis()
        dao.upsert(
            PurchaseLineEntity(
                localId = localId,
                serverId = null,
                entryLocalId = entryLocalId,
                materialLocalId = input.materialLocalId,
                quantity = input.quantity,
                unitPrice = input.unitPrice,
                totalPrice = input.quantity * input.unitPrice,
                supplier = input.supplier?.ifBlank { null },
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

    override suspend fun updateLine(lineLocalId: String, input: UpdatePurchaseLineInput) {
        val existing = dao.findByLocalId(lineLocalId) ?: return
        dao.upsert(
            existing.copy(
                quantity = input.quantity,
                unitPrice = input.unitPrice,
                totalPrice = input.quantity * input.unitPrice,
                supplier = input.supplier?.ifBlank { null },
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

internal fun PurchaseLineEntity.toPurchaseLine(): PurchaseLine = PurchaseLine(
    localId = localId,
    entryLocalId = entryLocalId,
    materialLocalId = materialLocalId,
    quantity = quantity,
    unitPrice = unitPrice,
    totalPrice = totalPrice,
    supplier = supplier,
)
