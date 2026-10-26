package com.dmb.chantiertracker.data.remote

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter

fun HttpRequestBuilder.sortedByIdPage(page: Int, size: Int) {
    parameter("page", page)
    parameter("size", size)
    parameter("sort", "id,asc")
}
