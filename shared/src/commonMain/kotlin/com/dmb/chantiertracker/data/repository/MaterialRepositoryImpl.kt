@file:OptIn(ExperimentalUuidApi::class)

package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.ConsumptionLineDao
import com.dmb.chantiertracker.data.local.db.MaterialDao
import com.dmb.chantiertracker.data.local.db.MaterialEntity
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.PurchaseLineDao
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.Clock
import com.dmb.chantiertracker.data.sync.SystemClock
import com.dmb.chantiertracker.data.sync.Syncer
import com.dmb.chantiertracker.domain.model.Material
import com.dmb.chantiertracker.domain.model.MaterialStock
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
    private val syncer: Syncer,
    private val scope: AppCoroutineScope,
    private val clock: Clock = SystemClock,
    private val newLocalId: () -> String = { Uuid.random().toString() },
) : MaterialRepository {

    override fun observeMaterials(projectLocalId: String): Flow<List<Material>> =
        materialDao.observeMaterialsForProject(projectLocalId).map { rows -> rows.map(MaterialEntity::toMaterial) }

    // Available stock is never persisted — always summed from the project's own
    // purchase/consumption lines, the same "compute, don't store" principle as
    // a stage's spent budget (see ADR-28). No adjustment bookkeeping needed:
    // creating, editing or deleting a line is automatically reflected on the
    // next read, there is nothing to keep in sync with the lines themselves.
    override fun observeStock(projectLocalId: String): Flow<List<MaterialStock>> =
        combine(
            materialDao.observeMaterialsForProject(projectLocalId),
            purchaseLineDao.observeLinesForProject(projectLocalId),
            consumptionLineDao.observeLinesForProject(projectLocalId),
        ) { materials, purchases, consumptions ->
            val inByMaterial = purchases.groupBy { it.materialLocalId }.mapValues { (_, lines) -> lines.sumOf { it.quantity } }
            val outByMaterial = consumptions.groupBy { it.materialLocalId }.mapValues { (_, lines) -> lines.sumOf { it.quantity } }
            materials.map { material ->
                MaterialStock(
                    materialLocalId = material.localId,
                    materialName = material.name,
                    unit = material.unit,
                    quantityIn = inByMaterial[material.localId] ?: 0.0,
                    quantityOut = outByMaterial[material.localId] ?: 0.0,
                )
            }
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
