package com.dmb.chantiertracker.presentation.main

import com.dmb.chantiertracker.support.FakeSignOutRepository
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.PlanUsage
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.support.FakeAccountRepository
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private val user = User(1, "jean@chantier.dev", "Jean", true, GlobalRole.USER)

    @Test
    fun exposes_account_identity_and_the_last_known_plan() = runTest {
        val auth = FakeAuthRepository()
        val account = FakeAccountRepository(planUsage = PlanUsage(Plan.LIBERTE, null))
        val vm = MainViewModel(auth, account, FakeSignOutRepository())
        auth.emitState(AuthState.Authenticated(user))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("Jean", state.userName)
        assertEquals("jean@chantier.dev", state.email)
        assertEquals(Plan.LIBERTE, state.plan)
        assertEquals(1, account.refreshCount, "opening the app kicks a plan refresh")
    }

    @Test
    fun exposes_the_founder_status_for_the_account_menu() = runTest {
        val auth = FakeAuthRepository()
        val account = FakeAccountRepository(planUsage = PlanUsage(Plan.FREE, projectsLimit = 3, isFounder = true))
        val vm = MainViewModel(auth, account, FakeSignOutRepository())
        advanceUntilIdle()

        assertEquals(Plan.FREE, vm.state.value.plan)
        assertTrue(vm.state.value.isFounder)
    }

    @Test
    fun nobody_is_a_founder_until_the_plan_usage_is_known() = runTest {
        val vm = MainViewModel(FakeAuthRepository(), FakeAccountRepository(planUsage = null), FakeSignOutRepository())
        advanceUntilIdle()

        assertEquals(false, vm.state.value.isFounder)
    }

    @Test
    fun plan_stays_null_until_it_is_known() = runTest {
        val auth = FakeAuthRepository()
        val vm = MainViewModel(auth, FakeAccountRepository(planUsage = null), FakeSignOutRepository())
        auth.emitState(AuthState.Authenticated(user))
        advanceUntilIdle()

        assertNull(vm.state.value.plan)
        assertEquals("Jean", vm.state.value.userName)
    }

    @Test
    fun logout_goes_through_the_sign_out_guard_never_straight_to_the_auth_repository() = runTest {
        val auth = FakeAuthRepository()
        val signOut = FakeSignOutRepository()
        val vm = MainViewModel(auth, FakeAccountRepository(), signOut)
        vm.logout()
        advanceUntilIdle()

        assertEquals(listOf(false), signOut.calls)
        assertTrue("logout" !in auth.calls, "the guard (ADR-69) decides, then signs out itself")
    }

    // ─── ADR-69 — sign-out guard ─────────────────────────────────────────────

    private fun signOutWith(vararg results: com.dmb.chantiertracker.domain.repository.SignOutResult) =
        FakeSignOutRepository().apply { this.results.addAll(results) }

    @Test
    fun signing_out_with_nothing_unsent_shows_no_dialog() = runTest {
        val signOut = signOutWith(com.dmb.chantiertracker.domain.repository.SignOutResult.SignedOut)
        val vm = MainViewModel(FakeAuthRepository(), FakeAccountRepository(), signOut)

        vm.logout()
        advanceUntilIdle()

        assertNull(vm.state.value.logoutPrompt)
        assertEquals(listOf(false), signOut.calls)
    }

    @Test
    fun a_blocked_sign_out_shows_how_many_writes_are_waiting_and_retry_asks_again() = runTest {
        val signOut = signOutWith(
            com.dmb.chantiertracker.domain.repository.SignOutResult.Blocked(com.dmb.chantiertracker.domain.model.UnsentWrites(entries = 3)),
            com.dmb.chantiertracker.domain.repository.SignOutResult.SignedOut,
        )
        val vm = MainViewModel(FakeAuthRepository(), FakeAccountRepository(), signOut)

        vm.logout()
        advanceUntilIdle()
        assertEquals(LogoutPrompt.Blocked(com.dmb.chantiertracker.domain.model.UnsentWrites(entries = 3)), vm.state.value.logoutPrompt)
        assertEquals(false, vm.state.value.isLoggingOut)

        vm.retryLogout()
        advanceUntilIdle()
        assertNull(vm.state.value.logoutPrompt)
        assertEquals(listOf(false, false), signOut.calls)
    }

    @Test
    fun refused_writes_ask_for_confirmation_and_confirming_signs_out() = runTest {
        val signOut = signOutWith(
            com.dmb.chantiertracker.domain.repository.SignOutResult.RefusedWritesLeft(com.dmb.chantiertracker.domain.model.UnsentWrites(entries = 2)),
            com.dmb.chantiertracker.domain.repository.SignOutResult.SignedOut,
        )
        val vm = MainViewModel(FakeAuthRepository(), FakeAccountRepository(), signOut)

        vm.logout()
        advanceUntilIdle()
        assertEquals(LogoutPrompt.RefusedWritesLeft(com.dmb.chantiertracker.domain.model.UnsentWrites(entries = 2)), vm.state.value.logoutPrompt)

        vm.logoutDespiteRefusedWrites()
        advanceUntilIdle()
        assertNull(vm.state.value.logoutPrompt)
        assertEquals(listOf(false, true), signOut.calls)
    }

    @Test
    fun dismissing_the_dialog_keeps_the_session() = runTest {
        val signOut = signOutWith(com.dmb.chantiertracker.domain.repository.SignOutResult.Blocked(com.dmb.chantiertracker.domain.model.UnsentWrites(entries = 1)))
        val vm = MainViewModel(FakeAuthRepository(), FakeAccountRepository(), signOut)
        vm.logout()
        advanceUntilIdle()

        vm.dismissLogoutPrompt()

        assertNull(vm.state.value.logoutPrompt)
        assertEquals(false, vm.state.value.isLoggingOut)
        assertEquals(1, signOut.calls.size)
    }
}

