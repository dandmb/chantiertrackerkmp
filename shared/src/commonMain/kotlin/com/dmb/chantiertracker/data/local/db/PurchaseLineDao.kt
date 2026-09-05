package com.dmb.chantiertracker.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseLineDao {

    @Query("SELECT * FROM purchase_lines WHERE entryLocalId = :entryLocalId AND pendingOp != 'DELETE' ORDER BY locallyModifiedAt")
    fun observeLinesForEntry(entryLocalId: String): Flow<List<PurchaseLineEntity>>

    // Joins through daily_entries/daily_logs/stages so the project's stock can
    // be computed from every purchase line in the project, whatever stage or
    // day it was logged under (stock is project-wide — see MaterialStock).
    @Query(
        "SELECT p.* FROM purchase_lines p " +
            "INNER JOIN daily_entries e ON e.localId = p.entryLocalId " +
            "INNER JOIN daily_logs l ON l.localId = e.dailyLogLocalId " +
            "INNER JOIN stages s ON s.localId = l.stageLocalId " +
            "WHERE s.projectLocalId = :projectLocalId AND p.pendingOp != 'DELETE'",
    )
    fun observeLinesForProject(projectLocalId: String): Flow<List<PurchaseLineEntity>>

    @Query("SELECT * FROM purchase_lines WHERE localId = :localId")
    suspend fun findByLocalId(localId: String): PurchaseLineEntity?

    @Query("SELECT * FROM purchase_lines WHERE serverId = :serverId")
    suspend fun findByServerId(serverId: Long): PurchaseLineEntity?

    @Query("SELECT * FROM purchase_lines WHERE syncStatus != 'SYNCED'")
    suspend fun findPending(): List<PurchaseLineEntity>

    @Upsert
    suspend fun upsert(line: PurchaseLineEntity)

    @Query("DELETE FROM purchase_lines WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)
}
