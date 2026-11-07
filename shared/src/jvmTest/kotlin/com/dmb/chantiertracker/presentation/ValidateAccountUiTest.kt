package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.presentation.auth.login.LoginScreen
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
class ValidateAccountUiTest {

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

    private fun ComposeUiTest.awaitText(text: String) =
        waitUntil(timeoutMillis = 5_000L) { onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun runPhoneTest(block: ComposeUiTest.() -> Unit) = runDesktopComposeUiTest(width = 412, height = 892) { block() }

    private fun ComposeUiTest.type(label: String, value: String) =
        onNode(hasSetTextAction() and hasText(label, substring = true)).performTextInput(value)

    @Test
    fun the_sign_in_screen_offers_to_validate_an_account() = runPhoneTest {
        var opened = 0
        mount { LoginScreen({}, {}, onOpenLegalDocument = {}, onValidateAccount = { opened++ }, viewModel = LoginViewModel(auth, FakeCheckoutLauncher(), FakeBillingRepository())) }

        onNodeWithText("Valider mon compte").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun validating_an_account_asks_address_and_password_sends_a_code_and_opens_the_code_screen() = runPhoneTest {
        auth.firstLoginCompleted = true
        auth.heldVerificationEmail = "jean@chantier.dev"
        auth.emitState(AuthState.Unauthenticated)
        mount { RootNavHost(RootViewModel(auth, com.dmb.chantiertracker.data.session.SessionNotices())) }
        awaitText("Valider mon compte")

        onNodeWithText("Valider mon compte").performScrollTo().performClick()
        awaitText("Recevoir un code")
        type("Email", "jean@chantier.dev")
        type("Mot de passe", "Password1234!")
        auth.loginError = DomainException.AccountNotVerified
        onNodeWithText("Recevoir un code").performScrollTo().performClick()

        awaitText("Saisis le code à 6 chiffres envoyé à jean@chantier.dev.")
        waitUntil(timeoutMillis = 5_000L) { "resendCode:jean@chantier.dev" in auth.calls }
        assertTrue("login:jean@chantier.dev:Password1234!" in auth.calls, "${auth.calls}")
        assertEquals(1, auth.calls.count { it == "resendCode:jean@chantier.dev" }, "one code, not two: ${auth.calls}")
    }

    @Test
    fun a_wrong_code_after_an_unverified_sign_in_also_points_at_the_password() = runPhoneTest {
        val failing = FakeAuthRepository().apply {
            heldVerificationEmail = "jean@chantier.dev"
            error = DomainException.InvalidCode
        }
        val vm = VerifyEmailViewModel(failing)
        mount { VerifyEmailScreen(email = "jean@chantier.dev", onVerified = {}, onCancelVerification = {}, unverifiedLogin = true, viewModel = vm) }
        vm.onCodeChange("123456")
        vm.submit("jean@chantier.dev")

        awaitText("Ce code ou ce mot de passe est incorrect")
    }
}
