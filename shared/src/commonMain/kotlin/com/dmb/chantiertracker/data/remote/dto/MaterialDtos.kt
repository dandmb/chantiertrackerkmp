package com.dmb.chantiertracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class MaterialDto(
    val id: Long,
    val projectId: Long,
    val name: String,
    val unit: String,
)

@Serializable
data class CreateMaterialRequestDto(
    val name: String,
    val unit: String,
)

@Serializable
data class UpdateMaterialRequestDto(
    val name: String? = null,
    val unit: String? = null,
)
