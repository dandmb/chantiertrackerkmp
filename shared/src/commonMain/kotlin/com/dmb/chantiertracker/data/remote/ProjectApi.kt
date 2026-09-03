package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.CreateProjectRequestDto
import com.dmb.chantiertracker.data.remote.dto.MemberDto
import com.dmb.chantiertracker.data.remote.dto.PageDto
import com.dmb.chantiertracker.data.remote.dto.ProjectDetailDto
import com.dmb.chantiertracker.data.remote.dto.ProjectDto
import com.dmb.chantiertracker.data.remote.dto.UpdateProjectRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class ProjectApi(private val client: HttpClient) {

    suspend fun list(): PageDto<ProjectDto> =
        client.get(ApiRoutes.PROJECTS).body()

    suspend fun get(id: Long): ProjectDetailDto =
        client.get(ApiRoutes.project(id)).body()

    suspend fun members(id: Long): PageDto<MemberDto> =
        client.get(ApiRoutes.projectMembers(id)).body()

    suspend fun create(body: CreateProjectRequestDto): ProjectDto =
        client.post(ApiRoutes.PROJECTS) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun update(id: Long, body: UpdateProjectRequestDto): ProjectDto =
        client.patch(ApiRoutes.project(id)) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun delete(id: Long) {
        client.delete(ApiRoutes.project(id))
    }
}
