package com.dmb.chantiertracker.domain.model

data class Attachment(
    val localId: String,
    val entryLocalId: String,
    val localPath: String,
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    // Video only — whole seconds, from the server; null for a photo.
    val durationSeconds: Int? = null,
    val uploadedAt: Long,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}
