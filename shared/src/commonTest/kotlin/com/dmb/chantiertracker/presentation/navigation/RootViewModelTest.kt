package com.dmb.chantiertracker.presentation.navigation

import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.User
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

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {

    private lateinit var repo: FakeAuthRepository

    @BeforeTest
    fun setUp() {
        installTestMainDispatcher()
        repo = FakeAuthRepository()
    }

    @AfterTest
    fun tearDown() = resetTestMainDispatcher()

    @Test
    fun first_ever_launch_flags_welcome_and_onboarding() = runTest {
        repo.firstLoginCompleted = false
        repo.onboardingSeen = false
        val vm = RootViewModel(repo)
        advanceUntilIdle()
        assertEquals(false, vm.hasLoggedInBefore.value)
        assertEquals(false, vm.hasSeenOnboarding.value)
    }

    @Test
    fun onboarding_shown_once_then_remembered() = runTest {
        repo.firstLoginCompleted = false
        repo.onboardingSeen = false
        val vm = RootViewModel(repo)
        advanceUntilIdle()
        assertEquals(false, vm.hasSeenOnboarding.value)

        vm.markOnboardingSeen()
        advanceUntilIdle()
        assertEquals(true, vm.hasSeenOnboarding.value)

        // A fresh launch (still not logged in) no longer flags onboarding.
        val next = RootViewModel(repo)
        advanceUntilIdle()
        assertEquals(true, next.hasSeenOnboarding.value)
        assertEquals(false, next.hasLoggedInBefore.value)
    }

    @Test
    fun launch_after_a_previous_login_never_flags_welcome() = runTest {
        repo.firstLoginCompleted = true
        val vm = RootViewModel(repo)
        advanceUntilIdle()
        assertEquals(true, vm.hasLoggedInBefore.value)
    }

    @Test
    fun login_flips_the_flag_so_logout_returns_to_login_not_welcome() = runTest {
        repo.firstLoginCompleted = false
        val vm = RootViewModel(repo)
        advanceUntilIdle()
        assertEquals(false, vm.hasLoggedInBefore.value)

        repo.login("a@b.dev", "secret")
        repo.emitState(AuthState.Authenticated(User(1, "a@b.dev", "A", true, GlobalRole.USER)))
        advanceUntilIdle()
        repo.emitState(AuthState.Unauthenticated)
        advanceUntilIdle()

        assertEquals(true, vm.hasLoggedInBefore.value)
    }
}
