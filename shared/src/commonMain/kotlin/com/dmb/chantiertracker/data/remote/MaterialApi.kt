package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.CreateMaterialRequestDto
import com.dmb.chantiertracker.data.remote.dto.MaterialDto
import com.dmb.chantiertracker.data.remote.dto.PageDto
import com.dmb.chantiertracker.data.remote.dto.UpdateMaterialRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class MaterialApi(private val client: HttpClient) {

    suspend fun list(projectId: Long): PageDto<MaterialDto> =
        client.get(ApiRoutes.projectMaterials(projectId)).body()

    suspend fun create(projectId: Long, body: CreateMaterialRequestDto): MaterialDto =
        client.post(ApiRoutes.projectMaterials(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun update(id: Long, body: UpdateMaterialRequestDto): MaterialDto =
        client.patch(ApiRoutes.material(id)) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
}
