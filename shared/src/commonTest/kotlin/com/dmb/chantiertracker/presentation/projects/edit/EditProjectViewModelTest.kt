package com.dmb.chantiertracker.presentation.projects.edit

import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.validation_name_required
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditProjectViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private fun detail(
        name: String = "Villa Vidal",
        description: String? = "Grande villa",
        location: String? = "Nîmes",
        currency: String = "EUR",
        timezone: String = "Europe/Paris",
        status: ProjectStatus = ProjectStatus.SUSPENDED,
    ) = ProjectDetail("p1", name, description, location, currency, timezone, status, ownerId = 1L)

    @Test
    fun prefills_the_form_from_the_stored_project() = runTest {
        val repo = FakeProjectRepository(detail = detail())
        val vm = EditProjectViewModel(repo)
        vm.load("p1")
        advanceUntilIdle()

        val s = vm.state.value
        assertTrue(s.prefilled)
        assertEquals("Villa Vidal", s.name)
        assertEquals("Grande villa", s.description)
        assertEquals("Nîmes", s.location)
        assertEquals("EUR", s.currency)
        assertEquals("Europe/Paris", s.timezone)
        assertTrue(s.timezone in s.timezoneOptions)
    }

    @Test
    fun an_uncommon_timezone_is_kept_in_the_options() = runTest {
        val repo = FakeProjectRepository(detail = detail(timezone = "Pacific/Noumea"))
        val vm = EditProjectViewModel(repo)
        vm.load("p1")
        advanceUntilIdle()

        assertEquals("Pacific/Noumea", vm.state.value.timezoneOptions.first())
    }

    @Test
    fun a_later_store_emission_does_not_clobber_the_users_edits() = runTest {
        val repo = FakeProjectRepository(detail = detail())
        val vm = EditProjectViewModel(repo)
        vm.load("p1")
        advanceUntilIdle()

        vm.onNameChange("Villa rénovée")
        repo.detailFlow.value = detail(name = "Renamed elsewhere")
        advanceUntilIdle()

        assertEquals("Villa rénovée", vm.state.value.name)
    }

    @Test
    fun blank_name_blocks_submission() = runTest {
        val repo = FakeProjectRepository(detail = detail())
        val vm = EditProjectViewModel(repo)
        vm.load("p1")
        advanceUntilIdle()

        vm.onNameChange("   ")
        vm.submit()
        advanceUntilIdle()

        assertEquals(Res.string.validation_name_required, vm.state.value.nameError)
        assertTrue(repo.log.none { it.startsWith("updateProject") })
    }

    @Test
    fun saving_calls_updateProject_with_the_edited_values_and_the_unchanged_status() = runTest {
        val repo = FakeProjectRepository(detail = detail(status = ProjectStatus.SUSPENDED))
        val vm = EditProjectViewModel(repo)
        vm.load("p1")
        advanceUntilIdle()

        vm.onNameChange("  Villa rénovée  ")
        vm.onDescriptionChange("   ")
        vm.onLocationChange("Montpellier")
        vm.onTimezoneChange("Europe/London")
        vm.submit()
        advanceUntilIdle()

        assertTrue(vm.state.value.saved)
        val input = repo.lastUpdateInput!!
        assertEquals("Villa rénovée", input.name)
        assertNull(input.description, "blanked optional field → null")
        assertEquals("Montpellier", input.location)
        assertEquals("Europe/London", input.timezone)
        assertEquals(ProjectStatus.SUSPENDED, input.status, "edit form does not touch the status")
        assertEquals(listOf("updateProject:p1:Villa rénovée"), repo.log)
    }

    @Test
    fun a_missing_project_is_flagged() = runTest {
        val repo = FakeProjectRepository(detail = null)
        val vm = EditProjectViewModel(repo)
        vm.load("gone")
        advanceUntilIdle()

        assertTrue(vm.state.value.isMissing)
        assertFalse(vm.state.value.prefilled)
    }
}
