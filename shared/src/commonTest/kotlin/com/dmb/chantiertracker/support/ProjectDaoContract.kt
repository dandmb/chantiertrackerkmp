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
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import kotlinx.coroutines.flow.first
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    verifySyncIssueListContract(db)
}

private suspend fun verifySyncIssueListContract(db: AppDatabase) {
    val updateRefused = com.dmb.chantiertracker.data.sync.SyncError.UPDATE_REFUSED
    db.stageDao().upsert(
        localStage("st-delete-refused", projectLocalId = "p-ok", name = "Toiture", serverId = 14, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.SYNCED, lastSyncError = com.dmb.chantiertracker.data.sync.SyncError.REJECTED)
            .copy(serverErrorCode = "PROJECT_INSUFFICIENT_ROLE"),
    )

    val unsettled = db.syncIssueDao().observeUnsettled().first().associateBy { it.localId }

    assertEquals(
        setOf(
            "p-refused", "p-edit",
            "st-waiting", "st-under-refused", "st-refused", "st-under-edit", "st-delete-refused",
            "m-under-refused", "m-refused",
            "e-refused", "e-under-waiting-stage", "e-under-refused-project", "e-under-refused-stage",
            "pl-waiting", "pl-under-refused-entry", "pl-with-refused-material", "pl-deep", "pl-edit", "pl-on-ghost",
            "cl-under-refused-entry", "cl-waiting",
            "a-under-refused-entry", "a-waiting", "a-too-large",
        ),
        unsettled.keys,
        "everything not settled, the refused delete included; the three ghosts and the synced rows are left out",
    )

    val project = unsettled.getValue("p-refused")
    assertEquals(SyncIssueTarget.PROJECT, project.target)
    assertEquals(listOf("p-refused", "Chantier p-refused", null, null, null), listOf(project.projectLocalId, project.projectName, project.stageLocalId, project.dailyLogLocalId, project.logDate))
    assertEquals("PLAN_LIMIT_EXCEEDED", project.serverErrorCode)

    val refusedDelete = unsettled.getValue("st-delete-refused")
    assertEquals(SyncIssueTarget.STAGE, refusedDelete.target)
    assertEquals(listOf(SyncStatus.SYNCED, PendingOp.NONE, "PROJECT_INSUFFICIENT_ROLE"), listOf(refusedDelete.syncStatus, refusedDelete.pendingOp, refusedDelete.serverErrorCode))
    assertEquals(listOf("p-ok", "st-delete-refused", "Toiture"), listOf(refusedDelete.projectLocalId, refusedDelete.stageLocalId, refusedDelete.stageName))

    val material = unsettled.getValue("m-refused")
    assertEquals(SyncIssueTarget.MATERIAL, material.target)
    assertEquals(listOf("p-ok", "Gravier", "unité", null), listOf(material.projectLocalId, material.label, material.unit, material.stageLocalId))

    val entry = unsettled.getValue("e-refused")
    assertEquals(SyncIssueTarget.ENTRY, entry.target)
    assertEquals(listOf("p-ok", "st-ok", "l-ok", "2026-09-05", "WORK"), listOf(entry.projectLocalId, entry.stageLocalId, entry.dailyLogLocalId, entry.logDate, entry.entryType))

    val deepLine = unsettled.getValue("pl-deep")
    assertEquals(SyncIssueTarget.PURCHASE_LINE, deepLine.target)
    assertEquals(
        listOf("p-refused", "Chantier p-refused", "st-under-refused", "Étape st-under-refused", "l-under-refused", "PURCHASE", "Sable", "unité"),
        listOf(deepLine.projectLocalId, deepLine.projectName, deepLine.stageLocalId, deepLine.stageName, deepLine.dailyLogLocalId, deepLine.entryType, deepLine.label, deepLine.unit),
    )
    assertEquals(1.0, deepLine.quantity)

    assertEquals(SyncIssueTarget.CONSUMPTION_LINE, unsettled.getValue("cl-under-refused-entry").target)
    assertEquals("WORK", unsettled.getValue("cl-under-refused-entry").entryType)

    val file = unsettled.getValue("a-too-large")
    assertEquals(SyncIssueTarget.ATTACHMENT, file.target)
    assertEquals(listOf("p-ok", "st-ok", "l-ok", "photo-a-too-large.jpg", "ATTACHMENT_TOO_LARGE"), listOf(file.projectLocalId, file.stageLocalId, file.dailyLogLocalId, file.label, file.serverErrorCode))

    db.purchaseLineDao().upsert(db.purchaseLineDao().findByLocalId("pl-edit")!!.copy(serverErrorCode = "STOCK_CONSUMED", quantity = 3.0, serverQuantity = 10.0))
    val refusedEdit = db.syncIssueDao().observeUnsettled().first().single { it.localId == "pl-edit" }
    assertEquals(listOf(3.0, 10.0), listOf(refusedEdit.quantity, refusedEdit.serverQuantity))

    db.syncIssueDao().sendRefusedUpdateAgain(SyncIssueTarget.PURCHASE_LINE, "pl-edit")
    val queued = db.purchaseLineDao().findByLocalId("pl-edit")!!
    assertEquals<List<Any?>>(listOf(SyncStatus.PENDING, PendingOp.UPDATE, null, null), listOf(queued.syncStatus, queued.pendingOp, queued.lastSyncError, queued.serverErrorCode))
    assertTrue(db.purchaseLineDao().findPending().any { it.localId == "pl-edit" }, "back in the queue of the next pass")

    db.syncIssueDao().freezeUnsentUpdateAgain(SyncIssueTarget.PURCHASE_LINE, "pl-edit", "STOCK_CONSUMED")
    val frozen = db.purchaseLineDao().findByLocalId("pl-edit")!!
    assertEquals(listOf(SyncStatus.CONFLICTED, updateRefused, "STOCK_CONSUMED"), listOf(frozen.syncStatus, frozen.lastSyncError, frozen.serverErrorCode))

    db.syncIssueDao().sendRefusedUpdateAgain(SyncIssueTarget.PROJECT, "p-edit")
    assertEquals(SyncStatus.PENDING, db.projectDao().findByLocalId("p-edit")!!.syncStatus)
    db.syncIssueDao().freezeUnsentUpdateAgain(SyncIssueTarget.PROJECT, "p-edit", null)
    assertEquals(updateRefused, db.projectDao().findByLocalId("p-edit")!!.lastSyncError)

    db.syncIssueDao().sendRefusedUpdateAgain(SyncIssueTarget.PROJECT, "p-refused")
    assertEquals(SyncStatus.CONFLICTED, db.projectDao().findByLocalId("p-refused")!!.syncStatus, "a refused creation is not an update to send again")
    db.syncIssueDao().freezeUnsentUpdateAgain(SyncIssueTarget.STAGE, "st-waiting", "X")
    assertEquals(SyncStatus.PENDING, db.stageDao().findByLocalId("st-waiting")!!.syncStatus, "a creation waiting to be sent is never frozen")

    val before = db.localDataDao().countUnsentByKind()
    db.projectDao().upsert(localProject("p-no-error", serverId = 90, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.PENDING))
    db.stageDao().upsert(localStage("st-no-error", projectLocalId = "p-ok", serverId = 91, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.PENDING))
    db.materialDao().upsert(localMaterial("m-no-error", projectLocalId = "p-ok", name = "Chaux", serverId = 92, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.PENDING))
    db.dailyEntryDao().upsert(localDailyEntry("e-no-error", dailyLogLocalId = "l-waiting", type = "WORK", serverId = 93, pendingOp = PendingOp.NONE, syncStatus = SyncStatus.PENDING))
    val after = db.localDataDao().countUnsentByKind()
    assertEquals(
        listOf(before.projects + 1, before.stages + 1, before.materials + 1, before.entries + 1),
        listOf(after.projects, after.stages, after.materials, after.entries),
        "an unsent row with no sync error at all is not a ghost: it must be counted",
    )
}

