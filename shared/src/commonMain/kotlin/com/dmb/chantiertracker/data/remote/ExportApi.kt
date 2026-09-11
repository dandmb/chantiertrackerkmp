package com.dmb.chantiertracker.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders

class ExportApi(private val client: HttpClient) {

    // GET /projects/{id}/export/pdf — any project member (404 if not a member);
    // 403 if the project OWNER's plan is FREE. Body = the raw PDF bytes; the
    // download name is in the Content-Disposition header. A per-request 60 s
    // timeout — the server generates the whole document in the request.
    suspend fun exportProjectPdf(projectId: Long): RawPdf {
        val response: HttpResponse = client.get(ApiRoutes.projectExportPdf(projectId)) {
            timeout { requestTimeoutMillis = 60_000L }
        }
        val contentDisposition = response.headers[HttpHeaders.ContentDisposition]
        return RawPdf(response.body(), contentDisposition)
    }
}

class RawPdf(val bytes: ByteArray, val contentDisposition: String?)
