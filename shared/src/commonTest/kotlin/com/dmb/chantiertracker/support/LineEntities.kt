package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.ConsumptionLineEntity
import com.dmb.chantiertracker.data.local.db.MaterialEntity
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.PurchaseLineEntity
import com.dmb.chantiertracker.data.local.db.SyncStatus

fun localMaterial(
    localId: String,
    projectLocalId: String = "proj-1",
    name: String = "Matériau $localId",
    unit: String = "unité",
    serverId: Long? = null,
    pendingOp: PendingOp = PendingOp.CREATE,
    syncStatus: SyncStatus = SyncStatus.PENDING,
    locallyModifiedAt: Long = 1_700_000_000_000L,
) = MaterialEntity(
    localId = localId,
    serverId = serverId,
    projectLocalId = projectLocalId,
    name = name,
    unit = unit,
    syncStatus = syncStatus,
    pendingOp = pendingOp,
    locallyModifiedAt = locallyModifiedAt,
    lastSyncedAt = null,
    remoteUpdatedAt = null,
    lastSyncError = null,
)

fun localPurchaseLine(
    localId: String,
    entryLocalId: String = "entry-1",
    materialLocalId: String = "material-1",
    quantity: Double = 1.0,
    unitPrice: Double = 1.0,
    totalPrice: Double = quantity * unitPrice,
    supplier: String? = null,
    serverId: Long? = null,
    pendingOp: PendingOp = PendingOp.CREATE,
    syncStatus: SyncStatus = SyncStatus.PENDING,
    locallyModifiedAt: Long = 1_700_000_000_000L,
) = PurchaseLineEntity(
    localId = localId,
    serverId = serverId,
    entryLocalId = entryLocalId,
    materialLocalId = materialLocalId,
    quantity = quantity,
    unitPrice = unitPrice,
    totalPrice = totalPrice,
    supplier = supplier,
    createdAt = null,
    syncStatus = syncStatus,
    pendingOp = pendingOp,
    locallyModifiedAt = locallyModifiedAt,
    lastSyncedAt = null,
    remoteUpdatedAt = null,
    lastSyncError = null,
)

fun localConsumptionLine(
    localId: String,
    entryLocalId: String = "entry-1",
    materialLocalId: String = "material-1",
    quantity: Double = 1.0,
    serverId: Long? = null,
    pendingOp: PendingOp = PendingOp.CREATE,
    syncStatus: SyncStatus = SyncStatus.PENDING,
    locallyModifiedAt: Long = 1_700_000_000_000L,
) = ConsumptionLineEntity(
    localId = localId,
    serverId = serverId,
    entryLocalId = entryLocalId,
    materialLocalId = materialLocalId,
    quantity = quantity,
    createdAt = null,
    syncStatus = syncStatus,
    pendingOp = pendingOp,
    locallyModifiedAt = locallyModifiedAt,
    lastSyncedAt = null,
    remoteUpdatedAt = null,
    lastSyncError = null,
)
