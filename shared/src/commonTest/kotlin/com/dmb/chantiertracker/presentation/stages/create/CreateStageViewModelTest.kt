package com.dmb.chantiertracker.presentation.stages.create

import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectMember
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.validation_date_format
import com.dmb.chantiertracker.resources.validation_end_before_start
import com.dmb.chantiertracker.resources.validation_name_required
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeProjectRepository
import com.dmb.chantiertracker.support.FakeStageRepository
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
class CreateStageViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private fun detail(ownerId: Long?) = ProjectDetail(
        localId = "p1", name = "Villa", description = null, location = null,
        currency = "EUR", timezone = "Europe/Paris", status = ProjectStatus.IN_PROGRESS, ownerId = ownerId,
    )

    private fun auth(userId: Long) = FakeAuthRepository().apply {
        emitState(AuthState.Authenticated(User(userId, "u@x.dev", "U", true, GlobalRole.USER)))
    }

    private fun vm(
        stages: FakeStageRepository = FakeStageRepository(),
        projects: FakeProjectRepository = FakeProjectRepository(detail = detail(ownerId = 1)),
        userId: Long = 1,
    ) = CreateStageViewModel(stages, projects, auth(userId))

    @Test
    fun owner_may_set_the_budget() = runTest {
        val v = vm(userId = 1, projects = FakeProjectRepository(detail = detail(ownerId = 1)))
        v.start("p1")
        advanceUntilIdle()
        assertTrue(v.state.value.canSetBudget)
    }

    @Test
    fun a_supervisor_may_not_set_the_budget_and_it_is_never_sent() = runTest {
        val projects = FakeProjectRepository(
            detail = detail(ownerId = 7),
            members = listOf(ProjectMember(userId = 9, name = "Sam", email = "s@x.dev", role = ProjectRole.SUPERVISOR)),
        )
        val stages = FakeStageRepository()
        val v = CreateStageViewModel(stages, projects, auth(userId = 9))
        v.start("p1")
        advanceUntilIdle()
        assertFalse(v.state.value.canSetBudget)

        v.onNameChange("Fondations")
        v.onBudgetChange("5000")
        v.submit()
        advanceUntilIdle()

        assertTrue(v.state.value.created)
        assertNull(stages.lastCreateInput?.estimatedBudget, "budget dropped client-side for a non-admin")
    }

    @Test
    fun blank_name_blocks_submission() = runTest {
        val stages = FakeStageRepository()
        val v = vm(stages = stages)
        v.start("p1")
        advanceUntilIdle()

        v.onNameChange("   ")
        v.submit()
        advanceUntilIdle()

        assertEquals(Res.string.validation_name_required, v.state.value.nameError)
        assertTrue(stages.log.isEmpty())
    }

    @Test
    fun a_malformed_date_still_blocks_submission_defensively() = runTest {
        // The calendar picker can't produce this, but the guard stays in the VM.
        val stages = FakeStageRepository()
        val v = vm(stages = stages)
        v.start("p1")
        advanceUntilIdle()

        v.onNameChange("Fondations")
        v.onStartDateChange("01/03/2026")
        v.submit()
        advanceUntilIdle()

        assertEquals(Res.string.validation_date_format, v.state.value.startDateError)
        assertTrue(stages.log.isEmpty())
    }

    @Test
    fun an_end_date_before_the_start_date_blocks_submission() = runTest {
        val stages = FakeStageRepository()
        val v = vm(stages = stages)
        v.start("p1")
        advanceUntilIdle()

        v.onNameChange("Fondations")
        v.onStartDateChange("2027-05-10")
        v.onEndDateChange("2027-05-01")
        v.submit()
        advanceUntilIdle()

        assertEquals(Res.string.validation_end_before_start, v.state.value.endDateError)
        assertTrue(stages.log.isEmpty())
    }

    @Test
    fun moving_the_start_past_a_chosen_end_clears_the_end() = runTest {
        val v = vm()
        v.start("p1")
        advanceUntilIdle()

        v.onEndDateChange("2027-05-01")
        v.onStartDateChange("2027-06-01")

        assertEquals("", v.state.value.endDate)
    }

    @Test
    fun successful_creation_flags_created_and_trims_optional_blanks() = runTest {
        val stages = FakeStageRepository()
        val v = vm(stages = stages, projects = FakeProjectRepository(detail = detail(ownerId = 1)))
        v.start("p1")
        advanceUntilIdle()

        v.onNameChange("  Gros œuvre  ")
        v.onDescriptionChange("   ")
        v.onBudgetChange("12000")
        v.onStartDateChange("2027-02-01")
        v.onEndDateChange("2027-05-15")
        v.submit()
        advanceUntilIdle()

        assertTrue(v.state.value.created)
        val input = stages.lastCreateInput!!
        assertEquals("p1", input.projectLocalId)
        assertEquals("Gros œuvre", input.name)
        assertNull(input.description)
        assertEquals(12000.0, input.estimatedBudget)
        assertEquals("2027-02-01", input.startDate)
        assertEquals("2027-05-15", input.endDate)
    }

    @Test
    fun creation_succeeds_locally_even_with_no_connectivity() = runTest {
        val stages = FakeStageRepository()
        val v = vm(stages = stages)
        v.start("p1")
        advanceUntilIdle()

        v.onNameChange("Fondations")
        v.submit()
        advanceUntilIdle()

        assertTrue(v.state.value.created)
        assertNull(v.state.value.formError)
        assertFalse(v.state.value.isSubmitting)
    }
}
