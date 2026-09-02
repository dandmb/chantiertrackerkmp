package com.dmb.chantiertracker.domain.model

data class Project(
    val localId: String,
    val name: String,
    val description: String?,
    val location: String?,
    val status: ProjectStatus,
    val createdAt: String? = null,
)

enum class ProjectStatus { IN_PROGRESS, SUSPENDED, COMPLETED, UNKNOWN }

data class ProjectDetail(
    val localId: String,
    val name: String,
    val description: String?,
    val location: String?,
    val currency: String,
    val timezone: String,
    val status: ProjectStatus,
    val ownerId: Long?,
)

data class ProjectMember(
    val userId: Long,
    val name: String,
    val email: String,
    val role: ProjectRole,
)

enum class ProjectRole { ADMIN, SUPERVISOR, UNKNOWN }

data class CreateProjectInput(
    val name: String,
    val description: String?,
    val location: String?,
    val currency: String?,
    val timezone: String,
)

data class UpdateProjectInput(
    val name: String,
    val description: String?,
    val location: String?,
    val currency: String,
    val timezone: String,
    val status: ProjectStatus,
)
