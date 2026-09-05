package com.dmb.chantiertracker.domain.model

data class Attachment(
    val localId: String,
    val entryLocalId: String,
    val localPath: String,
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val uploadedAt: Long,
)
