package com.dmb.chantiertracker.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDao {

    @Query("SELECT * FROM materials WHERE projectLocalId = :projectLocalId ORDER BY name")
    fun observeMaterialsForProject(projectLocalId: String): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM materials WHERE localId = :localId")
    suspend fun findByLocalId(localId: String): MaterialEntity?

    @Query("SELECT * FROM materials WHERE projectLocalId = :projectLocalId AND name = :name COLLATE NOCASE")
    suspend fun findByProjectAndName(projectLocalId: String, name: String): MaterialEntity?

    @Query("SELECT * FROM materials WHERE serverId = :serverId")
    suspend fun findByServerId(serverId: Long): MaterialEntity?

    @Query("SELECT * FROM materials WHERE syncStatus != 'SYNCED'")
    suspend fun findPending(): List<MaterialEntity>

    @Query("SELECT * FROM materials WHERE projectLocalId = :projectLocalId")
    suspend fun findForProject(projectLocalId: String): List<MaterialEntity>

    @Upsert
    suspend fun upsert(material: MaterialEntity)
}
