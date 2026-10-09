package com.dmb.chantiertracker.support

import com.dmb.chantiertracker.data.local.db.MaterialStockEntity

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
    assertNull(stored?.ownerPlan, "ownerPlan is null until a detail pull fills it (ADR-33)")
    assertNull(dao.findByLocalId("missing"))

    dao.upsert(sample("a").copy(ownerPlan = "SEMI_FLEX"))
    assertEquals("SEMI_FLEX", dao.findByLocalId("a")?.ownerPlan, "ownerPlan round-trips")

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
 * project) that `observeStockMovements` relies on for stock (ADR-71).
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
    assertNull(materialDao.findByProjectAndNameExactly("proj-a", "ciment"), "the exact lookup follows the unique index")
    assertEquals("m-ciment", materialDao.findByProjectAndNameExactly("proj-a", "Ciment")?.localId)
    materialDao.upsert(localMaterial("m-clash", projectLocalId = "proj-a", name = "Ciment", unit = "sac", serverId = 99))
    assertNull(materialDao.findByLocalId("m-clash"), "Room's @Upsert silently stores nothing when the (project, name) unique index refuses the row")
    assertEquals(2, materialDao.findForProject("proj-a").size)
    assertNull(materialDao.findByProjectAndName("proj-a", "Ciment introuvable"))

    purchaseDao.upsert(localPurchaseLine("pl-in-scope", entryLocalId = "entry-purchase", materialLocalId = "m-ciment", quantity = 100.0, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    purchaseDao.upsert(localPurchaseLine("pl-deleted", entryLocalId = "entry-purchase", materialLocalId = "m-ciment", quantity = 999.0, pendingOp = PendingOp.DELETE))
    consumptionDao.upsert(localConsumptionLine("cl-in-scope", entryLocalId = "entry-work", materialLocalId = "m-ciment", quantity = 40.0, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))

    assertEquals(listOf("pl-in-scope"), purchaseDao.observeLinesForEntry("entry-purchase").first().map { it.localId }, "pending delete hidden")
    assertEquals(
        listOf(999.0),
        purchaseDao.observeStockMovements("proj-a").first().map { it.quantity },
        "the join resolves to the project; only the lines that still move the stock (the pending delete), not the synced one",
    )
    assertTrue(purchaseDao.observeStockMovements("proj-b").first().isEmpty(), "a line on proj-a never leaks into proj-b's stock")
    assertTrue(consumptionDao.observeStockMovements("proj-a").first().isEmpty(), "a synced consumption is already in the server counter")
    assertEquals(listOf("pl-deleted"), purchaseDao.findPending().map { it.localId })

    stageDao.upsert(sampleStage("stage-a", "proj-a").copy(pendingOp = PendingOp.DELETE, syncStatus = SyncStatus.PENDING))
    val underDeletingStage = consumptionDao.observeStockMovements("proj-a").first().single()
    assertEquals(true, underDeletingStage.parentDeleting, "a line under a stage being deleted will be released by the server")
    assertEquals(40.0, underDeletingStage.quantity)

    db.materialAdoptionDao().mergeInto("m-fer", localMaterial("m-ciment", projectLocalId = "proj-a", name = "Ciment", unit = "sac", serverId = 7, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    assertNull(materialDao.findByLocalId("m-fer"), "the duplicate is gone")
    assertEquals(7L, materialDao.findByLocalId("m-ciment")?.serverId)

    purchaseDao.upsert(localPurchaseLine("pl-fer", entryLocalId = "entry-purchase", materialLocalId = "m-ciment", quantity = 1.0))
    materialDao.upsert(localMaterial("m-dup", projectLocalId = "proj-a", name = "Ciment bis", unit = "sac"))
    consumptionDao.upsert(localConsumptionLine("cl-dup", entryLocalId = "entry-work", materialLocalId = "m-dup", quantity = 1.0))
    db.materialAdoptionDao().mergeInto("m-dup", materialDao.findByLocalId("m-ciment")!!)
    assertEquals("m-ciment", consumptionDao.findByLocalId("cl-dup")?.materialLocalId, "the duplicate's lines move to the kept material")
    assertNull(materialDao.findByLocalId("m-dup"))

    entryDao.deleteByLocalId("entry-purchase")
    assertTrue("purchase lines cascade-deleted with their entry") { purchaseDao.findForEntry("entry-purchase").isEmpty() }
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

/** Shared checks for [com.dmb.chantiertracker.data.local.db.InvitationDao] on a real [AppDatabase]. */
suspend fun verifyInvitationDaoContract(db: AppDatabase) {
    val projectDao = db.projectDao()
    val invitationDao = db.invitationDao()

    projectDao.upsert(sample("proj-inv", serverId = 1L, op = PendingOp.NONE))

    invitationDao.upsertAll(
        listOf(
            localInvitation(1, projectLocalId = "proj-inv", createdAt = "2026-09-01T10:00:00"),
            localInvitation(2, projectLocalId = "proj-inv", email = "lea@chantier.dev", createdAt = "2026-09-03T10:00:00"),
        ),
    )

    assertEquals(
        listOf(2L, 1L),
        invitationDao.observeForProject("proj-inv").first().map { it.id },
        "newest invitation first",
    )
    assertEquals(2, invitationDao.findForProject("proj-inv").size)

    // upsert replaces the row for an existing id (read-through cache: status refreshed on each pull).
    invitationDao.upsertAll(listOf(localInvitation(1, projectLocalId = "proj-inv", status = "ACCEPTED")))
    assertEquals("ACCEPTED", invitationDao.findForProject("proj-inv").first { it.id == 1L }.status)

    invitationDao.deleteById(1L)
    assertEquals(listOf(2L), invitationDao.findForProject("proj-inv").map { it.id })

    invitationDao.clearForProject("proj-inv")
    assertTrue("cache cleared for the project") { invitationDao.findForProject("proj-inv").isEmpty() }

    // Deleting the parent project cascades to its invitations.
    invitationDao.upsertAll(listOf(localInvitation(3, projectLocalId = "proj-inv")))
    projectDao.deleteByLocalId("proj-inv")
    assertTrue("invitations cascade-deleted with their project") { invitationDao.findForProject("proj-inv").isEmpty() }
}

/** Shared checks for [com.dmb.chantiertracker.data.local.db.PlanUsageDao] on a real [AppDatabase]. */
suspend fun verifyPlanUsageDaoContract(db: AppDatabase) {
    val dao = db.planUsageDao()

    assertNull(dao.observe().first())

    dao.upsert(PlanUsageEntity(id = 0, plan = "FREE", projectsLimit = 1, refreshedAt = 1_000L))
    assertEquals("FREE", dao.observe().first()?.plan)
    assertEquals(1, dao.observe().first()?.projectsLimit)

    // Single row, id = 0: a later fetch replaces it (not a second row).
    dao.upsert(
        PlanUsageEntity(
            id = 0,
            plan = "LIBERTE",
            projectsLimit = null,
            refreshedAt = 2_000L,
            projectsUsed = 4,
            photosUsed = 120,
            photosLimit = null,
            videosUsed = 2,
            videosLimit = 20,
            videoDurationLimitSeconds = 300,
            supervisorsUsed = 1,
            supervisorsLimit = null,
            planExpiresAt = "2026-10-08T12:00:00",
            hasStripeCustomer = true,
        ),
    )
    val row = dao.observe().first()!!
    assertEquals("LIBERTE", row.plan)
    assertNull(row.projectsLimit)
    assertEquals(2_000L, row.refreshedAt)
    assertEquals(4, row.projectsUsed)
    assertEquals(120, row.photosUsed)
    assertNull(row.photosLimit)
    assertEquals(2, row.videosUsed)
    assertEquals(20, row.videosLimit)
    assertEquals(300, row.videoDurationLimitSeconds)
    assertEquals(1, row.supervisorsUsed)
    assertNull(row.supervisorsLimit)
    assertEquals("2026-10-08T12:00:00", row.planExpiresAt)
    assertEquals(true, row.hasStripeCustomer)
}

suspend fun verifyEditorIdentityDaoContract(db: AppDatabase) {
    val dao = db.editorIdentityDao()

    assertNull(dao.observe().first())

    dao.upsert(com.dmb.chantiertracker.data.local.db.EditorIdentityEntity(refreshedAt = 1_000L))
    assertNull(dao.observe().first()?.siret, "nothing filled in yet: every field null")

    // Single row, id = 0: a later fetch replaces it, nulls included.
    dao.upsert(
        com.dmb.chantiertracker.data.local.db.EditorIdentityEntity(
            firstName = "Jean", lastName = "Martin", siret = "123 456 789 00012",
            hostingProviderName = "Hetzner Online GmbH", refreshedAt = 2_000L,
        ),
    )
    dao.upsert(com.dmb.chantiertracker.data.local.db.EditorIdentityEntity(firstName = "Jean", refreshedAt = 3_000L))
    val row = dao.observe().first()
    assertEquals("Jean", row?.firstName)
    assertNull(row?.siret, "a field cleared on the backend is cleared here too")
    assertEquals(3_000L, row?.refreshedAt)
}

suspend fun verifyLocalDataDaoContract(db: AppDatabase) {
    val dao = db.localDataDao()
    assertEquals(0, dao.countUnsynced())

    db.projectDao().upsert(localProject("proj-1"))
    db.projectDao().upsertMembers(listOf(com.dmb.chantiertracker.data.local.db.ProjectMemberEntity("proj-1", 7, "Jean", "j@x.dev", "ADMIN")))
    db.stageDao().upsert(localStage("stage-1", projectLocalId = "proj-1"))
    db.dailyLogDao().upsert(localDailyLog("log-1", stageLocalId = "stage-1"))
    db.dailyEntryDao().upsert(localDailyEntry("entry-1", dailyLogLocalId = "log-1"))
    db.materialDao().upsert(localMaterial("material-1", projectLocalId = "proj-1"))
    db.purchaseLineDao().upsert(localPurchaseLine("pl-1", entryLocalId = "entry-1", materialLocalId = "material-1"))
    db.consumptionLineDao().upsert(localConsumptionLine("cl-1", entryLocalId = "entry-1", materialLocalId = "material-1"))
    db.attachmentDao().upsert(localAttachment("att-1", entryLocalId = "entry-1"))
    db.planUsageDao().upsert(com.dmb.chantiertracker.data.local.db.PlanUsageEntity(plan = "FREE", projectsLimit = 1, refreshedAt = 1L))
    db.editorIdentityDao().upsert(com.dmb.chantiertracker.data.local.db.EditorIdentityEntity(siret = "123", refreshedAt = 1L))
    db.stockDao().replaceCounters("proj-1", listOf(com.dmb.chantiertracker.data.local.db.MaterialStockEntity("proj-1", 7, 12.0, 0.0)), refreshedAt = 1L)

    assertEquals(7, dao.countUnsynced(), "project, stage, entry, material, both lines and the photo are pending")

    dao.eraseAll()

    assertEquals(0, dao.countUnsynced())
    assertTrue(db.projectDao().findAll().isEmpty())
    assertTrue(db.projectDao().observeMembers("proj-1").first().isEmpty())
    assertNull(db.stageDao().findByLocalId("stage-1"))
    assertNull(db.dailyEntryDao().findByLocalId("entry-1"))
    assertNull(db.materialDao().findByLocalId("material-1"))
    assertNull(db.purchaseLineDao().findByLocalId("pl-1"))
    assertNull(db.consumptionLineDao().findByLocalId("cl-1"))
    assertNull(db.attachmentDao().findByLocalId("att-1"))
    assertNull(db.planUsageDao().observe().first(), "the plan usage belongs to the account too")
    assertNull(db.editorIdentityDao().observe().first())
    assertNull(db.stockDao().findSnapshot("proj-1"), "the server stock belongs to the account too")
    assertTrue(db.stockDao().observeCounters("proj-1").first().isEmpty())
}


suspend fun verifyFindPendingSkipsRowsDeletedOnServer(db: AppDatabase) {
    val rejected = com.dmb.chantiertracker.data.sync.SyncError.REJECTED
    val gone = com.dmb.chantiertracker.data.sync.SyncError.DELETED_ON_SERVER
    fun <T> Triple<T, T, T>.all() = listOf(first, second, third)

    Triple("proj-p" to null, "proj-r" to rejected, "proj-d" to gone).all().forEach { (id, error) ->
        db.projectDao().upsert(localProject(id, syncStatus = if (error == null) SyncStatus.PENDING else SyncStatus.CONFLICTED, lastSyncError = error))
    }
    Triple("st-p" to null, "st-r" to rejected, "st-d" to gone).all().forEach { (id, error) ->
        db.stageDao().upsert(localStage(id, projectLocalId = "proj-p", syncStatus = if (error == null) SyncStatus.PENDING else SyncStatus.CONFLICTED, lastSyncError = error))
    }
    db.dailyLogDao().upsert(localDailyLog("log-1", stageLocalId = "st-p"))
    db.dailyLogDao().upsert(localDailyLog("log-2", stageLocalId = "st-p", date = "2026-09-06"))
    db.dailyEntryDao().upsert(localDailyEntry("e-p", dailyLogLocalId = "log-1", type = "PURCHASE"))
    db.dailyEntryDao().upsert(localDailyEntry("e-r", dailyLogLocalId = "log-1", type = "WORK", syncStatus = SyncStatus.CONFLICTED, lastSyncError = rejected))
    db.dailyEntryDao().upsert(localDailyEntry("e-d", dailyLogLocalId = "log-2", syncStatus = SyncStatus.CONFLICTED, lastSyncError = gone))
    Triple("m-p" to null, "m-r" to rejected, "m-d" to gone).all().forEach { (id, error) ->
        db.materialDao().upsert(localMaterial(id, projectLocalId = "proj-p").copy(syncStatus = if (error == null) SyncStatus.PENDING else SyncStatus.CONFLICTED, lastSyncError = error))
    }
    Triple("pl-p" to null, "pl-r" to rejected, "pl-d" to gone).all().forEach { (id, error) ->
        db.purchaseLineDao().upsert(localPurchaseLine(id, entryLocalId = "e-p", materialLocalId = "m-p").copy(syncStatus = if (error == null) SyncStatus.PENDING else SyncStatus.CONFLICTED, lastSyncError = error))
    }
    Triple("cl-p" to null, "cl-r" to rejected, "cl-d" to gone).all().forEach { (id, error) ->
        db.consumptionLineDao().upsert(localConsumptionLine(id, entryLocalId = "e-r", materialLocalId = "m-p").copy(syncStatus = if (error == null) SyncStatus.PENDING else SyncStatus.CONFLICTED, lastSyncError = error))
    }
    Triple("att-p" to null, "att-r" to rejected, "att-d" to gone).all().forEach { (id, error) ->
        db.attachmentDao().upsert(localAttachment(id, entryLocalId = "e-p").copy(syncStatus = if (error == null) SyncStatus.PENDING else SyncStatus.CONFLICTED, lastSyncError = error))
    }

    assertEquals(listOf("proj-p", "proj-r"), db.projectDao().findPending().map { it.localId }.sorted())
    assertEquals(listOf("st-p", "st-r"), db.stageDao().findPending().map { it.localId }.sorted())
    assertEquals(listOf("e-p", "e-r"), db.dailyEntryDao().findPending().map { it.localId }.sorted())
    assertEquals(listOf("m-p", "m-r"), db.materialDao().findPending().map { it.localId }.sorted())
    assertEquals(listOf("pl-p", "pl-r"), db.purchaseLineDao().findPending().map { it.localId }.sorted())
    assertEquals(listOf("cl-p", "cl-r"), db.consumptionLineDao().findPending().map { it.localId }.sorted())
    assertEquals(listOf("att-p", "att-r"), db.attachmentDao().findPending().map { it.localId }.sorted())
    assertEquals(21, db.localDataDao().countUnsynced(), "rows deleted on the server still count as unsent: signing out warns about them")
}

suspend fun verifyStockDaoContract(db: AppDatabase) {
    val dao = db.stockDao()
    db.projectDao().upsert(localProject("proj-s", serverId = 50, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    db.stageDao().upsert(localStage("stage-s", projectLocalId = "proj-s", serverId = 51, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    db.dailyLogDao().upsert(localDailyLog("log-s", stageLocalId = "stage-s"))
    db.dailyEntryDao().upsert(localDailyEntry("entry-s", dailyLogLocalId = "log-s", serverId = 52, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    db.materialDao().upsert(localMaterial("mat-s", projectLocalId = "proj-s", serverId = 7, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    val synced = localPurchaseLine("pl-s", entryLocalId = "entry-s", materialLocalId = "mat-s", quantity = 5.0, serverId = 60, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED).copy(serverQuantity = 5.0)

    dao.recordPurchaseLineSynced("proj-s", 7, synced, previousServerQuantity = null)
    assertEquals(5.0, db.purchaseLineDao().findByLocalId("pl-s")?.serverQuantity, "the line is written even when no stock was loaded")
    assertTrue(dao.observeCounters("proj-s").first().isEmpty(), "nothing to correct before the first load")

    dao.replaceCounters("proj-s", listOf(MaterialStockEntity("proj-s", 7, 12.0, 2.0)), refreshedAt = 3_000L)
    assertEquals(3_000L, dao.observeSnapshot("proj-s").first()?.refreshedAt)
    dao.recordPurchaseLineSynced("proj-s", 7, synced.copy(quantity = 8.0, serverQuantity = 8.0), previousServerQuantity = 5.0)
    assertEquals(15.0, dao.findCounter("proj-s", 7)?.quantityIn, "the server's own +3 is mirrored in the same transaction")
    db.consumptionLineDao().upsert(localConsumptionLine("cl-s", entryLocalId = "entry-s", materialLocalId = "mat-s", quantity = 2.0, serverId = 61, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    dao.recordConsumptionLineDeleted("proj-s", 7, "cl-s", serverQuantity = 2.0)
    assertNull(db.consumptionLineDao().findByLocalId("cl-s"))
    assertEquals(0.0, dao.findCounter("proj-s", 7)?.quantityOut)

    assertTrue(dao.findProjectsNeedingRefresh().isEmpty())
    dao.markNeedsRefresh("proj-s")
    assertEquals(listOf("proj-s"), dao.findProjectsNeedingRefresh())
    dao.replaceCounters("proj-s", listOf(MaterialStockEntity("proj-s", 7, 20.0, 1.0)), refreshedAt = 4_000L)
    assertTrue(dao.findProjectsNeedingRefresh().isEmpty(), "a reload clears the flag")
    assertEquals(listOf(20.0), dao.observeCounters("proj-s").first().map { it.quantityIn }, "a reload replaces the counters")

    db.projectDao().deleteByLocalId("proj-s")
    assertNull(dao.findSnapshot("proj-s"), "cascade with the project")
    assertTrue(dao.observeCounters("proj-s").first().isEmpty())
}

suspend fun verifySyncIssueContract(db: AppDatabase) {
    val rejected = com.dmb.chantiertracker.data.sync.SyncError.REJECTED
    val gone = com.dmb.chantiertracker.data.sync.SyncError.DELETED_ON_SERVER
    val updateRefused = com.dmb.chantiertracker.data.sync.SyncError.UPDATE_REFUSED
    val fileRefused = com.dmb.chantiertracker.data.sync.SyncError.FILE_REFUSED

    db.projectDao().upsert(localProject("p-ok", serverId = 1, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    db.projectDao().upsert(localProject("p-refused", syncStatus = SyncStatus.CONFLICTED, lastSyncError = com.dmb.chantiertracker.data.sync.SyncError.PLAN_LIMIT).copy(serverErrorCode = "PLAN_LIMIT_EXCEEDED"))
    db.projectDao().upsert(localProject("p-ghost", serverId = 3, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.CONFLICTED, lastSyncError = gone))
    db.projectDao().upsert(localProject("p-edit", serverId = 4, pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.CONFLICTED, lastSyncError = updateRefused))

    db.stageDao().upsert(localStage("st-ok", projectLocalId = "p-ok", serverId = 10, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    db.stageDao().upsert(localStage("st-waiting", projectLocalId = "p-ok"))
    db.stageDao().upsert(localStage("st-under-refused", projectLocalId = "p-refused"))
    db.stageDao().upsert(localStage("st-refused", projectLocalId = "p-ok", syncStatus = SyncStatus.CONFLICTED, lastSyncError = rejected))
    db.stageDao().upsert(localStage("st-ghost", projectLocalId = "p-ghost", serverId = 13, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.CONFLICTED, lastSyncError = gone))
    db.stageDao().upsert(localStage("st-under-edit", projectLocalId = "p-edit"))

    db.materialDao().upsert(localMaterial("m-ok", projectLocalId = "p-ok", name = "Ciment", serverId = 20, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    db.materialDao().upsert(localMaterial("m-under-refused", projectLocalId = "p-refused", name = "Sable"))
    db.materialDao().upsert(localMaterial("m-refused", projectLocalId = "p-ok", name = "Gravier").copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = rejected))

    listOf("l-ok" to "st-ok", "l-waiting" to "st-waiting", "l-under-refused" to "st-under-refused", "l-refused" to "st-refused", "l-ghost" to "st-ghost")
        .forEach { (log, stage) -> db.dailyLogDao().upsert(localDailyLog(log, stageLocalId = stage)) }
    db.dailyEntryDao().upsert(localDailyEntry("e-ok", dailyLogLocalId = "l-ok", serverId = 30, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED))
    db.dailyEntryDao().upsert(localDailyEntry("e-refused", dailyLogLocalId = "l-ok", type = "WORK", syncStatus = SyncStatus.CONFLICTED, lastSyncError = rejected))
    db.dailyEntryDao().upsert(localDailyEntry("e-under-waiting-stage", dailyLogLocalId = "l-waiting"))
    db.dailyEntryDao().upsert(localDailyEntry("e-under-refused-project", dailyLogLocalId = "l-under-refused"))
    db.dailyEntryDao().upsert(localDailyEntry("e-under-refused-stage", dailyLogLocalId = "l-refused"))
    db.dailyEntryDao().upsert(localDailyEntry("e-ghost", dailyLogLocalId = "l-ghost", serverId = 34, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.CONFLICTED, lastSyncError = gone))

    db.purchaseLineDao().upsert(localPurchaseLine("pl-waiting", entryLocalId = "e-ok", materialLocalId = "m-ok"))
    db.purchaseLineDao().upsert(localPurchaseLine("pl-under-refused-entry", entryLocalId = "e-refused", materialLocalId = "m-ok"))
    db.purchaseLineDao().upsert(localPurchaseLine("pl-with-refused-material", entryLocalId = "e-ok", materialLocalId = "m-refused"))
    db.purchaseLineDao().upsert(localPurchaseLine("pl-deep", entryLocalId = "e-under-refused-project", materialLocalId = "m-under-refused"))
    db.purchaseLineDao().upsert(localPurchaseLine("pl-edit", entryLocalId = "e-ok", materialLocalId = "m-ok", serverId = 40, pendingOp = PendingOp.UPDATE, syncStatus = SyncStatus.CONFLICTED).copy(lastSyncError = updateRefused))
    db.purchaseLineDao().upsert(localPurchaseLine("pl-on-ghost", entryLocalId = "e-ghost", materialLocalId = "m-ok", syncStatus = SyncStatus.CONFLICTED).copy(lastSyncError = gone))
    db.consumptionLineDao().upsert(localConsumptionLine("cl-under-refused-entry", entryLocalId = "e-refused", materialLocalId = "m-ok"))
    db.consumptionLineDao().upsert(localConsumptionLine("cl-waiting", entryLocalId = "e-ok", materialLocalId = "m-ok"))
    db.attachmentDao().upsert(localAttachment("a-under-refused-entry", entryLocalId = "e-refused"))
    db.attachmentDao().upsert(localAttachment("a-waiting", entryLocalId = "e-ok"))
    db.attachmentDao().upsert(localAttachment("a-too-large", entryLocalId = "e-ok").copy(syncStatus = SyncStatus.CONFLICTED, lastSyncError = fileRefused, serverErrorCode = "ATTACHMENT_TOO_LARGE"))

    assertEquals(listOf("st-under-refused"), db.stageDao().observeBlockedByParent().first(), "a stage under a refused project; not one under a project whose edit was refused")
    assertEquals(listOf("m-under-refused"), db.materialDao().observeBlockedByParent().first())
    assertEquals(
        listOf("e-under-refused-project", "e-under-refused-stage"),
        db.dailyEntryDao().observeBlockedByParent().first().sorted(),
        "an entry under a refused stage, or under a stage itself waiting on a refused project; not one under a stage that is simply waiting",
    )
    assertEquals(
        listOf("pl-deep", "pl-under-refused-entry", "pl-with-refused-material"),
        db.purchaseLineDao().observeBlockedByParent().first().sorted(),
    )
    assertEquals(listOf("cl-under-refused-entry"), db.consumptionLineDao().observeBlockedByParent().first())
    assertEquals(listOf("a-under-refused-entry"), db.attachmentDao().observeBlockedByParent().first())

    assertEquals(listOf("p-refused"), db.projectDao().findPending().map { it.localId }, "a refused edit and a ghost wait for the user, a refused creation is sent again")
    assertEquals(listOf("pl-deep", "pl-under-refused-entry", "pl-waiting", "pl-with-refused-material"), db.purchaseLineDao().findPending().map { it.localId }.sorted())
    assertEquals(listOf("a-under-refused-entry", "a-waiting"), db.attachmentDao().findPending().map { it.localId }.sorted(), "a refused file is not uploaded again")

    val unsent = db.localDataDao().countUnsentByKind()
    assertEquals(2, unsent.projects, "the refused creation and the refused edit; the ghost project is already on the server")
    assertEquals(4, unsent.stages, "the ghost stage does not count")
    assertEquals(2, unsent.materials)
    assertEquals(4, unsent.entries, "the ghost entry does not count")
    assertEquals(8, unsent.lines)
    assertEquals(3, unsent.attachments)
    assertEquals(26, db.localDataDao().countUnsynced(), "the raw count still sees the three ghosts (ADR-69 erase guard)")

    assertEquals("PLAN_LIMIT_EXCEEDED", db.projectDao().findByLocalId("p-refused")?.serverErrorCode)
    assertEquals("ATTACHMENT_TOO_LARGE", db.attachmentDao().findByLocalId("a-too-large")?.serverErrorCode)
}
