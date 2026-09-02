package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.ProjectEntity
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.parseServerTimestampMillis

fun serverMillis(iso: String): Long = parseServerTimestampMillis(iso)!!

fun localProject(
    localId: String,
    name: String = "Chantier $localId",
    serverId: Long? = null,
    pendingOp: PendingOp = PendingOp.CREATE,
    syncStatus: SyncStatus = SyncStatus.PENDING,
    locallyModifiedAt: Long = 1_700_000_000_000L,
    lastSyncedAt: Long? = null,
    remoteUpdatedAt: Long? = null,
    currency: String = "EUR",
    timezone: String = "Europe/Paris",
    lastSyncError: String? = null,
) = ProjectEntity(
    localId = localId,
    serverId = serverId,
    name = name,
    description = null,
    location = "Nîmes",
    currency = currency,
    timezone = timezone,
    status = "IN_PROGRESS",
    ownerId = 1L,
    createdAt = "2026-01-01T09:00:00",
    syncStatus = syncStatus,
    pendingOp = pendingOp,
    locallyModifiedAt = locallyModifiedAt,
    lastSyncedAt = lastSyncedAt,
    remoteUpdatedAt = remoteUpdatedAt,
    lastSyncError = lastSyncError,
)
