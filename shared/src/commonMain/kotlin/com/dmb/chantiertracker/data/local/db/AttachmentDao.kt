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

    @Query("SELECT * FROM attachments WHERE syncStatus != 'SYNCED' AND (lastSyncError IS NULL OR lastSyncError NOT IN ('DELETED_ON_SERVER', 'UPDATE_REFUSED', 'FILE_REFUSED'))")
    suspend fun findPending(): List<AttachmentEntity>

    @Query(
        """
        SELECT c.localId FROM attachments c JOIN daily_entries e ON e.localId = c.entryLocalId JOIN daily_logs l ON l.localId = e.dailyLogLocalId JOIN stages s ON s.localId = l.stageLocalId JOIN projects p ON p.localId = s.projectLocalId WHERE c.syncStatus = 'PENDING' AND (e.serverId IS NULL AND (e.syncStatus = 'CONFLICTED' OR (s.serverId IS NULL AND (s.syncStatus = 'CONFLICTED' OR (p.serverId IS NULL AND p.syncStatus = 'CONFLICTED')))))
        """,
    )
    fun observeBlockedByParent(): Flow<List<String>>

    @Query("SELECT * FROM attachments WHERE entryLocalId = :entryLocalId")
    suspend fun findForEntry(entryLocalId: String): List<AttachmentEntity>

    @Upsert
    suspend fun upsert(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)
}
