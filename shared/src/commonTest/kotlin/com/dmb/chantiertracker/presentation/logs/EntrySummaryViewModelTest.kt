package com.dmb.chantiertracker.presentation.logs

import com.dmb.chantiertracker.domain.model.DailyEntry
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.support.FakeDailyLogRepository
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EntrySummaryViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private fun vm(entry: DailyEntry?): Pair<EntrySummaryViewModel, FakeDailyLogRepository> {
        val repo = FakeDailyLogRepository().apply { entryFlow.value = entry }
        return EntrySummaryViewModel(repo) to repo
    }

    @Test
    fun prefills_from_the_entry_and_saves_the_trimmed_summary() = runTest {
        val (v, repo) = vm(DailyEntry("e1", "log-1", EntryType.PURCHASE, summary = "vieux résumé"))
        v.load("e1")
        advanceUntilIdle()
        assertEquals("vieux résumé", v.state.value.summary)

        v.onSummaryChange("  12 sacs livrés  ")
        v.submit()
        advanceUntilIdle()

        assertEquals("updateEntry:e1:12 sacs livrés", repo.log.single())
        assertTrue(v.state.value.saved)
    }

    @Test
    fun a_blank_work_title_blocks_saving() = runTest {
        val (v, repo) = vm(DailyEntry("e1", "log-1", EntryType.WORK, summary = null))
        v.load("e1")
        advanceUntilIdle()

        v.onSummaryChange("   ")
        assertFalse(v.state.value.canSave)

        v.submit()
        advanceUntilIdle()
        assertTrue(repo.log.isEmpty())
        assertFalse(v.state.value.saved)

        v.onSummaryChange("Coulage dalle")
        assertTrue(v.state.value.canSave)
    }

    @Test
    fun a_blank_purchase_summary_is_allowed() = runTest {
        val (v, repo) = vm(DailyEntry("e1", "log-1", EntryType.PURCHASE, summary = "x"))
        v.load("e1")
        advanceUntilIdle()

        v.onSummaryChange("")
        assertTrue(v.state.value.canSave)

        v.submit()
        advanceUntilIdle()

        assertEquals("updateEntry:e1:", repo.log.single())
    }

    @Test
    fun a_missing_entry_is_flagged() = runTest {
        val (v, _) = vm(null)
        v.load("gone")
        advanceUntilIdle()
        assertTrue(v.state.value.isMissing)
    }
}
