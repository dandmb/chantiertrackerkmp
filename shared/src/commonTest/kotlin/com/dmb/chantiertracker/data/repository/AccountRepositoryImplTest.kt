package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.remote.AccountApi
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.respondJson
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountRepositoryImplTest {

    private fun repo(planJson: String): AccountRepositoryImpl {
        val client = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) {
            assertEquals("/api/v1/users/me/plan-usage", it.url.encodedPath)
            respondJson(planJson)
        }
        return AccountRepositoryImpl(AccountApi(client.client))
    }

    @Test
    fun maps_known_plans() = runTest {
        assertEquals(Plan.FREE, repo("""{"plan":"FREE"}""").getCurrentPlan())
        assertEquals(Plan.SEMI_FLEX, repo("""{"plan":"SEMI_FLEX"}""").getCurrentPlan())
        assertEquals(Plan.LIBERTE, repo("""{"plan":"LIBERTE"}""").getCurrentPlan())
    }

    @Test
    fun unknown_plan_falls_back() = runTest {
        assertEquals(Plan.UNKNOWN, repo("""{"plan":"ENTERPRISE"}""").getCurrentPlan())
    }
}
