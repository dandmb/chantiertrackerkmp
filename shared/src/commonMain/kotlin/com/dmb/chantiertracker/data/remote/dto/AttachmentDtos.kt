package com.dmb.chantiertracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AttachmentDto(
    val id: Long,
    val entryId: Long,
    val originalName: String? = null,
    val mimeType: String? = null,
    val size: Long? = null,
    val durationSeconds: Int? = null,
    val uploadedById: Long? = null,
    val uploadedAt: String? = null,
)
