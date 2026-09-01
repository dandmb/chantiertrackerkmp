package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.TokenStorage
import com.dmb.chantiertracker.data.remote.configureChantierTrackerClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

const val TEST_BASE_URL = "https://api.chantiertracker.test/api/v1"

fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(
    content = body,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

fun MockRequestHandleScope.respondProblem(
    status: HttpStatusCode,
    detail: String,
    errors: Map<String, String>? = null,
): HttpResponseData {
    val errorsJson = errors?.entries?.joinToString(
        separator = ",",
        prefix = ""","errors":{""",
        postfix = "}",
    ) { """"${it.key}":"${it.value}"""" } ?: ""
    return respond(
        content = """{"status":${status.value},"detail":"$detail"$errorsJson}""",
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/problem+json"),
    )
}

class RecordingMockClient(
    tokenStorage: TokenStorage,
    onSessionExpired: suspend () -> Unit = {},
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val requests = mutableListOf<HttpRequestData>()

    val client: HttpClient = HttpClient(
        MockEngine { request ->
            requests += request
            handler(request)
        },
    ) {
        configureChantierTrackerClient(
            tokenStorage = tokenStorage,
            baseUrl = TEST_BASE_URL,
            enableLogging = false,
            onSessionExpired = onSessionExpired,
        )
    }
}
