package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.PageDto
import com.dmb.chantiertracker.data.remote.dto.ProjectDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class ProjectApi(private val client: HttpClient) {

    suspend fun list(): PageDto<ProjectDto> =
        client.get(ApiRoutes.PROJECTS).body()
}
