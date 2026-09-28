package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.DailyEntryEntity
import com.dmb.chantiertracker.data.local.db.DailyLogEntity
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus

fun localDailyLog(
    localId: String,
    stageLocalId: String = "stage-1",
    date: String = "2026-09-05",
    serverId: Long? = null,
    locallyCreatedAt: Long = 1_700_000_000_000L,
    lastSyncedAt: Long? = null,
) = DailyLogEntity(
    localId = localId,
    serverId = serverId,
    stageLocalId = stageLocalId,
    date = date,
    locallyCreatedAt = locallyCreatedAt,
    lastSyncedAt = lastSyncedAt,
)

fun localDailyEntry(
    localId: String,
    dailyLogLocalId: String = "log-1",
    type: String = "PURCHASE",
    summary: String? = null,
    serverId: Long? = null,
    pendingOp: PendingOp = PendingOp.CREATE,
    syncStatus: SyncStatus = SyncStatus.PENDING,
    locallyModifiedAt: Long = 1_700_000_000_000L,
    lastSyncedAt: Long? = null,
    remoteUpdatedAt: Long? = null,
    lastSyncError: String? = null,
) = DailyEntryEntity(
    localId = localId,
    serverId = serverId,
    dailyLogLocalId = dailyLogLocalId,
    type = type,
    summary = summary,
    createdById = null,
    createdAt = null,
    modifiedById = null,
    modifiedAt = null,
    syncStatus = syncStatus,
    pendingOp = pendingOp,
    locallyModifiedAt = locallyModifiedAt,
    lastSyncedAt = lastSyncedAt,
    remoteUpdatedAt = remoteUpdatedAt,
    lastSyncError = lastSyncError,
)
