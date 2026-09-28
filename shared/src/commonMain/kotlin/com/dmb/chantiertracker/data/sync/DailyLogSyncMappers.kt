package com.dmb.chantiertracker.data.sync

import com.dmb.chantiertracker.data.local.db.AttachmentEntity
import com.dmb.chantiertracker.data.local.db.ConsumptionLineEntity
import com.dmb.chantiertracker.data.local.db.DailyEntryEntity
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.PurchaseLineEntity
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.remote.dto.AttachmentDto
import com.dmb.chantiertracker.data.remote.dto.ConsumptionLineDto
import com.dmb.chantiertracker.data.remote.dto.CreateConsumptionLineRequestDto
import com.dmb.chantiertracker.data.remote.dto.CreatePurchaseLineRequestDto
import com.dmb.chantiertracker.data.remote.dto.DailyEntryDto
import com.dmb.chantiertracker.data.remote.dto.EntryRequestDto
import com.dmb.chantiertracker.data.remote.dto.PurchaseLineDto
import com.dmb.chantiertracker.data.remote.dto.UpdateConsumptionLineRequestDto
import com.dmb.chantiertracker.data.remote.dto.UpdatePurchaseLineRequestDto

// ─── daily entries ───────────────────────────────────────────────────────────
// `DailyEntryResponse` carries `modifiedAt`, but for a single-field summary the
// conflict window is tiny — entries fall back to last-writer-wins on push (like
// stages/lines/materials), not the optimistic-concurrency of projects.

fun DailyEntryDto.toSyncedEntity(
    localId: String,
    dailyLogLocalId: String,
    syncedAt: Long,
    previous: DailyEntryEntity? = null,
): DailyEntryEntity {
    val remoteMillis = parseServerTimestampMillis(modifiedAt ?: createdAt)
    return DailyEntryEntity(
        localId = localId,
        serverId = id,
        dailyLogLocalId = dailyLogLocalId,
        type = type,
        summary = summary,
        createdById = createdById,
        createdAt = createdAt,
        modifiedById = modifiedById,
        modifiedAt = modifiedAt,
        syncStatus = SyncStatus.SYNCED,
        pendingOp = PendingOp.NONE,
        locallyModifiedAt = remoteMillis ?: previous?.locallyModifiedAt ?: syncedAt,
        lastSyncedAt = syncedAt,
        remoteUpdatedAt = remoteMillis,
        lastSyncError = null,
    )
}

fun DailyEntryEntity.toEntryRequest() = EntryRequestDto(summary = summary)

// ─── purchase lines ──────────────────────────────────────────────────────────

fun PurchaseLineDto.toSyncedEntity(
    localId: String,
    entryLocalId: String,
    materialLocalId: String,
    syncedAt: Long,
    previous: PurchaseLineEntity? = null,
): PurchaseLineEntity = PurchaseLineEntity(
    localId = localId,
    serverId = id,
    entryLocalId = entryLocalId,
    materialLocalId = materialLocalId,
    quantity = quantity,
    unitPrice = unitPrice,
    totalPrice = totalPrice,
    supplier = supplier,
    createdAt = createdAt,
    syncStatus = SyncStatus.SYNCED,
    pendingOp = PendingOp.NONE,
    locallyModifiedAt = previous?.locallyModifiedAt ?: syncedAt,
    lastSyncedAt = syncedAt,
    remoteUpdatedAt = parseServerTimestampMillis(createdAt),
    lastSyncError = null,
)

fun PurchaseLineEntity.toCreateRequest(materialServerId: Long) = CreatePurchaseLineRequestDto(
    materialId = materialServerId,
    quantity = quantity,
    unitPrice = unitPrice,
    supplier = supplier?.ifBlank { null },
)

fun PurchaseLineEntity.toUpdateRequest() = UpdatePurchaseLineRequestDto(
    quantity = quantity,
    unitPrice = unitPrice,
    supplier = supplier?.ifBlank { null },
)

// ─── consumption lines ───────────────────────────────────────────────────────

fun ConsumptionLineDto.toSyncedEntity(
    localId: String,
    entryLocalId: String,
    materialLocalId: String,
    syncedAt: Long,
    previous: ConsumptionLineEntity? = null,
): ConsumptionLineEntity = ConsumptionLineEntity(
    localId = localId,
    serverId = id,
    entryLocalId = entryLocalId,
    materialLocalId = materialLocalId,
    quantity = quantity,
    createdAt = createdAt,
    syncStatus = SyncStatus.SYNCED,
    pendingOp = PendingOp.NONE,
    locallyModifiedAt = previous?.locallyModifiedAt ?: syncedAt,
    lastSyncedAt = syncedAt,
    remoteUpdatedAt = parseServerTimestampMillis(createdAt),
    lastSyncError = null,
)

fun ConsumptionLineEntity.toCreateRequest(materialServerId: Long) = CreateConsumptionLineRequestDto(
    materialId = materialServerId,
    quantity = quantity,
)

fun ConsumptionLineEntity.toUpdateRequest() = UpdateConsumptionLineRequestDto(quantity = quantity)

// ─── attachments ─────────────────────────────────────────────────────────────

fun AttachmentDto.toSyncedEntity(
    localId: String,
    entryLocalId: String,
    localPath: String,
    syncedAt: Long,
    previous: AttachmentEntity? = null,
): AttachmentEntity = AttachmentEntity(
    localId = localId,
    serverId = id,
    entryLocalId = entryLocalId,
    localPath = localPath,
    originalName = originalName ?: previous?.originalName ?: "photo.jpg",
    mimeType = mimeType ?: previous?.mimeType ?: "image/jpeg",
    sizeBytes = size ?: previous?.sizeBytes ?: 0L,
    durationSeconds = durationSeconds ?: previous?.durationSeconds,
    uploadedAt = parseServerTimestampMillis(uploadedAt) ?: previous?.uploadedAt ?: syncedAt,
    syncStatus = SyncStatus.SYNCED,
    pendingOp = PendingOp.NONE,
    locallyModifiedAt = previous?.locallyModifiedAt ?: syncedAt,
    lastSyncedAt = syncedAt,
    remoteUpdatedAt = parseServerTimestampMillis(uploadedAt),
    lastSyncError = null,
)
