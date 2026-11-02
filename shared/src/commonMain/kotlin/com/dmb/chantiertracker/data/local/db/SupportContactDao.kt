package com.dmb.chantiertracker.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SupportContactDao {

    @Query("SELECT * FROM support_contact WHERE id = 0")
    fun observe(): Flow<SupportContactEntity?>

    @Upsert
    suspend fun upsert(entity: SupportContactEntity)
}
