package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.LegalDocumentDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class LegalDocumentsApi(private val client: HttpClient) {

    suspend fun get(document: String, language: String): LegalDocumentDto =
        client.get(ApiRoutes.legalDocument(document, language)).body()
}
