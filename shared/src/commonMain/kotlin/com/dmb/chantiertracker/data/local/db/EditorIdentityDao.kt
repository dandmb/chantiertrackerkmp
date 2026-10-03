package com.dmb.chantiertracker.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EditorIdentityDao {

    @Query("SELECT * FROM editor_identity WHERE id = 0")
    fun observe(): Flow<EditorIdentityEntity?>

    @Upsert
    suspend fun upsert(entity: EditorIdentityEntity)
}
