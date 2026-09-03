package com.dmb.chantiertracker.data.sync

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.presentation.sync.SyncState
import com.dmb.chantiertracker.presentation.sync.SyncStateHolder
import com.dmb.chantiertracker.support.FakeBackgroundSync
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.FakeProjectBackend
import com.dmb.chantiertracker.support.FakeProjectDao
import com.dmb.chantiertracker.support.MutableClock
import com.dmb.chantiertracker.support.ServerMember
import com.dmb.chantiertracker.support.ServerProject
import com.dmb.chantiertracker.support.localProject
import com.dmb.chantiertracker.support.serverMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
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
        val backgroundSync: FakeBackgroundSync = FakeBackgroundSync(),
    ) {
        var idSeq = 0
        fun engine(scope: CoroutineScope, catchUpInterval: Duration = 15.minutes) = SyncEngine(
            dao = dao,
            api = backend.api(),
            connectivity = connectivity,
            syncState = syncState,
            scope = scope,
            clock = clock,
            newLocalId = { "pulled-${idSeq++}" },
            backgroundSync = backgroundSync,
            catchUpInterval = catchUpInterval,
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

    // ─── optimistic-concurrency conflict resolution (ADR-21) ────────────────
    // A pending local edit is pushed as-is unless the server's `updatedAt` moved
    // since our last sync of that row — then the server version wins. No device
    // clock is involved, so a server running a non-UTC zone can't spuriously
    // "win" and silently drop a legitimate offline edit.

    @Test
    fun pull_lets_the_server_win_when_its_row_changed_since_our_last_sync() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Server name", updatedAt = "2026-09-05T10:00:00"))
        f.dao.upsert(
            localProject(
                "p5", name = "Stale local edit", serverId = 5,
                pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING,
                remoteUpdatedAt = serverMillis("2026-09-02T10:00:00"),
            ),
        )
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        val row = f.dao.findByServerId(5)!!
        assertEquals("Server name", row.name, "server row moved since our sync → it wins")
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
        assertFalse(f.backend.receivedMethods.any { it.startsWith("PATCH") }, "no push of the losing local edit")
    }

    @Test
    fun push_applies_the_local_edit_when_the_server_row_is_unchanged_since_our_sync() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 7, name = "Old server name", updatedAt = "2026-09-01T10:00:00"))
        f.backend.patchAppliedAt = "2026-09-04T00:00:00"
        f.dao.upsert(
            localProject(
                "p7", name = "Fresh local edit", serverId = 7,
                pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING,
                // We last synced this row while the server said 2026-09-01T10:00:00, and it
                // hasn't moved since — so our edit is on a fresh base and pushes cleanly.
                remoteUpdatedAt = serverMillis("2026-09-01T10:00:00"),
            ),
        )
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        assertEquals("Fresh local edit", f.backend.projects.single { it.id == 7L }.name)
        assertEquals(SyncStatus.SYNCED, f.dao.findByServerId(7)!!.syncStatus)
    }

    @Test
    fun local_edit_is_dropped_regardless_of_wall_clock_when_the_server_is_ahead() = runTest {
        // Regression: the server serialises `updatedAt` in a non-UTC zone, so parsed
        // as UTC it looks hours "ahead" of the device clock — but it has NOT changed
        // since our sync, so the local edit must still be pushed, not silently lost.
        val f = Fixture(clock = MutableClock(serverMillis("2026-09-02T09:00:00")))
        f.backend.seed(ServerProject(id = 8, name = "Server", updatedAt = "2026-09-02T11:00:00"))
        f.backend.patchAppliedAt = "2026-09-02T11:30:00"
        f.dao.upsert(
            localProject(
                "p8", name = "Legit offline edit", serverId = 8,
                pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING,
                remoteUpdatedAt = serverMillis("2026-09-02T11:00:00"),
            ),
        )
        val engine = f.engine(backgroundScope)

        engine.syncNow()

        assertEquals("Legit offline edit", f.backend.projects.single { it.id == 8L }.name)
        assertEquals(SyncStatus.SYNCED, f.dao.findByServerId(8)!!.syncStatus)
    }

    @Test
    fun two_concurrent_edits_converge_on_the_first_writer() = runTest {
        // One server, two devices that both synced the same base row.
        val backend = FakeProjectBackend()
        backend.seed(ServerProject(id = 9, name = "Base", updatedAt = "2026-09-01T00:00:00"))

        val deviceA = Fixture(backend = backend)
        val deviceB = Fixture(backend = backend)
        val engineA = deviceA.engine(backgroundScope)
        val engineB = deviceB.engine(backgroundScope)
        engineA.syncNow()
        engineB.syncNow()

        // Both edit locally, based on the same synced base (copy() keeps remoteUpdatedAt).
        deviceA.dao.upsert(
            deviceA.dao.findByServerId(9)!!.copy(
                name = "A edit", pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING,
            ),
        )
        deviceB.dao.upsert(
            deviceB.dao.findByServerId(9)!!.copy(
                name = "B edit", pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING,
            ),
        )

        backend.patchAppliedAt = "2026-09-10T12:00:00"
        engineA.syncNow()
        assertEquals("A edit", backend.projects.single { it.id == 9L }.name, "A's base is fresh → A's edit lands")

        // B's base is now stale (server moved) → the server (A's) version wins, B's edit is dropped.
        engineB.syncNow()
        assertEquals("A edit", backend.projects.single { it.id == 9L }.name)
        assertEquals("A edit", deviceB.dao.findByServerId(9)!!.name)

        // A converges on the same version on its next sync.
        engineA.syncNow()
        assertEquals("A edit", deviceA.dao.findByServerId(9)!!.name)
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

    // ─── single-project pull (detail + members) ─────────────────────────────

    @Test
    fun sync_project_pulls_the_project_and_its_members_into_the_local_store() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Villa Vidal"))
        f.backend.seedMembers(
            5,
            ServerMember(userId = 1, name = "Owner", email = "o@x.dev", role = "ADMIN"),
            ServerMember(userId = 2, name = "Sam", email = "s@x.dev", role = "SUPERVISOR"),
        )
        f.dao.upsert(
            localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED),
        )
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p5"))

        val members = f.dao.observeMembers("p5").first()
        assertEquals(listOf(1L, 2L), members.map { it.userId }.sorted())
        assertEquals("SUPERVISOR", members.single { it.userId == 2L }.role)
    }

    @Test
    fun sync_project_replaces_members_that_are_no_longer_on_the_server() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Villa Vidal"))
        f.backend.seedMembers(5, ServerMember(userId = 1, name = "Owner", email = "o@x.dev"))
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        f.dao.upsertMembers(
            listOf(
                com.dmb.chantiertracker.data.local.db.ProjectMemberEntity("p5", 99, "Stale", "z@x.dev", "SUPERVISOR"),
            ),
        )
        val engine = f.engine(backgroundScope)

        engine.syncProject("p5")

        assertEquals(listOf(1L), f.dao.observeMembers("p5").first().map { it.userId })
    }

    @Test
    fun sync_project_drops_a_local_row_the_server_no_longer_has() = runTest {
        val f = Fixture()
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        val engine = f.engine(backgroundScope)

        engine.syncProject("p5")

        assertNull(f.dao.findByLocalId("p5"))
    }

    @Test
    fun sync_project_while_offline_is_skipped_and_never_touches_the_network() = runTest {
        val f = Fixture()
        f.connectivity.setOnline(false)
        f.dao.upsert(localProject("p5", serverId = 5, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Skipped>(engine.syncProject("p5"))
        assertTrue(f.backend.receivedMethods.isEmpty())
        assertEquals(SyncState.Offline, f.syncState.state.value)
    }

    @Test
    fun sync_project_still_without_a_server_id_after_the_push_is_skipped() = runTest {
        val f = Fixture()
        f.backend.planLimitReached = true
        f.dao.upsert(localProject("p5", serverId = null, pendingOp = PendingOp.CREATE))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Skipped>(engine.syncProject("p5"))
        assertEquals(SyncStatus.CONFLICTED, f.dao.findByLocalId("p5")!!.syncStatus)
        assertFalse(f.backend.receivedMethods.any { it.startsWith("GET /projects/") })
    }

    @Test
    fun sync_project_pushes_a_local_only_project_then_pulls_it_back() = runTest {
        val f = Fixture()
        f.dao.upsert(localProject("p5", name = "Local only", serverId = null, pendingOp = PendingOp.CREATE))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncProject("p5"))

        val row = f.dao.findByLocalId("p5")!!
        assertNotNull(row.serverId)
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
    }

    @Test
    fun sync_project_flushes_a_pending_local_edit_before_pulling() = runTest {
        val f = Fixture()
        f.backend.seed(ServerProject(id = 5, name = "Old name", updatedAt = "2026-09-01T10:00:00"))
        f.backend.patchAppliedAt = "2026-09-04T00:00:00"
        f.dao.upsert(
            localProject(
                "p5", name = "Fresh local edit", serverId = 5,
                pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.PENDING,
                remoteUpdatedAt = serverMillis("2026-09-01T10:00:00"),
            ),
        )
        val engine = f.engine(backgroundScope)

        engine.syncProject("p5")

        assertEquals("Fresh local edit", f.backend.projects.single { it.id == 5L }.name)
        assertEquals(SyncStatus.SYNCED, f.dao.findByLocalId("p5")!!.syncStatus)
    }

    // ─── background catch-up (ADR-22) ──────────────────────────────────────

    @Test
    fun a_pass_that_cannot_finish_hands_the_queue_to_the_os_scheduler() = runTest {
        val f = Fixture()
        f.connectivity.setOnline(false)
        f.dao.upsert(localProject("p1", pendingOp = PendingOp.CREATE))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Skipped>(engine.syncNowOrDeferToOs())

        assertEquals(1, f.backgroundSync.expeditedCount, "offline write → hand the queue to the OS scheduler")
    }

    @Test
    fun a_pass_that_succeeds_schedules_no_background_work() = runTest {
        val f = Fixture()
        f.dao.upsert(localProject("p1", pendingOp = PendingOp.CREATE))
        val engine = f.engine(backgroundScope)

        assertIs<SyncOutcome.Synced>(engine.syncNowOrDeferToOs())

        assertEquals(SyncStatus.SYNCED, f.dao.findByLocalId("p1")!!.syncStatus)
        assertEquals(0, f.backgroundSync.expeditedCount)
    }

    @Test
    fun start_runs_a_catch_up_pass_every_interval_with_no_connectivity_change() = runTest {
        val f = Fixture()
        f.connectivity.setOnline(false) // keep the pass cheap: no real HTTP, just an offline no-op
        val engine = f.engine(backgroundScope, catchUpInterval = 5.minutes)

        engine.start()
        runCurrent()
        val afterStart = f.connectivity.isOnlineChecks

        advanceTimeBy(16.minutes) // three 5-minute ticks
        runCurrent()

        assertEquals(3, f.connectivity.isOnlineChecks - afterStart, "one catch-up pass per interval")
    }

    @Test
    fun start_is_idempotent() = runTest {
        val f = Fixture()
        f.connectivity.setOnline(false)
        val engine = f.engine(backgroundScope, catchUpInterval = 5.minutes)

        engine.start()
        engine.start()
        runCurrent()
        val afterStart = f.connectivity.isOnlineChecks

        advanceTimeBy(6.minutes)
        runCurrent()

        assertEquals(1, f.connectivity.isOnlineChecks - afterStart, "one periodic loop, not two")
    }
}
