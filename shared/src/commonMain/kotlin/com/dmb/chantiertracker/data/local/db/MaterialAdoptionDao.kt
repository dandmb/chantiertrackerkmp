package com.dmb.chantiertracker.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
abstract class MaterialAdoptionDao {

    @Query("UPDATE purchase_lines SET materialLocalId = :keptLocalId WHERE materialLocalId = :duplicateLocalId")
    protected abstract suspend fun movePurchaseLines(duplicateLocalId: String, keptLocalId: String)

    @Query("UPDATE consumption_lines SET materialLocalId = :keptLocalId WHERE materialLocalId = :duplicateLocalId")
    protected abstract suspend fun moveConsumptionLines(duplicateLocalId: String, keptLocalId: String)

    @Query("DELETE FROM materials WHERE localId = :localId")
    protected abstract suspend fun deleteMaterial(localId: String)

    @Upsert
    protected abstract suspend fun upsertMaterial(material: MaterialEntity)

    @Transaction
    open suspend fun mergeInto(duplicateLocalId: String, kept: MaterialEntity) {
        movePurchaseLines(duplicateLocalId, kept.localId)
        moveConsumptionLines(duplicateLocalId, kept.localId)
        deleteMaterial(duplicateLocalId)
        upsertMaterial(kept)
    }
}
