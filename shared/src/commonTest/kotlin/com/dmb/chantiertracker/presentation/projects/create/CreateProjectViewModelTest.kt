package com.dmb.chantiertracker.presentation.projects.create

import com.dmb.chantiertracker.domain.model.DomainException
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateProjectViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    @Test
    fun starts_with_a_timezone_selected_from_the_options() {
        val vm = CreateProjectViewModel(FakeProjectRepository())
        val state = vm.state.value
        assertTrue(state.timezoneOptions.isNotEmpty())
        assertTrue(state.timezone in state.timezoneOptions)
    }

    @Test
    fun blank_name_blocks_submission() = runTest {
        val repo = FakeProjectRepository()
        val vm = CreateProjectViewModel(repo)
        vm.onNameChange("   ")
        vm.submit()
        advanceUntilIdle()

        assertEquals(Res.string.validation_name_required, vm.state.value.nameError)
        assertTrue(repo.log.isEmpty())
    }

    @Test
    fun successful_creation_flags_created_and_trims_optional_blanks() = runTest {
        val repo = FakeProjectRepository(createdId = 91L)
        val vm = CreateProjectViewModel(repo)
        vm.onNameChange("  Villa Vidal  ")
        vm.onDescriptionChange("   ")
        vm.onLocationChange("Nîmes")
        vm.onCurrencyChange("")
        vm.onTimezoneChange("Europe/Paris")
        vm.submit()
        advanceUntilIdle()

        assertTrue(vm.state.value.created)
        val input = repo.lastCreateInput!!
        assertEquals("Villa Vidal", input.name)
        assertNull(input.description)
        assertEquals("Nîmes", input.location)
        assertNull(input.currency)
        assertEquals("Europe/Paris", input.timezone)
    }

    @Test
    fun backend_error_is_surfaced_and_does_not_flag_created() = runTest {
        val repo = FakeProjectRepository(error = DomainException.PlanLimitReached)
        val vm = CreateProjectViewModel(repo)
        vm.onNameChange("Villa")
        vm.submit()
        advanceUntilIdle()

        assertIs<DomainException.PlanLimitReached>(vm.state.value.formError)
        assertFalse(vm.state.value.created)
        assertEquals(false, vm.state.value.isSubmitting)
    }
}
