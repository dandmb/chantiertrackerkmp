package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.ConsumptionLineEntity
import com.dmb.chantiertracker.data.local.db.MaterialStockEntity
import com.dmb.chantiertracker.data.local.db.PurchaseLineEntity
import com.dmb.chantiertracker.data.local.db.StockDao
import com.dmb.chantiertracker.data.local.db.StockSnapshotEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeStockDao(
    private val purchaseLineDao: FakePurchaseLineDao = FakePurchaseLineDao(),
    private val consumptionLineDao: FakeConsumptionLineDao = FakeConsumptionLineDao(),
) : StockDao() {

    val counters = MutableStateFlow<Map<Pair<String, Long>, MaterialStockEntity>>(emptyMap())
    val snapshots = MutableStateFlow<Map<String, StockSnapshotEntity>>(emptyMap())

    fun counter(projectLocalId: String, materialServerId: Long): MaterialStockEntity? = counters.value[projectLocalId to materialServerId]

    override fun observeCounters(projectLocalId: String): Flow<List<MaterialStockEntity>> =
        counters.map { rows -> rows.values.filter { it.projectLocalId == projectLocalId } }

    override fun observeSnapshot(projectLocalId: String): Flow<StockSnapshotEntity?> = snapshots.map { it[projectLocalId] }

    override suspend fun findSnapshot(projectLocalId: String): StockSnapshotEntity? = snapshots.value[projectLocalId]

    override suspend fun findCounter(projectLocalId: String, materialServerId: Long): MaterialStockEntity? =
        counters.value[projectLocalId to materialServerId]

    override suspend fun findProjectsNeedingRefresh(): List<String> =
        snapshots.value.values.filter { it.needsRefresh }.map { it.projectLocalId }

    override suspend fun markNeedsRefresh(projectLocalId: String) {
        val snapshot = snapshots.value[projectLocalId] ?: return
        snapshots.value = snapshots.value + (projectLocalId to snapshot.copy(needsRefresh = true))
    }

    override suspend fun clearCounters(projectLocalId: String) {
        counters.value = counters.value.filterKeys { it.first != projectLocalId }
    }

    override suspend fun upsertCounters(counters: List<MaterialStockEntity>) = counters.forEach { upsertCounter(it) }

    override suspend fun upsertCounter(counter: MaterialStockEntity) {
        counters.value = counters.value + ((counter.projectLocalId to counter.materialServerId) to counter)
    }

    override suspend fun upsertSnapshot(snapshot: StockSnapshotEntity) {
        snapshots.value = snapshots.value + (snapshot.projectLocalId to snapshot)
    }

    override suspend fun upsertPurchaseLine(line: PurchaseLineEntity) = purchaseLineDao.upsert(line)

    override suspend fun upsertConsumptionLine(line: ConsumptionLineEntity) = consumptionLineDao.upsert(line)

    override suspend fun deletePurchaseLine(localId: String) = purchaseLineDao.deleteByLocalId(localId)

    override suspend fun purchaseLineVersion(localId: String): Long? = purchaseLineDao.findByLocalId(localId)?.localVersion

    override suspend fun consumptionLineVersion(localId: String): Long? = consumptionLineDao.findByLocalId(localId)?.localVersion

    override suspend fun deleteConsumptionLine(localId: String) = consumptionLineDao.deleteByLocalId(localId)
}
