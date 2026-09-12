package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.AdminUserPageDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class AdminApi(private val client: HttpClient) {

    // GET /admin/users — SUPER_ADMIN only server-side (403 otherwise, but the
    // UI never reaches this call for anyone else — see the navigation gate).
    // Raw Spring `Pageable` (`sort=field,direction`), unlike the bespoke
    // sort/order pair History/Reports use — the web never sends `sort` at
    // all (no sort control there), but leaving pagination unsorted isn't
    // guaranteed stable across pages, so a default is always sent here.
    suspend fun listUsers(page: Int, size: Int): AdminUserPageDto =
        client.get(ApiRoutes.ADMIN_USERS) {
            parameter("page", page)
            parameter("size", size)
            parameter("sort", "createdAt,desc")
        }.body()
}
