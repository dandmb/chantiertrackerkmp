package com.dmb.chantiertracker.data.remote

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header

private val VALID_IDEMPOTENCY_KEY = Regex("^[A-Za-z0-9._:-]{1,128}$")

fun idempotencyKeyFor(localId: String): String? = localId.takeIf { VALID_IDEMPOTENCY_KEY.matches(it) }

fun HttpRequestBuilder.idempotencyKey(key: String?) {
    if (key != null) header("Idempotency-Key", key)
}
