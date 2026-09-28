package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.Attachment
import com.dmb.chantiertracker.domain.model.UploadFile
import kotlinx.coroutines.flow.Flow

interface AttachmentRepository {
    fun observeAttachments(entryLocalId: String): Flow<List<Attachment>>

    /** Photo — offline-first: saved locally + pushed by the SyncEngine (ADR-29). */
    suspend fun addAttachment(entryLocalId: String, bytes: ByteArray, originalName: String, mimeType: String): Attachment

    /**
     * Video — **online only** (ADR-35): raw video files are far too large to
     * queue on the device, and the server transcodes them anyway. The file is
     * **streamed** from disk, never loaded whole into memory (ADR-38). Uploads
     * with progress, then stores the transcoded MP4 locally as a SYNCED row.
     * Throws a `DomainException` on failure (`Network` offline, `NotFound` if
     * the entry itself hasn't synced yet, `PlanLimitReached` on a server refusal).
     */
    suspend fun uploadVideo(
        entryLocalId: String,
        video: UploadFile,
        onProgress: (Float) -> Unit = {},
    ): Attachment

    suspend fun deleteAttachment(attachmentLocalId: String)
}
