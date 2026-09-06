package com.dmb.chantiertracker.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {

    @Query("SELECT * FROM attachments WHERE entryLocalId = :entryLocalId AND pendingOp != 'DELETE' ORDER BY uploadedAt")
    fun observeForEntry(entryLocalId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE localId = :localId")
    suspend fun findByLocalId(localId: String): AttachmentEntity?

    @Query("SELECT * FROM attachments WHERE serverId = :serverId")
    suspend fun findByServerId(serverId: Long): AttachmentEntity?

    @Query("SELECT * FROM attachments WHERE syncStatus != 'SYNCED'")
    suspend fun findPending(): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE entryLocalId = :entryLocalId")
    suspend fun findForEntry(entryLocalId: String): List<AttachmentEntity>

    @Upsert
    suspend fun upsert(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)
}
