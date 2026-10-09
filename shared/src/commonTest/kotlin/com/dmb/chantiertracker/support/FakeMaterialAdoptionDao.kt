package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.MaterialAdoptionDao
import com.dmb.chantiertracker.data.local.db.MaterialEntity

class FakeMaterialAdoptionDao(
    private val materialDao: FakeMaterialDao = FakeMaterialDao(),
    private val purchaseLineDao: FakePurchaseLineDao = FakePurchaseLineDao(),
    private val consumptionLineDao: FakeConsumptionLineDao = FakeConsumptionLineDao(),
) : MaterialAdoptionDao() {

    override suspend fun movePurchaseLines(duplicateLocalId: String, keptLocalId: String) {
        purchaseLineDao.stored.filter { it.materialLocalId == duplicateLocalId }.forEach { purchaseLineDao.upsert(it.copy(materialLocalId = keptLocalId)) }
    }

    override suspend fun moveConsumptionLines(duplicateLocalId: String, keptLocalId: String) {
        consumptionLineDao.stored.filter { it.materialLocalId == duplicateLocalId }.forEach { consumptionLineDao.upsert(it.copy(materialLocalId = keptLocalId)) }
    }

    override suspend fun deleteMaterial(localId: String) = materialDao.delete(localId)

    override suspend fun upsertMaterial(material: MaterialEntity) = materialDao.upsert(material)
}
