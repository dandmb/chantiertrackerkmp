package com.dmb.chantiertracker.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PageDto<T>(val content: List<T> = emptyList())

@Serializable
data class ProjectDto(
    val id: Long,
    val name: String,
    val description: String? = null,
    val location: String? = null,
    val status: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val currency: String? = null,
    val timezone: String? = null,
    val ownerId: Long? = null,
)

@Serializable
data class ProjectDetailDto(
    val id: Long,
    val name: String,
    val description: String? = null,
    val location: String? = null,
    val currency: String,
    val timezone: String,
    val ownerId: Long,
    val ownerPlan: String? = null,
    val status: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class UpdateProjectRequestDto(
    val name: String? = null,
    val description: String? = null,
    val location: String? = null,
    val currency: String? = null,
    val timezone: String? = null,
    val status: String? = null,
)

@Serializable
data class MemberDto(
    val userId: Long,
    val name: String,
    val email: String,
    val role: String,
)

@Serializable
data class CreateProjectRequestDto(
    val name: String,
    val description: String? = null,
    val location: String? = null,
    val currency: String? = null,
    val timezone: String,
)
