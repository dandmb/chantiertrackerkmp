package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.InvitationStatus
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.support.FakeInvitationDao
import com.dmb.chantiertracker.support.FakeProjectBackend
import com.dmb.chantiertracker.support.FakeProjectDao
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.localInvitation
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.respondJson
import com.dmb.chantiertracker.support.respondProblem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InvitationRepositoryImplTest {

    private fun repo(
        dao: FakeInvitationDao = FakeInvitationDao(),
        projectDao: FakeProjectDao = FakeProjectDao(),
        backend: FakeProjectBackend = FakeProjectBackend(),
        syncer: FakeSyncer = FakeSyncer(),
    ) = InvitationRepositoryImpl(dao, backend.invitationApi(), projectDao, syncer)

    @Test
    fun observe_invitations_maps_rows_newest_first() = runTest {
        val dao = FakeInvitationDao(
            listOf(
                localInvitation(1, projectLocalId = "p1", email = "a@x.dev", createdAt = "2026-09-01T10:00:00"),
                localInvitation(2, projectLocalId = "p1", email = "b@x.dev", createdAt = "2026-09-03T10:00:00"),
                localInvitation(3, projectLocalId = "other", email = "c@x.dev"),
            ),
        )

        val invitations = repo(dao).observeInvitations("p1").first()

        assertEquals(listOf("b@x.dev", "a@x.dev"), invitations.map { it.email }, "other projects are excluded, newest first")
    }

    @Test
    fun unknown_role_and_status_strings_map_to_unknown() = runTest {
        val dao = FakeInvitationDao(
            listOf(localInvitation(1, projectLocalId = "p1", role = "MYSTERY", status = "WEIRD")),
        )

        val invitation = repo(dao).observeInvitations("p1").first().single()

        assertEquals(ProjectRole.UNKNOWN, invitation.role)
        assertEquals(InvitationStatus.UNKNOWN, invitation.status)
    }

    @Test
    fun a_declined_invitation_maps_to_declined_not_unknown() = runTest {
        val dao = FakeInvitationDao(
            listOf(localInvitation(1, projectLocalId = "p1", role = "SUPERVISOR", status = "DECLINED")),
        )

        assertEquals(InvitationStatus.DECLINED, repo(dao).observeInvitations("p1").first().single().status)
    }

    @Test
    fun known_role_and_status_strings_map_through() = runTest {
        val dao = FakeInvitationDao(
            listOf(localInvitation(1, projectLocalId = "p1", role = "SUPERVISOR", status = "PENDING")),
        )

        val invitation = repo(dao).observeInvitations("p1").first().single()

        assertEquals(ProjectRole.SUPERVISOR, invitation.role)
        assertEquals(InvitationStatus.PENDING, invitation.status)
    }

    @Test
    fun invite_posts_a_supervisor_invitation_then_re_pulls_the_project() = runTest {
        val projectDao = FakeProjectDao(listOf(localProject("p1", serverId = 42)))
        val backend = FakeProjectBackend().apply { seed(com.dmb.chantiertracker.support.ServerProject(id = 42, name = "Villa")) }
        val syncer = FakeSyncer()

        repo(projectDao = projectDao, backend = backend, syncer = syncer).invite("p1", "sam@x.dev")

        val created = backend.invitations.single()
        assertEquals("sam@x.dev", created.email)
        assertEquals("SUPERVISOR", created.role)
        assertEquals(listOf("p1"), syncer.syncedProjects, "the project is re-pulled so the new pending invitation shows up")
    }

    @Test
    fun invite_on_a_never_synced_project_fails_without_a_network_call() = runTest {
        val projectDao = FakeProjectDao(listOf(localProject("p1", serverId = null)))
        val backend = FakeProjectBackend()

        assertFailsWith<DomainException.NotFound> {
            repo(projectDao = projectDao, backend = backend).invite("p1", "sam@x.dev")
        }
        assertTrue(backend.invitations.isEmpty())
    }

    @Test
    fun invite_surfaces_a_server_plan_refusal() = runTest {
        val projectDao = FakeProjectDao(listOf(localProject("p1", serverId = 42)))
        val backend = FakeProjectBackend().apply {
            seed(com.dmb.chantiertracker.support.ServerProject(id = 42, name = "Villa"))
            planLimitReached = true
        }

        assertFailsWith<DomainException.CollaboratorLimitReached> {
            repo(projectDao = projectDao, backend = backend).invite("p1", "sam@x.dev")
        }
    }

    @Test
    fun list_incoming_maps_the_bare_array_from_users_me_invitations() = runTest {
        val backend = FakeProjectBackend().apply {
            seedPendingForMe(com.dmb.chantiertracker.support.ServerPendingInvitation("t1", 9, "Villa Vidal", "SUPERVISOR", "Jean"))
            seedPendingForMe(com.dmb.chantiertracker.support.ServerPendingInvitation("t2", 10, "Chalet", "ADMIN", null))
        }

        val incoming = repo(backend = backend).listIncomingInvitations()

        assertEquals(listOf("Villa Vidal", "Chalet"), incoming.map { it.projectName })
        assertEquals(ProjectRole.ADMIN, incoming[1].role)
        assertEquals(null, incoming[1].invitedByName, "a deleted inviter comes back as null")
    }

    @Test
    fun get_invitation_preview_maps_the_public_token_lookup() = runTest {
        val backend = FakeProjectBackend().apply {
            seedPendingForMe(com.dmb.chantiertracker.support.ServerPendingInvitation("tok", 9, "Villa Vidal", "SUPERVISOR", "Jean"))
        }

        val preview = repo(backend = backend).getInvitationPreview("tok")

        assertEquals(9L, preview.projectId)
        assertEquals("Villa Vidal", preview.projectName)
        assertEquals(InvitationStatus.PENDING, preview.status)
        assertEquals(ProjectRole.SUPERVISOR, preview.role)
        assertEquals("Jean", preview.invitedByName)
    }

    @Test
    fun get_invitation_preview_reads_a_deleted_inviter_as_null_and_an_admin_role() = runTest {
        val backend = FakeProjectBackend().apply {
            seedPendingForMe(com.dmb.chantiertracker.support.ServerPendingInvitation("tok", 9, "Villa Vidal", "ADMIN", null))
        }

        val preview = repo(backend = backend).getInvitationPreview("tok")

        assertEquals(ProjectRole.ADMIN, preview.role)
        assertEquals(null, preview.invitedByName)
    }

    @Test
    fun accepting_an_invitation_meant_for_another_address_is_its_own_refusal() = runTest {
        val backend = FakeProjectBackend().apply {
            seedPendingForMe(com.dmb.chantiertracker.support.ServerPendingInvitation("tok", 9, "Villa"))
            acceptStatus = HttpStatusCode.Forbidden
            acceptRefusalCode = "INVITATION_EMAIL_MISMATCH"
        }

        assertFailsWith<DomainException.InvitationForAnotherAccount> { repo(backend = backend).acceptInvitation("tok") }
    }

    @Test
    fun get_invitation_preview_maps_an_unknown_token_to_not_found() = runTest {
        val backend = FakeProjectBackend()

        assertFailsWith<DomainException.NotFound> {
            repo(backend = backend).getInvitationPreview("gone")
        }
    }

    @Test
    fun accept_of_a_link_posts_to_the_token_endpoint() = runTest {
        val backend = FakeProjectBackend().apply {
            seedPendingForMe(com.dmb.chantiertracker.support.ServerPendingInvitation("tok", 9, "Villa"))
        }

        repo(backend = backend).acceptInvitation("tok")

        assertTrue(backend.myPendingInvitations.isEmpty(), "the server marks it accepted")
    }

    @Test
    fun accept_maps_a_stale_invitation_to_not_found() = runTest {
        val backend = FakeProjectBackend()

        assertFailsWith<DomainException.NotFound> {
            repo(backend = backend).acceptInvitation("gone")
        }
    }

    @Test
    fun accept_of_a_listed_invitation_posts_its_id_for_the_signed_in_user() = runTest {
        val backend = FakeProjectBackend().apply {
            seedPendingForMe(com.dmb.chantiertracker.support.ServerPendingInvitation("tok", 9, "Villa", id = 31))
        }
        val repo = repo(backend = backend)

        assertEquals(31, repo.listIncomingInvitations().single().id)
        repo.acceptIncomingInvitation(31)

        assertTrue(backend.myPendingInvitations.isEmpty(), "the server marks it accepted")
    }

    @Test
    fun a_listed_invitation_gone_meanwhile_is_told_as_such() = runTest {
        assertFailsWith<DomainException.InvitationGone> { repo(backend = FakeProjectBackend()).acceptIncomingInvitation(31) }
        assertFailsWith<DomainException.InvitationGone> { repo(backend = FakeProjectBackend()).declineIncomingInvitation(31) }
    }

    @Test
    fun accepting_a_link_of_a_full_project_tells_the_collaborator_limit() = runTest {
        val backend = FakeProjectBackend().apply {
            seedPendingForMe(com.dmb.chantiertracker.support.ServerPendingInvitation("tok", 9, "Villa"))
            acceptStatus = HttpStatusCode.Forbidden
            acceptRefusalCode = "PLAN_LIMIT_EXCEEDED"
        }

        assertFailsWith<DomainException.CollaboratorLimitReached> { repo(backend = backend).acceptInvitation("tok") }
    }

    @Test
    fun a_pending_invitation_is_cancelled_then_sent_again() = runTest {
        val projectDao = FakeProjectDao(listOf(localProject("p1", serverId = 42)))
        val dao = FakeInvitationDao(listOf(com.dmb.chantiertracker.data.local.db.InvitationEntity(7, "p1", "SAM@x.dev", "SUPERVISOR", null, null, null, "PENDING")))
        val backend = FakeProjectBackend().apply {
            seed(com.dmb.chantiertracker.support.ServerProject(id = 42, name = "Villa"))
            seedInvitation(com.dmb.chantiertracker.support.ServerInvitation(id = 7, projectId = 42, email = "SAM@x.dev"))
        }

        repo(dao = dao, projectDao = projectDao, backend = backend).replacePendingInvitation("p1", "sam@x.dev")

        assertEquals(listOf("sam@x.dev"), backend.invitations.map { it.email })
        assertTrue(backend.invitations.none { it.id == 7L })
    }

    @Test
    fun invite_twice_tells_that_an_invitation_is_already_pending() = runTest {
        val projectDao = FakeProjectDao(listOf(localProject("p1", serverId = 42)))
        val backend = FakeProjectBackend().apply {
            seed(com.dmb.chantiertracker.support.ServerProject(id = 42, name = "Villa"))
            inviteRefusal = HttpStatusCode.Conflict to "INVITATION_ALREADY_PENDING"
        }

        assertFailsWith<DomainException.InvitationAlreadyPending> { repo(projectDao = projectDao, backend = backend).invite("p1", "sam@x.dev") }
    }

    @Test
    fun decline_of_a_listed_invitation_posts_its_id_for_the_signed_in_user() = runTest {
        val backend = FakeProjectBackend().apply {
            seedPendingForMe(com.dmb.chantiertracker.support.ServerPendingInvitation("tok", 9, "Villa", id = 31))
        }

        repo(backend = backend).declineIncomingInvitation(31)

        assertTrue(backend.myPendingInvitations.isEmpty(), "the server marks it declined")
    }

    @Test
    fun cancel_deletes_server_side_then_re_pulls_the_project() = runTest {
        val backend = FakeProjectBackend().apply {
            seedInvitation(com.dmb.chantiertracker.support.ServerInvitation(id = 7, projectId = 42, email = "sam@x.dev"))
        }
        val syncer = FakeSyncer()

        repo(backend = backend, syncer = syncer).cancelInvitation("p1", 7)

        assertTrue(backend.invitations.isEmpty())
        assertEquals(listOf("p1"), syncer.syncedProjects)
    }

    private suspend fun projectWithASupervisor(): Triple<FakeProjectBackend, FakeProjectDao, FakeSyncer> {
        val backend = FakeProjectBackend().apply {
            seed(com.dmb.chantiertracker.support.ServerProject(id = 42, name = "Villa"))
            seedMembers(
                42,
                com.dmb.chantiertracker.support.ServerMember(userId = 1, name = "Dan", email = "dan@x.dev", role = "ADMIN"),
                com.dmb.chantiertracker.support.ServerMember(userId = 9, name = "Awa", email = "awa@x.dev", role = "SUPERVISOR"),
            )
        }
        val projectDao = FakeProjectDao(listOf(localProject("p1", serverId = 42)))
        projectDao.upsertMembers(
            listOf(
                com.dmb.chantiertracker.data.local.db.ProjectMemberEntity("p1", 1, "Dan", "dan@x.dev", "ADMIN"),
                com.dmb.chantiertracker.data.local.db.ProjectMemberEntity("p1", 9, "Awa", "awa@x.dev", "SUPERVISOR"),
            ),
        )
        return Triple(backend, projectDao, FakeSyncer())
    }

    @Test
    fun removing_a_member_deletes_by_user_id_with_no_body_and_drops_the_local_row() = runTest {
        val (backend, projectDao, syncer) = projectWithASupervisor()

        repo(projectDao = projectDao, backend = backend, syncer = syncer).removeMember("p1", userId = 9)

        assertEquals(listOf("DELETE /projects/42/members/9"), backend.receivedMethods.filter { it.startsWith("DELETE") })
        assertEquals(listOf<String?>(null), backend.receivedMemberRemovalBodies, "no body")
        assertEquals(listOf(1L), projectDao.observeMembers("p1").first().map { it.userId })
        assertEquals(listOf("p1"), syncer.syncedProjects)
    }

    @Test
    fun a_member_already_removed_is_a_success_not_an_error() = runTest {
        val (backend, projectDao, syncer) = projectWithASupervisor()
        backend.members[42]!!.removeAll { it.userId == 9L }

        repo(projectDao = projectDao, backend = backend, syncer = syncer).removeMember("p1", userId = 9)

        assertEquals(listOf(1L), projectDao.observeMembers("p1").first().map { it.userId })
    }

    @Test
    fun a_supervisor_trying_to_remove_someone_is_refused() = runTest {
        val (backend, projectDao, syncer) = projectWithASupervisor()
        backend.refuseWith = { com.dmb.chantiertracker.support.ServerRefusal(io.ktor.http.HttpStatusCode.Forbidden, "PROJECT_INSUFFICIENT_ROLE") }

        assertFailsWith<DomainException.Forbidden> { repo(projectDao = projectDao, backend = backend, syncer = syncer).removeMember("p1", userId = 9) }
        assertEquals(listOf(1L, 9L), projectDao.observeMembers("p1").first().map { it.userId }.sorted())
    }

    @Test
    fun the_last_admin_cannot_be_removed() = runTest {
        val (backend, projectDao, syncer) = projectWithASupervisor()
        backend.refuseWith = { com.dmb.chantiertracker.support.ServerRefusal(io.ktor.http.HttpStatusCode.Conflict, "LAST_ADMIN_REMOVAL") }

        assertFailsWith<DomainException.LastAdminRemoval> { repo(projectDao = projectDao, backend = backend, syncer = syncer).removeMember("p1", userId = 1) }
        assertEquals(listOf(1L, 9L), projectDao.observeMembers("p1").first().map { it.userId }.sorted())
    }

    private fun sessionRepo(mock: com.dmb.chantiertracker.support.RecordingMockClient) =
        InvitationRepositoryImpl(FakeInvitationDao(), com.dmb.chantiertracker.data.remote.InvitationApi(mock.client), FakeProjectDao(), FakeSyncer())

    @Test
    fun an_expired_session_on_accept_is_renewed_then_the_accept_is_sent_once_more() = runTest {
        val storage = com.dmb.chantiertracker.support.FakeTokenStorage(com.dmb.chantiertracker.data.local.AuthTokens("perime", "refresh-1"))
        val mock = com.dmb.chantiertracker.support.RecordingMockClient(storage) { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/refresh-token" -> respondJson("""{"accessToken":"neuf","refreshToken":"refresh-2"}""")
                "/api/v1/invitations/tok/accept" ->
                    if (request.headers[io.ktor.http.HttpHeaders.Authorization] == "Bearer neuf") respondJson("""{"message":"ok"}""")
                    else respondProblem(HttpStatusCode.Unauthorized, "Authentification requise.", code = "AUTHENTICATION_REQUIRED")
                else -> error("unexpected ${request.url}")
            }
        }

        sessionRepo(mock).acceptInvitation("tok")

        assertEquals(
            listOf("/api/v1/invitations/tok/accept", "/api/v1/auth/refresh-token", "/api/v1/invitations/tok/accept"),
            mock.requests.map { it.url.encodedPath },
        )
        assertEquals("neuf", storage.tokens?.accessToken)
    }

    @Test
    fun a_refused_renewal_on_decline_ends_the_session_without_a_second_try() = runTest {
        val storage = com.dmb.chantiertracker.support.FakeTokenStorage(com.dmb.chantiertracker.data.local.AuthTokens("perime", "revoque"))
        var expired = 0
        val mock = com.dmb.chantiertracker.support.RecordingMockClient(storage, onSessionExpired = { expired++ }) { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/refresh-token" -> respondProblem(HttpStatusCode.Unauthorized, "Jeton invalide.", code = "INVALID_REFRESH_TOKEN")
                else -> respondProblem(HttpStatusCode.Unauthorized, "Authentification requise.", code = "AUTHENTICATION_REQUIRED")
            }
        }

        assertFailsWith<DomainException> { sessionRepo(mock).declineInvitation("tok") }

        assertEquals(1, mock.requests.count { it.url.encodedPath == "/api/v1/invitations/tok/decline" })
        assertEquals(null, storage.tokens)
        assertEquals(1, expired)
    }
}
