package com.dmb.chantiertracker.data.repository

import com.dmb.chantiertracker.data.local.db.PendingOp
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.data.sync.SyncError
import com.dmb.chantiertracker.data.sync.SyncOutcome
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.SyncIssue
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.SyncIssueParent
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.domain.repository.RetryOutcome
import com.dmb.chantiertracker.support.FakeAttachmentDao
import com.dmb.chantiertracker.support.FakeConsumptionLineDao
import com.dmb.chantiertracker.support.FakeDailyEntryDao
import com.dmb.chantiertracker.support.FakeMaterialDao
import com.dmb.chantiertracker.support.FakePurchaseLineDao
import com.dmb.chantiertracker.support.FakeStageDao
import com.dmb.chantiertracker.support.FakeSyncIssueDao
import com.dmb.chantiertracker.support.FakeSyncer
import com.dmb.chantiertracker.support.syncIssueRow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncIssueRepositoryImplTest {

    private val dao = FakeSyncIssueDao()
    private val stageDao = FakeStageDao()
    private val materialDao = FakeMaterialDao()
    private val entryDao = FakeDailyEntryDao()
    private val purchaseLineDao = FakePurchaseLineDao()
    private val consumptionLineDao = FakeConsumptionLineDao()
    private val attachmentDao = FakeAttachmentDao()
    private val syncer = FakeSyncer()
    private val repository =
        SyncIssueRepositoryImpl(dao, stageDao, materialDao, entryDao, purchaseLineDao, consumptionLineDao, attachmentDao, syncer)

    private val refusedEntry = syncIssueRow(
        SyncIssueTarget.ENTRY, "e1", serverErrorCode = "PROJECT_OR_STAGE_INACTIVE",
        stageLocalId = "st1", stageName = "Charpente", dailyLogLocalId = "l1", logDate = "2026-10-09", entryType = "PURCHASE",
    )
    private val waitingLine = syncIssueRow(
        SyncIssueTarget.PURCHASE_LINE, "pl1", syncStatus = SyncStatus.PENDING, lastSyncError = null,
        stageLocalId = "st1", stageName = "Charpente", dailyLogLocalId = "l1", logDate = "2026-10-09", entryType = "PURCHASE",
        label = "Ciment", unit = "sac", quantity = 3.0,
    )
    private val refusedLineUpdate = syncIssueRow(
        SyncIssueTarget.PURCHASE_LINE, "pl-edit", pendingOp = PendingOp.UPDATE, lastSyncError = SyncError.UPDATE_REFUSED,
        serverErrorCode = "PROJECT_OR_STAGE_INACTIVE", serverId = 40,
        stageLocalId = "st1", stageName = "Charpente", dailyLogLocalId = "l1", logDate = "2026-10-09", entryType = "PURCHASE",
        label = "Ciment", unit = "sac", quantity = 3.0, serverQuantity = 10.0,
    )

    @Test
    fun a_refused_entry_is_listed_with_its_reason_and_where_it_lives() = runTest {
        dao.rows.value = listOf(refusedEntry)

        val item = repository.observeIssues().first().single()

        assertEquals(SyncIssueTarget.ENTRY, item.target)
        assertEquals(SyncIssue(SyncIssueKind.REFUSED, RefusalReason.PROJECT_OR_STAGE_INACTIVE, "PROJECT_OR_STAGE_INACTIVE"), item.issue)
        assertEquals(listOf("p1", "Villa Vidal", "st1", "Charpente", "l1", "2026-10-09"), listOf(item.projectLocalId, item.projectName, item.stageLocalId, item.stageName, item.dailyLogLocalId, item.date))
        assertEquals(EntryType.PURCHASE, item.entryType)
    }

    @Test
    fun a_child_waiting_on_a_refused_parent_is_listed_as_blocked_and_one_simply_waiting_to_be_sent_is_not() = runTest {
        dao.rows.value = listOf(refusedEntry, waitingLine, waitingLine.copy(localId = "pl-just-pending"))
        purchaseLineDao.blockedByParent.value = listOf("pl1")

        val items = repository.observeIssues().first().associateBy { it.localId }

        assertEquals(setOf("e1", "pl1"), items.keys)
        assertEquals(SyncIssue(SyncIssueKind.BLOCKED_BY_PARENT), items.getValue("pl1").issue)
        assertEquals(listOf("Ciment", "sac", 3.0), listOf(items.getValue("pl1").label, items.getValue("pl1").unit, items.getValue("pl1").quantity))
    }

    @Test
    fun every_table_feeds_the_blocked_children() = runTest {
        val pending = { target: SyncIssueTarget, id: String -> syncIssueRow(target, id, syncStatus = SyncStatus.PENDING, lastSyncError = null) }
        dao.rows.value = listOf(
            pending(SyncIssueTarget.STAGE, "st"), pending(SyncIssueTarget.MATERIAL, "m"), pending(SyncIssueTarget.ENTRY, "e"),
            pending(SyncIssueTarget.PURCHASE_LINE, "pl"), pending(SyncIssueTarget.CONSUMPTION_LINE, "cl"), pending(SyncIssueTarget.ATTACHMENT, "a"),
        )
        stageDao.blockedByParent.value = listOf("st")
        materialDao.blockedByParent.value = listOf("m")
        entryDao.blockedByParent.value = listOf("e")
        purchaseLineDao.blockedByParent.value = listOf("pl")
        consumptionLineDao.blockedByParent.value = listOf("cl")
        attachmentDao.blockedByParent.value = listOf("a")

        val items = repository.observeIssues().first()

        assertEquals(6, items.size)
        assertTrue(items.all { it.issue.kind == SyncIssueKind.BLOCKED_BY_PARENT })
    }

    @Test
    fun the_count_is_what_the_server_refused_and_leaves_the_waiting_children_out() = runTest {
        dao.rows.value = listOf(
            refusedEntry, waitingLine, waitingLine.copy(localId = "pl-just-pending"), refusedLineUpdate,
            syncIssueRow(SyncIssueTarget.STAGE, "st-del", syncStatus = SyncStatus.SYNCED, pendingOp = PendingOp.NONE, serverId = 5),
            syncIssueRow(SyncIssueTarget.ENTRY, "e-gone", lastSyncError = SyncError.DELETED_ON_SERVER),
        )
        purchaseLineDao.blockedByParent.value = listOf("pl1")

        assertEquals(5, repository.observeIssues().first().size, "the waiting child is still listed")
        assertEquals(4, repository.observeIssueCount().first(), "refused creation, refused update, refused delete, gone on the server; not the waiting child")
    }

    private val pendingUnder = { target: SyncIssueTarget, id: String -> waitingLine.copy(target = target, localId = id) }

    @Test
    fun a_waiting_line_names_the_refused_entry_it_depends_on() = runTest {
        dao.rows.value = listOf(refusedEntry, waitingLine)
        purchaseLineDao.blockedByParent.value = listOf("pl1")

        val line = repository.observeIssues().first().single { it.localId == "pl1" }

        assertEquals(SyncIssueParent(SyncIssueTarget.ENTRY, name = null, entryType = EntryType.PURCHASE, date = "2026-10-09"), line.blockedBy)
    }

    @Test
    fun a_waiting_child_names_the_nearest_refused_parent_up_the_chain() = runTest {
        val refusedProject = syncIssueRow(SyncIssueTarget.PROJECT, "p1", lastSyncError = SyncError.PLAN_LIMIT)
        val waitingStage = syncIssueRow(SyncIssueTarget.STAGE, "st1", syncStatus = SyncStatus.PENDING, lastSyncError = null, stageLocalId = "st1", stageName = "Charpente")
        val waitingEntry = refusedEntry.copy(syncStatus = SyncStatus.PENDING, lastSyncError = null, serverErrorCode = null)
        dao.rows.value = listOf(refusedProject, waitingStage, waitingEntry, waitingLine, pendingUnder(SyncIssueTarget.ATTACHMENT, "a1"))
        stageDao.blockedByParent.value = listOf("st1")
        entryDao.blockedByParent.value = listOf("e1")
        purchaseLineDao.blockedByParent.value = listOf("pl1")
        attachmentDao.blockedByParent.value = listOf("a1")

        val items = repository.observeIssues().first().associateBy { it.localId }

        val project = SyncIssueParent(SyncIssueTarget.PROJECT, name = "Villa Vidal")
        assertEquals(listOf(project, project, project, project), listOf("st1", "e1", "pl1", "a1").map { items.getValue(it).blockedBy })
        assertNull(items.getValue("p1").blockedBy, "what is refused depends on nothing")
    }

    @Test
    fun a_waiting_entry_names_its_refused_stage_and_a_waiting_line_its_refused_material() = runTest {
        val refusedStage = syncIssueRow(SyncIssueTarget.STAGE, "st1", stageLocalId = "st1", stageName = "Charpente")
        val waitingEntry = refusedEntry.copy(syncStatus = SyncStatus.PENDING, lastSyncError = null, serverErrorCode = null)
        val refusedMaterial = syncIssueRow(SyncIssueTarget.MATERIAL, "m1", label = "Ciment", unit = "sac")
        val lineOnAnotherDay = waitingLine.copy(localId = "pl-material", stageLocalId = "st2", stageName = "Bardage", dailyLogLocalId = "l2", logDate = "2026-10-08")
        dao.rows.value = listOf(refusedStage, waitingEntry, refusedMaterial, lineOnAnotherDay)
        entryDao.blockedByParent.value = listOf("e1")
        purchaseLineDao.blockedByParent.value = listOf("pl-material")

        val items = repository.observeIssues().first().associateBy { it.localId }

        assertEquals(SyncIssueParent(SyncIssueTarget.STAGE, name = "Charpente"), items.getValue("e1").blockedBy)
        assertEquals(SyncIssueParent(SyncIssueTarget.MATERIAL, name = "Ciment"), items.getValue("pl-material").blockedBy)
    }

    @Test
    fun a_waiting_child_whose_parent_cannot_be_told_has_no_parent_rather_than_a_wrong_one() = runTest {
        dao.rows.value = listOf(waitingLine)
        purchaseLineDao.blockedByParent.value = listOf("pl1")

        assertNull(repository.observeIssues().first().single().blockedBy)
    }

    @Test
    fun a_refused_delete_and_a_row_gone_on_the_server_are_listed() = runTest {
        dao.rows.value = listOf(
            syncIssueRow(SyncIssueTarget.STAGE, "st-del", syncStatus = SyncStatus.SYNCED, pendingOp = PendingOp.NONE, serverId = 5, serverErrorCode = "PROJECT_INSUFFICIENT_ROLE"),
            syncIssueRow(SyncIssueTarget.ENTRY, "e-gone", lastSyncError = SyncError.DELETED_ON_SERVER),
        )

        val items = repository.observeIssues().first().associateBy { it.localId }

        assertEquals(SyncIssue(SyncIssueKind.DELETE_REFUSED, RefusalReason.INSUFFICIENT_ROLE, "PROJECT_INSUFFICIENT_ROLE"), items.getValue("st-del").issue)
        assertEquals(SyncIssue(SyncIssueKind.DELETED_ON_SERVER), items.getValue("e-gone").issue)
    }

    @Test
    fun retrying_a_refused_creation_runs_a_sync_and_reports_it_accepted_once_it_left() = runTest {
        dao.rows.value = listOf(refusedEntry)
        val item = repository.observeIssues().first().single()
        syncer.onSync = { dao.rows.value = emptyList() }

        assertEquals(RetryOutcome.ACCEPTED, repository.retry(item))
        assertEquals(1, syncer.syncCount)
        assertTrue(dao.sentAgain.isEmpty(), "a refused creation is already sent at every pass")
    }

    @Test
    fun retrying_reports_still_refused_when_the_item_is_still_listed_after_the_sync() = runTest {
        dao.rows.value = listOf(refusedEntry)
        val item = repository.observeIssues().first().single()

        assertEquals(RetryOutcome.STILL_REFUSED, repository.retry(item))
        assertEquals(1, syncer.syncCount)
    }

    @Test
    fun retrying_a_refused_update_puts_it_back_in_the_queue_before_the_sync() = runTest {
        dao.rows.value = listOf(refusedLineUpdate)
        val item = repository.observeIssues().first().single()
        assertEquals(SyncIssueKind.UPDATE_REFUSED, item.issue.kind)
        var queuedWhenTheSyncRan = false
        syncer.onSync = {
            queuedWhenTheSyncRan = dao.rows.value.single().syncStatus == SyncStatus.PENDING
            dao.rows.value = emptyList()
        }

        assertEquals(RetryOutcome.ACCEPTED, repository.retry(item))
        assertEquals(listOf(SyncIssueTarget.PURCHASE_LINE to "pl-edit"), dao.sentAgain)
        assertTrue(queuedWhenTheSyncRan)
    }

    @Test
    fun a_retry_that_could_not_reach_the_server_says_so_and_keeps_the_refused_update_listed() = runTest {
        dao.rows.value = listOf(refusedLineUpdate)
        val item = repository.observeIssues().first().single()

        syncer.outcome = SyncOutcome.Skipped
        assertEquals(RetryOutcome.NOT_SENT, repository.retry(item))
        syncer.outcome = SyncOutcome.Failed(DomainException.Network)
        assertEquals(RetryOutcome.NOT_SENT, repository.retry(item))

        assertEquals(Triple(SyncIssueTarget.PURCHASE_LINE, "pl-edit", "PROJECT_OR_STAGE_INACTIVE"), dao.frozenAgain.first())
        assertEquals(item, repository.observeIssues().first().single(), "offline, the refused update stays listed with its reason")
    }

    @Test
    fun an_item_that_cannot_be_retried_is_never_sent() = runTest {
        dao.rows.value = listOf(refusedEntry.copy(serverErrorCode = "DUPLICATE_ENTRY"))
        val item = repository.observeIssues().first().single()

        assertEquals(RetryOutcome.STILL_REFUSED, repository.retry(item))
        assertEquals(0, syncer.syncCount)
    }
}
