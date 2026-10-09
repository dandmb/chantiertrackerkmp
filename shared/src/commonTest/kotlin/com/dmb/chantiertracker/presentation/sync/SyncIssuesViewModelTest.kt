package com.dmb.chantiertracker.presentation.sync

import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.RefusalReason
import com.dmb.chantiertracker.domain.model.SyncIssue
import com.dmb.chantiertracker.domain.model.SyncIssueKind
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.domain.repository.RetryOutcome
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
        assertEquals(7, state.total)
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
        assertEquals(2, vm.state.value.total)

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
        assertEquals(2, vm.count.value)

        repo.items.value = emptyList()
        advanceUntilIdle()

        assertEquals(0, vm.count.value)
        job.cancel()
    }
}
