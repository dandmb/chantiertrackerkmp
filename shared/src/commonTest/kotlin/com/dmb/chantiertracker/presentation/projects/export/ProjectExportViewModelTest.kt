package com.dmb.chantiertracker.presentation.projects.export

import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.support.FakeExportRepository
import com.dmb.chantiertracker.support.FakePdfSharer
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.coroutines.CompletableDeferred
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
class ProjectExportViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private fun vm(
        repo: FakeExportRepository = FakeExportRepository(),
        sharer: FakePdfSharer = FakePdfSharer(),
    ) = Triple(ProjectExportViewModel(repo, sharer), repo, sharer)

    @Test
    fun exporting_generates_then_hands_the_file_to_the_platform_sharer() = runTest {
        val (v, repo, sharer) = vm()

        v.export("p1")
        advanceUntilIdle()

        assertEquals(listOf("p1"), repo.calls)
        assertEquals(listOf("/cache/exports/chantier-villa-2026-09-08.pdf"), sharer.shared)
        assertFalse(v.state.value.isExporting)
        assertNull(v.state.value.error)
    }

    @Test
    fun a_second_export_while_one_is_running_is_ignored() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeExportRepository().apply { onCall = { gate.await() } }
        val (v, _, _) = vm(repo)

        v.export("p1")
        advanceUntilIdle()
        assertTrue(v.state.value.isExporting)

        v.export("p1")
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("p1"), repo.calls, "the in-flight export blocks a second one")
    }

    @Test
    fun a_domain_failure_is_surfaced_and_nothing_is_shared() = runTest {
        val repo = FakeExportRepository().apply { error = DomainException.PlanLimitReached }
        val (v, _, sharer) = vm(repo)

        v.export("p1")
        advanceUntilIdle()

        assertEquals(DomainException.PlanLimitReached, v.state.value.error)
        assertTrue(sharer.shared.isEmpty())
        assertFalse(v.state.value.isExporting)
    }

    @Test
    fun a_share_failure_surfaces_as_unexpected() = runTest {
        val sharer = FakePdfSharer().apply { error = IllegalStateException("no activity") }
        val (v, _, _) = vm(sharer = sharer)

        v.export("p1")
        advanceUntilIdle()

        assertEquals(DomainException.Unexpected, v.state.value.error)
        assertFalse(v.state.value.isExporting)
    }

    @Test
    fun clear_error_resets_it() = runTest {
        val repo = FakeExportRepository().apply { error = DomainException.Network }
        val (v, _, _) = vm(repo)
        v.export("p1")
        advanceUntilIdle()
        assertEquals(DomainException.Network, v.state.value.error)

        v.clearError()
        assertNull(v.state.value.error)
    }
}
