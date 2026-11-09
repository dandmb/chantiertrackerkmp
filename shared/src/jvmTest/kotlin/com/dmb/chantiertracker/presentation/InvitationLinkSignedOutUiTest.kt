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
class InvitationLinkSignedOutUiTest {

    private val auth = FakeAuthRepository()
    private val invitations = com.dmb.chantiertracker.presentation.invitations.InvitationDeepLinkDispatcher()
    private val opener = com.dmb.chantiertracker.support.FakeUrlOpener()

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
                    single { invitations }
                    single<com.dmb.chantiertracker.presentation.billing.UrlOpener> { opener }
                    single { com.dmb.chantiertracker.data.sync.AppCoroutineScope() }
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
    fun a_link_opened_while_signed_out_keeps_the_token_instead_of_opening_the_browser() = runPhoneTest {
        auth.emitState(AuthState.Unauthenticated)
        com.dmb.chantiertracker.presentation.invitations.dispatchInvitationDeepLink("https://chantiertracker.com/invitations/jeton-42")

        assertEquals("jeton-42", invitations.pending.value)
        assertTrue(opener.opened.isEmpty(), "browser opened: ${opener.opened}")
    }

    @Test
    fun a_pending_invitation_leads_a_signed_out_member_to_the_sign_in_and_keeps_the_token() = runPhoneTest {
        auth.onboardingSeen = true
        auth.emitState(AuthState.Unauthenticated)
        invitations.dispatch("jeton-42")
        mount { RootNavHost(RootViewModel(auth, com.dmb.chantiertracker.data.session.SessionNotices())) }

        awaitText("Connecte-toi pour rejoindre le projet.")
        onNodeWithText("Valider mon compte").assertExists()
        assertEquals("jeton-42", invitations.pending.value)
    }

    @Test
    fun a_member_who_signed_in_before_sees_the_join_notice_on_the_sign_in_opened_by_the_link() = runPhoneTest {
        invitations.dispatch("jeton-42")
        openRootOnLogin()

        awaitText("Connecte-toi pour rejoindre le projet.")
        assertEquals("jeton-42", invitations.pending.value)
    }

    @Test
    fun a_link_received_while_the_sign_in_is_shown_adds_the_join_notice() = runPhoneTest {
        openRootOnLogin()
        onNodeWithText("Connecte-toi pour suivre tes chantiers.").assertExists()

        invitations.dispatch("jeton-42")

        awaitText("Connecte-toi pour rejoindre le projet.")
    }

    @Test
    fun an_invitation_left_open_when_the_session_ended_comes_back_on_the_sign_in() = runPhoneTest {
        invitations.dispatch("jeton-42")
        invitations.consume()
        openRootOnLogin()

        awaitText("Connecte-toi pour rejoindre le projet.")
        assertEquals("jeton-42", invitations.pending.value)
    }
}
