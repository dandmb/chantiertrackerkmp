package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.Attachment
import com.dmb.chantiertracker.domain.model.UploadFile
import kotlinx.coroutines.flow.Flow

interface AttachmentRepository {
    fun observeAttachments(entryLocalId: String): Flow<List<Attachment>>

    /** Photo — offline-first: saved locally + pushed by the SyncEngine (ADR-29). */
    suspend fun addAttachment(entryLocalId: String, bytes: ByteArray, originalName: String, mimeType: String): Attachment

    suspend fun addVideo(entryLocalId: String, video: UploadFile): Attachment

    suspend fun deleteAttachment(attachmentLocalId: String)
}
