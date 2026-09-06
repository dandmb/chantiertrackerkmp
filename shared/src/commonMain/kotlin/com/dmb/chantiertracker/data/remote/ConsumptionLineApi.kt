package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.ConsumptionLineDto
import com.dmb.chantiertracker.data.remote.dto.CreateConsumptionLineRequestDto
import com.dmb.chantiertracker.data.remote.dto.PageDto
import com.dmb.chantiertracker.data.remote.dto.UpdateConsumptionLineRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class ConsumptionLineApi(private val client: HttpClient) {

    suspend fun list(entryId: Long): PageDto<ConsumptionLineDto> =
        client.get(ApiRoutes.entryConsumptionLines(entryId)).body()

    suspend fun create(entryId: Long, body: CreateConsumptionLineRequestDto): ConsumptionLineDto =
        client.post(ApiRoutes.entryConsumptionLines(entryId)) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun update(id: Long, body: UpdateConsumptionLineRequestDto): ConsumptionLineDto =
        client.patch(ApiRoutes.consumptionLine(id)) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun delete(id: Long) {
        client.delete(ApiRoutes.consumptionLine(id))
    }
}
