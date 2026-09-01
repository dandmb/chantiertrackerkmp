package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.core.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
    tokenProvider: AuthTokenProvider = NoAuthTokenProvider(),
    baseUrl: String = AppConfig.baseUrl,
    enableLogging: Boolean = AppConfig.enableNetworkLogging,
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
            loadTokens {
                tokenProvider.currentAccessToken()?.let { access ->
                    BearerTokens(access, tokenProvider.currentRefreshToken())
                }
            }
            refreshTokens {
                tokenProvider.refresh()?.let { access ->
                    BearerTokens(access, tokenProvider.currentRefreshToken())
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
    engine: HttpClientEngineFactory<*> = httpClientEngine(),
    tokenProvider: AuthTokenProvider = NoAuthTokenProvider(),
    baseUrl: String = AppConfig.baseUrl,
    enableLogging: Boolean = AppConfig.enableNetworkLogging,
): HttpClient = HttpClient(engine) {
    configureChantierTrackerClient(tokenProvider, baseUrl, enableLogging)
}
