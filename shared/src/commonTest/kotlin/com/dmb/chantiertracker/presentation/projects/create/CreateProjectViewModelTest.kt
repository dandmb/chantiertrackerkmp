package com.dmb.chantiertracker.presentation.projects.create

import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.PlanUsage
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.validation_name_required
import com.dmb.chantiertracker.support.FakeAccountRepository
import com.dmb.chantiertracker.support.FakeAuthRepository
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

    private fun auth() = FakeAuthRepository().apply {
        emitState(AuthState.Authenticated(User(1, "u@x.dev", "U", true, GlobalRole.USER)))
    }

    private fun vm(
        projects: FakeProjectRepository = FakeProjectRepository(),
        account: FakeAccountRepository = FakeAccountRepository(),
    ) = CreateProjectViewModel(projects, account, auth())

    @Test
    fun starts_with_a_timezone_selected_from_the_options() {
        val state = vm().state.value
        assertTrue(state.timezoneOptions.isNotEmpty())
        assertTrue(state.timezone in state.timezoneOptions)
    }

    @Test
    fun blank_name_blocks_submission() = runTest {
        val repo = FakeProjectRepository()
        val v = vm(projects = repo)
        v.onNameChange("   ")
        v.submit()
        advanceUntilIdle()

        assertEquals(Res.string.validation_name_required, v.state.value.nameError)
        assertTrue(repo.log.isEmpty())
    }

    @Test
    fun successful_creation_flags_created_and_trims_optional_blanks() = runTest {
        val repo = FakeProjectRepository()
        val v = vm(projects = repo)
        v.onNameChange("  Villa Vidal  ")
        v.onDescriptionChange("   ")
        v.onLocationChange("Nîmes")
        v.onCurrencyChange("")
        v.onTimezoneChange("Europe/Paris")
        v.submit()
        advanceUntilIdle()

        assertTrue(v.state.value.created)
        val input = repo.lastCreateInput!!
        assertEquals("Villa Vidal", input.name)
        assertNull(input.description)
        assertEquals("Nîmes", input.location)
        assertNull(input.currency)
        assertEquals("Europe/Paris", input.timezone)
    }

    @Test
    fun creation_succeeds_locally_even_with_no_connectivity() = runTest {
        val repo = FakeProjectRepository()
        val v = vm(projects = repo)
        v.onNameChange("Villa")
        v.submit()
        advanceUntilIdle()

        assertTrue(v.state.value.created)
        assertNull(v.state.value.formError)
        assertEquals(false, v.state.value.isSubmitting)
        assertEquals("Villa", repo.lastCreateInput?.name)
    }

    @Test
    fun an_unexpected_local_write_failure_is_surfaced() = runTest {
        val repo = FakeProjectRepository().apply { createError = IllegalStateException("disk full") }
        val v = vm(projects = repo)
        v.onNameChange("Villa")
        v.submit()
        advanceUntilIdle()

        assertIs<DomainException.Unexpected>(v.state.value.formError)
        assertFalse(v.state.value.created)
    }

    // ─── plan limit: blocked upfront, no Room write (ADR-25) ────────────────

    @Test
    fun opening_the_screen_refreshes_the_plan() = runTest {
        val account = FakeAccountRepository()
        vm(account = account)
        advanceUntilIdle()
        assertEquals(1, account.refreshCount)
    }

    @Test
    fun a_free_account_at_its_project_limit_blocks_creation_without_writing() = runTest {
        val repo = FakeProjectRepository()
        val account = FakeAccountRepository(planUsage = PlanUsage(Plan.FREE, projectsLimit = 1))
        val v = vm(projects = repo, account = account)
        repo.activeProjectCountFlow.value = 1
        advanceUntilIdle()

        assertTrue(v.state.value.atProjectLimit)

        v.onNameChange("Deuxième chantier")
        v.submit()
        advanceUntilIdle()

        assertFalse(v.state.value.created)
        assertTrue(repo.log.isEmpty(), "nothing written to the local store")
        assertNull(repo.lastCreateInput)
    }

    @Test
    fun below_the_limit_creation_is_allowed() = runTest {
        val repo = FakeProjectRepository()
        val account = FakeAccountRepository(planUsage = PlanUsage(Plan.SEMI_FLEX, projectsLimit = 3))
        val v = vm(projects = repo, account = account)
        repo.activeProjectCountFlow.value = 2
        advanceUntilIdle()

        assertFalse(v.state.value.atProjectLimit)

        v.onNameChange("Troisième")
        v.submit()
        advanceUntilIdle()

        assertTrue(v.state.value.created)
    }

    @Test
    fun an_unlimited_plan_never_blocks() = runTest {
        val repo = FakeProjectRepository()
        val account = FakeAccountRepository(planUsage = PlanUsage(Plan.LIBERTE, projectsLimit = null))
        val v = vm(projects = repo, account = account)
        repo.activeProjectCountFlow.value = 42
        advanceUntilIdle()

        assertFalse(v.state.value.atProjectLimit)
    }

    @Test
    fun an_unknown_plan_fails_open() = runTest {
        // No plan info yet (offline cold start, never fetched) → don't block.
        val repo = FakeProjectRepository()
        val v = vm(projects = repo, account = FakeAccountRepository(planUsage = null))
        repo.activeProjectCountFlow.value = 5
        advanceUntilIdle()

        assertFalse(v.state.value.atProjectLimit)
    }

    @Test
    fun the_block_lifts_when_a_project_is_removed_locally() = runTest {
        val repo = FakeProjectRepository()
        val account = FakeAccountRepository(planUsage = PlanUsage(Plan.FREE, projectsLimit = 1))
        val v = vm(projects = repo, account = account)
        repo.activeProjectCountFlow.value = 1
        advanceUntilIdle()
        assertTrue(v.state.value.atProjectLimit)

        repo.activeProjectCountFlow.value = 0
        advanceUntilIdle()
        assertFalse(v.state.value.atProjectLimit)
    }
}
