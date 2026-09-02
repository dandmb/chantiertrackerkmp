package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.ProjectMemberEntity
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.UpdateProjectInput
import com.dmb.chantiertracker.support.FakeProjectDao
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.MutableClock
import com.dmb.chantiertracker.support.localProject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProjectRepositoryImplTest {

    private fun repo(
        dao: FakeProjectDao = FakeProjectDao(),
        syncer: FakeSyncer = FakeSyncer(),
        clock: MutableClock = MutableClock(2_000L),
    ) = ProjectRepositoryImpl(dao, syncer, AppCoroutineScope(), clock, newLocalId = { "fixed-local-id" })

    @Test
    fun observe_projects_maps_stored_rows_to_the_domain_model() = runTest {
        val dao = FakeProjectDao(
            listOf(
                localProject("a", name = "Alpha", pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
                localProject("b", name = "Beta", pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
            ),
        )

        val projects = repo(dao).observeProjects().first()

        assertEquals(listOf("Alpha"), projects.map { it.name }, "rows pending deletion are hidden")
        assertEquals("a", projects.single().localId)
        assertEquals(ProjectStatus.IN_PROGRESS, projects.single().status)
    }

    @Test
    fun observe_project_maps_a_single_row_to_the_detail_model() = runTest {
        val dao = FakeProjectDao(listOf(localProject("a", name = "Alpha")))

        val detail = repo(dao).observeProject("a").first()

        assertEquals("Alpha", detail?.name)
        assertEquals("EUR", detail?.currency)
        assertEquals("Europe/Paris", detail?.timezone)
        assertNull(repo(FakeProjectDao()).observeProject("missing").first())
    }

    @Test
    fun observe_members_maps_roles() = runTest {
        val dao = FakeProjectDao()
        dao.upsertMembers(
            listOf(
                ProjectMemberEntity("a", 1L, "Alice", "a@x.dev", "ADMIN"),
                ProjectMemberEntity("a", 2L, "Bob", "b@x.dev", "SUPERVISOR"),
            ),
        )

        val members = repo(dao).observeMembers("a").first()

        assertEquals(ProjectRole.ADMIN, members[0].role)
        assertEquals(ProjectRole.SUPERVISOR, members[1].role)
    }

    @Test
    fun create_project_writes_a_pending_create_row_and_nudges_the_syncer() = runTest {
        val dao = FakeProjectDao()
        val syncer = FakeSyncer()
        val clock = MutableClock(4_242L)

        val localId = repo(dao, syncer, clock).createProject(
            CreateProjectInput(name = "Villa", description = "  ", location = null, currency = null, timezone = "Europe/Paris"),
        )

        assertEquals("fixed-local-id", localId)
        val row = dao.findByLocalId(localId)!!
        assertEquals("Villa", row.name)
        assertNull(row.description, "blank optional fields are stored as null")
        assertEquals("USD", row.currency, "no currency → USD locally, same rule as the backend")
        assertNull(row.serverId)
        assertEquals(SyncStatus.PENDING, row.syncStatus)
        assertEquals(PendingOp.CREATE, row.pendingOp)
        assertEquals(4_242L, row.locallyModifiedAt)
        assertEquals(1, syncer.requestCount)
    }

    @Test
    fun refresh_delegates_to_a_full_sync_pass() = runTest {
        val syncer = FakeSyncer()
        repo(syncer = syncer).refresh()
        assertEquals(1, syncer.syncCount)
    }

    private fun update(name: String = "Villa rénovée") = UpdateProjectInput(
        name = name,
        description = "  ",
        location = null,
        currency = "",
        timezone = "Africa/Douala",
        status = ProjectStatus.SUSPENDED,
    )

    @Test
    fun update_project_writes_a_pending_update_row_and_nudges_the_syncer() = runTest {
        val dao = FakeProjectDao(
            listOf(localProject("p1", serverId = 9, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED)),
        )
        val syncer = FakeSyncer()

        repo(dao, syncer, MutableClock(5_555L)).updateProject("p1", update(name = "Villa rénovée"))

        val row = dao.findByLocalId("p1")!!
        assertEquals("Villa rénovée", row.name)
        assertNull(row.description, "blank optional fields collapse to null")
        assertEquals("EUR", row.currency, "a blank currency keeps the stored one")
        assertEquals("Africa/Douala", row.timezone)
        assertEquals("SUSPENDED", row.status)
        assertEquals(SyncStatus.PENDING, row.syncStatus)
        assertEquals(PendingOp.UPDATE, row.pendingOp)
        assertEquals(5_555L, row.locallyModifiedAt)
        assertEquals(9L, row.serverId, "the server id is preserved")
        assertEquals(1, syncer.requestCount)
    }

    @Test
    fun update_project_on_a_row_that_never_synced_keeps_it_a_create() = runTest {
        val dao = FakeProjectDao(listOf(localProject("p1", serverId = null, pendingOp = PendingOp.CREATE)))

        repo(dao).updateProject("p1", update())

        assertEquals(PendingOp.CREATE, dao.findByLocalId("p1")!!.pendingOp)
    }

    @Test
    fun update_project_ignores_an_unknown_local_id() = runTest {
        val dao = FakeProjectDao()
        val syncer = FakeSyncer()

        repo(dao, syncer).updateProject("missing", update())

        assertNull(dao.findByLocalId("missing"))
        assertEquals(0, syncer.requestCount)
    }

    @Test
    fun delete_project_that_reached_the_server_is_marked_pending_delete() = runTest {
        val dao = FakeProjectDao(
            listOf(localProject("p1", serverId = 9, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED)),
        )
        val syncer = FakeSyncer()

        repo(dao, syncer, MutableClock(6_000L)).deleteProject("p1")

        val row = dao.findByLocalId("p1")!!
        assertEquals(PendingOp.DELETE, row.pendingOp)
        assertEquals(SyncStatus.PENDING, row.syncStatus)
        assertEquals(6_000L, row.locallyModifiedAt)
        assertEquals(1, syncer.requestCount)
    }

    @Test
    fun delete_project_that_never_reached_the_server_is_dropped_immediately() = runTest {
        val dao = FakeProjectDao(listOf(localProject("p1", serverId = null, pendingOp = PendingOp.CREATE)))
        val syncer = FakeSyncer()

        repo(dao, syncer).deleteProject("p1")

        assertNull(dao.findByLocalId("p1"))
        assertEquals(0, syncer.requestCount, "nothing to sync — it only ever existed locally")
    }

    @Test
    fun refresh_project_delegates_to_a_single_project_sync() = runTest {
        val syncer = FakeSyncer()

        repo(syncer = syncer).refreshProject("p1")

        assertEquals(listOf("p1"), syncer.syncedProjects)
    }

    @Test
    fun update_then_observe_reflects_the_edit_locally() = runTest {
        val dao = FakeProjectDao(
            listOf(localProject("p1", name = "Old", serverId = 9, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED)),
        )
        val r = repo(dao)

        r.updateProject("p1", update(name = "New"))

        assertNotNull(r.observeProject("p1").first())
        assertEquals("New", r.observeProject("p1").first()!!.name)
    }
}
