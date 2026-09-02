package com.dmb.chantiertracker.data.sync

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.presentation.sync.SyncState
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.FakeProjectBackend
import com.dmb.chantiertracker.support.FakeProjectDao
import com.dmb.chantiertracker.support.MutableClock
import com.dmb.chantiertracker.support.ServerProject
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.serverMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncEngineTest {

    private class Fixture(
        val dao: FakeProjectDao = FakeProjectDao(),
        val backend: FakeProjectBackend = FakeProjectBackend(),
        val connectivity: FakeConnectivityObserver = FakeConnectivityObserver(),
        val clock: MutableClock = MutableClock(serverMillis("2026-09-02T09:00:00")),
        val syncState: SyncStateHolder = SyncStateHolder(),
    ) {
        var idSeq = 0
        fun engine(scope: CoroutineScope) = SyncEngine(
            dao = dao,
            api = backend.api(),
            connectivity = connectivity,
            syncState = syncState,
            scope = scope,
            clock = clock,
            newLocalId = { "pulled-${idSeq++}" },
        )
    }

    // ─── offline write, then sync on reconnect ───────────────────────────────

    @Test
    fun offline_create_is_kept_local_and_pushed_once_connectivity_returns() = runTest {
        val f = Fixture()
        f.connectivity.setOnline(false)
        val engine = f.engine(backgroundScope)

        f.dao.upsert(localProject("p1", name = "Villa Vidal", pendingOp = PendingOp.CREATE))

        assertIs<SyncOutcome.Skipped>(engine.syncNow())
        assertTrue(f.backend.receivedMethods.isEmpty(), "no network call while offline")
        assertEquals(SyncState.Offline, f.syncState.state.value)
        assertEquals(listOf("Villa Vidal"), f.dao.observeProjects().first().map { it.name })

        f.connectivity.setOnline(true)
        assertIs<SyncOutcome.Synced>(engine.syncNow())

        val stored = f.dao.findByLocalId("p1")!!
        assertEquals(SyncStatus.SYNCED, stored.syncStatus)
        assertEquals(PendingOp.NONE, stored.pendingOp)
        assertNotNull(stored.serverId)
        assertEquals(listOf("Villa Vidal"), f.backend.projects.map { it.name })
        assertEquals(SyncState.Idle, f.syncState.state.value)
    }

    @Test
    fun reads_come_from_room_with_zero_connectivity_and_never_touch_the_network() = runTest {
        val f = Fixture()
        f.connectivity.setOnline(false)
        f.dao.upsertAll(
            listOf(
                localProject("a", name = "Alpha", serverId = 1, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
                localProject("b", name = "Beta", serverId = 2, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
            ),
        )
        val engine = f.engine(backgroundScope)

        assertEquals(setOf("Alpha", "Beta"), f.dao.observeProjects().first().map { it.name }.toSet())
        assertEquals("Alpha", f.dao.observeProject("a").first()?.name)
        assertIs<SyncOutcome.Skipped>(engine.syncNow())
        assertTrue(f.backend.receivedMethods.isEmpty())
    }

    // ─── last-write-wins by timestamp ───────────────────────────────────────

    @Test
    fun pull_lets_a_newer_server_row_overwrite_a_stale_local_pending_edit() = runTest {
        val f = Fixture()
        f.backend.seed(
            ServerProject(id = 5, name = "Server name", updatedAt = "2026-09-05T10:00:00"),
        )
        f.dao.upsert(
            localProject(
                "p5", name = "Stale local edit", serverId = 5,
                pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING,
                locallyModifiedAt = serverMillis("2026-09-02T10:00:00"),
            ),
        )
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        val row = f.dao.findByServerId(5)!!
        assertEquals("Server name", row.name, "server edit is newer → it wins")
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
        assertFalse(f.backend.receivedMethods.any { it.startsWith("PATCH") }, "no push of the losing local edit")
    }

    @Test
    fun push_keeps_a_local_edit_that_is_newer_than_the_server_row() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 7, name = "Old server name", updatedAt = "2026-09-01T10:00:00"))
        f.backend.patchAppliedAt = "2026-09-04T00:00:00"
        f.dao.upsert(
            localProject(
                "p7", name = "Fresh local edit", serverId = 7,
                pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING,
                locallyModifiedAt = serverMillis("2026-09-03T10:00:00"),
            ),
        )
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        assertEquals("Fresh local edit", f.backend.projects.single { it.id == 7L }.name, "local edit is newer → it wins")
        assertEquals(SyncStatus.SYNCED, f.dao.findByServerId(7)!!.syncStatus)
    }

    @Test
    fun two_concurrent_edits_converge_on_the_more_recently_edited_version() = runTest {
        // One server, two devices that both synced the same base row.
        val backend = FakeProjectBackend()
        backend.seed(ServerProject(id = 9, name = "Base", updatedAt = "2026-09-01T00:00:00"))

        val deviceA = Fixture(backend = backend)
        val deviceB = Fixture(backend = backend)
        val engineA = deviceA.engine(backgroundScope)
        val engineB = deviceB.engine(backgroundScope)
        engineA.syncNow()
        engineB.syncNow()

        // A edits early, B edits later (B's is the "most recent" change).
        deviceA.dao.upsert(
            deviceA.dao.findByServerId(9)!!.copy(
                name = "A edit", pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING,
                locallyModifiedAt = serverMillis("2026-09-10T08:00:00"),
            ),
        )
        deviceB.dao.upsert(
            deviceB.dao.findByServerId(9)!!.copy(
                name = "B edit", pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING,
                locallyModifiedAt = serverMillis("2026-09-10T20:00:00"),
            ),
        )

        // A syncs first; the server stamps the write between the two edit times.
        backend.patchAppliedAt = "2026-09-10T12:00:00"
        engineA.syncNow()
        assertEquals("A edit", backend.projects.single { it.id == 9L }.name)

        // B syncs second; B's edit is more recent than the server row → B wins.
        engineB.syncNow()
        assertEquals("B edit", backend.projects.single { it.id == 9L }.name)

        // A converges on the winning version on its next sync.
        engineA.syncNow()
        assertEquals("B edit", deviceA.dao.findByServerId(9)!!.name)
    }

    // ─── push failures ─────────────────────────────────────────────────────

    @Test
    fun offline_create_rejected_by_the_plan_limit_is_flagged_conflicted_not_dropped() = runTest {
        val f = Fixture()
        f.backend.planLimitReached = true
        f.dao.upsert(localProject("p1", name = "Second project", pendingOp = PendingOp.CREATE))
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        val row = f.dao.findByLocalId("p1")!!
        assertEquals(SyncStatus.CONFLICTED, row.syncStatus)
        assertEquals(SyncError.PLAN_LIMIT, row.lastSyncError)
        assertNull(row.serverId)
    }

    // ─── delete + server-side removal ──────────────────────────────────────

    @Test
    fun pending_delete_is_pushed_and_the_row_is_removed_locally() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 3, name = "To delete"))
        f.dao.upsert(
            localProject("p3", serverId = 3, pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING),
        )
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        assertNull(f.dao.findByLocalId("p3"))
        assertTrue(f.backend.projects.none { it.id == 3L })
    }

    @Test
    fun pull_drops_local_rows_deleted_on_the_server() = runTest {
        val f = Fixture()
        f.dao.upsert(
            localProject("gone", serverId = 42, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
        )
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        assertNull(f.dao.findByLocalId("gone"))
    }

    @Test
    fun pull_inserts_projects_first_seen_on_the_server() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 11, name = "Remote only", currency = "XAF", timezone = "Africa/Douala"))
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        val row = f.dao.findByServerId(11)!!
        assertEquals("Remote only", row.name)
        assertEquals("XAF", row.currency)
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
    }
}
