package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.remote.AdminApi
import com.dmb.chantiertracker.data.remote.InvitationApi
import com.dmb.chantiertracker.data.remote.ReportApi
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.support.FakeDailyEntryDao
import com.dmb.chantiertracker.support.FakeInvitationDao
import com.dmb.chantiertracker.support.FakeProjectDao
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.localDailyEntry
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.respondJson
import com.dmb.chantiertracker.support.respondProblem
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OnlineCreationsKeepTheirKeyTest {

    private enum class Answer { CUT, SERVER_ERROR, IN_PROGRESS, REFUSED, CREATED }

    private class Server(private val created: String) {
        var next = Answer.CREATED
        val storage = FakeTokenStorage(AuthTokens("a", "r"))
        val mock = RecordingMockClient(storage) {
            when (next) {
                Answer.CUT -> throw kotlinx.io.IOException("answer lost")
                Answer.SERVER_ERROR -> respondProblem(HttpStatusCode.InternalServerError, "Erreur.", code = "INTERNAL_ERROR")
                Answer.IN_PROGRESS -> respondProblem(HttpStatusCode.Conflict, "En cours.", code = "IDEMPOTENCY_KEY_IN_PROGRESS")
                Answer.REFUSED -> respondProblem(HttpStatusCode.Forbidden, "Accès refusé.", code = "PROJECT_INSUFFICIENT_ROLE")
                Answer.CREATED -> respondJson(created, HttpStatusCode.Created)
            }
        }
        val keys get() = mock.requests.filter { it.method.value == "POST" }.map { it.headers["Idempotency-Key"] }

        suspend fun send(answer: Answer, create: suspend () -> Unit) {
            next = answer
            try {
                create()
            } catch (e: DomainException) {
                Unit
            }
        }
    }

    private suspend fun aCreationKeepsItsKeyUntilItHasAnAnswer(server: Server, create: suspend () -> Unit, another: suspend () -> Unit) {
        server.send(Answer.CUT, create)
        server.send(Answer.SERVER_ERROR, create)
        server.send(Answer.IN_PROGRESS, create)
        server.send(Answer.CREATED, create)
        server.send(Answer.CREATED, create)
        server.send(Answer.REFUSED, create)
        server.send(Answer.CREATED, create)
        server.send(Answer.CREATED, another)

        val keys = server.keys
        keys.forEach { assertNotNull(it, "every creation carries a key: $keys") }
        keys.forEach { assertTrue(Regex("^[A-Za-z0-9._:-]{1,128}$").matches(it!!), "valid key: $it") }
        assertEquals(1, keys.subList(0, 4).toSet().size, "resent after a cut, a 5xx and an in-progress answer: the same key, $keys")
        assertNotEquals(keys[3], keys[4], "after a success, the same request again is a new creation")
        assertNotEquals(keys[5], keys[6], "after a refusal, the next attempt is a new creation")
        assertEquals(keys.size, keys.toSet().size + 3, "only the three resends share a key: $keys")
        assertTrue(keys[7] !in keys.subList(0, 7), "another request has its own key")
    }

    @Test
    fun an_invitation_keeps_its_key_until_it_has_an_answer() = runTest {
        val server = Server("""{"id":40,"projectId":5,"email":"sam@chantier.dev","role":"SUPERVISOR","status":"PENDING"}""")
        val projects = FakeProjectDao()
        projects.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        val invitations = InvitationRepositoryImpl(FakeInvitationDao(), InvitationApi(server.mock.client), projects, FakeSyncer())

        aCreationKeepsItsKeyUntilItHasAnAnswer(
            server,
            create = { invitations.invite("p5", "sam@chantier.dev") },
            another = { invitations.invite("p5", "lea@chantier.dev") },
        )
    }

    @Test
    fun a_report_keeps_its_key_until_it_has_an_answer() = runTest {
        val server = Server("""{"id":3,"entryId":900,"entryDate":"2026-09-05","message":"Erreur de quantité","createdAt":"2026-09-05T10:00:00","processed":false}""")
        val entries = FakeDailyEntryDao()
        entries.upsert(localDailyEntry("e900", serverId = 900, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        val reports = ReportRepositoryImpl(ReportApi(server.mock.client), entries, FakeProjectDao())

        aCreationKeepsItsKeyUntilItHasAnAnswer(
            server,
            create = { reports.createReport("e900", "Erreur de quantité") },
            another = { reports.createReport("e900", "Photo floue") },
        )
    }

    @Test
    fun an_account_created_by_the_admin_keeps_its_key_until_it_has_an_answer() = runTest {
        val server = Server(
            """{"id":12,"email":"sam@chantier.dev","name":"Sam","active":true,"globalRole":"USER","projectCount":0,"createdAt":"2026-09-05T10:00:00","plan":"FREE"}""",
        )
        val admin = AdminRepositoryImpl(AdminApi(server.mock.client))

        aCreationKeepsItsKeyUntilItHasAnAnswer(
            server,
            create = { admin.createUser("sam@chantier.dev", "Sam", "TempPass1234!", GlobalRole.USER) },
            another = { admin.createUser("lea@chantier.dev", "Léa", "TempPass1234!", GlobalRole.USER) },
        )
    }
}
