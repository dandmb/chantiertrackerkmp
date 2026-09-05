package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.Attachment
import kotlinx.coroutines.flow.Flow

interface AttachmentRepository {
    fun observeAttachments(entryLocalId: String): Flow<List<Attachment>>
    suspend fun addAttachment(entryLocalId: String, bytes: ByteArray, originalName: String, mimeType: String): Attachment
    suspend fun deleteAttachment(attachmentLocalId: String)
}
