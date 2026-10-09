package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.ConsumptionLineDao
import com.dmb.chantiertracker.data.local.db.ConsumptionLineEntity
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [ConsumptionLineDao] — see [FakePurchaseLineDao] for why [projectForEntry] stands in for the real join. */
class FakeConsumptionLineDao(
    initial: List<ConsumptionLineEntity> = emptyList(),
    private val projectForEntry: () -> Map<String, String> = { emptyMap() },
) : ConsumptionLineDao {

    private val lines = MutableStateFlow(initial.associateBy { it.localId })

    val stored: List<ConsumptionLineEntity> get() = lines.value.values.toList()

    override fun observeLinesForEntry(entryLocalId: String): Flow<List<ConsumptionLineEntity>> =
        lines.map { rows -> rows.values.filter { it.entryLocalId == entryLocalId && it.pendingOp != PendingOp.DELETE } }

    override fun observeStockMovements(projectLocalId: String): Flow<List<com.dmb.chantiertracker.data.local.db.StockMovementRow>> =
        lines.map { rows ->
            rows.values
                .filter { projectForEntry()[it.entryLocalId] == projectLocalId && it.syncStatus == SyncStatus.PENDING }
                .map { com.dmb.chantiertracker.data.local.db.StockMovementRow(it.materialLocalId, it.quantity, it.serverQuantity, it.syncStatus, it.pendingOp, parentDeleting = false) }
        }

    override suspend fun findByLocalId(localId: String): ConsumptionLineEntity? = lines.value[localId]

    override suspend fun findByServerId(serverId: Long): ConsumptionLineEntity? =
        lines.value.values.firstOrNull { it.serverId == serverId }

    override suspend fun findPending(): List<ConsumptionLineEntity> =
        lines.value.values.filter { it.syncStatus != SyncStatus.SYNCED && it.lastSyncError !in com.dmb.chantiertracker.data.sync.SyncError.WAITING_FOR_THE_USER }

    val blockedByParent = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())

    override fun observeBlockedByParent(): kotlinx.coroutines.flow.Flow<List<String>> = blockedByParent

    override suspend fun findForEntry(entryLocalId: String): List<ConsumptionLineEntity> =
        lines.value.values.filter { it.entryLocalId == entryLocalId }

    override suspend fun upsert(line: ConsumptionLineEntity) {
        lines.value = lines.value + (line.localId to line)
    }

    override suspend fun deleteByLocalId(localId: String) {
        lines.value = lines.value - localId
    }
}
