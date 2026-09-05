package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.AppDatabase
import com.dmb.chantiertracker.data.local.db.DailyLogEntity
import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.PlanUsageEntity
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

    // Active-owned count (plan-limit numerator, ADR-25): IN_PROGRESS, not DELETE,
    // owned by :ownerId OR not yet synced (ownerId null).
    dao.upsertAll(
        listOf(
            sample("mine-1", op = PendingOp.NONE).copy(ownerId = 42L, status = "IN_PROGRESS"),
            sample("mine-local", op = PendingOp.CREATE).copy(ownerId = null, status = "IN_PROGRESS"),
            sample("mine-suspended", op = PendingOp.NONE).copy(ownerId = 42L, status = "SUSPENDED"),
            sample("mine-deleting", op = PendingOp.DELETE).copy(ownerId = 42L, status = "IN_PROGRESS"),
            sample("someone-elses", op = PendingOp.NONE).copy(ownerId = 99L, status = "IN_PROGRESS"),
        ),
    )
    assertEquals(2, dao.observeActiveOwnedCount(42L).first(), "mine-1 + the unsynced local one; suspended / deleting / others excluded")

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

/** Shared checks for [com.dmb.chantiertracker.data.local.db.DailyLogDao] + [com.dmb.chantiertracker.data.local.db.DailyEntryDao] on a real [AppDatabase]. */
suspend fun verifyDailyLogDaoContract(db: AppDatabase) {
    val projectDao = db.projectDao()
    val logDao = db.dailyLogDao()
    val entryDao = db.dailyEntryDao()

    // A daily log needs its parent stage to exist (foreign key), and the
    // stage needs its parent project (existing contract, verifyStageDaoContract).
    projectDao.upsert(sample("host-a", serverId = 1L, op = PendingOp.NONE))
    projectDao.upsert(sample("host-b", serverId = 2L, op = PendingOp.NONE))
    db.stageDao().upsert(sampleStage("stage-a", "host-a"))
    db.stageDao().upsert(sampleStage("stage-b", "host-b"))

    logDao.upsert(DailyLogEntity("log-late", null, "stage-a", "2026-09-10", 1_000L, null))
    logDao.upsert(DailyLogEntity("log-early", null, "stage-a", "2026-09-01", 1_000L, null))
    logDao.upsert(DailyLogEntity("log-other", null, "stage-b", "2026-09-05", 1_000L, null))

    assertEquals(
        listOf("log-late", "log-early"),
        logDao.observeLogsForStage("stage-a").first().map { it.localId },
        "date descending, most recent first",
    )
    assertEquals("2026-09-01", logDao.findByStageAndDate("stage-a", "2026-09-01")?.date)
    assertNull(logDao.findByStageAndDate("stage-a", "2026-01-01"))

    entryDao.upsert(localDailyEntry("entry-purchase", dailyLogLocalId = "log-late", type = "PURCHASE", pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    entryDao.upsert(localDailyEntry("entry-work", dailyLogLocalId = "log-late", type = "WORK", pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    entryDao.upsert(localDailyEntry("entry-gone", dailyLogLocalId = "log-early", type = "PURCHASE", pendingOp = PendingOp.DELETE))

    assertEquals(
        setOf("entry-purchase", "entry-work"),
        entryDao.observeEntriesForLog("log-late").first().map { it.localId }.toSet(),
    )
    assertTrue(entryDao.observeEntriesForLog("log-early").first().isEmpty(), "pending delete is hidden")

    val stageAEntries = entryDao.observeEntriesForStage("stage-a").first().map { it.localId }.toSet()
    assertEquals(setOf("entry-purchase", "entry-work"), stageAEntries, "join across daily_logs scopes entries to their stage")

    assertEquals(listOf("entry-gone"), entryDao.findPending().map { it.localId })
    assertEquals("entry-purchase", entryDao.findByLogAndType("log-late", "PURCHASE")?.localId)
    assertEquals(2, entryDao.findForLog("log-late").size, "findForLog ignores pendingOp, for reconciliation")

    entryDao.deleteByLocalId("entry-work")
    assertNull(entryDao.findByLocalId("entry-work"))

    // Deleting the parent stage cascades to its logs, which cascades to their entries.
    db.stageDao().deleteByLocalId("stage-a")
    assertTrue("logs cascade-deleted with their stage") { logDao.observeLogsForStage("stage-a").first().isEmpty() }
    assertTrue("entries cascade-deleted with their log") { entryDao.findForLog("log-late").isEmpty() }
    assertEquals(listOf("log-other"), logDao.observeLogsForStage("stage-b").first().map { it.localId })
}

/**
 * Shared checks for [com.dmb.chantiertracker.data.local.db.MaterialDao],
 * [com.dmb.chantiertracker.data.local.db.PurchaseLineDao] and
 * [com.dmb.chantiertracker.data.local.db.ConsumptionLineDao] on a real
 * [AppDatabase] — in particular the join chain (line → entry → log → stage →
 * project) that `observeLinesForProject` relies on for stock (ADR-28).
 */
suspend fun verifyMaterialAndLineDaoContract(db: AppDatabase) {
    val projectDao = db.projectDao()
    val stageDao = db.stageDao()
    val logDao = db.dailyLogDao()
    val entryDao = db.dailyEntryDao()
    val materialDao = db.materialDao()
    val purchaseDao = db.purchaseLineDao()
    val consumptionDao = db.consumptionLineDao()

    projectDao.upsert(sample("proj-a", serverId = 1L, op = PendingOp.NONE))
    projectDao.upsert(sample("proj-b", serverId = 2L, op = PendingOp.NONE))
    stageDao.upsert(sampleStage("stage-a", "proj-a"))
    logDao.upsert(DailyLogEntity("log-a", null, "stage-a", "2026-09-05", 1_000L, null))
    entryDao.upsert(localDailyEntry("entry-purchase", dailyLogLocalId = "log-a", type = "PURCHASE", pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    entryDao.upsert(localDailyEntry("entry-work", dailyLogLocalId = "log-a", type = "WORK", pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))

    materialDao.upsert(localMaterial("m-ciment", projectLocalId = "proj-a", name = "Ciment", unit = "sac"))
    materialDao.upsert(localMaterial("m-fer", projectLocalId = "proj-a", name = "Fer", unit = "barre"))
    materialDao.upsert(localMaterial("m-other", projectLocalId = "proj-b", name = "Ciment", unit = "sac"))

    assertEquals(listOf("Ciment", "Fer"), materialDao.observeMaterialsForProject("proj-a").first().map { it.name })
    assertEquals("m-ciment", materialDao.findByProjectAndName("proj-a", "ciment")?.localId, "name lookup is case-insensitive")
    assertNull(materialDao.findByProjectAndName("proj-a", "Ciment introuvable"))

    purchaseDao.upsert(localPurchaseLine("pl-in-scope", entryLocalId = "entry-purchase", materialLocalId = "m-ciment", quantity = 100.0, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    purchaseDao.upsert(localPurchaseLine("pl-deleted", entryLocalId = "entry-purchase", materialLocalId = "m-ciment", quantity = 999.0, pendingOp = PendingOp.DELETE))
    consumptionDao.upsert(localConsumptionLine("cl-in-scope", entryLocalId = "entry-work", materialLocalId = "m-ciment", quantity = 40.0, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))

    assertEquals(listOf("pl-in-scope"), purchaseDao.observeLinesForEntry("entry-purchase").first().map { it.localId }, "pending delete hidden")
    assertEquals(
        listOf("pl-in-scope"),
        purchaseDao.observeLinesForProject("proj-a").first().map { it.localId },
        "the join resolves through daily_entries/daily_logs/stages to the project",
    )
    assertTrue(purchaseDao.observeLinesForProject("proj-b").first().isEmpty(), "a line on proj-a never leaks into proj-b's stock")
    assertEquals(listOf("cl-in-scope"), consumptionDao.observeLinesForProject("proj-a").first().map { it.localId })
    assertEquals(listOf("pl-deleted"), purchaseDao.findPending().map { it.localId })

    // Deleting the parent entry cascades to its lines; the material itself is untouched.
    entryDao.deleteByLocalId("entry-purchase")
    assertTrue("purchase lines cascade-deleted with their entry") { purchaseDao.observeLinesForProject("proj-a").first().isEmpty() }
    assertEquals("Ciment", materialDao.findByLocalId("m-ciment")?.name, "materials are never cascade-deleted")
}

/** Shared checks for [com.dmb.chantiertracker.data.local.db.AttachmentDao] on a real [AppDatabase] (ADR-29). */
suspend fun verifyAttachmentDaoContract(db: AppDatabase) {
    val projectDao = db.projectDao()
    val stageDao = db.stageDao()
    val logDao = db.dailyLogDao()
    val entryDao = db.dailyEntryDao()
    val attachmentDao = db.attachmentDao()

    projectDao.upsert(sample("proj-a", serverId = 1L, op = PendingOp.NONE))
    stageDao.upsert(sampleStage("stage-a", "proj-a"))
    logDao.upsert(DailyLogEntity("log-a", null, "stage-a", "2026-09-05", 1_000L, null))
    entryDao.upsert(localDailyEntry("entry-purchase-a", dailyLogLocalId = "log-a", type = "PURCHASE", pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    entryDao.upsert(localDailyEntry("entry-purchase-b", dailyLogLocalId = "log-a", type = "WORK", pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))

    attachmentDao.upsert(localAttachment("att-1", entryLocalId = "entry-purchase-a", pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    attachmentDao.upsert(localAttachment("att-deleted", entryLocalId = "entry-purchase-a", pendingOp = PendingOp.DELETE))
    attachmentDao.upsert(localAttachment("att-other-entry", entryLocalId = "entry-purchase-b", pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))

    assertEquals(
        listOf("att-1"),
        attachmentDao.observeForEntry("entry-purchase-a").first().map { it.localId },
        "another entry's photo and a pending delete are both hidden",
    )
    assertEquals(listOf("att-deleted"), attachmentDao.findPending().map { it.localId })

    // Deleting the parent entry cascades to its attachments.
    entryDao.deleteByLocalId("entry-purchase-a")
    assertTrue("attachments cascade-deleted with their entry") { attachmentDao.observeForEntry("entry-purchase-a").first().isEmpty() }
    assertNull(attachmentDao.findByLocalId("att-1"))
}

/** Shared checks for [com.dmb.chantiertracker.data.local.db.PlanUsageDao] on a real [AppDatabase]. */
suspend fun verifyPlanUsageDaoContract(db: AppDatabase) {
    val dao = db.planUsageDao()

    assertNull(dao.observe().first())

    dao.upsert(PlanUsageEntity(id = 0, plan = "FREE", projectsLimit = 1, refreshedAt = 1_000L))
    assertEquals("FREE", dao.observe().first()?.plan)
    assertEquals(1, dao.observe().first()?.projectsLimit)

    // Single row, id = 0: a later fetch replaces it (not a second row).
    dao.upsert(PlanUsageEntity(id = 0, plan = "LIBERTE", projectsLimit = null, refreshedAt = 2_000L))
    val row = dao.observe().first()!!
    assertEquals("LIBERTE", row.plan)
    assertNull(row.projectsLimit)
    assertEquals(2_000L, row.refreshedAt)
}
