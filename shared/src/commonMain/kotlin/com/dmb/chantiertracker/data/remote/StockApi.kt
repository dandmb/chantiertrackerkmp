package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.MaterialStockDto
import com.dmb.chantiertracker.data.remote.dto.PageDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class StockApi(private val client: HttpClient) {

    suspend fun list(projectId: Long, page: Int, size: Int): PageDto<MaterialStockDto> =
        client.get(ApiRoutes.projectStock(projectId)) {
            parameter("page", page)
            parameter("size", size)
        }.body()
}
