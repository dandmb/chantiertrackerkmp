package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.presentation.auth.login.LoginScreen
import com.dmb.chantiertracker.presentation.auth.register.RegisterViewModel
import com.dmb.chantiertracker.presentation.auth.login.LoginViewModel
import com.dmb.chantiertracker.presentation.auth.verify.VerifyEmailScreen
import com.dmb.chantiertracker.presentation.auth.verify.VerifyEmailViewModel
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.navigation.RootNavHost
import com.dmb.chantiertracker.presentation.navigation.RootViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeBillingRepository
import com.dmb.chantiertracker.support.FakeCheckoutLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.domain.repository.BillingRepository
import com.dmb.chantiertracker.presentation.billing.CheckoutLauncher
import org.koin.core.module.dsl.viewModelOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class RegisterBackUiTest {

    private val auth = FakeAuthRepository()

    @BeforeTest fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        startKoin {
            modules(
                module {
                    single<AuthRepository> { auth }
                    single<BillingRepository> { FakeBillingRepository() }
                    single<CheckoutLauncher> { FakeCheckoutLauncher() }
                    viewModelOf(::LoginViewModel)
                    viewModelOf(::VerifyEmailViewModel)
                    viewModelOf(::RegisterViewModel)
                },
            )
        }
    }

    @AfterTest fun tearDown() {
        stopKoin()
        customAppLocale = null
        Dispatchers.resetMain()
    }

    private fun ComposeUiTest.mount(locale: String = "fr", content: @Composable () -> Unit) = setContent {
        customAppLocale = locale
        AppEnvironment { AppTheme { Box(Modifier.fillMaxSize()) { content() } } }
    }

    private fun ComposeUiTest.awaitText(text: String, ignore: Boolean = false) =
        waitUntil(timeoutMillis = 5_000L) { onAllNodes(hasText(text, substring = true, ignoreCase = ignore)).fetchSemanticsNodes().isNotEmpty() }

    private fun runPhoneTest(block: ComposeUiTest.() -> Unit) = runDesktopComposeUiTest(width = 412, height = 892) { block() }

    private fun ComposeUiTest.type(label: String, value: String) =
        onNode(hasSetTextAction() and hasText(label, substring = true)).performTextInput(value)

    private fun ComposeUiTest.openRootOnLogin() {
        auth.firstLoginCompleted = true
        auth.emitState(AuthState.Unauthenticated)
        mount { RootNavHost(RootViewModel(auth, com.dmb.chantiertracker.data.session.SessionNotices())) }
        awaitText("Valider mon compte")
    }

    @Test
    fun back_from_the_code_screen_after_signing_up_returns_to_the_sign_up_and_stays_there() = runPhoneTest {
        auth.firstLoginCompleted = true
        auth.emitState(AuthState.Unauthenticated)
        mount { RootNavHost(RootViewModel(auth, com.dmb.chantiertracker.data.session.SessionNotices())) }
        awaitText("Créer un compte")
        onNodeWithText("Créer un compte").performScrollTo().performClick()
        awaitText("S'inscrire")
        type("Nom", "Jean Marchand")
        type("Email", "jean@chantier.dev")
        type("Mot de passe", "Password1234!")
        onNodeWithText("S'inscrire").performScrollTo().performClick()
        awaitText("Saisis le code à 6 chiffres envoyé à jean@chantier.dev.")

        onAllNodes(androidx.compose.ui.test.hasContentDescription("Retour")).onFirst().performClick()
        waitForIdle()
        mainClock.advanceTimeBy(2_000)
        waitForIdle()

        onNodeWithText("S'inscrire").assertExists()
        assertEquals(0, onAllNodes(hasText("Saisis le code à 6 chiffres envoyé", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun the_new_password_screen_never_tells_whether_the_account_exists() = runPhoneTest {
        mount {
            com.dmb.chantiertracker.presentation.auth.reset.ResetPasswordScreen(
                email = "inconnu@chantier.dev", onReset = {}, onBackToLogin = {},
                viewModel = com.dmb.chantiertracker.presentation.auth.reset.ResetPasswordViewModel(auth),
            )
        }
        awaitText("Si un compte existe pour cette adresse, un code vient d'être envoyé.")
        assertEquals(0, onAllNodes(hasText("Code envoyé à", substring = true)).fetchSemanticsNodes().size)
    }
}
