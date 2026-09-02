package com.dmb.chantiertracker.presentation.projects

import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Project
import com.dmb.chantiertracker.domain.model.ProjectSort
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.support.FakeProjectRepository
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private val older = Project(1, "Ancien", null, "Nîmes", ProjectStatus.IN_PROGRESS, createdAt = "2026-01-01T09:00:00")
    private val newer = Project(2, "Récent", null, null, ProjectStatus.SUSPENDED, createdAt = "2026-06-15T12:00:00")
    private val unsortedFromApi = listOf(older, newer)

    private fun vm(repo: FakeProjectRepository, holder: ProjectSortHolder = ProjectSortHolder()) =
        ProjectsViewModel(repo, holder)

    @Test
    fun first_enter_loads_newest_first_by_default() = runTest {
        val repo = FakeProjectRepository(projects = unsortedFromApi)
        val vm = vm(repo)
        vm.onEnter()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(listOf(newer, older), state.projects)
        assertEquals(ProjectSort.NEWEST_FIRST, vm.sort.value)
        assertEquals(1, repo.calls)
    }

    @Test
    fun changing_sort_reorders_without_reloading() = runTest {
        val holder = ProjectSortHolder()
        val repo = FakeProjectRepository(projects = unsortedFromApi)
        val vm = vm(repo, holder)
        vm.onEnter()
        advanceUntilIdle()
        assertEquals(listOf(newer, older), vm.state.value.projects)

        holder.set(ProjectSort.OLDEST_FIRST)
        advanceUntilIdle()

        assertEquals(listOf(older, newer), vm.state.value.projects)
        assertEquals(1, repo.calls, "changer le tri ne relance pas l'appel réseau")
    }

    @Test
    fun no_projects_flags_empty_state() = runTest {
        val vm = vm(FakeProjectRepository(projects = emptyList()))
        vm.onEnter()
        advanceUntilIdle()

        assertTrue(vm.state.value.isEmpty)
    }

    @Test
    fun failure_surfaces_error_not_empty_state() = runTest {
        val vm = vm(FakeProjectRepository(error = DomainException.Network))
        vm.onEnter()
        advanceUntilIdle()

        assertIs<DomainException.Network>(vm.state.value.error)
        assertFalse(vm.state.value.isEmpty)
    }

    @Test
    fun re_enter_after_success_refreshes_silently_and_keeps_the_active_sort() = runTest {
        val holder = ProjectSortHolder().apply { set(ProjectSort.OLDEST_FIRST) }
        val repo = FakeProjectRepository(projects = listOf(older))
        val vm = vm(repo, holder)
        vm.onEnter()
        advanceUntilIdle()
        assertEquals(listOf(older), vm.state.value.projects)

        repo.projects = unsortedFromApi
        vm.onEnter()
        advanceUntilIdle()

        assertEquals(listOf(older, newer), vm.state.value.projects, "toujours trié plus ancien d'abord")
        assertFalse(vm.state.value.isLoading)
        assertEquals(2, repo.calls)
    }

    @Test
    fun re_enter_after_error_retries_with_spinner() = runTest {
        val repo = FakeProjectRepository(error = DomainException.Network)
        val vm = vm(repo)
        vm.onEnter()
        advanceUntilIdle()
        assertIs<DomainException.Network>(vm.state.value.error)

        repo.error = null
        repo.projects = unsortedFromApi
        vm.onEnter()
        advanceUntilIdle()

        assertEquals(listOf(newer, older), vm.state.value.projects)
        assertEquals(null, vm.state.value.error)
    }
}
