package com.dmb.chantiertracker.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Serializable
private data class Ping(val message: String)

class HttpClientFactoryTest {

    private val baseUrl = "https://api.chantiertracker.com/api/v1"

    private fun client(
        tokenProvider: AuthTokenProvider = NoAuthTokenProvider(),
        capture: (io.ktor.client.request.HttpRequestData) -> Unit = {},
    ): HttpClient {
        val engine = MockEngine { request ->
            capture(request)
            respond(
                content = """{"message":"pong"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return HttpClient(engine) {
            configureChantierTrackerClient(
                tokenProvider = tokenProvider,
                baseUrl = baseUrl,
                enableLogging = false,
            )
        }
    }

    @Test
    fun resolves_relative_path_against_base_url() = runTest {
        var url: String? = null
        val c = client(capture = { url = it.url.toString() })
        c.get("ping")
        assertEquals("$baseUrl/ping", url)
    }

    @Test
    fun sends_json_accept_header() = runTest {
        var accept: String? = null
        val c = client(capture = { accept = it.headers[HttpHeaders.Accept] })
        c.get("ping")
        assertEquals("application/json", accept)
    }

    @Test
    fun deserializes_json_body() = runTest {
        val c = client()
        val ping: Ping = c.get("ping").body()
        assertEquals("pong", ping.message)
    }

    @Test
    fun attaches_bearer_token_when_provider_has_one() = runTest {
        var auth: String? = null
        val provider = object : AuthTokenProvider {
            override suspend fun currentAccessToken() = "abc123"
            override suspend fun currentRefreshToken() = "refresh123"
            override suspend fun refresh() = "abc123"
        }
        val c = client(tokenProvider = provider, capture = { auth = it.headers[HttpHeaders.Authorization] })
        c.get("ping")
        assertEquals("Bearer abc123", auth)
    }

    @Test
    fun no_authorization_header_without_token() = runTest {
        var auth: String? = "sentinel"
        val c = client(capture = { auth = it.headers[HttpHeaders.Authorization] })
        c.get("ping")
        assertNull(auth)
    }
}
