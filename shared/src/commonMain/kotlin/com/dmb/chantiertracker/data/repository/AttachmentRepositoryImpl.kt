@file:OptIn(ExperimentalUuidApi::class)

package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.AttachmentFileStore
import com.dmb.chantiertracker.data.local.db.AttachmentDao
import com.dmb.chantiertracker.data.local.db.AttachmentEntity
import com.dmb.chantiertracker.data.local.db.DailyEntryDao
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.remote.AttachmentApi
import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.Clock
import com.dmb.chantiertracker.data.sync.SystemClock
import com.dmb.chantiertracker.data.sync.Syncer
import com.dmb.chantiertracker.data.sync.parseServerTimestampMillis
import com.dmb.chantiertracker.domain.model.Attachment
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.UploadFile
import com.dmb.chantiertracker.domain.repository.AttachmentRepository
import kotlinx.io.buffered
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class AttachmentRepositoryImpl(
    private val dao: AttachmentDao,
    private val entryDao: DailyEntryDao,
    private val api: AttachmentApi,
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

    // Online only (ADR-35). The raw video is streamed straight to the server
    // (never buffered in memory, never saved to the device — a raw 2-min clip
    // runs to hundreds of MB, ADR-38); the server transcodes it, and we pull
    // the small MP4 back to store locally as a normal SYNCED row, so it behaves
    // like any other attachment afterwards.
    override suspend fun uploadVideo(
        entryLocalId: String,
        video: UploadFile,
        onProgress: (Float) -> Unit,
    ): Attachment {
        val entryServerId = entryDao.findByLocalId(entryLocalId)?.serverId
            ?: throw DomainException.NotFound
        val dto = apiCall {
            api.upload(
                entryId = entryServerId,
                contentLength = video.size(),
                fileName = video.name,
                mimeType = video.mimeType,
                openSource = { video.openSource().buffered() },
            ) { sent, total ->
                if (total != null && total > 0) onProgress((sent.toFloat() / total).coerceIn(0f, 1f))
            }
        }
        val transcoded = apiCall { api.download(dto.id) }
        val storedName = dto.originalName ?: video.name
        val localPath = fileStore.save(transcoded, storedName)
        val now = clock.nowEpochMillis()
        val entity = AttachmentEntity(
            localId = newLocalId(),
            serverId = dto.id,
            entryLocalId = entryLocalId,
            localPath = localPath,
            originalName = storedName,
            mimeType = dto.mimeType ?: "video/mp4",
            sizeBytes = dto.size ?: transcoded.size.toLong(),
            durationSeconds = dto.durationSeconds,
            uploadedAt = parseServerTimestampMillis(dto.uploadedAt) ?: now,
            syncStatus = SyncStatus.SYNCED,
            pendingOp = PendingOp.NONE,
            locallyModifiedAt = now,
            lastSyncedAt = now,
            remoteUpdatedAt = parseServerTimestampMillis(dto.uploadedAt),
            lastSyncError = null,
        )
        dao.upsert(entity)
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
    durationSeconds = durationSeconds,
    uploadedAt = uploadedAt,
)
