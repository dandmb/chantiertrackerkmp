package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.StageDao
import com.dmb.chantiertracker.data.local.db.StageEntity
import com.dmb.chantiertracker.data.local.db.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [StageDao] — lets sync/repository tests run on every platform without Room. */
class FakeStageDao(initial: List<StageEntity> = emptyList()) : StageDao {

    private val stages = MutableStateFlow(initial.associateBy { it.localId })

    val stored: List<StageEntity> get() = stages.value.values.toList()

    override fun observeStagesForProject(projectLocalId: String): Flow<List<StageEntity>> =
        stages.map { rows ->
            rows.values
                .filter { it.projectLocalId == projectLocalId && it.pendingOp != PendingOp.DELETE }
                .sortedWith(compareBy({ it.startDate == null }, { it.startDate }, { it.name }))
        }

    override fun observeStage(localId: String): Flow<StageEntity?> =
        stages.map { it[localId] }

    override suspend fun findByLocalId(localId: String): StageEntity? = stages.value[localId]

    override suspend fun findByServerId(serverId: Long): StageEntity? =
        stages.value.values.firstOrNull { it.serverId == serverId }

    override suspend fun findPending(): List<StageEntity> =
        stages.value.values.filter { it.syncStatus != SyncStatus.SYNCED }

    override suspend fun findForProject(projectLocalId: String): List<StageEntity> =
        stages.value.values.filter { it.projectLocalId == projectLocalId }

    override suspend fun upsert(stage: StageEntity) {
        stages.value = stages.value + (stage.localId to stage)
    }

    override suspend fun upsertAll(stages: List<StageEntity>) {
        this.stages.value = this.stages.value + stages.associateBy { it.localId }
    }

    override suspend fun deleteByLocalId(localId: String) {
        stages.value = stages.value - localId
    }
}
