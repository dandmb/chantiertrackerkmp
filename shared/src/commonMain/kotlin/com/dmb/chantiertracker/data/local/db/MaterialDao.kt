package com.dmb.chantiertracker.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDao : LocalChangesDao<MaterialEntity> {

    @Query("SELECT * FROM materials WHERE projectLocalId = :projectLocalId ORDER BY name")
    fun observeMaterialsForProject(projectLocalId: String): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM materials WHERE localId = :localId")
    override suspend fun findByLocalId(localId: String): MaterialEntity?

    @Query("SELECT * FROM materials WHERE projectLocalId = :projectLocalId AND name = :name COLLATE NOCASE")
    suspend fun findByProjectAndName(projectLocalId: String, name: String): MaterialEntity?

    @Query("SELECT * FROM materials WHERE projectLocalId = :projectLocalId AND name = :name")
    suspend fun findByProjectAndNameExactly(projectLocalId: String, name: String): MaterialEntity?

    @Query("SELECT * FROM materials WHERE serverId = :serverId")
    suspend fun findByServerId(serverId: Long): MaterialEntity?

    @Query("SELECT * FROM materials WHERE syncStatus != 'SYNCED' AND (lastSyncError IS NULL OR lastSyncError NOT IN ('DELETED_ON_SERVER', 'UPDATE_REFUSED', 'FILE_REFUSED'))")
    suspend fun findPending(): List<MaterialEntity>

    @Query(
        """
        SELECT c.localId FROM materials c JOIN projects p ON p.localId = c.projectLocalId WHERE c.syncStatus = 'PENDING' AND (p.serverId IS NULL AND p.syncStatus = 'CONFLICTED')
        """,
    )
    fun observeBlockedByParent(): Flow<List<String>>

    @Query("SELECT * FROM materials WHERE projectLocalId = :projectLocalId")
    suspend fun findForProject(projectLocalId: String): List<MaterialEntity>

    @Upsert
    override suspend fun upsert(row: MaterialEntity)

    @Query("DELETE FROM materials WHERE localId = :localId")
    override suspend fun deleteByLocalId(localId: String)
}
