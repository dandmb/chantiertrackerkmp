package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.ProjectMemberEntity
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.support.FakeProjectDao
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.MutableClock
import com.dmb.chantiertracker.support.localProject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
