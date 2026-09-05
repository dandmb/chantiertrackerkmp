@file:OptIn(ExperimentalUuidApi::class)

package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.AttachmentFileStore
import com.dmb.chantiertracker.data.local.db.AttachmentDao
import com.dmb.chantiertracker.data.local.db.AttachmentEntity
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.Clock
import com.dmb.chantiertracker.data.sync.SystemClock
import com.dmb.chantiertracker.data.sync.Syncer
import com.dmb.chantiertracker.domain.model.Attachment
import com.dmb.chantiertracker.domain.repository.AttachmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class AttachmentRepositoryImpl(
    private val dao: AttachmentDao,
    private val fileStore: AttachmentFileStore,
    private val syncer: Syncer,
    private val scope: AppCoroutineScope,
    private val clock: Clock = SystemClock,
    private val newLocalId: () -> String = { Uuid.random().toString() },
) : AttachmentRepository {

    override fun observeAttachments(entryLocalId: String): Flow<List<Attachment>> =
        dao.observeForEntry(entryLocalId).map { rows -> rows.map(AttachmentEntity::toAttachment) }

    override suspend fun addAttachment(entryLocalId: String, bytes: ByteArray, originalName: String, mimeType: String): Attachment {
        val localId = newLocalId()
        val localPath = fileStore.save(bytes, originalName)
        val now = clock.nowEpochMillis()
        val entity = AttachmentEntity(
            localId = localId,
            serverId = null,
            entryLocalId = entryLocalId,
            localPath = localPath,
            originalName = originalName,
            mimeType = mimeType,
            sizeBytes = bytes.size.toLong(),
            uploadedAt = now,
            syncStatus = SyncStatus.PENDING,
            pendingOp = PendingOp.CREATE,
            locallyModifiedAt = now,
            lastSyncedAt = null,
            remoteUpdatedAt = null,
            lastSyncError = null,
        )
        dao.upsert(entity)
        syncer.requestSync()
        return entity.toAttachment()
    }

    // The local copy is removed right away — once pendingOp = DELETE the row
    // is already hidden from observeAttachments (see AttachmentDao), so
    // nothing reads the bytes again before the deletion itself is pushed.
    override suspend fun deleteAttachment(attachmentLocalId: String) {
        val existing = dao.findByLocalId(attachmentLocalId) ?: return
        fileStore.delete(existing.localPath)
        if (existing.serverId == null) {
            dao.deleteByLocalId(attachmentLocalId)
            return
        }
        dao.upsert(
            existing.copy(
                syncStatus = SyncStatus.PENDING,
                pendingOp = PendingOp.DELETE,
                locallyModifiedAt = clock.nowEpochMillis(),
                lastSyncError = null,
            ),
        )
        syncer.requestSync()
    }
}

internal fun AttachmentEntity.toAttachment(): Attachment = Attachment(
    localId = localId,
    entryLocalId = entryLocalId,
    localPath = localPath,
    originalName = originalName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    uploadedAt = uploadedAt,
)
