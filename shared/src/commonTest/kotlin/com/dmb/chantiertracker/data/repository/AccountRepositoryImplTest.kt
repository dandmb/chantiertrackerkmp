package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.local.db.PlanUsageEntity
import com.dmb.chantiertracker.data.remote.AccountApi
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.support.FakePlanUsageDao
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.MutableClock
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.respondJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountRepositoryImplTest {

    private fun repo(
        dao: FakePlanUsageDao = FakePlanUsageDao(),
        clock: MutableClock = MutableClock(7_000L),
        respond: () -> String,
    ): AccountRepositoryImpl {
        val client = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) {
            assertEquals("/api/v1/users/me/plan-usage", it.url.encodedPath)
            respondJson(respond())
        }
        return AccountRepositoryImpl(AccountApi(client.client), dao, clock)
    }

    @Test
    fun refresh_stores_plan_and_limit_and_observe_maps_them() = runTest {
        val dao = FakePlanUsageDao()
        val r = repo(dao) { """{"plan":"FREE","projectsLimit":1}""" }

        assertNull(r.observePlanUsage().first())

        r.refreshPlanUsage()

        val usage = r.observePlanUsage().first()!!
        assertEquals(Plan.FREE, usage.plan)
        assertEquals(1, usage.projectsLimit)
    }

    @Test
    fun a_null_limit_means_unlimited() = runTest {
        val r = repo { """{"plan":"LIBERTE","projectsLimit":null}""" }
        r.refreshPlanUsage()
        assertEquals(null, r.observePlanUsage().first()!!.projectsLimit)
        assertEquals(Plan.LIBERTE, r.observePlanUsage().first()!!.plan)
    }

    @Test
    fun an_unknown_plan_falls_back() = runTest {
        val r = repo { """{"plan":"ENTERPRISE","projectsLimit":99}""" }
        r.refreshPlanUsage()
        assertEquals(Plan.UNKNOWN, r.observePlanUsage().first()!!.plan)
    }

    @Test
    fun refresh_never_throws_and_keeps_the_last_known_value_on_failure() = runTest {
        val dao = FakePlanUsageDao(PlanUsageEntity(0, "FREE", 1, 1_000L))
        val client = RecordingMockClient(FakeTokenStorage(AuthTokens("a", "r"))) {
            respondJson("""{"status":500}""", HttpStatusCode.InternalServerError)
        }
        val r = AccountRepositoryImpl(AccountApi(client.client), dao, MutableClock(1L))

        r.refreshPlanUsage() // must not throw

        val usage = r.observePlanUsage().first()!!
        assertEquals(Plan.FREE, usage.plan)
        assertEquals(1, usage.projectsLimit)
    }
}
