@file:OptIn(ExperimentalUuidApi::class)

package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.ConsumptionLineDao
import com.dmb.chantiertracker.data.local.db.MaterialDao
import com.dmb.chantiertracker.data.local.db.MaterialEntity
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.PurchaseLineDao
import com.dmb.chantiertracker.data.local.db.StockDao
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.Clock
import com.dmb.chantiertracker.data.sync.SystemClock
import com.dmb.chantiertracker.data.sync.Syncer
import com.dmb.chantiertracker.domain.model.Material
import com.dmb.chantiertracker.domain.model.ProjectStock
import com.dmb.chantiertracker.domain.repository.MaterialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class MaterialRepositoryImpl(
    private val materialDao: MaterialDao,
    private val purchaseLineDao: PurchaseLineDao,
    private val consumptionLineDao: ConsumptionLineDao,
    private val stockDao: StockDao,
    private val syncer: Syncer,
    private val scope: AppCoroutineScope,
    private val clock: Clock = SystemClock,
    private val newLocalId: () -> String = { Uuid.random().toString() },
) : MaterialRepository {

    override fun observeMaterials(projectLocalId: String): Flow<List<Material>> =
        materialDao.observeMaterialsForProject(projectLocalId).map { rows -> rows.map(MaterialEntity::toMaterial) }

    override fun observeStock(projectLocalId: String): Flow<ProjectStock> =
        combine(
            materialDao.observeMaterialsForProject(projectLocalId),
            stockDao.observeCounters(projectLocalId),
            stockDao.observeSnapshot(projectLocalId),
            purchaseLineDao.observeStockMovements(projectLocalId),
            consumptionLineDao.observeStockMovements(projectLocalId),
        ) { materials, counters, snapshot, purchases, consumptions ->
            projectStock(materials, counters, snapshot, purchases, consumptions)
        }

    override suspend fun createMaterial(projectLocalId: String, name: String, unit: String): Material {
        val existing = materialDao.findByProjectAndName(projectLocalId, name)
        if (existing != null) return existing.toMaterial()

        val localId = newLocalId()
        materialDao.upsert(
            MaterialEntity(
                localId = localId,
                serverId = null,
                projectLocalId = projectLocalId,
                name = name,
                unit = unit,
                syncStatus = SyncStatus.PENDING,
                pendingOp = PendingOp.CREATE,
                locallyModifiedAt = clock.nowEpochMillis(),
                lastSyncedAt = null,
                remoteUpdatedAt = null,
                lastSyncError = null,
            ),
        )
        syncer.requestSync()
        return Material(localId, projectLocalId, name, unit)
    }
}

internal fun MaterialEntity.toMaterial(): Material = Material(
    localId = localId,
    projectLocalId = projectLocalId,
    name = name,
    unit = unit,
)
