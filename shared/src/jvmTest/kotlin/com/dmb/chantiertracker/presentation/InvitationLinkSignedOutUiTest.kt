package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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
    private val preferences = com.dmb.chantiertracker.support.FakeAppPreferences()
    private var invitations = com.dmb.chantiertracker.presentation.invitations.InvitationDeepLinkDispatcher(preferences)
    private val opener = com.dmb.chantiertracker.support.FakeUrlOpener()
    private val invitationRepository = com.dmb.chantiertracker.support.FakeInvitationRepository()

    @BeforeTest fun setUp() {
        invitationFor(accountExists = true)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        startKoin {
            modules(
                module {
                    single<AuthRepository> { auth }
                    single<BillingRepository> { FakeBillingRepository() }
                    single<CheckoutLauncher> { FakeCheckoutLauncher() }
                    viewModelOf(::LoginViewModel)
                    viewModelOf(::VerifyEmailViewModel)
                    factory { invitations }
                    single<com.dmb.chantiertracker.domain.repository.InvitationRepository> { invitationRepository }
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
    private fun invitationFor(accountExists: Boolean, status: com.dmb.chantiertracker.domain.model.InvitationStatus = com.dmb.chantiertracker.domain.model.InvitationStatus.PENDING) {
        invitationRepository.preview = com.dmb.chantiertracker.domain.model.InvitationPreview(
            projectId = 9, projectName = "Villa Vidal",
            status = status,
            accountExists = accountExists,
        )
    }

    private fun ComposeUiTest.absent(text: String) =
        assertEquals(0, onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().size, "« $text » ne doit pas s'afficher")

    @Test
    fun a_link_for_an_address_without_an_account_leads_straight_to_the_sign_up_with_the_join_notice() = runPhoneTest {
        invitationFor(accountExists = false)
        auth.onboardingSeen = true
        auth.emitState(AuthState.Unauthenticated)
        invitations.dispatch("jeton-42")
        mount { RootNavHost(RootViewModel(auth, com.dmb.chantiertracker.data.session.SessionNotices())) }

        awaitText("Crée ton compte pour rejoindre le projet.")
        onNodeWithText("S'inscrire").assertExists()
        absent("Valider mon compte")
        assertEquals("jeton-42", invitations.pending.value)
    }

    @Test
    fun a_link_without_an_account_on_a_phone_already_used_to_sign_in_also_opens_the_sign_up() = runPhoneTest {
        invitationFor(accountExists = false)
        invitations.dispatch("jeton-42")
        auth.firstLoginCompleted = true
        auth.emitState(AuthState.Unauthenticated)
        mount { RootNavHost(RootViewModel(auth, com.dmb.chantiertracker.data.session.SessionNotices())) }

        awaitText("Crée ton compte pour rejoindre le projet.")
        assertEquals("jeton-42", invitations.pending.value)
    }

    @Test
    fun choosing_sign_in_on_the_sign_up_opened_by_the_link_shows_the_sign_in_with_the_invitation_and_stays_there() = runPhoneTest {
        invitationFor(accountExists = false)
        auth.onboardingSeen = true
        auth.emitState(AuthState.Unauthenticated)
        invitations.dispatch("jeton-42")
        mount { RootNavHost(RootViewModel(auth, com.dmb.chantiertracker.data.session.SessionNotices())) }
        awaitText("Crée ton compte pour rejoindre le projet.")

        onNodeWithText("Se connecter").performScrollTo().performClick()
        awaitText("Connecte-toi pour rejoindre le projet.")
        mainClock.advanceTimeBy(2_000)
        waitForIdle()

        onNodeWithText("Valider mon compte").assertExists()
        absent("Crée ton compte pour rejoindre le projet.")
        assertEquals("jeton-42", invitations.pending.value)
    }

    private var launches by mutableIntStateOf(0)

    private fun ComposeUiTest.mountRelaunchable() =
        mount { key(launches) { RootNavHost(RootViewModel(auth, com.dmb.chantiertracker.data.session.SessionNotices())) } }

    private fun ComposeUiTest.relaunchFromTheIcon(afterARestart: Boolean = false) {
        if (afterARestart) invitations = com.dmb.chantiertracker.presentation.invitations.InvitationDeepLinkDispatcher(preferences)
        launches++
        waitForIdle()
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
    }

    private fun ComposeUiTest.openTheSignUpFromALinkWithoutAccount() {
        invitationFor(accountExists = false)
        auth.onboardingSeen = true
        auth.emitState(AuthState.Unauthenticated)
        invitations.dispatch("jeton-42")
        mountRelaunchable()
        awaitText("Crée ton compte pour rejoindre le projet.")
    }

    @Test
    fun relaunching_after_a_link_opened_the_sign_up_shows_the_sign_in_with_the_invitation() = runPhoneTest {
        openTheSignUpFromALinkWithoutAccount()

        relaunchFromTheIcon()

        awaitText("Connecte-toi pour rejoindre le projet.")
        onNodeWithText("Valider mon compte").assertExists()
        absent("Crée ton compte pour rejoindre le projet.")
        assertEquals("jeton-42", invitations.pending.value)
    }

    @Test
    fun after_a_phone_restart_the_sign_in_still_shows_the_invitation() = runPhoneTest {
        openTheSignUpFromALinkWithoutAccount()

        relaunchFromTheIcon(afterARestart = true)

        awaitText("Connecte-toi pour rejoindre le projet.")
        absent("Crée ton compte pour rejoindre le projet.")
        assertEquals("jeton-42", invitations.pending.value)
    }

    @Test
    fun create_an_account_from_the_relaunched_sign_in_shows_the_sign_up_with_the_invitation() = runPhoneTest {
        openTheSignUpFromALinkWithoutAccount()
        relaunchFromTheIcon()
        awaitText("Connecte-toi pour rejoindre le projet.")

        onNodeWithText("Créer un compte").performScrollTo().performClick()

        awaitText("Crée ton compte pour rejoindre le projet.")
        assertEquals("jeton-42", invitations.pending.value)
    }

    @Test
    fun opening_the_link_again_after_a_relaunch_leads_to_the_sign_up_again() = runPhoneTest {
        openTheSignUpFromALinkWithoutAccount()
        relaunchFromTheIcon()
        awaitText("Connecte-toi pour rejoindre le projet.")

        invitations.dispatch("jeton-42")

        awaitText("Crée ton compte pour rejoindre le projet.")
    }

    @Test
    fun ignore_on_the_sign_in_forgets_the_invitation_even_after_a_restart() = runPhoneTest {
        openTheSignUpFromALinkWithoutAccount()
        relaunchFromTheIcon()
        awaitText("Connecte-toi pour rejoindre le projet.")

        onNodeWithText("Ignorer").performClick()
        waitForIdle()

        absent("Connecte-toi pour rejoindre le projet.")
        onNodeWithText("Connecte-toi pour suivre tes chantiers.").assertExists()
        assertEquals(null, invitations.pending.value)
        relaunchFromTheIcon(afterARestart = true)
        absent("pour rejoindre le projet.")
        assertEquals(null, invitations.pending.value)
        assertTrue(preferences.store.isEmpty(), "jeton gardé : ${preferences.store}")
    }

    @Test
    fun ignore_on_the_sign_up_forgets_the_invitation() = runPhoneTest {
        openTheSignUpFromALinkWithoutAccount()

        onNodeWithText("Ignorer").performClick()
        waitForIdle()

        absent("Crée ton compte pour rejoindre le projet.")
        assertEquals(null, invitations.pending.value)
        assertTrue(preferences.store.isEmpty(), "jeton gardé : ${preferences.store}")
    }

    @Test
    fun an_expired_invitation_is_forgotten_while_signed_out() = runPhoneTest {
        invitationFor(accountExists = false, status = com.dmb.chantiertracker.domain.model.InvitationStatus.EXPIRED)
        invitations.dispatch("jeton-42")
        openRootOnLogin()

        waitUntil(timeoutMillis = 5_000L) { invitations.pending.value == null }
        absent("pour rejoindre le projet.")
        assertTrue(preferences.store.isEmpty(), "jeton gardé : ${preferences.store}")
    }

    @Test
    fun an_unknown_invitation_is_forgotten_while_signed_out() = runPhoneTest {
        invitationRepository.preview = null
        invitations.dispatch("jeton-42")
        openRootOnLogin()

        waitUntil(timeoutMillis = 5_000L) { invitations.pending.value == null }
        absent("pour rejoindre le projet.")
    }

    @Test
    fun an_unreachable_server_keeps_the_invitation() = runPhoneTest {
        invitationRepository.previewError = DomainException.Network
        invitations.dispatch("jeton-42")
        openRootOnLogin()

        awaitText("Connecte-toi pour rejoindre le projet.")
        assertEquals("jeton-42", invitations.pending.value)
    }

    @Test
    fun the_sign_in_opened_by_the_link_on_a_fresh_install_is_the_first_screen_so_back_leaves_the_app() = runPhoneTest {
        auth.emitState(AuthState.Unauthenticated)
        invitations.dispatch("jeton-42")
        mount { RootNavHost(RootViewModel(auth, com.dmb.chantiertracker.data.session.SessionNotices())) }

        awaitText("Connecte-toi pour rejoindre le projet.")
        mainClock.advanceTimeBy(2_000)
        waitForIdle()

        assertEquals(0, onAllNodes(androidx.compose.ui.test.hasContentDescription("Retour")).fetchSemanticsNodes().size, "retour vers l'accueil")
    }

    @Test
    fun relaunching_from_the_welcome_screen_also_makes_the_sign_in_the_first_screen() = runPhoneTest {
        openTheSignUpFromALinkWithoutAccount()

        relaunchFromTheIcon()

        awaitText("Connecte-toi pour rejoindre le projet.")
        assertEquals(0, onAllNodes(androidx.compose.ui.test.hasContentDescription("Retour")).fetchSemanticsNodes().size, "retour vers l'accueil")
    }

    @Test
    fun a_link_for_an_existing_account_still_leads_to_the_sign_in() = runPhoneTest {
        invitationFor(accountExists = true)
        auth.onboardingSeen = true
        auth.emitState(AuthState.Unauthenticated)
        invitations.dispatch("jeton-42")
        mount { RootNavHost(RootViewModel(auth, com.dmb.chantiertracker.data.session.SessionNotices())) }

        awaitText("Connecte-toi pour rejoindre le projet.")
        onNodeWithText("Valider mon compte").assertExists()
        absent("Crée ton compte")
    }
}
