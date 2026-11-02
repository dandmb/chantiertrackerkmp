package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.LegalSettingsDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class LegalSettingsApi(private val client: HttpClient) {

    suspend fun get(): LegalSettingsDto = client.get(ApiRoutes.LEGAL_SETTINGS).body()
}
