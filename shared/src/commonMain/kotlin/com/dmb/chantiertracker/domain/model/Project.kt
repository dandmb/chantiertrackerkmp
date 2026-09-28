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
    // The project owner's plan (never the caller's) — gates the supervisor
    // limit, mirrors the backend. Null until the first detail pull (ADR-33).
    val ownerPlan: Plan? = null,
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
