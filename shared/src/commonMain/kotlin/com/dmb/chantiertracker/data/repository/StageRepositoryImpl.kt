@file:OptIn(ExperimentalUuidApi::class)

package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.StageDao
import com.dmb.chantiertracker.data.local.db.StageEntity
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.Clock
import com.dmb.chantiertracker.data.sync.SystemClock
import com.dmb.chantiertracker.data.sync.Syncer
import com.dmb.chantiertracker.domain.model.CreateStageInput
import com.dmb.chantiertracker.domain.model.Stage
import com.dmb.chantiertracker.domain.model.StageDetail
import com.dmb.chantiertracker.domain.model.StageStatus
import com.dmb.chantiertracker.domain.model.UpdateStageInput
import com.dmb.chantiertracker.domain.repository.StageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class StageRepositoryImpl(
    private val dao: StageDao,
    private val syncer: Syncer,
    private val scope: AppCoroutineScope,
    private val clock: Clock = SystemClock,
    private val newLocalId: () -> String = { Uuid.random().toString() },
) : StageRepository {

    override fun observeStages(projectLocalId: String): Flow<List<Stage>> =
        dao.observeStagesForProject(projectLocalId).map { rows -> rows.map(StageEntity::toStage) }

    override fun observeStage(stageLocalId: String): Flow<StageDetail?> =
        dao.observeStage(stageLocalId).map { it?.toStageDetail() }

    override suspend fun createStage(input: CreateStageInput): String {
        val localId = newLocalId()
        val now = clock.nowEpochMillis()
        dao.upsert(
            StageEntity(
                localId = localId,
                serverId = null,
                projectLocalId = input.projectLocalId,
                name = input.name,
                description = input.description?.ifBlank { null },
                estimatedBudget = input.estimatedBudget,
                startDate = input.startDate?.ifBlank { null },
                endDate = input.endDate?.ifBlank { null },
                status = StageStatus.IN_PROGRESS.name,
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

    override suspend fun updateStage(stageLocalId: String, input: UpdateStageInput) {
        val existing = dao.findByLocalId(stageLocalId) ?: return
        dao.upsert(
            existing.copy(
                name = input.name,
                description = input.description?.ifBlank { null },
                estimatedBudget = input.estimatedBudget,
                startDate = input.startDate?.ifBlank { null },
                endDate = input.endDate?.ifBlank { null },
                status = input.status.name,
                syncStatus = SyncStatus.PENDING,
                pendingOp = if (existing.pendingOp == PendingOp.CREATE) PendingOp.CREATE else PendingOp.UPDATE,
                locallyModifiedAt = clock.nowEpochMillis(),
                lastSyncError = null,
            ),
        )
        syncer.requestSync()
    }

    override suspend fun deleteStage(stageLocalId: String) {
        val existing = dao.findByLocalId(stageLocalId) ?: return
        if (existing.serverId == null) {
            dao.deleteByLocalId(stageLocalId)
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

    override suspend fun refreshStages(projectLocalId: String) {
        syncer.syncProject(projectLocalId)
    }

    override suspend fun refreshStage(stageLocalId: String) {
        syncer.syncStage(stageLocalId)
    }
}

internal fun String.toStageStatus(): StageStatus = when (uppercase()) {
    "IN_PROGRESS" -> StageStatus.IN_PROGRESS
    "COMPLETED" -> StageStatus.COMPLETED
    else -> StageStatus.UNKNOWN
}

internal fun StageEntity.toStage(): Stage = Stage(
    localId = localId,
    projectLocalId = projectLocalId,
    name = name,
    estimatedBudget = estimatedBudget,
    status = status.toStageStatus(),
)

internal fun StageEntity.toStageDetail(): StageDetail = StageDetail(
    localId = localId,
    projectLocalId = projectLocalId,
    name = name,
    description = description,
    estimatedBudget = estimatedBudget,
    startDate = startDate,
    endDate = endDate,
    status = status.toStageStatus(),
)
