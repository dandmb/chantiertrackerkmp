package com.dmb.chantiertracker.presentation.projects

import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Project
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

    private val sample = listOf(
        Project(1, "Villa Vidal", null, "Nîmes", ProjectStatus.IN_PROGRESS),
        Project(2, "Hangar Est", null, null, ProjectStatus.SUSPENDED),
    )

    @Test
    fun loads_projects_on_init() = runTest {
        val repo = FakeProjectRepository(projects = sample)
        val vm = ProjectsViewModel(repo)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(sample, state.projects)
        assertFalse(state.isEmpty)
        assertEquals(1, repo.calls)
    }

    @Test
    fun no_projects_flags_empty_state() = runTest {
        val vm = ProjectsViewModel(FakeProjectRepository(projects = emptyList()))
        advanceUntilIdle()

        assertTrue(vm.state.value.isEmpty)
    }

    @Test
    fun failure_surfaces_error_not_empty_state() = runTest {
        val vm = ProjectsViewModel(FakeProjectRepository(error = DomainException.Network))
        advanceUntilIdle()

        val state = vm.state.value
        assertIs<DomainException.Network>(state.error)
        assertFalse(state.isEmpty)
    }

    @Test
    fun retry_reloads() = runTest {
        val repo = FakeProjectRepository(error = DomainException.Network)
        val vm = ProjectsViewModel(repo)
        advanceUntilIdle()
        assertIs<DomainException.Network>(vm.state.value.error)

        repo.error = null
        repo.projects = sample
        vm.load()
        advanceUntilIdle()

        assertEquals(sample, vm.state.value.projects)
        assertEquals(null, vm.state.value.error)
        assertEquals(2, repo.calls)
    }
}
