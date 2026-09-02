package com.dmb.chantiertracker.domain.repository

import com.dmb.chantiertracker.domain.model.Project

interface ProjectRepository {
    suspend fun getProjects(): List<Project>
}
