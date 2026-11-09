package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
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
    fun the_sign_in_screen_offers_to_validate_an_account_like_the_web_with_the_typed_address() = runPhoneTest {
        var opened: String? = null
        mount { LoginScreen({}, {}, onOpenLegalDocument = {}, onValidateAccount = { opened = it }, viewModel = LoginViewModel(auth, FakeCheckoutLauncher(), FakeBillingRepository())) }

        onNodeWithText("Compte pas encore validé ?", substring = true).assertExists()
        type("Email", "jean@chantier.dev")
        onNodeWithText("Valider mon compte").performScrollTo().performClick()
        assertEquals("jean@chantier.dev", opened)
    }

    @Test
    fun both_prompts_of_the_sign_in_are_centred_like_the_web() = runPhoneTest {
        mount { LoginScreen({}, {}, onOpenLegalDocument = {}, viewModel = LoginViewModel(auth, FakeCheckoutLauncher(), FakeBillingRepository())) }

        val create = onNodeWithText("Pas encore de compte ?", substring = true).performScrollTo().getBoundsInRoot()
        val createLink = onNodeWithText("Créer un compte").getBoundsInRoot()
        val validate = onNodeWithText("Compte pas encore validé ?", substring = true).getBoundsInRoot()
        val validateLink = onNodeWithText("Valider mon compte").getBoundsInRoot()
        val middle = 206f
        val createCentre = (create.left.value + createLink.right.value) / 2
        val validateCentre = (validate.left.value + validateLink.right.value) / 2
        assertTrue(kotlin.math.abs(createCentre - middle) < 3f, "Créer un compte centré : $createCentre")
        assertTrue(kotlin.math.abs(validateCentre - middle) < 3f, "Valider mon compte centré : $validateCentre")
    }

    @Test
    fun validate_my_account_opens_the_code_screen_of_the_web_without_signing_in_or_sending_a_code() = runPhoneTest {
        openRootOnLogin()
        type("Email", "jean@chantier.dev")
        onNodeWithText("Valider mon compte").performScrollTo().performClick()

        awaitText("Saisis le code à 6 chiffres reçu par email à l'inscription. Tu ne le retrouves pas ? Demande un nouveau code.")
        onNodeWithText("Vérifie ton email", ignoreCase = true).assertExists()
        onNode(hasSetTextAction() and hasText("jean@chantier.dev", substring = true)).assertExists()
        onNodeWithText("Celui que tu as choisi à l'inscription.").assertExists()
        onNodeWithText("Renvoyer le code").assertExists()
        onNodeWithText("Retour à la connexion").assertExists()
        waitForIdle()
        assertTrue(auth.calls.none { it.startsWith("login:") || it.startsWith("resendCode:") }, "${auth.calls}")
    }

    @Test
    fun the_code_and_the_password_activate_the_account_and_lead_back_to_the_sign_in() = runPhoneTest {
        openRootOnLogin()
        onNodeWithText("Valider mon compte").performScrollTo().performClick()
        awaitText("Reçu par email", ignore = true)
        type("Email", "jean@chantier.dev")
        type("Mot de passe", "Password1234!")
        type("Code", "123456")
        onNodeWithText("Vérifier").performScrollTo().performClick()

        awaitText("Compte vérifié, tu peux te connecter.")
        assertTrue("verifyEmail:jean@chantier.dev:123456:Password1234!" in auth.calls, "${auth.calls}")
        assertTrue(auth.calls.none { it.startsWith("login:") }, "${auth.calls}")
    }

    @Test
    fun a_new_code_needs_the_address_then_is_sent_to_it() = runPhoneTest {
        openRootOnLogin()
        onNodeWithText("Valider mon compte").performScrollTo().performClick()
        awaitText("Retour à la connexion")

        onNodeWithText("Renvoyer le code").performScrollTo().performClick()
        awaitText("Renseigne ton email avant de renvoyer un code.")
        assertTrue(auth.calls.none { it.startsWith("resendCode:") }, "${auth.calls}")

        type("Email", "jean@chantier.dev")
        onNodeWithText("Renvoyer le code").performScrollTo().performClick()
        awaitText("Un nouveau code a été envoyé.")
        assertEquals(1, auth.calls.count { it == "resendCode:jean@chantier.dev" }, "${auth.calls}")
    }

    @Test
    fun back_to_the_sign_in_from_the_code_screen() = runPhoneTest {
        openRootOnLogin()
        onNodeWithText("Valider mon compte").performScrollTo().performClick()
        awaitText("Retour à la connexion")

        onNodeWithText("Retour à la connexion").performScrollTo().performClick()
        awaitText("Compte pas encore validé ?")
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
