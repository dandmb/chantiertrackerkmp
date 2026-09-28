package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.CreateStageRequestDto
import com.dmb.chantiertracker.data.remote.dto.PageDto
import com.dmb.chantiertracker.data.remote.dto.StageDto
import com.dmb.chantiertracker.data.remote.dto.UpdateStageRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class StageApi(private val client: HttpClient) {

    suspend fun list(projectId: Long): PageDto<StageDto> =
        client.get(ApiRoutes.projectStages(projectId)).body()

    suspend fun get(id: Long): StageDto =
        client.get(ApiRoutes.stage(id)).body()

    suspend fun create(projectId: Long, body: CreateStageRequestDto): StageDto =
        client.post(ApiRoutes.projectStages(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun update(id: Long, body: UpdateStageRequestDto): StageDto =
        client.patch(ApiRoutes.stage(id)) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun delete(id: Long) {
        client.delete(ApiRoutes.stage(id))
    }
}
