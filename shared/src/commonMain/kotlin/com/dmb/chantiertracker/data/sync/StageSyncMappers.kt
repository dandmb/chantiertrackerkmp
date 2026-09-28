package com.dmb.chantiertracker.data.sync

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.StageEntity
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.remote.dto.CreateStageRequestDto
import com.dmb.chantiertracker.data.remote.dto.StageDto
import com.dmb.chantiertracker.data.remote.dto.UpdateStageRequestDto

private val SERVER_STAGE_STATUSES = setOf("IN_PROGRESS", "COMPLETED")

/**
 * A `WorkStage` has no `updatedAt` server-side (only an immutable `createdAt`),
 * so stage conflict resolution can't compare two server timestamps the way
 * projects do (ADR-21). It falls back to **last-writer-wins on push**: a pending
 * local edit is always pushed (after a 404 check), and a pull only overwrites a
 * row with `pendingOp == NONE`. `remoteUpdatedAt` is filled from `createdAt` for
 * symmetry with `ProjectEntity` but never drives a decision.
 */
fun StageDto.toSyncedEntity(
    localId: String,
    projectLocalId: String,
    syncedAt: Long,
    previous: StageEntity? = null,
): StageEntity {
    val remoteMillis = parseServerTimestampMillis(createdAt)
    return StageEntity(
        localId = localId,
        serverId = id,
        projectLocalId = projectLocalId,
        name = name,
        description = description,
        estimatedBudget = estimatedBudget,
        startDate = startDate,
        endDate = endDate,
        status = status,
        syncStatus = SyncStatus.SYNCED,
        pendingOp = PendingOp.NONE,
        locallyModifiedAt = remoteMillis ?: previous?.locallyModifiedAt ?: syncedAt,
        lastSyncedAt = syncedAt,
        remoteUpdatedAt = remoteMillis,
        lastSyncError = null,
    )
}

fun StageEntity.toCreateRequest() = CreateStageRequestDto(
    name = name,
    description = description?.ifBlank { null },
    estimatedBudget = estimatedBudget,
    startDate = startDate?.ifBlank { null },
    endDate = endDate?.ifBlank { null },
)

fun StageEntity.toUpdateRequest() = UpdateStageRequestDto(
    name = name,
    description = description,
    estimatedBudget = estimatedBudget,
    startDate = startDate,
    endDate = endDate,
    status = status.takeIf { it in SERVER_STAGE_STATUSES },
)
