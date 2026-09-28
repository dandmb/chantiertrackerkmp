package com.dmb.chantiertracker.data.sync

import com.dmb.chantiertracker.data.local.db.MaterialEntity
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.remote.dto.CreateMaterialRequestDto
import com.dmb.chantiertracker.data.remote.dto.MaterialDto
import com.dmb.chantiertracker.data.remote.dto.UpdateMaterialRequestDto

// A material has no `updatedAt` server-side (the backend PATCHes name/unit
// only, never deletes) — same situation as a stage. Conflict resolution is
// last-writer-wins on push, after a NONE-vs-pending check on pull.
fun MaterialDto.toSyncedEntity(
    localId: String,
    projectLocalId: String,
    syncedAt: Long,
    previous: MaterialEntity? = null,
): MaterialEntity = MaterialEntity(
    localId = localId,
    serverId = id,
    projectLocalId = projectLocalId,
    name = name,
    unit = unit,
    syncStatus = SyncStatus.SYNCED,
    pendingOp = PendingOp.NONE,
    locallyModifiedAt = previous?.locallyModifiedAt ?: syncedAt,
    lastSyncedAt = syncedAt,
    remoteUpdatedAt = null,
    lastSyncError = null,
)

fun MaterialEntity.toCreateRequest() = CreateMaterialRequestDto(name = name, unit = unit)

fun MaterialEntity.toUpdateRequest() = UpdateMaterialRequestDto(name = name, unit = unit)
