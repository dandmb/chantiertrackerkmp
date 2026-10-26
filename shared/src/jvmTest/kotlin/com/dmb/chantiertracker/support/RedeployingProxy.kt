package com.dmb.chantiertracker.support

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Stands between a device and the real backend like the production reverse proxy. A request
 * that [redeployingFor] accepts gets the proxy's own answer during a redeploy: a 404 in plain
 * text, not the backend's problem format. Every other request is forwarded unchanged; one that
 * [loseTheAnswerFor] accepts reaches the backend, then the connection is cut before the answer.
 */
class RedeployingProxy(private val backendBaseUrl: String) : AutoCloseable {

    @Volatile var redeployingFor: (method: String, path: String) -> Boolean = { _, _ -> false }
    val answeredWhileRedeploying = mutableListOf<String>()
    @Volatile var loseTheAnswerFor: (method: String, path: String) -> Boolean = { _, _ -> false }

    data class Forwarded(val method: String, val path: String, val idempotencyKey: String?, val status: Int, val replayed: Boolean, val answerLost: Boolean)
    val forwarded = mutableListOf<Forwarded>()

    private val forwarder = HttpClient.newHttpClient()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            exchange.use {
                val path = exchange.requestURI.toString()
                if (redeployingFor(exchange.requestMethod, path)) {
                    synchronized(answeredWhileRedeploying) { answeredWhileRedeploying += "${exchange.requestMethod} $path" }
                    val body = "404 page not found".toByteArray()
                    exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
                    exchange.sendResponseHeaders(404, body.size.toLong())
                    exchange.responseBody.write(body)
                    return@use
                }
                val origin = URI(backendBaseUrl).let { "${it.scheme}://${it.authority}" }
                val request = HttpRequest.newBuilder(URI(origin + path))
                    .method(exchange.requestMethod, HttpRequest.BodyPublishers.ofByteArray(exchange.requestBody.readAllBytes()))
                exchange.requestHeaders.forEach { (name, values) ->
                    if (name.lowercase() !in RESTRICTED_HEADERS) values.forEach { request.header(name, it) }
                }
                val response = forwarder.send(request.build(), HttpResponse.BodyHandlers.ofByteArray())
                val lost = loseTheAnswerFor(exchange.requestMethod, path)
                synchronized(forwarded) {
                    forwarded += Forwarded(
                        exchange.requestMethod, path, exchange.requestHeaders.getFirst("Idempotency-Key"), response.statusCode(),
                        response.headers().firstValue("Idempotent-Replayed").orElse(null) == "true", lost,
                    )
                }
                if (lost) return@use
                response.headers().map().forEach { (name, values) ->
                    if (name.lowercase() !in RESTRICTED_HEADERS && name != ":status") values.forEach { exchange.responseHeaders.add(name, it) }
                }
                val body = response.body()
                exchange.sendResponseHeaders(response.statusCode(), if (body.isEmpty()) -1 else body.size.toLong())
                if (body.isNotEmpty()) exchange.responseBody.write(body)
            }
        }
        start()
    }

    val baseUrl: String = "http://127.0.0.1:${server.address.port}" + URI(backendBaseUrl).path

    override fun close() = server.stop(0)

    private companion object {
        val RESTRICTED_HEADERS = setOf("host", "content-length", "connection", "transfer-encoding", "upgrade", "expect", "keep-alive")
    }
}
