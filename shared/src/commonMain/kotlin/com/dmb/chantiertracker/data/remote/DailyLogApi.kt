package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.DailyEntryDto
import com.dmb.chantiertracker.data.remote.dto.DailyLogDetailDto
import com.dmb.chantiertracker.data.remote.dto.DailyLogSummaryDto
import com.dmb.chantiertracker.data.remote.dto.EntryRequestDto
import com.dmb.chantiertracker.data.remote.dto.PageDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class DailyLogApi(private val client: HttpClient) {

    suspend fun listLogs(stageId: Long): PageDto<DailyLogSummaryDto> =
        client.get(ApiRoutes.stageLogs(stageId)).body()

    suspend fun getLog(id: Long): DailyLogDetailDto =
        client.get(ApiRoutes.log(id)).body()

    suspend fun createPurchaseEntry(stageId: Long, date: String, body: EntryRequestDto): DailyEntryDto =
        client.post(ApiRoutes.stageLogPurchases(stageId, date)) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun createWorkEntry(stageId: Long, date: String, body: EntryRequestDto): DailyEntryDto =
        client.post(ApiRoutes.stageLogWorks(stageId, date)) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun updateEntry(id: Long, body: EntryRequestDto): DailyEntryDto =
        client.patch(ApiRoutes.entry(id)) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun deleteEntry(id: Long) {
        client.delete(ApiRoutes.entry(id))
    }
}
