package com.dmb.chantiertracker.presentation.main

import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.Plan
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private val user = User(1, "jean@chantier.dev", "Jean", true, GlobalRole.USER)

    @Test
    fun exposes_account_identity_and_plan() = runTest {
        val auth = FakeAuthRepository()
        val vm = MainViewModel(auth, FakeAccountRepository(plan = Plan.LIBERTE))
        auth.emitState(AuthState.Authenticated(user))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("Jean", state.userName)
        assertEquals("jean@chantier.dev", state.email)
        assertEquals(Plan.LIBERTE, state.plan)
    }

    @Test
    fun plan_failure_is_swallowed() = runTest {
        val auth = FakeAuthRepository()
        val vm = MainViewModel(auth, FakeAccountRepository(error = RuntimeException("boom")))
        auth.emitState(AuthState.Authenticated(user))
        advanceUntilIdle()

        assertEquals(null, vm.state.value.plan)
        assertEquals("Jean", vm.state.value.userName)
    }

    @Test
    fun logout_delegates_to_repository() = runTest {
        val auth = FakeAuthRepository()
        val vm = MainViewModel(auth, FakeAccountRepository())
        vm.logout()
        advanceUntilIdle()

        assertTrue(auth.calls.contains("logout"))
    }
}
