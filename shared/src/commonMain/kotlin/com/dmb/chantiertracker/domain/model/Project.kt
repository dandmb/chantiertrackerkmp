package com.dmb.chantiertracker.domain.model

data class Project(
    val id: Long,
    val name: String,
    val description: String?,
    val location: String?,
    val status: ProjectStatus,
)

enum class ProjectStatus { IN_PROGRESS, SUSPENDED, COMPLETED, UNKNOWN }
