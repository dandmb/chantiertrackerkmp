package com.dmb.chantiertracker.presentation.projects.export

import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.support.FakeExportRepository
import com.dmb.chantiertracker.support.FakePdfOpener
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

private class Harness(
    val repo: FakeExportRepository = FakeExportRepository(),
    val opener: FakePdfOpener = FakePdfOpener(),
    val sharer: FakePdfSharer = FakePdfSharer(),
) {
    val vm = ProjectExportViewModel(repo, opener, sharer)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectExportViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    @Test
    fun exporting_generates_the_file_without_forcing_open_or_share() = runTest {
        val h = Harness()

        h.vm.export("p1")
        advanceUntilIdle()

        assertEquals(listOf("p1"), h.repo.calls)
        assertEquals("/cache/exports/chantier-villa-2026-09-08.pdf", h.vm.state.value.exported?.path)
        assertTrue(h.opener.opened.isEmpty(), "export() alone must not open the file")
        assertTrue(h.sharer.shared.isEmpty(), "export() alone must not share the file")
        assertFalse(h.vm.state.value.isExporting)
        assertNull(h.vm.state.value.error)
    }

    @Test
    fun a_second_export_while_one_is_running_is_ignored() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeExportRepository().apply { onCall = { gate.await() } }
        val h = Harness(repo)

        h.vm.export("p1")
        advanceUntilIdle()
        assertTrue(h.vm.state.value.isExporting)

        h.vm.export("p1")
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("p1"), h.repo.calls, "the in-flight export blocks a second one")
    }

    @Test
    fun a_domain_failure_is_surfaced_and_nothing_is_exported() = runTest {
        val repo = FakeExportRepository().apply { error = DomainException.PlanLimitReached }
        val h = Harness(repo)

        h.vm.export("p1")
        advanceUntilIdle()

        assertEquals(DomainException.PlanLimitReached, h.vm.state.value.error)
        assertNull(h.vm.state.value.exported)
        assertFalse(h.vm.state.value.isExporting)
    }

    @Test
    fun open_hands_the_exported_file_to_the_platform_opener() = runTest {
        val h = Harness()
        h.vm.export("p1")
        advanceUntilIdle()

        h.vm.open()
        advanceUntilIdle()

        assertEquals(listOf("/cache/exports/chantier-villa-2026-09-08.pdf"), h.opener.opened)
        assertTrue(h.sharer.shared.isEmpty())
    }

    @Test
    fun share_hands_the_exported_file_to_the_platform_sharer() = runTest {
        val h = Harness()
        h.vm.export("p1")
        advanceUntilIdle()

        h.vm.share()
        advanceUntilIdle()

        assertEquals(listOf("/cache/exports/chantier-villa-2026-09-08.pdf"), h.sharer.shared)
        assertTrue(h.opener.opened.isEmpty())
    }

    @Test
    fun open_and_share_before_any_export_do_nothing() = runTest {
        val h = Harness()

        h.vm.open()
        h.vm.share()
        advanceUntilIdle()

        assertTrue(h.opener.opened.isEmpty())
        assertTrue(h.sharer.shared.isEmpty())
    }

    @Test
    fun an_open_failure_surfaces_as_unexpected() = runTest {
        val opener = FakePdfOpener().apply { error = IllegalStateException("no viewer app") }
        val h = Harness(opener = opener)
        h.vm.export("p1")
        advanceUntilIdle()

        h.vm.open()
        advanceUntilIdle()

        assertEquals(DomainException.Unexpected, h.vm.state.value.error)
    }

    @Test
    fun a_share_failure_surfaces_as_unexpected() = runTest {
        val sharer = FakePdfSharer().apply { error = IllegalStateException("no activity") }
        val h = Harness(sharer = sharer)
        h.vm.export("p1")
        advanceUntilIdle()

        h.vm.share()
        advanceUntilIdle()

        assertEquals(DomainException.Unexpected, h.vm.state.value.error)
    }

    @Test
    fun clear_error_resets_it() = runTest {
        val repo = FakeExportRepository().apply { error = DomainException.Network }
        val h = Harness(repo)
        h.vm.export("p1")
        advanceUntilIdle()
        assertEquals(DomainException.Network, h.vm.state.value.error)

        h.vm.clearError()
        assertNull(h.vm.state.value.error)
    }
}
