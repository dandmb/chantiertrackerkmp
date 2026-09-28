package com.dmb.chantiertracker.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface InvitationDao {

    @Query("SELECT * FROM invitations WHERE projectLocalId = :projectLocalId ORDER BY createdAt DESC")
    fun observeForProject(projectLocalId: String): Flow<List<InvitationEntity>>

    @Query("SELECT * FROM invitations WHERE projectLocalId = :projectLocalId")
    suspend fun findForProject(projectLocalId: String): List<InvitationEntity>

    @Upsert
    suspend fun upsertAll(invitations: List<InvitationEntity>)

    @Query("DELETE FROM invitations WHERE projectLocalId = :projectLocalId")
    suspend fun clearForProject(projectLocalId: String)

    @Query("DELETE FROM invitations WHERE id = :id")
    suspend fun deleteById(id: Long)
}
