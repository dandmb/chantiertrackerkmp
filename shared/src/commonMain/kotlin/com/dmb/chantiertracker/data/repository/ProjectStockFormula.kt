package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.MaterialEntity
import com.dmb.chantiertracker.data.local.db.MaterialStockEntity
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.StockMovementRow
import com.dmb.chantiertracker.data.local.db.StockSnapshotEntity
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.domain.model.MaterialStock
import com.dmb.chantiertracker.domain.model.ProjectStock

fun projectStock(
    materials: List<MaterialEntity>,
    counters: List<MaterialStockEntity>,
    snapshot: StockSnapshotEntity?,
    purchases: List<StockMovementRow>,
    consumptions: List<StockMovementRow>,
): ProjectStock {
    val counterByMaterialServerId = counters.associateBy { it.materialServerId }
    val pendingIn = purchases.pendingDeltaByMaterial()
    val pendingOut = consumptions.pendingDeltaByMaterial()
    val stock = materials.map { material ->
        val counter = material.serverId?.let { counterByMaterialServerId[it] }
        MaterialStock(
            materialLocalId = material.localId,
            materialName = material.name,
            unit = material.unit,
            quantityIn = (counter?.quantityIn ?: 0.0) + (pendingIn[material.localId] ?: 0.0),
            quantityOut = (counter?.quantityOut ?: 0.0) + (pendingOut[material.localId] ?: 0.0),
        )
    }
    return ProjectStock(stock, snapshot?.refreshedAt)
}

private fun List<StockMovementRow>.pendingDeltaByMaterial(): Map<String, Double> =
    groupBy { it.materialLocalId }.mapValues { (_, rows) -> rows.sumOf { it.pendingDelta() } }

fun StockMovementRow.pendingDelta(): Double = when {
    parentDeleting -> -(serverQuantity ?: 0.0)
    syncStatus != SyncStatus.PENDING -> 0.0
    pendingOp == PendingOp.DELETE -> -(serverQuantity ?: 0.0)
    else -> quantity - (serverQuantity ?: 0.0)
}
