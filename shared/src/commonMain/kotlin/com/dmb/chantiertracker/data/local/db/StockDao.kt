package com.dmb.chantiertracker.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class StockDao {

    @Query("SELECT * FROM material_stock WHERE projectLocalId = :projectLocalId")
    abstract fun observeCounters(projectLocalId: String): Flow<List<MaterialStockEntity>>

    @Query("SELECT * FROM stock_snapshots WHERE projectLocalId = :projectLocalId")
    abstract fun observeSnapshot(projectLocalId: String): Flow<StockSnapshotEntity?>

    @Query("SELECT * FROM stock_snapshots WHERE projectLocalId = :projectLocalId")
    abstract suspend fun findSnapshot(projectLocalId: String): StockSnapshotEntity?

    @Query("SELECT * FROM material_stock WHERE projectLocalId = :projectLocalId AND materialServerId = :materialServerId")
    abstract suspend fun findCounter(projectLocalId: String, materialServerId: Long): MaterialStockEntity?

    @Query("SELECT projectLocalId FROM stock_snapshots WHERE needsRefresh = 1")
    abstract suspend fun findProjectsNeedingRefresh(): List<String>

    @Query("UPDATE stock_snapshots SET needsRefresh = 1 WHERE projectLocalId = :projectLocalId")
    abstract suspend fun markNeedsRefresh(projectLocalId: String)

    @Query("DELETE FROM material_stock WHERE projectLocalId = :projectLocalId")
    protected abstract suspend fun clearCounters(projectLocalId: String)

    @Upsert protected abstract suspend fun upsertCounters(counters: List<MaterialStockEntity>)
    @Upsert protected abstract suspend fun upsertCounter(counter: MaterialStockEntity)
    @Upsert protected abstract suspend fun upsertSnapshot(snapshot: StockSnapshotEntity)
    @Upsert protected abstract suspend fun upsertPurchaseLine(line: PurchaseLineEntity)
    @Upsert protected abstract suspend fun upsertConsumptionLine(line: ConsumptionLineEntity)

    @Query("DELETE FROM purchase_lines WHERE localId = :localId")
    protected abstract suspend fun deletePurchaseLine(localId: String)

    @Query("SELECT localVersion FROM purchase_lines WHERE localId = :localId")
    protected abstract suspend fun purchaseLineVersion(localId: String): Long?

    @Query("SELECT localVersion FROM consumption_lines WHERE localId = :localId")
    protected abstract suspend fun consumptionLineVersion(localId: String): Long?

    @Query("DELETE FROM consumption_lines WHERE localId = :localId")
    protected abstract suspend fun deleteConsumptionLine(localId: String)

    @Transaction
    open suspend fun replaceCounters(projectLocalId: String, counters: List<MaterialStockEntity>, refreshedAt: Long) {
        clearCounters(projectLocalId)
        upsertCounters(counters)
        upsertSnapshot(StockSnapshotEntity(projectLocalId, refreshedAt, needsRefresh = false))
    }

    @Transaction
    open suspend fun recordPurchaseLineSynced(projectLocalId: String, materialServerId: Long, line: PurchaseLineEntity, previousServerQuantity: Double?): Boolean {
        if (purchaseLineVersion(line.localId) != line.localVersion) return false
        upsertPurchaseLine(line)
        adjustCounter(projectLocalId, materialServerId, deltaIn = (line.serverQuantity ?: 0.0) - (previousServerQuantity ?: 0.0), deltaOut = 0.0)
        return true
    }

    @Transaction
    open suspend fun recordConsumptionLineSynced(projectLocalId: String, materialServerId: Long, line: ConsumptionLineEntity, previousServerQuantity: Double?): Boolean {
        if (consumptionLineVersion(line.localId) != line.localVersion) return false
        upsertConsumptionLine(line)
        adjustCounter(projectLocalId, materialServerId, deltaIn = 0.0, deltaOut = (line.serverQuantity ?: 0.0) - (previousServerQuantity ?: 0.0))
        return true
    }

    @Transaction
    open suspend fun recordPurchaseLineDeleted(projectLocalId: String, materialServerId: Long, line: PurchaseLineEntity, serverQuantity: Double): Boolean {
        if (purchaseLineVersion(line.localId) != line.localVersion) return false
        deletePurchaseLine(line.localId)
        adjustCounter(projectLocalId, materialServerId, deltaIn = -serverQuantity, deltaOut = 0.0)
        return true
    }

    @Transaction
    open suspend fun recordConsumptionLineDeleted(projectLocalId: String, materialServerId: Long, line: ConsumptionLineEntity, serverQuantity: Double): Boolean {
        if (consumptionLineVersion(line.localId) != line.localVersion) return false
        deleteConsumptionLine(line.localId)
        adjustCounter(projectLocalId, materialServerId, deltaIn = 0.0, deltaOut = -serverQuantity)
        return true
    }

    private suspend fun adjustCounter(projectLocalId: String, materialServerId: Long, deltaIn: Double, deltaOut: Double) {
        if (findSnapshot(projectLocalId) == null) return
        val counter = findCounter(projectLocalId, materialServerId) ?: MaterialStockEntity(projectLocalId, materialServerId, 0.0, 0.0)
        upsertCounter(counter.copy(quantityIn = counter.quantityIn + deltaIn, quantityOut = counter.quantityOut + deltaOut))
    }
}
