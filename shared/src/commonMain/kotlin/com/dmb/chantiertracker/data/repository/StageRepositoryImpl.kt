@file:OptIn(ExperimentalUuidApi::class)

package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.StageDao
import com.dmb.chantiertracker.data.local.db.StageEntity
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.Clock
import com.dmb.chantiertracker.data.sync.Syncer
import com.dmb.chantiertracker.data.sync.SystemClock
import com.dmb.chantiertracker.data.sync.syncIssue
import com.dmb.chantiertracker.domain.model.CreateStageInput
import com.dmb.chantiertracker.domain.model.Stage
import com.dmb.chantiertracker.domain.model.StageDetail
import com.dmb.chantiertracker.domain.model.StageStatus
import com.dmb.chantiertracker.domain.model.UpdateStageInput
import com.dmb.chantiertracker.domain.repository.StageRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class StageRepositoryImpl(
    private val dao: StageDao,
    private val syncer: Syncer,
    private val scope: AppCoroutineScope,
    private val clock: Clock = SystemClock,
    private val newLocalId: () -> String = { Uuid.random().toString() },
) : StageRepository {

    override fun observeStages(projectLocalId: String): Flow<List<Stage>> =
        combine(dao.observeStagesForProject(projectLocalId), dao.observeBlockedByParent()) { rows, blocked ->
            rows.map { it.toStage(blockedByParent = it.localId in blocked) }
        }

    override fun observeStage(stageLocalId: String): Flow<StageDetail?> =
        dao.observeStage(stageLocalId).map { it?.toStageDetail() }

    override suspend fun createStage(input: CreateStageInput): String {
        val localId = newLocalId()
        val now = clock.nowEpochMillis()
        dao.insertNew(
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
        dao.changeLocally(stageLocalId) { existing ->
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
                serverErrorCode = null,
            )
        } ?: return
        syncer.requestSync()
    }

    override suspend fun deleteStage(stageLocalId: String) {
        val existing = dao.changeLocally(stageLocalId) { existing ->
            if (existing.serverId == null) null else existing.copy(
                syncStatus = SyncStatus.PENDING,
                pendingOp = PendingOp.DELETE,
                locallyModifiedAt = clock.nowEpochMillis(),
                lastSyncError = null,
                serverErrorCode = null,
            )
        } ?: return
        if (existing.serverId == null) return
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

internal fun StageEntity.toStage(blockedByParent: Boolean = false): Stage = Stage(
    localId = localId,
    projectLocalId = projectLocalId,
    name = name,
    estimatedBudget = estimatedBudget,
    status = status.toStageStatus(),
    syncIssue = syncIssue(blockedByParent),
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
