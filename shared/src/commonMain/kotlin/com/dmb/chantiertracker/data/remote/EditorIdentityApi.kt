package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.EditorIdentityDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class EditorIdentityApi(private val client: HttpClient) {

    // Public on the backend: the legal pages are read before any account exists.
    suspend fun get(): EditorIdentityDto = client.get(ApiRoutes.EDITOR_IDENTITY).body()
}
