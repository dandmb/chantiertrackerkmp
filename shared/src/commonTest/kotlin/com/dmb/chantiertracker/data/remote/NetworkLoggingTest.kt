package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.respondJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkLoggingTest {

    @Test
    fun debug_logs_carry_neither_a_token_nor_a_request_or_response_body() = runTest {
        val logged = StringBuilder()
        val client = HttpClient(MockEngine { respondJson("""{"accessToken":"answered-token"}""") }) {
            configureChantierTrackerClient(
                tokenStorage = FakeTokenStorage(AuthTokens("stored-token", "refresh-token")),
                baseUrl = "https://test.local/api/v1",
                enableLogging = true,
                networkLogger = object : Logger {
                    override fun log(message: String) { logged.appendLine(message) }
                },
                onSessionExpired = {},
            )
        }

        client.post("auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"password":"Password1234!"}""")
        }

        assertTrue("auth/login" in logged)
        listOf("stored-token", "refresh-token", "answered-token", "Password1234!").forEach { secret ->
            assertFalse(secret in logged, secret)
        }
    }
}
