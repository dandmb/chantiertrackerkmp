package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.TokenStorage
import com.dmb.chantiertracker.data.remote.configureChantierTrackerClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher

const val TEST_BASE_URL = "https://api.chantiertracker.test/api/v1"

fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(
    content = body,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

fun MockRequestHandleScope.respondBytes(
    bytes: ByteArray,
    contentType: String = "application/pdf",
    contentDisposition: String? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData {
    val headers = buildList {
        add(HttpHeaders.ContentType to contentType)
        if (contentDisposition != null) add(HttpHeaders.ContentDisposition to contentDisposition)
    }
    return respond(
        content = bytes,
        status = status,
        headers = io.ktor.http.headers { headers.forEach { (k, v) -> append(k, v) } },
    )
}

fun MockRequestHandleScope.respondProblem(
    status: HttpStatusCode,
    detail: String,
    errors: Map<String, String>? = null,
    extraHeaders: Map<String, String> = emptyMap(),
    code: String? = null,
): HttpResponseData {
    val errorsJson = errors?.entries?.joinToString(
        separator = ",",
        prefix = ""","errors":{""",
        postfix = "}",
    ) { """"${it.key}":"${it.value}"""" } ?: ""
    val codeJson = code?.let { ""","code":"$it"""" } ?: ""
    return respond(
        content = """{"status":${status.value},"detail":"$detail"$codeJson$errorsJson}""",
        status = status,
        headers = io.ktor.http.headers {
            append(HttpHeaders.ContentType, "application/problem+json")
            extraHeaders.forEach { (name, value) -> append(name, value) }
        },
    )
}

enum class RedeployAnswer { PROXY_TEXT, EMPTY, JSON_WITHOUT_CODE, PROBLEM_WITHOUT_CODE }

fun MockRequestHandleScope.respondWhileRedeploying(answer: RedeployAnswer = RedeployAnswer.PROXY_TEXT): HttpResponseData = when (answer) {
    RedeployAnswer.PROXY_TEXT -> respond("404 page not found", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "text/plain; charset=utf-8"))
    RedeployAnswer.EMPTY -> respond("", HttpStatusCode.NotFound)
    RedeployAnswer.JSON_WITHOUT_CODE -> respondJson("""{"timestamp":"2026-10-23T10:00:00Z","status":404,"error":"Not Found","path":"/api/v1/projects"}""", HttpStatusCode.NotFound)
    RedeployAnswer.PROBLEM_WITHOUT_CODE -> respondProblem(HttpStatusCode.NotFound, "Not Found")
}

class RecordingMockClient(
    tokenStorage: TokenStorage,
    onSessionExpired: suspend () -> Unit = {},
    dispatcher: CoroutineDispatcher? = null,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val requests = mutableListOf<HttpRequestData>()

    val client: HttpClient = HttpClient(
        MockEngine(
            MockEngineConfig().apply {
                this.dispatcher = dispatcher
                addHandler { request ->
                    requests += request
                    handler(request)
                }
            },
        ),
    ) {
        configureChantierTrackerClient(
            tokenStorage = tokenStorage,
            baseUrl = TEST_BASE_URL,
            enableLogging = false,
            onSessionExpired = onSessionExpired,
        )
    }
}
