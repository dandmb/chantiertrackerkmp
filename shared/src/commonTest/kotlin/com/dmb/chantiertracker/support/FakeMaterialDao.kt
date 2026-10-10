package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.MaterialDao
import com.dmb.chantiertracker.data.local.db.MaterialEntity
import com.dmb.chantiertracker.data.local.db.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [MaterialDao]. Reproduces Room's `@Upsert` against the (projectLocalId, name) unique
 * index: a new row clashing on the name is silently not stored; an existing row renamed into a
 * clash throws.
 */
class FakeMaterialDao(initial: List<MaterialEntity> = emptyList()) : MaterialDao {

    private val materials = MutableStateFlow(initial.associateBy { it.localId })

    val stored: List<MaterialEntity> get() = materials.value.values.toList()

    override fun observeMaterialsForProject(projectLocalId: String): Flow<List<MaterialEntity>> =
        materials.map { rows -> rows.values.filter { it.projectLocalId == projectLocalId }.sortedBy { it.name } }

    override suspend fun findByLocalId(localId: String): MaterialEntity? = materials.value[localId]

    override suspend fun findByProjectAndName(projectLocalId: String, name: String): MaterialEntity? =
        materials.value.values.firstOrNull { it.projectLocalId == projectLocalId && it.name.equals(name, ignoreCase = true) }

    override suspend fun findByProjectAndNameExactly(projectLocalId: String, name: String): MaterialEntity? =
        materials.value.values.firstOrNull { it.projectLocalId == projectLocalId && it.name == name }

    override suspend fun findByServerId(serverId: Long): MaterialEntity? =
        materials.value.values.firstOrNull { it.serverId == serverId }

    override suspend fun findPending(): List<MaterialEntity> =
        materials.value.values.filter { it.syncStatus != SyncStatus.SYNCED && it.lastSyncError !in com.dmb.chantiertracker.data.sync.SyncError.WAITING_FOR_THE_USER }

    val blockedByParent = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())

    override fun observeBlockedByParent(): kotlinx.coroutines.flow.Flow<List<String>> = blockedByParent

    override suspend fun findForProject(projectLocalId: String): List<MaterialEntity> =
        materials.value.values.filter { it.projectLocalId == projectLocalId }

    override suspend fun upsert(material: MaterialEntity) {
        val clash = materials.value.values.any {
            it.localId != material.localId && it.projectLocalId == material.projectLocalId && it.name == material.name
        }
        if (clash) {
            check(material.localId !in materials.value) { "UNIQUE constraint failed: materials.projectLocalId, materials.name" }
            return
        }
        materials.value = materials.value + (material.localId to material)
    }

    fun delete(localId: String) {
        materials.value = materials.value - localId
    }

    override suspend fun deleteByLocalId(localId: String) {
        materials.value = materials.value - localId
    }
}
