package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.MaterialDao
import com.dmb.chantiertracker.data.local.db.MaterialEntity
import com.dmb.chantiertracker.data.local.db.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [MaterialDao] — lets sync/repository tests run on every platform without Room. */
class FakeMaterialDao(initial: List<MaterialEntity> = emptyList()) : MaterialDao {

    private val materials = MutableStateFlow(initial.associateBy { it.localId })

    val stored: List<MaterialEntity> get() = materials.value.values.toList()

    override fun observeMaterialsForProject(projectLocalId: String): Flow<List<MaterialEntity>> =
        materials.map { rows -> rows.values.filter { it.projectLocalId == projectLocalId }.sortedBy { it.name } }

    override suspend fun findByLocalId(localId: String): MaterialEntity? = materials.value[localId]

    override suspend fun findByProjectAndName(projectLocalId: String, name: String): MaterialEntity? =
        materials.value.values.firstOrNull { it.projectLocalId == projectLocalId && it.name.equals(name, ignoreCase = true) }

    override suspend fun findByServerId(serverId: Long): MaterialEntity? =
        materials.value.values.firstOrNull { it.serverId == serverId }

    override suspend fun findPending(): List<MaterialEntity> =
        materials.value.values.filter { it.syncStatus != SyncStatus.SYNCED }

    override suspend fun upsert(material: MaterialEntity) {
        materials.value = materials.value + (material.localId to material)
    }
}
