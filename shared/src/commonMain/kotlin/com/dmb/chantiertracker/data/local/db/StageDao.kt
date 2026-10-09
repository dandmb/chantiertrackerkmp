package com.dmb.chantiertracker.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StageDao {

    @Query(
        "SELECT * FROM stages WHERE projectLocalId = :projectLocalId AND pendingOp != 'DELETE' " +
            "ORDER BY (startDate IS NULL), startDate, name",
    )
    fun observeStagesForProject(projectLocalId: String): Flow<List<StageEntity>>

    @Query("SELECT * FROM stages WHERE localId = :localId")
    fun observeStage(localId: String): Flow<StageEntity?>

    @Query("SELECT * FROM stages WHERE localId = :localId")
    suspend fun findByLocalId(localId: String): StageEntity?

    @Query("SELECT * FROM stages WHERE serverId = :serverId")
    suspend fun findByServerId(serverId: Long): StageEntity?

    @Query("SELECT * FROM stages WHERE syncStatus != 'SYNCED' AND (lastSyncError IS NULL OR lastSyncError NOT IN ('DELETED_ON_SERVER', 'UPDATE_REFUSED', 'FILE_REFUSED'))")
    suspend fun findPending(): List<StageEntity>

    @Query(
        """
        SELECT c.localId FROM stages c JOIN projects p ON p.localId = c.projectLocalId WHERE c.syncStatus = 'PENDING' AND (p.serverId IS NULL AND p.syncStatus = 'CONFLICTED')
        """,
    )
    fun observeBlockedByParent(): Flow<List<String>>

    @Query("SELECT * FROM stages WHERE projectLocalId = :projectLocalId")
    suspend fun findForProject(projectLocalId: String): List<StageEntity>

    @Upsert
    suspend fun upsert(stage: StageEntity)

    @Upsert
    suspend fun upsertAll(stages: List<StageEntity>)

    @Query("DELETE FROM stages WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)
}