suspend fun verifySyncIssueActionsContract(db: AppDatabase) {
    val rejected = com.dmb.chantiertracker.data.sync.SyncError.REJECTED
    val gone = com.dmb.chantiertracker.data.sync.SyncError.DELETED_ON_SERVER
    val updateRefused = com.dmb.chantiertracker.data.sync.SyncError.UPDATE_REFUSED
    val actions = db.syncIssueActionDao()
    val synced = SyncStatus.SYNCED
    val refused = SyncStatus.CONFLICTED

    db.projectDao().upsert(localProject("p-refused", syncStatus = refused, lastSyncError = com.dmb.chantiertracker.data.sync.SyncError.PLAN_LIMIT))
    db.stageDao().upsert(localStage("s1", projectLocalId = "p-refused"))
    db.stageDao().upsert(localStage("s2", projectLocalId = "p-refused"))
    db.materialDao().upsert(localMaterial("m1", projectLocalId = "p-refused", name = "Sable"))
    db.dailyLogDao().upsert(localDailyLog("l1", stageLocalId = "s1"))
    db.dailyEntryDao().upsert(localDailyEntry("e1", dailyLogLocalId = "l1", type = "PURCHASE"))
    db.dailyEntryDao().upsert(localDailyEntry("e2", dailyLogLocalId = "l1", type = "WORK"))
    db.purchaseLineDao().upsert(localPurchaseLine("pl1", entryLocalId = "e1", materialLocalId = "m1"))
    db.consumptionLineDao().upsert(localConsumptionLine("cl1", entryLocalId = "e2", materialLocalId = "m1"))
    db.attachmentDao().upsert(localAttachment("a1", entryLocalId = "e1", localPath = "files/a1.jpg"))

    db.projectDao().upsert(localProject("p-ok", serverId = 1, pendingOp = PendingOp.NONE, syncStatus = synced))
    db.stageDao().upsert(localStage("s-ok", projectLocalId = "p-ok", serverId = 10, pendingOp = PendingOp.NONE, syncStatus = synced))
    db.stageDao().upsert(localStage("s-delete-refused", projectLocalId = "p-ok", serverId = 11, pendingOp = PendingOp.NONE, syncStatus = synced, lastSyncError = rejected).copy(serverErrorCode = "PROJECT_INSUFFICIENT_ROLE"))
    db.materialDao().upsert(localMaterial("m-ok", projectLocalId = "p-ok", name = "Ciment", serverId = 20, pendingOp = PendingOp.NONE, syncStatus = synced))
    db.materialDao().upsert(localMaterial("m-refused", projectLocalId = "p-ok", name = "Gravier").copy(syncStatus = refused, lastSyncError = rejected))
    db.dailyLogDao().upsert(localDailyLog("l-ok", stageLocalId = "s-ok", date = "2026-09-05", serverId = 800))
    db.dailyLogDao().upsert(localDailyLog("l-two", stageLocalId = "s-ok", date = "2026-09-06", serverId = 801))
    db.dailyLogDao().upsert(localDailyLog("l-local", stageLocalId = "s-ok", date = "2026-09-07"))
    db.dailyLogDao().upsert(localDailyLog("l-gone", stageLocalId = "s-ok", date = "2026-09-08", serverId = 803))
    db.dailyEntryDao().upsert(localDailyEntry("e-ok", dailyLogLocalId = "l-ok", type = "PURCHASE", serverId = 30, pendingOp = PendingOp.NONE, syncStatus = synced))
    db.dailyEntryDao().upsert(localDailyEntry("e-ok-work", dailyLogLocalId = "l-ok", type = "WORK", serverId = 31, pendingOp = PendingOp.NONE, syncStatus = synced))
    db.dailyEntryDao().upsert(localDailyEntry("e-refused", dailyLogLocalId = "l-two", type = "WORK", syncStatus = refused, lastSyncError = rejected))
    db.dailyEntryDao().upsert(localDailyEntry("e-local-day", dailyLogLocalId = "l-local", type = "PURCHASE", syncStatus = refused, lastSyncError = rejected))
    db.dailyEntryDao().upsert(localDailyEntry("e-gone", dailyLogLocalId = "l-gone", type = "PURCHASE", serverId = 77, pendingOp = PendingOp.UPDATE, syncStatus = refused, lastSyncError = gone))
    db.purchaseLineDao().upsert(localPurchaseLine("pl-ok", entryLocalId = "e-ok", materialLocalId = "m-ok", serverId = 40, pendingOp = PendingOp.NONE, syncStatus = synced))
    db.purchaseLineDao().upsert(localPurchaseLine("pl-with-refused-material", entryLocalId = "e-ok", materialLocalId = "m-refused"))
    db.purchaseLineDao().upsert(localPurchaseLine("pl-on-gone", entryLocalId = "e-gone", materialLocalId = "m-ok"))
    db.purchaseLineDao().upsert(
        localPurchaseLine("pl-change", entryLocalId = "e-ok", materialLocalId = "m-ok", quantity = 3.0, serverId = 41, pendingOp = PendingOp.UPDATE, syncStatus = refused)
            .copy(lastSyncError = updateRefused, serverErrorCode = "STOCK_CONSUMED", serverQuantity = 10.0),
    )
    db.consumptionLineDao().upsert(localConsumptionLine("cl-under-refused", entryLocalId = "e-refused", materialLocalId = "m-ok"))
    db.consumptionLineDao().upsert(
        localConsumptionLine("cl-change", entryLocalId = "e-ok-work", materialLocalId = "m-ok", quantity = 50.0, serverId = 60, pendingOp = PendingOp.UPDATE, syncStatus = refused)
            .copy(lastSyncError = updateRefused, serverErrorCode = "INSUFFICIENT_STOCK", serverQuantity = 2.0),
    )
    db.consumptionLineDao().upsert(
        localConsumptionLine("cl-change-unknown", entryLocalId = "e-ok-work", materialLocalId = "m-ok", quantity = 9.0, serverId = 61, pendingOp = PendingOp.UPDATE, syncStatus = refused)
            .copy(lastSyncError = updateRefused, serverQuantity = null),
    )
    db.attachmentDao().upsert(localAttachment("a-refused", entryLocalId = "e-ok", localPath = "files/af.jpg").copy(syncStatus = refused, lastSyncError = com.dmb.chantiertracker.data.sync.SyncError.FILE_REFUSED))

    assertEquals(8, actions.countLinked(SyncIssueTarget.PROJECT, "p-refused"), "2 stages, 1 material, 2 entries, 2 lines, 1 file")
    assertEquals(5, actions.countLinked(SyncIssueTarget.STAGE, "s1"), "2 entries, 2 lines, 1 file")
    assertEquals(0, actions.countLinked(SyncIssueTarget.STAGE, "s2"))
    assertEquals(2, actions.countLinked(SyncIssueTarget.ENTRY, "e1"))
    assertEquals(2, actions.countLinked(SyncIssueTarget.MATERIAL, "m1"))
    assertEquals(1, actions.countLinked(SyncIssueTarget.ENTRY, "e-refused"))
    assertEquals(1, actions.countLinked(SyncIssueTarget.MATERIAL, "m-refused"))
    assertEquals(1, actions.countLinked(SyncIssueTarget.ENTRY, "e-gone"))
    assertEquals(0, actions.countLinked(SyncIssueTarget.PURCHASE_LINE, "pl1"))
    assertEquals(0, actions.countLinked(SyncIssueTarget.ATTACHMENT, "a-refused"))

    assertEquals(emptyList(), actions.removeLocally(SyncIssueTarget.PURCHASE_LINE, "pl-change"), "a refused change exists on the server: never removed")
    assertNotNull(db.purchaseLineDao().findByLocalId("pl-change"))
    assertEquals(emptyList(), actions.removeLocally(SyncIssueTarget.STAGE, "s-ok"))
    assertEquals(emptyList(), actions.removeLocally(SyncIssueTarget.PROJECT, "p-ok"))
    assertEquals(emptyList(), actions.removeLocally(SyncIssueTarget.STAGE, "s-delete-refused"))
    assertEquals(emptyList(), actions.removeLocally(SyncIssueTarget.ENTRY, "e1"), "an entry simply waiting is not removed on its own")
    assertNotNull(db.dailyEntryDao().findByLocalId("e1"))
    assertEquals(emptyList(), actions.removeLocally(SyncIssueTarget.ENTRY, "does-not-exist"))
    assertNotNull(db.stageDao().findByLocalId("s-ok"))
    assertNotNull(db.projectDao().findByLocalId("p-ok"))

    assertEquals(listOf("files/af.jpg"), actions.removeLocally(SyncIssueTarget.ATTACHMENT, "a-refused"), "the file to delete is handed back")
    assertNull(db.attachmentDao().findByLocalId("a-refused"))
    assertEquals(emptyList(), actions.removeLocally(SyncIssueTarget.ATTACHMENT, "a-refused"), "a second call finds nothing and does nothing")

    actions.removeLocally(SyncIssueTarget.MATERIAL, "m-refused")
    assertNull(db.materialDao().findByLocalId("m-refused"))
    assertNull(db.purchaseLineDao().findByLocalId("pl-with-refused-material"), "the line that needed the refused material goes with it")
    assertNotNull(db.purchaseLineDao().findByLocalId("pl-ok"))

    actions.removeLocally(SyncIssueTarget.ENTRY, "e-refused")
    assertNull(db.dailyEntryDao().findByLocalId("e-refused"))
    assertNull(db.consumptionLineDao().findByLocalId("cl-under-refused"))
    assertNotNull(db.dailyLogDao().findByLocalId("l-two"), "a day the server knows is kept")

    actions.removeLocally(SyncIssueTarget.ENTRY, "e-local-day")
    assertNull(db.dailyEntryDao().findByLocalId("e-local-day"))
    assertNull(db.dailyLogDao().findByLocalId("l-local"), "a day that only existed for the discarded entry goes with it")

    actions.removeLocally(SyncIssueTarget.ENTRY, "e-gone")
    assertNull(db.dailyEntryDao().findByLocalId("e-gone"), "what was deleted on the server can be purged even with a server id")
    assertNull(db.purchaseLineDao().findByLocalId("pl-on-gone"))

    assertEquals(listOf("files/a1.jpg"), actions.removeLocally(SyncIssueTarget.PROJECT, "p-refused"))
    assertNull(db.projectDao().findByLocalId("p-refused"))
    assertEquals(listOf<Any?>(null, null, null, null, null, null, null, null, null), listOf(
        db.stageDao().findByLocalId("s1"), db.stageDao().findByLocalId("s2"), db.materialDao().findByLocalId("m1"), db.dailyLogDao().findByLocalId("l1"),
        db.dailyEntryDao().findByLocalId("e1"), db.dailyEntryDao().findByLocalId("e2"), db.purchaseLineDao().findByLocalId("pl1"),
        db.consumptionLineDao().findByLocalId("cl1"), db.attachmentDao().findByLocalId("a1"),
    ))
    assertEquals(emptyList(), actions.removeLocally(SyncIssueTarget.PROJECT, "p-refused"))

    assertEquals(emptyList(), db.projectDao().findPending().map { it.localId }, "nothing removed is left in the queue of the next pass")
    assertEquals(emptyList(), db.stageDao().findPending().map { it.localId })
    assertEquals(emptyList(), db.materialDao().findPending().map { it.localId })
    assertEquals(emptyList(), db.dailyEntryDao().findPending().map { it.localId })
    assertEquals(emptyList(), db.purchaseLineDao().findPending().map { it.localId })
    assertEquals(emptyList(), db.consumptionLineDao().findPending().map { it.localId })
    assertEquals(emptyList(), db.attachmentDao().findPending().map { it.localId })
    assertEquals(
        setOf("s-delete-refused", "pl-change", "cl-change", "cl-change-unknown"),
        db.syncIssueDao().observeUnsettled().first().map { it.localId }.toSet(),
        "only what was not acted upon is still to review",
    )
    assertEquals(listOf("e-ok", "e-ok-work"), db.dailyEntryDao().findForLog("l-ok").map { it.localId }.sorted(), "the synced entries are untouched")

    actions.forgetRefusedDelete(SyncIssueTarget.PURCHASE_LINE, "pl-change")
    assertEquals(updateRefused, db.purchaseLineDao().findByLocalId("pl-change")!!.lastSyncError, "only a refused delete is forgotten")
    actions.forgetRefusedDelete(SyncIssueTarget.STAGE, "s-delete-refused")
    val forgotten = db.stageDao().findByLocalId("s-delete-refused")!!
    assertEquals<List<Any?>>(listOf(synced, PendingOp.NONE, null, null), listOf(forgotten.syncStatus, forgotten.pendingOp, forgotten.lastSyncError, forgotten.serverErrorCode))
    actions.forgetRefusedDelete(SyncIssueTarget.STAGE, "s-delete-refused")

    assertTrue(!actions.restoreKnownServerValue(SyncIssueTarget.PURCHASE_LINE, "pl-change"), "the price and supplier the server holds are not on the device")
    assertEquals(3.0, db.purchaseLineDao().findByLocalId("pl-change")!!.quantity)
    assertTrue(!actions.restoreKnownServerValue(SyncIssueTarget.CONSUMPTION_LINE, "cl-change-unknown"))
    assertEquals(9.0, db.consumptionLineDao().findByLocalId("cl-change-unknown")!!.quantity)
    assertTrue(actions.restoreKnownServerValue(SyncIssueTarget.CONSUMPTION_LINE, "cl-change"))
    val restored = db.consumptionLineDao().findByLocalId("cl-change")!!
    assertEquals<List<Any?>>(listOf(2.0, synced, PendingOp.NONE, null, null), listOf(restored.quantity, restored.syncStatus, restored.pendingOp, restored.lastSyncError, restored.serverErrorCode))
    assertTrue(!actions.restoreKnownServerValue(SyncIssueTarget.CONSUMPTION_LINE, "cl-change"), "restoring twice changes nothing more")
    assertEquals(setOf("pl-change", "cl-change-unknown"), db.syncIssueDao().observeUnsettled().first().map { it.localId }.toSet())
}

