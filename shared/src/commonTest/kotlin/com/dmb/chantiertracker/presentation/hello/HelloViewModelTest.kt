package com.dmb.chantiertracker.presentation.hello

import com.dmb.chantiertracker.domain.model.AppInfo
import com.dmb.chantiertracker.domain.repository.AppInfoRepository
import com.dmb.chantiertracker.domain.usecase.GetWelcomeMessageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class HelloViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class FakeAppInfoRepository(
        private val info: AppInfo,
    ) : AppInfoRepository {
        override suspend fun getAppInfo(): AppInfo = info
    }

    @Test
    fun starts_in_loading_then_emits_content() = runTest {
        val repo = FakeAppInfoRepository(
            AppInfo(platformName = "TestPlatform", apiBaseUrl = "https://example.test/api/v1"),
        )
        val viewModel = HelloViewModel(GetWelcomeMessageUseCase(repo))

        assertIs<HelloUiState.Loading>(viewModel.uiState.value)

        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<HelloUiState.Content>(state)
        assertEquals("Hello ChantierTracker", state.message.title)
        assertEquals("TestPlatform", state.message.platformName)
        assertEquals("https://example.test/api/v1", state.message.apiBaseUrl)
    }
}
