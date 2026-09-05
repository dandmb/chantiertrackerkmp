package com.dmb.chantiertracker.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyLogDao {

    @Query("SELECT * FROM daily_logs WHERE stageLocalId = :stageLocalId ORDER BY date DESC")
    fun observeLogsForStage(stageLocalId: String): Flow<List<DailyLogEntity>>

    @Query("SELECT * FROM daily_logs WHERE localId = :localId")
    fun observeLog(localId: String): Flow<DailyLogEntity?>

    @Query("SELECT * FROM daily_logs WHERE localId = :localId")
    suspend fun findByLocalId(localId: String): DailyLogEntity?

    @Query("SELECT * FROM daily_logs WHERE stageLocalId = :stageLocalId AND date = :date")
    suspend fun findByStageAndDate(stageLocalId: String, date: String): DailyLogEntity?

    @Query("SELECT * FROM daily_logs WHERE serverId = :serverId")
    suspend fun findByServerId(serverId: Long): DailyLogEntity?

    @Upsert
    suspend fun upsert(log: DailyLogEntity)

    @Query("DELETE FROM daily_logs WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)
}
