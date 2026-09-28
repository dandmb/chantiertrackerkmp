package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.CreatePurchaseLineRequestDto
import com.dmb.chantiertracker.data.remote.dto.PageDto
import com.dmb.chantiertracker.data.remote.dto.PurchaseLineDto
import com.dmb.chantiertracker.data.remote.dto.UpdatePurchaseLineRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class PurchaseLineApi(private val client: HttpClient) {

    suspend fun list(entryId: Long): PageDto<PurchaseLineDto> =
        client.get(ApiRoutes.entryPurchaseLines(entryId)).body()

    suspend fun create(entryId: Long, body: CreatePurchaseLineRequestDto): PurchaseLineDto =
        client.post(ApiRoutes.entryPurchaseLines(entryId)) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun update(id: Long, body: UpdatePurchaseLineRequestDto): PurchaseLineDto =
        client.patch(ApiRoutes.purchaseLine(id)) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun delete(id: Long) {
        client.delete(ApiRoutes.purchaseLine(id))
    }
}
