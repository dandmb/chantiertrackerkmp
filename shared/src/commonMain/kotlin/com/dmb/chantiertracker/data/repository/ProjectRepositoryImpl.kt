package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.remote.ProjectApi
import com.dmb.chantiertracker.data.remote.apiCall
import com.dmb.chantiertracker.data.remote.dto.ProjectDto
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.repository.ProjectRepository

class ProjectRepositoryImpl(
    private val api: ProjectApi,
) : ProjectRepository {

    override suspend fun getProjects(): List<Project> =
        apiCall { api.list() }.content.map(ProjectDto::toProject)
}

private fun ProjectDto.toProject(): Project = Project(
    id = id,
    name = name,
    description = description,
    location = location,
    status = when (status.uppercase()) {
        "IN_PROGRESS" -> ProjectStatus.IN_PROGRESS
        "SUSPENDED" -> ProjectStatus.SUSPENDED
        "COMPLETED" -> ProjectStatus.COMPLETED
        else -> ProjectStatus.UNKNOWN
    },
)
