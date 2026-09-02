package com.dmb.chantiertracker.presentation.projects

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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private val older = Project("p1", "Ancien", null, "Nîmes", ProjectStatus.IN_PROGRESS, createdAt = "2026-01-01T09:00:00")
    private val newer = Project("p2", "Récent", null, null, ProjectStatus.SUSPENDED, createdAt = "2026-06-15T12:00:00")

    private fun vm(repo: FakeProjectRepository, holder: ProjectSortHolder = ProjectSortHolder()) =
        ProjectsViewModel(repo, holder)

    @Test
    fun list_comes_from_the_local_store_newest_first_by_default() = runTest {
        val repo = FakeProjectRepository(projects = listOf(older, newer))
        val vm = vm(repo)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(listOf(newer, older), state.projects)
        assertEquals(ProjectSort.NEWEST_FIRST, vm.sort.value)
    }

    @Test
    fun changing_sort_reorders_the_same_local_data() = runTest {
        val holder = ProjectSortHolder()
        val repo = FakeProjectRepository(projects = listOf(older, newer))
        val vm = vm(repo, holder)
        advanceUntilIdle()
        assertEquals(listOf(newer, older), vm.state.value.projects)

        holder.set(ProjectSort.OLDEST_FIRST)
        advanceUntilIdle()

        assertEquals(listOf(older, newer), vm.state.value.projects)
        assertEquals(0, repo.refreshCount, "changer le tri ne relance aucune sync")
    }

    @Test
    fun a_new_project_appearing_in_the_store_shows_up_without_a_reload() = runTest {
        val repo = FakeProjectRepository(projects = listOf(older))
        val vm = vm(repo)
        advanceUntilIdle()
        assertEquals(listOf(older), vm.state.value.projects)

        repo.projectsFlow.value = listOf(older, newer)
        advanceUntilIdle()

        assertEquals(listOf(newer, older), vm.state.value.projects)
    }

    @Test
    fun empty_store_flags_the_empty_state() = runTest {
        val vm = vm(FakeProjectRepository(projects = emptyList()))
        advanceUntilIdle()

        assertTrue(vm.state.value.isEmpty)
    }

    @Test
    fun on_enter_triggers_a_background_refresh() = runTest {
        val repo = FakeProjectRepository(projects = listOf(older))
        val vm = vm(repo)
        advanceUntilIdle()

        vm.onEnter()
        advanceUntilIdle()

        assertEquals(1, repo.refreshCount)
    }
}
