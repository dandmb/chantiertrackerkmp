package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.remote.ProjectApi
import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.data.remote.dto.CreateProjectRequestDto
import com.dmb.chantiertracker.data.remote.dto.MemberDto
import com.dmb.chantiertracker.data.remote.dto.ProjectDetailDto
import com.dmb.chantiertracker.data.remote.dto.ProjectDto
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectMember
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.repository.ProjectRepository

class ProjectRepositoryImpl(
    private val api: ProjectApi,
) : ProjectRepository {

    override suspend fun getProjects(): List<Project> =
        apiCall { api.list() }.content.map(ProjectDto::toProject)

    override suspend fun getProject(id: Long): ProjectDetail =
        apiCall { api.get(id) }.toDetail()

    override suspend fun getMembers(id: Long): List<ProjectMember> =
        apiCall { api.members(id) }.content.map(MemberDto::toMember)

    override suspend fun createProject(input: CreateProjectInput): Long =
        apiCall {
            api.create(
                CreateProjectRequestDto(
                    name = input.name,
                    description = input.description?.ifBlank { null },
                    location = input.location?.ifBlank { null },
                    currency = input.currency?.ifBlank { null },
                    timezone = input.timezone,
                ),
            )
        }.id
}

private fun String.toProjectStatus(): ProjectStatus = when (uppercase()) {
    "IN_PROGRESS" -> ProjectStatus.IN_PROGRESS
    "SUSPENDED" -> ProjectStatus.SUSPENDED
    "COMPLETED" -> ProjectStatus.COMPLETED
    else -> ProjectStatus.UNKNOWN
}

private fun ProjectDto.toProject(): Project = Project(
    id = id,
    name = name,
    description = description,
    location = location,
    status = status.toProjectStatus(),
    createdAt = createdAt,
)

private fun ProjectDetailDto.toDetail(): ProjectDetail = ProjectDetail(
    id = id,
    name = name,
    description = description,
    location = location,
    currency = currency,
    timezone = timezone,
    status = status.toProjectStatus(),
    ownerId = ownerId,
)

private fun MemberDto.toMember(): ProjectMember = ProjectMember(
    userId = userId,
    name = name,
    email = email,
    role = when (role.uppercase()) {
        "ADMIN" -> ProjectRole.ADMIN
        "SUPERVISOR" -> ProjectRole.SUPERVISOR
        else -> ProjectRole.UNKNOWN
    },
)
