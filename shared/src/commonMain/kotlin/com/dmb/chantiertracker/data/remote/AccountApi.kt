package com.dmb.chantiertracker.data.remote

import com.dmb.chantiertracker.data.remote.dto.PlanUsageDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class AccountApi(private val client: HttpClient) {

    suspend fun planUsage(): PlanUsageDto =
        client.get(ApiRoutes.USERS_ME_PLAN_USAGE).body()
}
