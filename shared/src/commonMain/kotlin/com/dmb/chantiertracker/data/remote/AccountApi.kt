package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.AccountDeletionPreviewDto
import com.dmb.chantiertracker.data.remote.dto.DeleteAccountRequestDto
import com.dmb.chantiertracker.data.remote.dto.PlanUsageDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class AccountApi(private val client: HttpClient) {

    suspend fun planUsage(): PlanUsageDto =
        client.get(ApiRoutes.USERS_ME_PLAN_USAGE).body()

    suspend fun deletionPreview(): AccountDeletionPreviewDto =
        client.get(ApiRoutes.USERS_ME_DELETION_PREVIEW).body()

    // DELETE with a body: the password stays out of the URL (and of access logs).
    suspend fun deleteMe(password: String) {
        client.delete(ApiRoutes.USERS_ME) {
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequestDto(password))
        }
    }
}
