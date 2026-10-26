@file:OptIn(ExperimentalUuidApi::class)

package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.AttachmentFileStore
import com.dmb.chantiertracker.data.local.isAPhotoTooLargeForTheServer
import com.dmb.chantiertracker.data.local.db.AttachmentDao
import com.dmb.chantiertracker.data.local.db.AttachmentEntity
import com.dmb.chantiertracker.data.local.db.DailyEntryDao
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.remote.AttachmentApi
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.data.sync.Clock
import com.dmb.chantiertracker.data.sync.Syncer
import com.dmb.chantiertracker.data.sync.SystemClock
import com.dmb.chantiertracker.data.sync.syncIssue
import com.dmb.chantiertracker.domain.model.Attachment
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.MAX_VIDEO_BYTES
import com.dmb.chantiertracker.domain.model.UploadFile
import com.dmb.chantiertracker.domain.repository.AttachmentRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

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
        combine(dao.observeForEntry(entryLocalId), dao.observeBlockedByParent(), syncer.uploadProgress) { rows, blocked, progress ->
            rows.map { toAttachment(it, blockedByParent = it.localId in blocked).copy(uploadProgress = progress[it.localId]) }
        }

    // `Attachment.localPath` is the **current** absolute path, resolved from the
    // stable key Room stores (ADR-41) — so the media player / image decoder
    // always get a live path even after an iOS reinstall moved the container.
    private fun toAttachment(entity: AttachmentEntity, blockedByParent: Boolean = false): Attachment = Attachment(
        localId = entity.localId,
        entryLocalId = entity.entryLocalId,
        localPath = fileStore.absolutePathOf(entity.localPath),
        originalName = entity.originalName,
        mimeType = entity.mimeType,
        sizeBytes = entity.sizeBytes,
        durationSeconds = entity.durationSeconds,
        uploadedAt = entity.uploadedAt,
        syncIssue = entity.syncIssue(blockedByParent),
        uploadedById = entity.uploadedById,
        sentToTheServer = entity.serverId != null,
    )

    override suspend fun addAttachment(entryLocalId: String, bytes: ByteArray, originalName: String, mimeType: String): Attachment {
        if (mimeType.startsWith("image/") && isAPhotoTooLargeForTheServer(bytes)) throw DomainException.ImageTooLarge
        return queue(entryLocalId, fileStore.save(bytes, originalName), originalName, mimeType, bytes.size.toLong())
    }

    override suspend fun addVideo(entryLocalId: String, video: UploadFile): Attachment {
        if (video.size() > MAX_VIDEO_BYTES) throw DomainException.FileTooLarge
        return scope.async {
            val localPath = fileStore.saveFrom(video.openSource(), video.name)
            queue(entryLocalId, localPath, video.name, video.mimeType, fileStore.sizeOf(localPath))
        }.await()
    }

    private suspend fun queue(entryLocalId: String, localPath: String, originalName: String, mimeType: String, sizeBytes: Long): Attachment {
        val now = clock.nowEpochMillis()
        val entity = AttachmentEntity(
            localId = newLocalId(),
            serverId = null,
            entryLocalId = entryLocalId,
            localPath = localPath,
            originalName = originalName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            uploadedAt = now,
            syncStatus = SyncStatus.PENDING,
            pendingOp = PendingOp.CREATE,
            locallyModifiedAt = now,
            lastSyncedAt = null,
            remoteUpdatedAt = null,
            lastSyncError = null,
        )
        dao.insertNew(entity)
        syncer.requestSync()
        return toAttachment(entity)
    }

    // The local copy is removed right away — once pendingOp = DELETE the row
    // is already hidden from observeAttachments (see AttachmentDao), so
    // nothing reads the bytes again before the deletion itself is pushed.
    override suspend fun deleteAttachment(attachmentLocalId: String) {
        val existing = dao.changeLocally(attachmentLocalId) { existing ->
            if (existing.serverId == null) null else existing.copy(
                syncStatus = SyncStatus.PENDING,
                pendingOp = PendingOp.DELETE,
                locallyModifiedAt = clock.nowEpochMillis(),
                lastSyncError = null,
                serverErrorCode = null,
            )
        } ?: return
        fileStore.delete(existing.localPath)
        if (existing.serverId == null) return
        syncer.requestSync()
    }
}
