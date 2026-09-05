package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.DailyLogDao
import com.dmb.chantiertracker.data.local.db.DailyLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [DailyLogDao] — lets sync/repository tests run on every platform without Room. */
class FakeDailyLogDao(initial: List<DailyLogEntity> = emptyList()) : DailyLogDao {

    private val logs = MutableStateFlow(initial.associateBy { it.localId })

    val stored: List<DailyLogEntity> get() = logs.value.values.toList()

    override fun observeLogsForStage(stageLocalId: String): Flow<List<DailyLogEntity>> =
        logs.map { rows ->
            rows.values.filter { it.stageLocalId == stageLocalId }.sortedByDescending { it.date }
        }

    override fun observeLog(localId: String): Flow<DailyLogEntity?> =
        logs.map { it[localId] }

    override suspend fun findByLocalId(localId: String): DailyLogEntity? = logs.value[localId]

    override suspend fun findByStageAndDate(stageLocalId: String, date: String): DailyLogEntity? =
        logs.value.values.firstOrNull { it.stageLocalId == stageLocalId && it.date == date }

    override suspend fun findByServerId(serverId: Long): DailyLogEntity? =
        logs.value.values.firstOrNull { it.serverId == serverId }

    override suspend fun findForStage(stageLocalId: String): List<DailyLogEntity> =
        logs.value.values.filter { it.stageLocalId == stageLocalId }

    override suspend fun upsert(log: DailyLogEntity) {
        logs.value = logs.value + (log.localId to log)
    }

    override suspend fun deleteByLocalId(localId: String) {
        logs.value = logs.value - localId
    }
}
