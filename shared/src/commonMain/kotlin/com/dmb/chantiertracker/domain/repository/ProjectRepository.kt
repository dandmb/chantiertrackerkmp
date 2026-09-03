package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectMember
import com.dmb.chantiertracker.domain.model.UpdateProjectInput
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun observeProjects(): Flow<List<Project>>
    fun observeProject(localId: String): Flow<ProjectDetail?>
    fun observeMembers(localId: String): Flow<List<ProjectMember>>

    /** Writes the project to the local store immediately and returns its stable local id. Sync happens in the background. */
    suspend fun createProject(input: CreateProjectInput): String

    /** Applies the edit to the local store immediately, flagged pending. Sync happens in the background. */
    suspend fun updateProject(localId: String, input: UpdateProjectInput)

    /** Marks the project for deletion locally (or drops it outright if it never reached the server). Sync happens in the background. */
    suspend fun deleteProject(localId: String)

    /** Best-effort pull of the whole list from the server into the local store. Never throws. */
    suspend fun refresh()

    /** Best-effort pull of one project and its members from the server into the local store. Never throws. */
    suspend fun refreshProject(localId: String)
}
