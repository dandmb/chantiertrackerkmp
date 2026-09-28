package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.StageEntity
import com.dmb.chantiertracker.data.local.db.SyncStatus

fun localStage(
    localId: String,
    projectLocalId: String = "proj-1",
    name: String = "Étape $localId",
    serverId: Long? = null,
    estimatedBudget: Double? = null,
    startDate: String? = null,
    endDate: String? = null,
    status: String = "IN_PROGRESS",
    pendingOp: PendingOp = PendingOp.CREATE,
    syncStatus: SyncStatus = SyncStatus.PENDING,
    locallyModifiedAt: Long = 1_700_000_000_000L,
    lastSyncedAt: Long? = null,
    remoteUpdatedAt: Long? = null,
    lastSyncError: String? = null,
) = StageEntity(
    localId = localId,
    serverId = serverId,
    projectLocalId = projectLocalId,
    name = name,
    description = null,
    estimatedBudget = estimatedBudget,
    startDate = startDate,
    endDate = endDate,
    status = status,
    syncStatus = syncStatus,
    pendingOp = pendingOp,
    locallyModifiedAt = locallyModifiedAt,
    lastSyncedAt = lastSyncedAt,
    remoteUpdatedAt = remoteUpdatedAt,
    lastSyncError = lastSyncError,
)
