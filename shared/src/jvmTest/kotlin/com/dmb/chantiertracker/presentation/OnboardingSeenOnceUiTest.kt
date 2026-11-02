package com.dmb.chantiertracker.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.data.session.SessionNotices
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.navigation.RootNavHost
import com.dmb.chantiertracker.presentation.navigation.RootViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class OnboardingSeenOnceUiTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest fun tearDown() {
        customAppLocale = null
        Dispatchers.resetMain()
    }

    @Test
    fun the_tutorial_is_recorded_as_seen_as_soon_as_it_shows_so_closing_it_never_brings_it_back() = runComposeUiTest {
        val auth = FakeAuthRepository().apply { emitState(AuthState.Unauthenticated) }
        setContent {
            customAppLocale = "fr"
            AppEnvironment { AppTheme { RootNavHost(RootViewModel(auth, SessionNotices())) } }
        }
        waitForIdle()

        onNodeWithText("Passer").assertIsDisplayed()
        assertTrue(auth.onboardingSeen, "recorded while still on screen: leaving the app now does not show it again")
        onNodeWithText("Passer").assertIsDisplayed()
    }
}
