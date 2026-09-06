package com.dmb.chantiertracker.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsumptionLineDao {

    @Query("SELECT * FROM consumption_lines WHERE entryLocalId = :entryLocalId AND pendingOp != 'DELETE' ORDER BY locallyModifiedAt")
    fun observeLinesForEntry(entryLocalId: String): Flow<List<ConsumptionLineEntity>>

    // Same join chain as PurchaseLineDao.observeLinesForProject — stock is project-wide.
    @Query(
        "SELECT c.* FROM consumption_lines c " +
            "INNER JOIN daily_entries e ON e.localId = c.entryLocalId " +
            "INNER JOIN daily_logs l ON l.localId = e.dailyLogLocalId " +
            "INNER JOIN stages s ON s.localId = l.stageLocalId " +
            "WHERE s.projectLocalId = :projectLocalId AND c.pendingOp != 'DELETE'",
    )
    fun observeLinesForProject(projectLocalId: String): Flow<List<ConsumptionLineEntity>>

    @Query("SELECT * FROM consumption_lines WHERE localId = :localId")
    suspend fun findByLocalId(localId: String): ConsumptionLineEntity?

    @Query("SELECT * FROM consumption_lines WHERE serverId = :serverId")
    suspend fun findByServerId(serverId: Long): ConsumptionLineEntity?

    @Query("SELECT * FROM consumption_lines WHERE syncStatus != 'SYNCED'")
    suspend fun findPending(): List<ConsumptionLineEntity>

    @Query("SELECT * FROM consumption_lines WHERE entryLocalId = :entryLocalId")
    suspend fun findForEntry(entryLocalId: String): List<ConsumptionLineEntity>

    @Upsert
    suspend fun upsert(line: ConsumptionLineEntity)

    @Query("DELETE FROM consumption_lines WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)
}
