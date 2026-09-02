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
)
