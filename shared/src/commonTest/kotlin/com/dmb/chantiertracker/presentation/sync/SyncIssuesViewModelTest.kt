package com.dmb.chantiertracker.presentation.sync

import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.SyncIssue
import com.dmb.chantiertracker.domain.model.SyncIssueAction
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.domain.repository.RetryOutcome
import com.dmb.chantiertracker.domain.repository.RevertOutcome
import com.dmb.chantiertracker.support.FakeSyncIssueRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.issueItem
import com.dmb.chantiertracker.support.refusedIssue
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
class SyncIssuesViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private val suspended = refusedIssue(RefusalReason.PROJECT_OR_STAGE_INACTIVE)
    private val blocked = SyncIssue(SyncIssueKind.BLOCKED_BY_PARENT)

    private fun onDay(target: SyncIssueTarget, id: String, issue: SyncIssue, date: String, log: String, stage: String = "st1", stageName: String = "Charpente", type: EntryType = EntryType.PURCHASE) =
        issueItem(target, id, issue, stageLocalId = stage, stageName = stageName, dailyLogLocalId = log, date = date, entryType = type)

    private val entry = onDay(SyncIssueTarget.ENTRY, "e1", suspended, "2026-10-09", "l1")
    private val line = onDay(SyncIssueTarget.PURCHASE_LINE, "pl1", blocked, "2026-10-09", "l1")

    @Test
    fun nothing_to_review_is_an_empty_state_not_a_loading_one() = runTest {
        val vm = SyncIssuesViewModel(FakeSyncIssueRepository())
        assertTrue(vm.state.value.isLoading)
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.isEmpty)
        assertEquals(0, vm.state.value.total)
    }

    @Test
    fun items_are_grouped_by_project_then_stage_then_day() = runTest {
        val repo = FakeSyncIssueRepository(
            listOf(
                line,
                entry,
                onDay(SyncIssueTarget.ENTRY, "e-old", suspended, "2026-10-02", "l0", type = EntryType.WORK),
                onDay(SyncIssueTarget.ENTRY, "e-other-stage", suspended, "2026-10-09", "l9", stage = "st0", stageName = "Bardage"),
                issueItem(SyncIssueTarget.STAGE, "st1", refusedIssue(RefusalReason.INSUFFICIENT_ROLE, kind = SyncIssueKind.UPDATE_REFUSED), stageLocalId = "st1", stageName = "Charpente"),
                issueItem(SyncIssueTarget.MATERIAL, "m1", refusedIssue(RefusalReason.DUPLICATE_MATERIAL), label = "Ciment"),
                issueItem(SyncIssueTarget.PROJECT, "p0", refusedIssue(RefusalReason.PLAN_LIMIT), projectLocalId = "p0", projectName = "Atelier"),
            ),
        )
        val vm = SyncIssuesViewModel(repo)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(6, state.total, "the line waiting on its entry is shown but not counted")
        assertEquals(listOf("Atelier", "Villa Vidal"), state.projects.map { it.projectName }, "projects in alphabetical order")

        val atelier = state.projects[0]
        assertEquals(listOf("p0"), atelier.items.map { it.localId })
        assertTrue(atelier.stages.isEmpty())

        val villa = state.projects[1]
        assertEquals(listOf("m1"), villa.items.map { it.localId }, "a material belongs to the project, not to a stage")
        assertEquals(listOf("Bardage", "Charpente"), villa.stages.map { it.stageName })

        val charpente = villa.stages[1]
        assertEquals(listOf("st1"), charpente.items.map { it.localId }, "the stage itself comes before its days")
        assertEquals(listOf("2026-10-09", "2026-10-02"), charpente.days.map { it.date }, "most recent day first")
        assertEquals(listOf("e1", "pl1"), charpente.days[0].items.map { it.localId }, "an entry comes before the lines waiting on it")
    }

    @Test
    fun the_list_follows_the_repository() = runTest {
        val repo = FakeSyncIssueRepository(listOf(entry, line))
        val vm = SyncIssuesViewModel(repo)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.total)
        assertEquals(listOf("e1", "pl1"), vm.state.value.projects.single().stages.single().days.single().items.map { it.localId })

        repo.items.value = emptyList()
        advanceUntilIdle()

        assertTrue(vm.state.value.isEmpty)
    }

    @Test
    fun retrying_shows_which_item_is_being_sent_and_then_that_it_was_accepted() = runTest {
        val repo = FakeSyncIssueRepository(listOf(entry, line))
        repo.retryGate = CompletableDeferred()
        val vm = SyncIssuesViewModel(repo)
        advanceUntilIdle()

        vm.retry(entry)
        advanceUntilIdle()
        assertEquals(entry.key, vm.state.value.retryingKey)
        assertNull(vm.state.value.notice)

        repo.retryGate!!.complete(Unit)
        advanceUntilIdle()

        assertNull(vm.state.value.retryingKey)
        assertEquals(RetryOutcome.ACCEPTED, vm.state.value.notice)
        assertEquals(listOf(entry), repo.retried)
    }

    @Test
    fun a_second_retry_is_ignored_while_one_is_running() = runTest {
        val repo = FakeSyncIssueRepository(listOf(entry, line))
        repo.retryGate = CompletableDeferred()
        val vm = SyncIssuesViewModel(repo)
        advanceUntilIdle()

        vm.retry(entry)
        vm.retry(entry)
        advanceUntilIdle()
        repo.retryGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repo.retried.size)
    }

    @Test
    fun a_retry_still_refused_or_not_sent_is_reported_and_the_notice_can_be_dismissed() = runTest {
        val repo = FakeSyncIssueRepository(listOf(entry))
        val vm = SyncIssuesViewModel(repo)
        advanceUntilIdle()

        repo.retryOutcome = RetryOutcome.STILL_REFUSED
        vm.retry(entry)
        advanceUntilIdle()
        assertEquals(RetryOutcome.STILL_REFUSED, vm.state.value.notice)
        assertEquals(1, vm.state.value.total)

        repo.retryOutcome = RetryOutcome.NOT_SENT
        vm.retry(entry)
        advanceUntilIdle()
        assertEquals(RetryOutcome.NOT_SENT, vm.state.value.notice)

        vm.dismissNotice()
        assertNull(vm.state.value.notice)
    }

    @Test
    fun the_count_shown_in_the_top_bar_and_on_the_projects_list_follows_the_repository() = runTest {
        val repo = FakeSyncIssueRepository(listOf(entry, line))
        val vm = SyncIssueCountViewModel(repo)
        val collected = mutableListOf<Int>()
        val job = backgroundScope.launch { vm.count.collect { collected += it } }
        advanceUntilIdle()
        assertEquals(1, vm.count.value, "the refused entry; not the line waiting on it")

        repo.items.value = emptyList()
        advanceUntilIdle()

        assertEquals(0, vm.count.value)
        job.cancel()
    }

    // ─── actions (tranche 3) ─────────────────────────────────────────────────

    private val duplicate = onDay(SyncIssueTarget.ENTRY, "e-dup", refusedIssue(RefusalReason.DUPLICATE_ENTRY), "2026-10-09", "l1")
    private val goneOnServer = onDay(SyncIssueTarget.ENTRY, "e-gone", SyncIssue(SyncIssueKind.DELETED_ON_SERVER), "2026-10-08", "l0")
    private val refusedDelete = issueItem(SyncIssueTarget.STAGE, "st-del", refusedIssue(RefusalReason.INSUFFICIENT_ROLE, kind = SyncIssueKind.DELETE_REFUSED), stageLocalId = "st-del", stageName = "Toiture")
    private val refusedChange = onDay(SyncIssueTarget.PURCHASE_LINE, "pl-edit", refusedIssue(RefusalReason.STOCK_CONSUMED, kind = SyncIssueKind.UPDATE_REFUSED), "2026-10-09", "l1")
    private val refusedChangeKnownHere =
        onDay(SyncIssueTarget.CONSUMPTION_LINE, "cl-edit", refusedIssue(RefusalReason.INSUFFICIENT_STOCK, kind = SyncIssueKind.UPDATE_REFUSED), "2026-10-09", "l1", type = EntryType.WORK)
            .copy(quantity = 50.0, serverQuantity = 2.0)

    @Test
    fun discarding_first_asks_for_confirmation_with_the_number_of_linked_entries() = runTest {
        val repo = FakeSyncIssueRepository(listOf(duplicate, line))
        repo.linked = 3
        val vm = SyncIssuesViewModel(repo)
        advanceUntilIdle()

        vm.discard(duplicate)
        advanceUntilIdle()

        assertEquals(SyncIssueConfirmation(duplicate, SyncIssueAction.DISCARD, linkedCount = 3), vm.state.value.confirmation)
        assertTrue(repo.actions.isEmpty(), "nothing is removed before the user confirms")
    }

    @Test
    fun cancelling_the_confirmation_removes_nothing() = runTest {
        val repo = FakeSyncIssueRepository(listOf(duplicate))
        val vm = SyncIssuesViewModel(repo)
        advanceUntilIdle()
        vm.discard(duplicate)
        advanceUntilIdle()

        vm.dismissConfirmation()
        advanceUntilIdle()

        assertNull(vm.state.value.confirmation)
        assertTrue(repo.actions.isEmpty())
        assertEquals(1, vm.state.value.total)
    }

    @Test
    fun confirming_discards_once_updates_the_count_and_says_so() = runTest {
        val repo = FakeSyncIssueRepository(listOf(duplicate, entry))
        repo.actionGate = CompletableDeferred()
        val vm = SyncIssuesViewModel(repo)
        advanceUntilIdle()
        vm.discard(duplicate)
        advanceUntilIdle()

        vm.confirm()
        vm.confirm()
        advanceUntilIdle()
        assertEquals(duplicate.key, vm.state.value.busyKey)
        assertNull(vm.state.value.confirmation)
        vm.discard(entry)
        vm.retry(entry)
        advanceUntilIdle()
        assertNull(vm.state.value.confirmation, "no other action starts while one is running")

        repo.actionGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("discard ${duplicate.key}"), repo.actions, "a double tap discards once")
        assertTrue(repo.retried.isEmpty())
        assertNull(vm.state.value.busyKey)
        assertEquals(SyncIssueActionNotice.DISCARDED, vm.state.value.actionNotice)
        assertEquals(1, vm.state.value.total, "the title follows without reopening the screen")
    }

    @Test
    fun acknowledging_a_leaf_acts_at_once_and_a_parent_with_linked_entries_asks_first() = runTest {
        val repo = FakeSyncIssueRepository(listOf(goneOnServer, duplicate))
        val vm = SyncIssuesViewModel(repo)
        advanceUntilIdle()

        vm.acknowledge(goneOnServer)
        advanceUntilIdle()
        assertEquals(listOf("acknowledge ${goneOnServer.key}"), repo.actions)
        assertEquals(SyncIssueActionNotice.ACKNOWLEDGED, vm.state.value.actionNotice)
        assertNull(vm.state.value.confirmation)

        val goneStage = issueItem(SyncIssueTarget.STAGE, "st-gone", SyncIssue(SyncIssueKind.DELETED_ON_SERVER), stageLocalId = "st-gone", stageName = "Bardage")
        repo.items.value = repo.items.value + goneStage
        repo.linked = 2
        advanceUntilIdle()
        vm.acknowledge(goneStage)
        advanceUntilIdle()
        assertEquals(SyncIssueConfirmation(goneStage, SyncIssueAction.ACKNOWLEDGE, linkedCount = 2), vm.state.value.confirmation)
        assertEquals(1, repo.actions.size)

        vm.confirm()
        advanceUntilIdle()
        assertEquals("acknowledge ${goneStage.key}", repo.actions.last())
    }

    @Test
    fun a_creation_refused_for_an_unknown_reason_is_discarded_after_the_same_confirmation_and_never_acknowledged() = runTest {
        val unknown = issueItem(SyncIssueTarget.STAGE, "st-unknown", refusedIssue(RefusalReason.UNKNOWN), stageLocalId = "st-unknown", stageName = "Bardage")
        val repo = FakeSyncIssueRepository(listOf(unknown)).apply { linked = 2 }
        val vm = SyncIssuesViewModel(repo)
        advanceUntilIdle()

        vm.acknowledge(unknown)
        advanceUntilIdle()
        assertNull(vm.state.value.confirmation, "got it is not an action of a refused creation")
        assertTrue(repo.actions.isEmpty())

        vm.discard(unknown)
        advanceUntilIdle()
        assertEquals(SyncIssueConfirmation(unknown, SyncIssueAction.DISCARD, linkedCount = 2), vm.state.value.confirmation)
        assertTrue(repo.actions.isEmpty(), "nothing is removed before the confirmation")

        vm.confirm()
        advanceUntilIdle()
        assertEquals(listOf("discard ${unknown.key}"), repo.actions)
        assertEquals(SyncIssueActionNotice.DISCARDED, vm.state.value.actionNotice)
    }

    @Test
    fun acknowledging_a_refused_delete_never_asks_since_nothing_is_removed() = runTest {
        val repo = FakeSyncIssueRepository(listOf(refusedDelete))
        repo.linked = 5
        val vm = SyncIssuesViewModel(repo)
        advanceUntilIdle()

        vm.acknowledge(refusedDelete)
        advanceUntilIdle()

        assertNull(vm.state.value.confirmation)
        assertEquals(listOf("acknowledge ${refusedDelete.key}"), repo.actions)
        assertTrue(vm.state.value.isEmpty)
    }

    @Test
    fun reverting_reports_each_outcome() = runTest {
        val repo = FakeSyncIssueRepository(listOf(refusedChange))
        val vm = SyncIssuesViewModel(repo)
        advanceUntilIdle()

        repo.revertOutcome = RevertOutcome.FAILED
        vm.revert(refusedChange)
        advanceUntilIdle()
        assertEquals(SyncIssueActionNotice.REVERT_FAILED, vm.state.value.actionNotice)

        repo.revertOutcome = RevertOutcome.GONE_ON_SERVER
        vm.revert(refusedChange)
        advanceUntilIdle()
        assertEquals(SyncIssueActionNotice.REVERT_GONE_ON_SERVER, vm.state.value.actionNotice)

        repo.revertOutcome = RevertOutcome.NEEDS_CONNECTION
        vm.revert(refusedChange)
        advanceUntilIdle()
        assertEquals(SyncIssueActionNotice.REVERT_NEEDS_CONNECTION, vm.state.value.actionNotice)
        assertEquals(1, vm.state.value.total)

        repo.revertOutcome = RevertOutcome.RESTORED
        vm.revert(refusedChange)
        advanceUntilIdle()
        assertEquals(SyncIssueActionNotice.REVERTED, vm.state.value.actionNotice)
        assertTrue(vm.state.value.isEmpty)
        assertEquals(4, repo.actions.size)

        vm.dismissNotice()
        assertNull(vm.state.value.actionNotice)
    }

    @Test
    fun offline_a_revert_whose_server_value_is_unknown_is_not_even_attempted() = runTest {
        val repo = FakeSyncIssueRepository(listOf(refusedChange, refusedChangeKnownHere))
        val vm = SyncIssuesViewModel(repo)
        advanceUntilIdle()
        assertTrue(vm.state.value.isOnline)
        assertTrue(vm.state.value.canRevert(refusedChange))

        repo.online.value = false
        advanceUntilIdle()
        assertFalse(vm.state.value.isOnline)
        assertFalse(vm.state.value.canRevert(refusedChange))
        assertTrue(vm.state.value.canRevert(refusedChangeKnownHere), "its server value is on the device")

        vm.revert(refusedChange)
        advanceUntilIdle()
        assertTrue(repo.actions.isEmpty())
        assertEquals(SyncIssueActionNotice.REVERT_NEEDS_CONNECTION, vm.state.value.actionNotice)

        vm.revert(refusedChangeKnownHere)
        advanceUntilIdle()
        assertEquals(listOf("revert ${refusedChangeKnownHere.key}"), repo.actions)
    }

    @Test
    fun an_action_the_item_does_not_offer_is_ignored() = runTest {
        val repo = FakeSyncIssueRepository(listOf(refusedChange, line, goneOnServer))
        val vm = SyncIssuesViewModel(repo)
        advanceUntilIdle()

        vm.discard(refusedChange)
        vm.discard(line)
        vm.acknowledge(refusedChange)
        vm.revert(goneOnServer)
        advanceUntilIdle()

        assertTrue(repo.actions.isEmpty())
        assertNull(vm.state.value.confirmation)
    }

    @Test
    fun the_badge_count_follows_an_action_without_reopening_anything() = runTest {
        val repo = FakeSyncIssueRepository(listOf(duplicate, entry, line))
        val count = SyncIssueCountViewModel(repo)
        val vm = SyncIssuesViewModel(repo)
        val job = backgroundScope.launch { count.count.collect { } }
        advanceUntilIdle()
        assertEquals(2, count.count.value)

        vm.discard(duplicate)
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()

        assertEquals(1, count.count.value)
        assertEquals(1, vm.state.value.total)
        job.cancel()
    }

    @Test
    fun a_double_tap_on_got_it_while_the_linked_entries_are_still_being_counted_acknowledges_once() = runTest {
        val repo = FakeSyncIssueRepository(listOf(goneOnServer, duplicate))
        repo.linkedGate = CompletableDeferred()
        repo.actionGate = CompletableDeferred()
        val vm = SyncIssuesViewModel(repo)
        advanceUntilIdle()

        vm.acknowledge(goneOnServer)
        vm.acknowledge(goneOnServer)
        advanceUntilIdle()
        repo.linkedGate!!.complete(Unit)
        advanceUntilIdle()
        repo.actionGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("acknowledge ${goneOnServer.key}"), repo.actions, "both taps passed the first check before the action started: only one may act")
        assertEquals(SyncIssueActionNotice.ACKNOWLEDGED, vm.state.value.actionNotice)
    }
}
