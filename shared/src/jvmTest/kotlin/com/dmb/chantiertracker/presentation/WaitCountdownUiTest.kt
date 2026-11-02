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
import com.dmb.chantiertracker.presentation.auth.forgot.ForgotPasswordScreen
import com.dmb.chantiertracker.presentation.auth.forgot.ForgotPasswordViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class WaitCountdownUiTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest fun tearDown() {
        customAppLocale = null
        Dispatchers.resetMain()
    }

    @Test
    fun a_thirty_minute_lock_counts_down_and_the_forgotten_password_link_stays_usable() = runComposeUiTest {
        mainClock.autoAdvance = false
        var openedWith: String? = null
        val viewModel = LoginViewModel(
            FakeAuthRepository().apply { error = DomainException.AccountLocked(retryAfterSeconds = 1_800) },
            FakeCheckoutLauncher(), FakeBillingRepository(),
        )
        setContent {
            customAppLocale = "fr"
            AppEnvironment { AppTheme { LoginScreen({}, { openedWith = it }, onOpenLegalDocument = {}, viewModel = viewModel) } }
        }
        viewModel.onEmailChange("jean@chantier.dev")
        viewModel.onPasswordChange("Password1234!")
        mainClock.advanceTimeByFrame()

        onNodeWithText("Se connecter").performClick()
        mainClock.advanceTimeByFrame()

        onNodeWithText("Compte bloqué après trop de tentatives. Réessaie dans 30:00.").assertExists()
        onNodeWithText("Réinitialiser ton mot de passe débloque aussi ton compte.").assertExists()
        onNodeWithText("Mot de passe oublié ?").performClick()
        mainClock.advanceTimeByFrame()
        assertEquals("jean@chantier.dev", openedWith)
    }

    @Test
    fun the_reset_screen_opens_with_the_address_already_filled() = runComposeUiTest {
        val viewModel = ForgotPasswordViewModel(FakeAuthRepository())
        setContent {
            customAppLocale = "fr"
            AppEnvironment { AppTheme { ForgotPasswordScreen(onCodeSent = {}, prefilledEmail = "jean@chantier.dev", viewModel = viewModel) } }
        }

        waitForIdle()
        onNodeWithText("jean@chantier.dev").assertExists()
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
