package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.HistoryPageDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class HistoryApi(private val client: HttpClient) {

    // GET /projects/{id}/history — ADMIN-only server-side (403 otherwise).
    // sort ∈ {date, action}, order ∈ {asc, desc}; a malformed value answers 400.
    suspend fun projectHistory(
        projectId: Long,
        page: Int,
        size: Int,
        sort: String,
        order: String,
    ): HistoryPageDto =
        client.get(ApiRoutes.projectHistory(projectId)) {
            parameter("page", page)
            parameter("size", size)
            parameter("sort", sort)
            parameter("order", order)
        }.body()
}
