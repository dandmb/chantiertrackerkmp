package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectMember

interface ProjectRepository {
    suspend fun getProjects(): List<Project>
    suspend fun getProject(id: Long): ProjectDetail
    suspend fun getMembers(id: Long): List<ProjectMember>
    suspend fun createProject(input: CreateProjectInput): Long
}
