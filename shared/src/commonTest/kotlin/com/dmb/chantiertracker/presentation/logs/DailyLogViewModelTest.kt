package com.dmb.chantiertracker.presentation.logs

import com.dmb.chantiertracker.domain.model.Attachment
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.ConsumptionLine
import com.dmb.chantiertracker.domain.model.DailyEntry
import com.dmb.chantiertracker.domain.model.DailyLogDetail
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.Material
import com.dmb.chantiertracker.domain.model.MaterialStock
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectMember
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.PurchaseLine
import com.dmb.chantiertracker.domain.model.StageDetail
import com.dmb.chantiertracker.domain.model.StageStatus
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.presentation.todayIn
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.entry_summary_required_work
import com.dmb.chantiertracker.support.FakeAttachmentRepository
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeConsumptionLineRepository
import com.dmb.chantiertracker.support.FakeDailyLogRepository
import com.dmb.chantiertracker.support.FakeMaterialRepository
import com.dmb.chantiertracker.support.FakeProjectRepository
import com.dmb.chantiertracker.support.FakePurchaseLineRepository
import com.dmb.chantiertracker.support.FakeStageRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DailyLogViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private val today = todayIn("Europe/Paris").toString()

    private fun logDetail(date: String = today, entries: List<DailyEntry> = emptyList()) =
        DailyLogDetail(localId = "log-1", stageLocalId = "s1", date = date, entries = entries)

    private fun stageRepo(status: StageStatus = StageStatus.IN_PROGRESS) = FakeStageRepository(
        detail = StageDetail(
            localId = "s1", projectLocalId = "p1", name = "Gros œuvre", description = null,
            estimatedBudget = null, startDate = null, endDate = null, status = status,
        ),
    )

    private fun projectRepo(
        ownerId: Long? = 1L,
        status: ProjectStatus = ProjectStatus.IN_PROGRESS,
        members: List<ProjectMember> = emptyList(),
    ) = FakeProjectRepository(
        detail = ProjectDetail(
            localId = "p1", name = "Villa", description = null, location = null,
            currency = "EUR", timezone = "Europe/Paris", status = status, ownerId = ownerId,
        ),
        members = members,
    )

    private fun auth(userId: Long) = FakeAuthRepository().apply {
        emitState(AuthState.Authenticated(User(userId, "u@x.dev", "U", true, GlobalRole.USER)))
    }

    private fun vm(
        logs: FakeDailyLogRepository,
        stages: FakeStageRepository = stageRepo(),
        projects: FakeProjectRepository = projectRepo(),
        authRepo: FakeAuthRepository = auth(userId = 1L),
        materials: FakeMaterialRepository = FakeMaterialRepository(),
        purchaseLines: FakePurchaseLineRepository = FakePurchaseLineRepository(),
        consumptionLines: FakeConsumptionLineRepository = FakeConsumptionLineRepository(),
        attachments: FakeAttachmentRepository = FakeAttachmentRepository(),
    ) = DailyLogViewModel(logs, stages, projects, authRepo, materials, purchaseLines, consumptionLines, attachments)

    @Test
    fun observes_the_log_and_kicks_a_pull() = runTest {
        val logs = FakeDailyLogRepository(detail = logDetail())
        val v = vm(logs)
        v.load("log-1")
        advanceUntilIdle()

        val state = v.state.value
        assertFalse(state.isLoading)
        assertEquals(today, state.detail?.date)
        assertEquals(1, logs.refreshLogCount)
        assertTrue(logs.log.contains("refreshLog:log-1"))
    }

    @Test
    fun the_owner_can_edit_a_past_day() = runTest {
        val logs = FakeDailyLogRepository(detail = logDetail(date = "2020-01-01"))
        val v = vm(logs, projects = projectRepo(ownerId = 1L))
        v.load("log-1")
        advanceUntilIdle()

        assertTrue(v.state.value.canEdit, "an ADMIN can edit any date")
    }

    @Test
    fun a_supervisor_can_only_edit_todays_log() = runTest {
        val members = listOf(ProjectMember(userId = 9, name = "Sam", email = "s@x.dev", role = ProjectRole.SUPERVISOR))
        val logs = FakeDailyLogRepository(detail = logDetail(date = "2020-01-01"))
        val v = vm(logs, projects = projectRepo(ownerId = 1L, members = members), authRepo = auth(userId = 9L))
        v.load("log-1")
        advanceUntilIdle()

        assertFalse(v.state.value.canEdit, "not today's log")
    }

    @Test
    fun a_supervisor_can_edit_todays_log_while_the_project_and_stage_are_active() = runTest {
        val members = listOf(ProjectMember(userId = 9, name = "Sam", email = "s@x.dev", role = ProjectRole.SUPERVISOR))
        val logs = FakeDailyLogRepository(detail = logDetail(date = today))
        val v = vm(logs, projects = projectRepo(ownerId = 1L, members = members), authRepo = auth(userId = 9L))
        v.load("log-1")
        advanceUntilIdle()

        assertTrue(v.state.value.canEdit)
    }

    @Test
    fun a_supervisor_cannot_edit_todays_log_once_the_stage_is_completed() = runTest {
        val members = listOf(ProjectMember(userId = 9, name = "Sam", email = "s@x.dev", role = ProjectRole.SUPERVISOR))
        val logs = FakeDailyLogRepository(detail = logDetail(date = today))
        val v = vm(
            logs,
            stages = stageRepo(status = StageStatus.COMPLETED),
            projects = projectRepo(ownerId = 1L, members = members),
            authRepo = auth(userId = 9L),
        )
        v.load("log-1")
        advanceUntilIdle()

        assertFalse(v.state.value.canEdit)
    }

    @Test
    fun a_missing_log_is_flagged_once_loading_settles() = runTest {
        val logs = FakeDailyLogRepository(detail = null)
        val v = vm(logs)
        v.load("gone")
        advanceUntilIdle()

        assertFalse(v.state.value.isLoading)
        assertTrue(v.state.value.isMissing)
    }

    @Test
    fun retry_pulls_this_log_again() = runTest {
        val logs = FakeDailyLogRepository(detail = null)
        val v = vm(logs)
        v.load("log-1")
        advanceUntilIdle()

        v.retry()
        advanceUntilIdle()

        assertEquals(2, logs.refreshLogCount)
    }

    @Test
    fun add_entry_delegates_to_the_repository_using_the_logs_own_stage_and_date() = runTest {
        val logs = FakeDailyLogRepository(detail = logDetail(date = "2026-03-10"))
        val v = vm(logs)
        v.load("log-1")
        advanceUntilIdle()

        v.addEntry(EntryType.WORK)
        advanceUntilIdle()

        assertEquals("createWorkEntry:s1:2026-03-10", logs.log.last())
    }

    @Test
    fun editing_a_summary_then_saving_updates_the_repository_and_closes_the_dialog() = runTest {
        val entry = DailyEntry("e1", "log-1", EntryType.PURCHASE, summary = null)
        val logs = FakeDailyLogRepository(detail = logDetail(entries = listOf(entry)))
        val v = vm(logs)
        v.load("log-1")
        advanceUntilIdle()

        v.startEditingSummary("e1")
        assertEquals("e1", v.state.value.editingEntryLocalId)

        v.saveSummary("  20 sacs de ciment livrés  ")
        advanceUntilIdle()

        assertEquals("updateEntry:e1:20 sacs de ciment livrés", logs.log.last())
        assertNull(v.state.value.editingEntryLocalId)
        assertFalse(v.state.value.isSubmitting)
    }

    @Test
    fun a_blank_work_title_blocks_saving_and_writes_nothing() = runTest {
        val entry = DailyEntry("e1", "log-1", EntryType.WORK, summary = null)
        val logs = FakeDailyLogRepository(detail = logDetail(entries = listOf(entry)))
        val v = vm(logs)
        v.load("log-1")
        advanceUntilIdle()

        v.startEditingSummary("e1")
        v.saveSummary("   ")
        advanceUntilIdle()

        assertEquals(Res.string.entry_summary_required_work, v.state.value.summaryError)
        assertTrue(logs.log.none { it.startsWith("updateEntry") })
        assertEquals("e1", v.state.value.editingEntryLocalId, "the dialog stays open on error")
    }

    @Test
    fun a_blank_purchase_summary_is_allowed() = runTest {
        val entry = DailyEntry("e1", "log-1", EntryType.PURCHASE, summary = "Ancien résumé")
        val logs = FakeDailyLogRepository(detail = logDetail(entries = listOf(entry)))
        val v = vm(logs)
        v.load("log-1")
        advanceUntilIdle()

        v.startEditingSummary("e1")
        v.saveSummary("   ")
        advanceUntilIdle()

        assertEquals("updateEntry:e1:", logs.log.last())
        assertNull(v.state.value.editingEntryLocalId)
    }

    @Test
    fun cancel_editing_clears_the_dialog_state_without_writing() = runTest {
        val entry = DailyEntry("e1", "log-1", EntryType.PURCHASE, summary = "x")
        val logs = FakeDailyLogRepository(detail = logDetail(entries = listOf(entry)))
        val v = vm(logs)
        v.load("log-1")
        advanceUntilIdle()

        v.startEditingSummary("e1")
        v.cancelEditingSummary()

        assertNull(v.state.value.editingEntryLocalId)
        assertTrue(logs.log.none { it.startsWith("updateEntry") })
    }

    // ─── isAdmin / materials / stock / lines exposure ───────────────────────

    @Test
    fun is_admin_is_exposed_separately_from_can_edit() = runTest {
        // A SUPERVISOR on an active project/today's log can edit, but is not admin —
        // deletion (gated on isAdmin, not canEdit) must stay unavailable to them.
        val members = listOf(ProjectMember(userId = 9, name = "Sam", email = "s@x.dev", role = ProjectRole.SUPERVISOR))
        val logs = FakeDailyLogRepository(detail = logDetail(date = today))
        val v = vm(logs, projects = projectRepo(ownerId = 1L, members = members), authRepo = auth(userId = 9L))
        v.load("log-1")
        advanceUntilIdle()

        assertTrue(v.state.value.canEdit)
        assertFalse(v.state.value.isAdmin)
    }

    @Test
    fun the_owner_is_admin() = runTest {
        val logs = FakeDailyLogRepository(detail = logDetail())
        val v = vm(logs, projects = projectRepo(ownerId = 1L), authRepo = auth(userId = 1L))
        v.load("log-1")
        advanceUntilIdle()

        assertTrue(v.state.value.isAdmin)
    }

    @Test
    fun materials_and_stock_are_exposed_from_the_material_repository() = runTest {
        val logs = FakeDailyLogRepository(detail = logDetail())
        val materials = FakeMaterialRepository(
            materials = listOf(Material("m1", "p1", "Ciment", "sac")),
            stock = listOf(MaterialStock("m1", "Ciment", "sac", quantityIn = 10.0, quantityOut = 2.0)),
        )
        val v = vm(logs, materials = materials)
        v.load("log-1")
        advanceUntilIdle()

        assertEquals(listOf("Ciment"), v.state.value.materials.map { it.name })
        assertEquals(8.0, v.state.value.stock.single().available)
    }

    @Test
    fun purchase_and_consumption_lines_are_scoped_to_their_own_entry() = runTest {
        val purchaseEntry = DailyEntry("e1", "log-1", EntryType.PURCHASE, summary = null)
        val workEntry = DailyEntry("e2", "log-1", EntryType.WORK, summary = null)
        val logs = FakeDailyLogRepository(detail = logDetail(entries = listOf(purchaseEntry, workEntry)))
        val purchaseLines = FakePurchaseLineRepository(lines = listOf(PurchaseLine("pl1", "e1", "m1", 12.0, 3.0, 36.0, null)))
        val consumptionLines = FakeConsumptionLineRepository(lines = listOf(ConsumptionLine("cl1", "e2", "m1", 4.0)))
        val v = vm(logs, purchaseLines = purchaseLines, consumptionLines = consumptionLines)
        v.load("log-1")
        advanceUntilIdle()

        assertEquals(listOf("pl1"), v.state.value.purchaseLines.map { it.localId })
        assertEquals(listOf("cl1"), v.state.value.consumptionLines.map { it.localId })
    }

    // ─── materials referential ───────────────────────────────────────────────

    @Test
    fun create_material_delegates_to_the_repository_for_the_logs_project() = runTest {
        val logs = FakeDailyLogRepository(detail = logDetail())
        val materials = FakeMaterialRepository()
        val v = vm(logs, materials = materials)
        v.load("log-1")
        advanceUntilIdle()

        val created = v.createMaterial("Ciment", "sac")

        assertEquals("createMaterial:p1:Ciment:sac", materials.log.single())
        assertEquals("p1", created?.projectLocalId)
    }

    @Test
    fun create_material_returns_null_before_the_project_is_known() = runTest {
        val v = vm(FakeDailyLogRepository(detail = null))
        assertNull(v.createMaterial("Ciment", "sac"))
    }

    // ─── purchase lines ──────────────────────────────────────────────────────

    @Test
    fun create_purchase_line_delegates_to_the_repository() = runTest {
        val logs = FakeDailyLogRepository(detail = logDetail())
        val purchaseLines = FakePurchaseLineRepository()
        val v = vm(logs, purchaseLines = purchaseLines)
        v.load("log-1")
        advanceUntilIdle()

        v.createPurchaseLine("e1", "m1", 12.0, 3.5, "Quincaillerie")

        assertEquals("createLine:e1:m1:12.0:3.5:Quincaillerie", purchaseLines.log.single())
    }

    @Test
    fun update_and_delete_purchase_line_delegate_to_the_repository() = runTest {
        val logs = FakeDailyLogRepository(detail = logDetail())
        val purchaseLines = FakePurchaseLineRepository()
        val v = vm(logs, purchaseLines = purchaseLines)
        v.load("log-1")
        advanceUntilIdle()

        v.updatePurchaseLine("pl1", 5.0, 2.0, null)
        v.deletePurchaseLine("pl1")
        advanceUntilIdle()

        assertEquals(listOf("updateLine:pl1:5.0:2.0:null", "deleteLine:pl1"), purchaseLines.log)
    }

    // ─── consumption lines (stock-limited) ───────────────────────────────────

    @Test
    fun create_consumption_line_within_stock_succeeds() = runTest {
        val logs = FakeDailyLogRepository(detail = logDetail())
        val materials = FakeMaterialRepository(stock = listOf(MaterialStock("m1", "Ciment", "sac", quantityIn = 10.0, quantityOut = 0.0)))
        val consumptionLines = FakeConsumptionLineRepository()
        val v = vm(logs, materials = materials, consumptionLines = consumptionLines)
        v.load("log-1")
        advanceUntilIdle()

        val ok = v.createConsumptionLine("e2", "m1", 4.0)

        assertTrue(ok)
        assertEquals("createLine:e2:m1:4.0", consumptionLines.log.single())
    }

    @Test
    fun create_consumption_line_exceeding_stock_is_blocked_and_writes_nothing() = runTest {
        val logs = FakeDailyLogRepository(detail = logDetail())
        val materials = FakeMaterialRepository(stock = listOf(MaterialStock("m1", "Ciment", "sac", quantityIn = 10.0, quantityOut = 0.0)))
        val consumptionLines = FakeConsumptionLineRepository()
        val v = vm(logs, materials = materials, consumptionLines = consumptionLines)
        v.load("log-1")
        advanceUntilIdle()

        val ok = v.createConsumptionLine("e2", "m1", 11.0)

        assertFalse(ok)
        assertTrue(consumptionLines.log.isEmpty())
    }

    @Test
    fun update_consumption_line_gives_back_its_own_quantity_before_checking_the_ceiling() = runTest {
        val workEntry = DailyEntry("e2", "log-1", EntryType.WORK, summary = null)
        val logs = FakeDailyLogRepository(detail = logDetail(entries = listOf(workEntry)))
        // 10 available after this line's own 5 units are already subtracted (quantityOut includes it).
        val materials = FakeMaterialRepository(stock = listOf(MaterialStock("m1", "Ciment", "sac", quantityIn = 20.0, quantityOut = 10.0)))
        val consumptionLines = FakeConsumptionLineRepository(lines = listOf(ConsumptionLine("cl1", "e2", "m1", 5.0)))
        val v = vm(logs, materials = materials, consumptionLines = consumptionLines)
        v.load("log-1")
        advanceUntilIdle()

        // Ceiling = available (10) + this line's own quantity (5) = 15.
        val ok = v.updateConsumptionLine("cl1", "m1", 15.0)

        assertTrue(ok)
        assertEquals("updateLine:cl1:15.0", consumptionLines.log.single())
    }

    @Test
    fun update_consumption_line_beyond_its_ceiling_is_blocked() = runTest {
        val workEntry = DailyEntry("e2", "log-1", EntryType.WORK, summary = null)
        val logs = FakeDailyLogRepository(detail = logDetail(entries = listOf(workEntry)))
        val materials = FakeMaterialRepository(stock = listOf(MaterialStock("m1", "Ciment", "sac", quantityIn = 20.0, quantityOut = 10.0)))
        val consumptionLines = FakeConsumptionLineRepository(lines = listOf(ConsumptionLine("cl1", "e2", "m1", 5.0)))
        val v = vm(logs, materials = materials, consumptionLines = consumptionLines)
        v.load("log-1")
        advanceUntilIdle()

        val ok = v.updateConsumptionLine("cl1", "m1", 15.1)

        assertFalse(ok)
        assertTrue(consumptionLines.log.isEmpty())
    }

    @Test
    fun delete_consumption_line_delegates_to_the_repository() = runTest {
        val logs = FakeDailyLogRepository(detail = logDetail())
        val consumptionLines = FakeConsumptionLineRepository()
        val v = vm(logs, consumptionLines = consumptionLines)
        v.load("log-1")
        advanceUntilIdle()

        v.deleteConsumptionLine("cl1")
        advanceUntilIdle()

        assertEquals(listOf("deleteLine:cl1"), consumptionLines.log)
    }

    // ─── attachments (justificatifs — PURCHASE entry only) ──────────────────

    @Test
    fun attachments_are_exposed_from_the_purchase_entry() = runTest {
        val purchaseEntry = DailyEntry("e1", "log-1", EntryType.PURCHASE, summary = null)
        val logs = FakeDailyLogRepository(detail = logDetail(entries = listOf(purchaseEntry)))
        val attachments = FakeAttachmentRepository(
            attachments = listOf(Attachment("a1", "e1", "fake-attachments/a1.jpg", "facture.jpg", "image/jpeg", 1_024L, 0L)),
        )
        val v = vm(logs, attachments = attachments)
        v.load("log-1")
        advanceUntilIdle()

        assertEquals(listOf("a1"), v.state.value.attachments.map { it.localId })
    }

    @Test
    fun add_attachment_delegates_to_the_repository() = runTest {
        val logs = FakeDailyLogRepository(detail = logDetail())
        val attachments = FakeAttachmentRepository()
        val v = vm(logs, attachments = attachments)
        v.load("log-1")
        advanceUntilIdle()

        v.addAttachment("e1", byteArrayOf(1, 2, 3), "facture.jpg", "image/jpeg")

        assertEquals("addAttachment:e1:facture.jpg:image/jpeg:3", attachments.log.single())
    }

    @Test
    fun delete_attachment_delegates_to_the_repository() = runTest {
        val logs = FakeDailyLogRepository(detail = logDetail())
        val attachments = FakeAttachmentRepository()
        val v = vm(logs, attachments = attachments)
        v.load("log-1")
        advanceUntilIdle()

        v.deleteAttachment("a1")
        advanceUntilIdle()

        assertEquals(listOf("deleteAttachment:a1"), attachments.log)
    }
}
