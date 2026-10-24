package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.AuthStateHolder
import com.dmb.chantiertracker.data.local.AuthTokens
import com.dmb.chantiertracker.data.remote.AccountApi
import com.dmb.chantiertracker.data.session.LocalDataOwnerStore
import com.dmb.chantiertracker.data.session.SessionNotice
import com.dmb.chantiertracker.data.session.SessionNotices
import com.dmb.chantiertracker.domain.model.AccountDeletionPreview
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DeletionBlocker
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.MemberLosingAccess
import com.dmb.chantiertracker.domain.model.OwnedProjectToDelete
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.model.ProjectToLeave
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.support.FakeAppPreferences
import com.dmb.chantiertracker.support.FakeLocalDataEraser
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.FakeTokenStorage
import com.dmb.chantiertracker.support.RecordingMockClient
import com.dmb.chantiertracker.support.respondJson
import com.dmb.chantiertracker.support.respondProblem
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountDeletionRepositoryImplTest {

    private class Fixture(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        val tokens = FakeTokenStorage(AuthTokens("access", "refresh"))
        val holder = AuthStateHolder().apply { update(AuthState.Authenticated(User(7, "sam@chantier.dev", "Sam", true, GlobalRole.USER))) }
        val syncer = FakeSyncer()
        var erasedInsideTheLock: Boolean? = null
        val eraser = FakeLocalDataEraser { erasedInsideTheLock = syncer.inExclusive }
        val preferences = FakeAppPreferences()
        val owner = LocalDataOwnerStore(preferences).apply { record(User(7, "sam@chantier.dev", "Sam", true, GlobalRole.USER)) }
        val notices = SessionNotices()
        val mock = RecordingMockClient(tokens, handler = handler)
        val repo = AccountDeletionRepositoryImpl(AccountApi(mock.client), tokens, holder, syncer, eraser, owner, notices)

        fun nothingWasErased() {
            assertEquals(0, eraser.eraseCount, "nothing erased")
            assertEquals(AuthTokens("access", "refresh"), tokens.tokens, "session kept")
            assertTrue(holder.state.value is AuthState.Authenticated, "still signed in")
            assertNotNull(owner.ownerId(), "owner kept")
            assertNull(notices.notice.value)
        }
    }

    private val previewJson = """
        {"canDelete":true,"blockers":[],"founder":true,
         "ownedProjects":[{"id":5,"name":"Villa","otherMembers":[{"name":"Yann Leroy","role":"SUPERVISOR"},{"name":"Léa Martin","role":"ADMIN"}]},
                          {"id":6,"name":"Hangar","otherMembers":[]}],
         "memberships":[{"projectId":9,"projectName":"Toiture Marchand","role":"SUPERVISOR"}],
         "pendingInvitations":2}
    """.trimIndent()

    @Test
    fun the_preview_lists_what_disappears_and_who_loses_access() = runTest {
        val f = Fixture { respondJson(previewJson) }

        val preview = f.repo.preview()

        assertEquals(
            AccountDeletionPreview(
                canDelete = true, blockers = emptyList(), founder = true,
                ownedProjects = listOf(
                    OwnedProjectToDelete("Villa", listOf(MemberLosingAccess("Yann Leroy", ProjectRole.SUPERVISOR), MemberLosingAccess("Léa Martin", ProjectRole.ADMIN))),
                    OwnedProjectToDelete("Hangar", emptyList()),
                ),
                memberships = listOf(ProjectToLeave("Toiture Marchand", ProjectRole.SUPERVISOR)),
                pendingInvitations = 2,
            ),
            preview,
        )
        assertEquals("/api/v1/users/me/deletion-preview", f.mock.requests.single().url.encodedPath)
    }

    @Test
    fun the_preview_names_its_blockers() = runTest {
        val f = Fixture { respondJson("""{"canDelete":false,"blockers":["LAST_SUPER_ADMIN","SUBSCRIPTION_STILL_RENEWING"],"founder":false,"ownedProjects":[],"memberships":[],"pendingInvitations":0}""") }

        val preview = f.repo.preview()

        assertEquals(false, preview.canDelete)
        assertEquals(listOf(DeletionBlocker.LAST_SUPER_ADMIN, DeletionBlocker.SUBSCRIPTION_STILL_RENEWING), preview.blockers)
    }

    @Test
    fun after_the_204_the_session_the_local_data_and_the_owner_are_gone_and_the_sign_in_screen_says_so() = runTest {
        val f = Fixture { respond("", HttpStatusCode.NoContent) }

        f.repo.deleteAccount("MonMotDePasse1!")

        val request = f.mock.requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/api/v1/users/me", request.url.encodedPath)
        assertEquals("""{"password":"MonMotDePasse1!"}""", request.body.toByteArray().decodeToString(), "the password goes in the body, never in the URL")
        assertTrue("MonMotDePasse" !in request.url.toString(), "not in the URL")
        assertNull(f.tokens.tokens, "tokens erased")
        assertEquals(1, f.eraser.eraseCount, "Room, files and exports erased")
        assertEquals(true, f.erasedInsideTheLock, "erased while no sync pass can run (the sync queue goes with Room)")
        assertNull(f.owner.ownerId(), "the account's name and e-mail are forgotten on the device")
        assertEquals(AuthState.Unauthenticated, f.holder.state.value)
        assertEquals(SessionNotice.AccountDeleted, f.notices.notice.value)
    }

    @Test
    fun a_wrong_password_is_said_and_nothing_is_erased() = runTest {
        val f = Fixture { respondProblem(HttpStatusCode.BadRequest, "Mot de passe actuel incorrect.", code = "INVALID_CURRENT_PASSWORD") }

        assertFailsWith<DomainException.InvalidCurrentPassword> { f.repo.deleteAccount("faux") }

        f.nothingWasErased()
    }

    @Test
    fun the_last_super_admin_is_refused_with_its_reason_and_nothing_is_erased() = runTest {
        val f = Fixture { respondProblem(HttpStatusCode.Conflict, "Dernier super-admin.", code = "LAST_SUPER_ADMIN") }

        val failure = assertFailsWith<DomainException.AccountDeletionBlocked> { f.repo.deleteAccount("MonMotDePasse1!") }

        assertEquals(DeletionBlocker.LAST_SUPER_ADMIN, failure.blocker)
        f.nothingWasErased()
    }

    @Test
    fun a_renewing_subscription_is_refused_with_its_reason_and_nothing_is_erased() = runTest {
        val f = Fixture { respondProblem(HttpStatusCode.Conflict, "Abonnement actif.", code = "SUBSCRIPTION_STILL_RENEWING") }

        val failure = assertFailsWith<DomainException.AccountDeletionBlocked> { f.repo.deleteAccount("MonMotDePasse1!") }

        assertEquals(DeletionBlocker.SUBSCRIPTION_STILL_RENEWING, failure.blocker)
        f.nothingWasErased()
    }

    @Test
    fun too_many_attempts_give_the_wait_and_nothing_is_erased() = runTest {
        val f = Fixture {
            respondProblem(HttpStatusCode.TooManyRequests, "Trop de requêtes.", code = "RATE_LIMITED", extraHeaders = mapOf(HttpHeaders.RetryAfter to "540"))
        }

        assertEquals(DomainException.RateLimited(540), assertFailsWith<DomainException.RateLimited> { f.repo.deleteAccount("MonMotDePasse1!") })

        f.nothingWasErased()
    }

    @Test
    fun a_cut_while_deleting_erases_nothing() = runTest {
        val f = Fixture { throw kotlinx.io.IOException("cut") }

        assertFailsWith<DomainException.Network> { f.repo.deleteAccount("MonMotDePasse1!") }

        f.nothingWasErased()
    }
}
