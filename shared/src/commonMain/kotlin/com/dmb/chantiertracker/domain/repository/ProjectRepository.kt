package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectMember
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun observeProjects(): Flow<List<Project>>
    fun observeProject(localId: String): Flow<ProjectDetail?>
    fun observeMembers(localId: String): Flow<List<ProjectMember>>

    /** Writes the project to the local store immediately and returns its stable local id. Sync happens in the background. */
    suspend fun createProject(input: CreateProjectInput): String

    /** Best-effort pull from the server into the local store. Never throws. */
    suspend fun refresh()
}
