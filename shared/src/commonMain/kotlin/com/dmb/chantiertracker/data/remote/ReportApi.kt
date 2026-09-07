package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.CreateReportRequestDto
import com.dmb.chantiertracker.data.remote.dto.ReportDto
import com.dmb.chantiertracker.data.remote.dto.ReportPageDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class ReportApi(private val client: HttpClient) {

    // POST /entries/{id}/reports — any project member; 404 if the entry is
    // gone, 403 if the project/stage is inactive.
    suspend fun create(entryId: Long, body: CreateReportRequestDto): ReportDto =
        client.post(ApiRoutes.entryReports(entryId)) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    // GET /projects/{id}/reports — ADMIN-only server-side (403 otherwise),
    // fixed sort (createdAt desc), standard `page`/`size` paging.
    suspend fun projectReports(projectId: Long, page: Int, size: Int): ReportPageDto =
        client.get(ApiRoutes.projectReports(projectId)) {
            parameter("page", page)
            parameter("size", size)
        }.body()

    // PATCH /reports/{id}/process — ADMIN of the report's project; no body.
    suspend fun process(reportId: Long): ReportDto =
        client.patch(ApiRoutes.reportProcess(reportId)).body()
}
