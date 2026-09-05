package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.DailyEntryDao
import com.dmb.chantiertracker.data.local.db.DailyEntryEntity
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [DailyEntryDao] — lets sync/repository tests run on every platform without Room. */
class FakeDailyEntryDao(
    initial: List<DailyEntryEntity> = emptyList(),
    private val logsByLocalId: () -> Map<String, String> = { emptyMap() },
) : DailyEntryDao {

    private val entries = MutableStateFlow(initial.associateBy { it.localId })

    val stored: List<DailyEntryEntity> get() = entries.value.values.toList()

    override fun observeEntriesForLog(dailyLogLocalId: String): Flow<List<DailyEntryEntity>> =
        entries.map { rows ->
            rows.values.filter { it.dailyLogLocalId == dailyLogLocalId && it.pendingOp != PendingOp.DELETE }
        }

    override fun observeEntriesForStage(stageLocalId: String): Flow<List<DailyEntryEntity>> =
        entries.map { rows ->
            rows.values.filter {
                logsByLocalId()[it.dailyLogLocalId] == stageLocalId && it.pendingOp != PendingOp.DELETE
            }
        }

    override suspend fun findByLocalId(localId: String): DailyEntryEntity? = entries.value[localId]

    override suspend fun findByServerId(serverId: Long): DailyEntryEntity? =
        entries.value.values.firstOrNull { it.serverId == serverId }

    override suspend fun findByLogAndType(dailyLogLocalId: String, type: String): DailyEntryEntity? =
        entries.value.values.firstOrNull { it.dailyLogLocalId == dailyLogLocalId && it.type == type }

    override suspend fun findPending(): List<DailyEntryEntity> =
        entries.value.values.filter { it.syncStatus != SyncStatus.SYNCED }

    override suspend fun findForLog(dailyLogLocalId: String): List<DailyEntryEntity> =
        entries.value.values.filter { it.dailyLogLocalId == dailyLogLocalId }

    override suspend fun upsert(entry: DailyEntryEntity) {
        entries.value = entries.value + (entry.localId to entry)
    }

    override suspend fun deleteByLocalId(localId: String) {
        entries.value = entries.value - localId
    }
}
