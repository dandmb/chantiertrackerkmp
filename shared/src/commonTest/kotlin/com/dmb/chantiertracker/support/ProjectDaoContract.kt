package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.ProjectEntity
import com.dmb.chantiertracker.data.local.db.ProjectMemberEntity
import com.dmb.chantiertracker.data.local.db.StageEntity
import com.dmb.chantiertracker.data.local.db.SyncStatus
import kotlinx.coroutines.flow.first
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun sample(localId: String, serverId: Long? = null, op: PendingOp = PendingOp.CREATE) =
    ProjectEntity(
        localId = localId,
        serverId = serverId,
        name = "Chantier $localId",
        description = null,
        location = "Nîmes",
        currency = "EUR",
        timezone = "Europe/Paris",
        status = "IN_PROGRESS",
        ownerId = 7L,
        createdAt = "2026-09-01T09:00:00",
        syncStatus = if (op == PendingOp.NONE) SyncStatus.SYNCED else SyncStatus.PENDING,
        pendingOp = op,
        locallyModifiedAt = 1_000L,
        lastSyncedAt = null,
        remoteUpdatedAt = null,
        lastSyncError = null,
    )

/** Shared behavioural checks run against a freshly built [AppDatabase] on every platform. */
suspend fun verifyProjectDaoContract(db: AppDatabase) {
    val dao = db.projectDao()

    dao.upsert(sample("a"))
    val stored = dao.findByLocalId("a")
    assertEquals("Chantier a", stored?.name)
    assertEquals(SyncStatus.PENDING, stored?.syncStatus)
    assertEquals(PendingOp.CREATE, stored?.pendingOp)
    assertNull(dao.findByLocalId("missing"))

    dao.upsertAll(
        listOf(
            sample("pending-1"),
            sample("synced-1", serverId = 10L, op = PendingOp.NONE),
            sample("gone", serverId = 5L, op = PendingOp.DELETE),
        ),
    )
    assertEquals(listOf("a", "gone", "pending-1"), dao.findPending().map { it.localId }.sorted())
    assertEquals("Chantier synced-1", dao.findByServerId(10L)?.name)

    val visible = dao.observeProjects().first().map { it.localId }.toSet()
    assertTrue("visible list keeps non-deleted rows") { "a" in visible && "synced-1" in visible }
    assertTrue("pending delete is hidden from the visible list") { "gone" !in visible }

    dao.deleteByLocalId("a")
    assertNull(dao.findByLocalId("a"))

    dao.upsertMembers(
        listOf(
            ProjectMemberEntity("p1", 1L, "Alice", "a@x.dev", "ADMIN"),
            ProjectMemberEntity("p1", 2L, "Bob", "b@x.dev", "SUPERVISOR"),
            ProjectMemberEntity("p2", 3L, "Carol", "c@x.dev", "ADMIN"),
        ),
    )
    assertEquals(2, dao.observeMembers("p1").first().size)
    dao.clearMembers("p1")
    assertTrue("members cleared for p1") { dao.observeMembers("p1").first().isEmpty() }
    assertEquals(1, dao.observeMembers("p2").first().size)
}

private fun sampleStage(
    localId: String,
    projectLocalId: String,
    serverId: Long? = null,
    startDate: String? = null,
    op: PendingOp = PendingOp.CREATE,
) = StageEntity(
    localId = localId,
    serverId = serverId,
    projectLocalId = projectLocalId,
    name = "Étape $localId",
    description = null,
    estimatedBudget = null,
    startDate = startDate,
    endDate = null,
    status = "IN_PROGRESS",
    syncStatus = if (op == PendingOp.NONE) SyncStatus.SYNCED else SyncStatus.PENDING,
    pendingOp = op,
    locallyModifiedAt = 1_000L,
    lastSyncedAt = null,
    remoteUpdatedAt = null,
    lastSyncError = null,
)

/** Shared behavioural checks for [com.dmb.chantiertracker.data.local.db.StageDao] on a real [AppDatabase]. */
suspend fun verifyStageDaoContract(db: AppDatabase) {
    val projectDao = db.projectDao()
    val dao = db.stageDao()

    // A stage row needs its parent project to exist (foreign key).
    projectDao.upsert(sample("host-a", serverId = 1L, op = PendingOp.NONE))
    projectDao.upsert(sample("host-b", serverId = 2L, op = PendingOp.NONE))

    dao.upsert(sampleStage("s-late", "host-a", startDate = "2026-05-01"))
    dao.upsert(sampleStage("s-early", "host-a", startDate = "2026-01-01"))
    dao.upsert(sampleStage("s-nodate", "host-a"))
    dao.upsert(sampleStage("s-synced", "host-a", serverId = 90L, op = PendingOp.NONE))
    dao.upsert(sampleStage("s-gone", "host-a", serverId = 91L, op = PendingOp.DELETE))
    dao.upsert(sampleStage("s-other", "host-b"))

    assertEquals("Étape s-synced", dao.findByServerId(90L)?.name)
    assertEquals(
        listOf("s-early", "s-gone", "s-late", "s-nodate"),
        dao.findPending().filter { it.projectLocalId == "host-a" }.map { it.localId }.sorted(),
    )

    val visibleA = dao.observeStagesForProject("host-a").first().map { it.localId }
    assertEquals(listOf("s-early", "s-late", "s-nodate", "s-synced"), visibleA, "dated first (asc), then undated by name; DELETE hidden")
    assertEquals(listOf("s-other"), dao.observeStagesForProject("host-b").first().map { it.localId })

    dao.deleteByLocalId("s-early")
    assertNull(dao.findByLocalId("s-early"))

    // Deleting the parent project cascades to its stages.
    projectDao.deleteByLocalId("host-a")
    assertTrue("stages cascade-deleted with their project") { dao.findForProject("host-a").isEmpty() }
    assertEquals(listOf("s-other"), dao.findForProject("host-b").map { it.localId })
}
