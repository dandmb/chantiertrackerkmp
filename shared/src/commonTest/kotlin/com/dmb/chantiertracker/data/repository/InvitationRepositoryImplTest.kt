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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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

        assertFailsWith<DomainException.PlanLimitReached> {
            repo(projectDao = projectDao, backend = backend).invite("p1", "sam@x.dev")
        }
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
}
