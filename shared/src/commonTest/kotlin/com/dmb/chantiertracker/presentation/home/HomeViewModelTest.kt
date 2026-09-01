package com.dmb.chantiertracker.presentation.home

import com.dmb.chantiertracker.domain.model.AppInfo
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.domain.repository.AppInfoRepository
import com.dmb.chantiertracker.domain.usecase.GetWelcomeMessageUseCase
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
class HomeViewModelTest {

    private lateinit var repo: FakeAuthRepository
    private lateinit var viewModel: HomeViewModel

    private val user = User(1, "jean@chantier.dev", "Jean", true, GlobalRole.USER)

    @BeforeTest
    fun setUp() {
        installTestMainDispatcher()
        repo = FakeAuthRepository()
        val appInfoRepo = object : AppInfoRepository {
            override suspend fun getAppInfo() = AppInfo("TestPlatform", "https://example.test/api/v1")
        }
        viewModel = HomeViewModel(repo, GetWelcomeMessageUseCase(appInfoRepo))
    }

    @AfterTest
    fun tearDown() = resetTestMainDispatcher()

    @Test
    fun shows_user_name_and_platform() = runTest {
        repo.emitState(AuthState.Authenticated(user))
        advanceUntilIdle()
        assertEquals("Jean", viewModel.state.value.userName)
        assertEquals("jean@chantier.dev", viewModel.state.value.email)
        assertEquals("TestPlatform", viewModel.state.value.platformName)
    }

    @Test
    fun logout_delegates_to_repository() = runTest {
        repo.emitState(AuthState.Authenticated(user))
        advanceUntilIdle()
        viewModel.logout()
        advanceUntilIdle()
        assertTrue(repo.calls.contains("logout"))
    }
}
