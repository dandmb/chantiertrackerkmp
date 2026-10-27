package com.dmb.chantiertracker.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.presentation.auth.login.LoginScreen
import com.dmb.chantiertracker.presentation.auth.login.LoginViewModel
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeBillingRepository
import com.dmb.chantiertracker.support.FakeCheckoutLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class WaitCountdownUiTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest fun tearDown() {
        customAppLocale = null
        Dispatchers.resetMain()
    }

    @Test
    fun a_locked_account_counts_down_every_second_and_the_button_comes_back_at_zero() = runComposeUiTest {
        mainClock.autoAdvance = false
        val viewModel = LoginViewModel(
            FakeAuthRepository().apply { error = DomainException.AccountLocked(retryAfterSeconds = 62) },
            FakeCheckoutLauncher(), FakeBillingRepository(),
        )
        setContent {
            customAppLocale = "fr"
            AppEnvironment { AppTheme { LoginScreen({}, {}, onOpenLegalDocument = {}, viewModel = viewModel) } }
        }
        viewModel.onEmailChange("jean@chantier.dev")
        viewModel.onPasswordChange("Password1234!")
        mainClock.advanceTimeByFrame()

        onNodeWithText("Se connecter").performClick()
        mainClock.advanceTimeByFrame()
        onNodeWithText("Compte bloqué après trop de tentatives. Réessaie dans 1:02.").assertExists()
        onNodeWithText("Se connecter").assertIsNotEnabled()

        mainClock.advanceTimeBy(3_000L)
        onNodeWithText("Compte bloqué après trop de tentatives. Réessaie dans 59 secondes.").assertExists()
        onNodeWithText("Se connecter").assertIsNotEnabled()

        mainClock.advanceTimeBy(59_000L)
        onNodeWithText("Tu peux réessayer maintenant.").assertExists()
        onNodeWithText("Se connecter").assertIsEnabled()
    }
}
