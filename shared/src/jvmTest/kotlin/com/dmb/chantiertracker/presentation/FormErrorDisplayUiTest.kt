package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.ProjectStatus
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.presentation.auth.components.FORM_ERROR_SNACKBAR_TAG
import com.dmb.chantiertracker.presentation.auth.login.LoginScreen
import com.dmb.chantiertracker.presentation.auth.login.LoginViewModel
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.stages.create.CreateStageScreen
import com.dmb.chantiertracker.presentation.stages.create.CreateStageViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeBillingRepository
import com.dmb.chantiertracker.support.FakeCheckoutLauncher
import com.dmb.chantiertracker.support.FakeProjectRepository
import com.dmb.chantiertracker.support.FakeStageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class FormErrorDisplayUiTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest fun tearDown() {
        customAppLocale = null
        Dispatchers.resetMain()
    }

    private fun ComposeUiTest.show(locale: String = "fr", screen: @Composable () -> Unit) = setContent {
        customAppLocale = locale
        AppEnvironment { AppTheme { Box(Modifier.size(412.dp, 892.dp)) { screen() } } }
    }

    private fun ComposeUiTest.inSnackbar(text: String) = onNode(hasText(text) and hasAnyAncestor(hasTestTag(FORM_ERROR_SNACKBAR_TAG)))

    private fun ComposeUiTest.assertNowhere(text: String) {
        waitForIdle()
        check(onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isEmpty()) { "« $text » must not be shown" }
    }

    private fun login(error: DomainException?) = LoginViewModel(
        FakeAuthRepository().apply { this.error = error },
        FakeCheckoutLauncher(),
        FakeBillingRepository(),
    )

    @Test
    fun a_general_login_error_shows_in_a_snackbar() = runComposeUiTest {
        val vm = login(DomainException.InvalidCredentials)
        show { LoginScreen({}, {}, onOpenLegalDocument = {}, viewModel = vm) }
        vm.onEmailChange("jean@chantier.dev")
        vm.onPasswordChange("Password1234!")
        onNodeWithText("Se connecter").performClick()

        waitUntil(timeoutMillis = 5_000L) { onAllNodes(hasText("Email ou mot de passe incorrect.") and hasAnyAncestor(hasTestTag(FORM_ERROR_SNACKBAR_TAG))).fetchSemanticsNodes().isNotEmpty() }
        inSnackbar("Email ou mot de passe incorrect.").assertExists()
    }

    @Test
    fun the_snackbar_sits_at_the_top_on_ink_with_a_salmon_icon_a_lemon_close_and_never_covers_a_button() = runComposeUiTest {
        val vm = login(DomainException.InvalidCredentials)
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme(darkTheme = false) {
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f)) {
                        Box(Modifier.size(412.dp, 892.dp)) { LoginScreen({}, {}, onOpenLegalDocument = {}, viewModel = vm) }
                    }
                }
            }
        }
        vm.onEmailChange("jean@chantier.dev")
        vm.onPasswordChange("Password1234!")
        onNodeWithText("Se connecter").performClick()
        waitUntil(timeoutMillis = 5_000L) { onAllNodes(hasText("Email ou mot de passe incorrect.") and hasAnyAncestor(hasTestTag(FORM_ERROR_SNACKBAR_TAG))).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()

        val snackbar = onAllNodes(hasTestTag(FORM_ERROR_SNACKBAR_TAG)).fetchSemanticsNodes().single().boundsInRoot
        val close = inSnackbar("Fermer").assertHasClickAction().fetchSemanticsNode()
        val card = onAllNodes(hasText("Email ou mot de passe incorrect.") and hasAnyAncestor(hasTestTag(FORM_ERROR_SNACKBAR_TAG))).fetchSemanticsNodes().single()
        check(card.boundsInRoot.top < 892f / 3) { "at the top: ${card.boundsInRoot}" }
        val buttons = onAllNodes(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Button))
            .fetchSemanticsNodes().filter { it.id != close.id }
        val inkCard = onAllNodes(hasAnyAncestor(hasTestTag(FORM_ERROR_SNACKBAR_TAG)) and hasText("Email ou mot de passe incorrect.")).fetchSemanticsNodes().single().boundsInRoot
        buttons.forEach { check(!it.boundsInRoot.overlaps(snackbarCard(inkCard, close.boundsInRoot))) { "covers a button at ${it.boundsInRoot}" } }

        val pixels = onRoot().captureToImage().toPixelMap()
        val area = snackbarCard(inkCard, close.boundsInRoot)
        val colours = (area.top.toInt() until area.bottom.toInt()).flatMap { y -> (area.left.toInt() until area.right.toInt()).map { x -> pixels[x, y] } }.toSet()
        check(androidx.compose.ui.graphics.Color(0xFF22300D) in colours) { "ink background" }
        check(androidx.compose.ui.graphics.Color(0xFFE8743B) !in colours) { "no orange rule any more" }
        check(androidx.compose.ui.graphics.Color(0xFFF2A182) in colours) { "salmon error icon" }
        check(snackbar.width > 0f)
    }

    private fun snackbarCard(text: androidx.compose.ui.geometry.Rect, close: androidx.compose.ui.geometry.Rect) =
        androidx.compose.ui.geometry.Rect(text.left - 40f, minOf(text.top, close.top) - 4f, close.right, maxOf(text.bottom, close.bottom) + 4f)

    @Test
    fun a_field_error_stays_under_its_field_and_never_reaches_the_snackbar() = runComposeUiTest {
        val vm = login(null)
        show { LoginScreen({}, {}, onOpenLegalDocument = {}, viewModel = vm) }
        vm.onEmailChange("pas-une-adresse")
        vm.onPasswordChange("Password1234!")
        onNodeWithText("Se connecter").performClick()
        waitForIdle()

        onNodeWithText("L'email doit être valide.").assertExists()
        check(onAllNodes(hasAnyAncestor(hasTestTag(FORM_ERROR_SNACKBAR_TAG))).fetchSemanticsNodes().isEmpty()) { "no snackbar for a field error" }
    }

    @Test
    fun a_wait_counts_down_inside_the_disabled_button_and_the_snackbar_gives_no_time() = runComposeUiTest {
        mainClock.autoAdvance = false
        val vm = login(DomainException.RateLimited(retryAfterSeconds = 42))
        show { LoginScreen({}, {}, onOpenLegalDocument = {}, viewModel = vm) }
        vm.onEmailChange("jean@chantier.dev")
        vm.onPasswordChange("Password1234!")
        mainClock.advanceTimeByFrame()
        onNodeWithText("Se connecter").performClick()
        mainClock.advanceTimeBy(500L)

        onNodeWithText("Réessayer dans 42 s").assertExists().assertIsNotEnabled()
        inSnackbar("Trop de tentatives.").assertExists()
        assertNowhere("Réessaie dans")

        mainClock.advanceTimeBy(3_000L)
        onNodeWithText("Réessayer dans 39 s").assertIsNotEnabled()

        mainClock.advanceTimeBy(40_000L)
        onNodeWithText("Se connecter").assertIsEnabled()
    }

    @Test
    fun a_wait_over_a_minute_reads_as_a_clock_in_english() = runComposeUiTest {
        mainClock.autoAdvance = false
        val vm = login(DomainException.AccountLocked(retryAfterSeconds = 1_800))
        show(locale = "en") { LoginScreen({}, {}, onOpenLegalDocument = {}, viewModel = vm) }
        vm.onEmailChange("jean@chantier.dev")
        vm.onPasswordChange("Password1234!")
        mainClock.advanceTimeByFrame()
        onNode(hasText("Sign in") and androidx.compose.ui.test.hasClickAction()).performClick()
        mainClock.advanceTimeBy(500L)

        onNodeWithText("Try again in 30:00").assertExists().assertIsNotEnabled()
        inSnackbar("Account locked after too many attempts.").assertExists()
    }

    @Test
    fun a_general_error_of_another_form_shows_in_a_snackbar_too() = runComposeUiTest {
        val user = User(1, "jean@chantier.dev", "Jean", true, GlobalRole.USER)
        val auth = FakeAuthRepository().apply { emitState(AuthState.Authenticated(user)) }
        val projects = FakeProjectRepository(
            detail = ProjectDetail(
                localId = "1", name = "Villa", description = null, location = null,
                currency = "EUR", timezone = "Europe/Paris", status = ProjectStatus.IN_PROGRESS, ownerId = 1L,
            ),
        )
        val stages = FakeStageRepository().apply { createError = DomainException.ServiceUnavailable }
        val vm = CreateStageViewModel(stages, projects, auth)
        show { CreateStageScreen(projectLocalId = "1", onCreated = {}, viewModel = vm) }
        vm.onNameChange("Fondations")
        onNodeWithText("Créer l'étape").performClick()

        val message = "Le service est momentanément indisponible. Réessaie dans quelques minutes."
        waitUntil(timeoutMillis = 5_000L) { onAllNodes(hasText(message) and hasAnyAncestor(hasTestTag(FORM_ERROR_SNACKBAR_TAG))).fetchSemanticsNodes().isNotEmpty() }
    }
}
