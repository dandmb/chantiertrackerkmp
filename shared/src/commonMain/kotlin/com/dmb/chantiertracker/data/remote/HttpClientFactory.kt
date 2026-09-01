package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.local.TokenStorage
import com.dmb.chantiertracker.data.remote.dto.LoginResponseDto
import com.dmb.chantiertracker.data.remote.dto.RefreshTokenRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

val AppJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
    encodeDefaults = true
}

// Extension sur HttpClientConfig pour être partagée entre createHttpClient et les tests (MockEngine).
fun HttpClientConfig<*>.configureChantierTrackerClient(
    tokenStorage: TokenStorage,
    baseUrl: String,
    enableLogging: Boolean,
    onSessionExpired: suspend () -> Unit,
) {
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    expectSuccess = true

    install(ContentNegotiation) {
        json(AppJson)
    }

    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
    }

    if (enableLogging) {
        install(Logging) {
            level = LogLevel.ALL
        }
    }

    install(Auth) {
        bearer {
            // TokenStorage est la source de vérité : pas de cache mémoire, sinon un
            // token remplacé (login, reset de mot de passe) reste ignoré jusqu'au redémarrage.
            cacheTokens = false
            loadTokens {
                tokenStorage.get()?.let { BearerTokens(it.accessToken, it.refreshToken) }
            }
            refreshTokens {
                val currentRefresh = tokenStorage.get()?.refreshToken
                if (currentRefresh == null) {
                    onSessionExpired()
                    return@refreshTokens null
                }
                val response = client.post(ApiRoutes.AUTH_REFRESH_TOKEN) {
                    markAsRefreshTokenRequest()
                    expectSuccess = false
                    contentType(ContentType.Application.Json)
                    setBody(RefreshTokenRequestDto(currentRefresh))
                }
                if (response.status.isSuccess()) {
                    val fresh = response.body<LoginResponseDto>()
                    tokenStorage.save(AuthTokens(fresh.accessToken, fresh.refreshToken))
                    BearerTokens(fresh.accessToken, fresh.refreshToken)
                } else {
                    tokenStorage.clear()
                    onSessionExpired()
                    null
                }
            }
            sendWithoutRequest { true }
        }
    }

    defaultRequest {
        url(normalizedBaseUrl)
        header(HttpHeaders.Accept, ContentType.Application.Json.toString())
    }
}

fun createHttpClient(
    engine: HttpClientEngineFactory<*>,
    tokenStorage: TokenStorage,
    baseUrl: String,
    enableLogging: Boolean,
    onSessionExpired: suspend () -> Unit,
): HttpClient = HttpClient(engine) {
    configureChantierTrackerClient(tokenStorage, baseUrl, enableLogging, onSessionExpired)
}
