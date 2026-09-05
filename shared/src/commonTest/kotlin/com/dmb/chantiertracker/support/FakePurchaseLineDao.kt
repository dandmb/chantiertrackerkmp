package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.PurchaseLineDao
import com.dmb.chantiertracker.data.local.db.PurchaseLineEntity
import com.dmb.chantiertracker.data.local.db.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [PurchaseLineDao]. [projectForEntry] stands in for the real DAO's
 * join through daily_entries/daily_logs/stages — tests supply the
 * entryLocalId → projectLocalId mapping directly instead of building the
 * whole chain (that join itself is covered by the Room contract test).
 */
class FakePurchaseLineDao(
    initial: List<PurchaseLineEntity> = emptyList(),
    private val projectForEntry: () -> Map<String, String> = { emptyMap() },
) : PurchaseLineDao {

    private val lines = MutableStateFlow(initial.associateBy { it.localId })

    val stored: List<PurchaseLineEntity> get() = lines.value.values.toList()

    override fun observeLinesForEntry(entryLocalId: String): Flow<List<PurchaseLineEntity>> =
        lines.map { rows -> rows.values.filter { it.entryLocalId == entryLocalId && it.pendingOp != PendingOp.DELETE } }

    override fun observeLinesForProject(projectLocalId: String): Flow<List<PurchaseLineEntity>> =
        lines.map { rows ->
            rows.values.filter { projectForEntry()[it.entryLocalId] == projectLocalId && it.pendingOp != PendingOp.DELETE }
        }

    override suspend fun findByLocalId(localId: String): PurchaseLineEntity? = lines.value[localId]

    override suspend fun findByServerId(serverId: Long): PurchaseLineEntity? =
        lines.value.values.firstOrNull { it.serverId == serverId }

    override suspend fun findPending(): List<PurchaseLineEntity> =
        lines.value.values.filter { it.syncStatus != SyncStatus.SYNCED }

    override suspend fun upsert(line: PurchaseLineEntity) {
        lines.value = lines.value + (line.localId to line)
    }

    override suspend fun deleteByLocalId(localId: String) {
        lines.value = lines.value - localId
    }
}
