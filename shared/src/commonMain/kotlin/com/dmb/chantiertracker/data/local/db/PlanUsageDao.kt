package com.dmb.chantiertracker.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanUsageDao {

    @Query("SELECT * FROM plan_usage WHERE id = 0")
    fun observe(): Flow<PlanUsageEntity?>

    @Upsert
    suspend fun upsert(entity: PlanUsageEntity)
}
