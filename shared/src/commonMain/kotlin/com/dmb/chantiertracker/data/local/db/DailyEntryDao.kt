package com.dmb.chantiertracker.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyEntryDao {

    @Query("SELECT * FROM daily_entries WHERE dailyLogLocalId = :dailyLogLocalId AND pendingOp != 'DELETE'")
    fun observeEntriesForLog(dailyLogLocalId: String): Flow<List<DailyEntryEntity>>

    // Joins through daily_logs so the stage's log list can show, per day, which
    // entry types exist without a second round-trip per row.
    @Query(
        "SELECT e.* FROM daily_entries e " +
            "INNER JOIN daily_logs l ON l.localId = e.dailyLogLocalId " +
            "WHERE l.stageLocalId = :stageLocalId AND e.pendingOp != 'DELETE'",
    )
    fun observeEntriesForStage(stageLocalId: String): Flow<List<DailyEntryEntity>>

    @Query("SELECT * FROM daily_entries WHERE localId = :localId")
    fun observeEntry(localId: String): Flow<DailyEntryEntity?>

    @Query("SELECT * FROM daily_entries WHERE localId = :localId")
    suspend fun findByLocalId(localId: String): DailyEntryEntity?

    @Query("SELECT * FROM daily_entries WHERE serverId = :serverId")
    suspend fun findByServerId(serverId: Long): DailyEntryEntity?

    @Query("SELECT * FROM daily_entries WHERE dailyLogLocalId = :dailyLogLocalId AND type = :type")
    suspend fun findByLogAndType(dailyLogLocalId: String, type: String): DailyEntryEntity?

    @Query("SELECT * FROM daily_entries WHERE syncStatus != 'SYNCED' AND (lastSyncError IS NULL OR lastSyncError NOT IN ('DELETED_ON_SERVER', 'UPDATE_REFUSED', 'FILE_REFUSED'))")
    suspend fun findPending(): List<DailyEntryEntity>

    @Query(
        """
        SELECT c.localId FROM daily_entries c JOIN daily_logs l ON l.localId = c.dailyLogLocalId JOIN stages s ON s.localId = l.stageLocalId JOIN projects p ON p.localId = s.projectLocalId WHERE c.syncStatus = 'PENDING' AND (s.serverId IS NULL AND (s.syncStatus = 'CONFLICTED' OR (p.serverId IS NULL AND p.syncStatus = 'CONFLICTED')))
        """,
    )
    fun observeBlockedByParent(): Flow<List<String>>

    @Query("SELECT * FROM daily_entries WHERE dailyLogLocalId = :dailyLogLocalId")
    suspend fun findForLog(dailyLogLocalId: String): List<DailyEntryEntity>

    @Upsert
    suspend fun upsert(entry: DailyEntryEntity)

    @Query("DELETE FROM daily_entries WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)
}
