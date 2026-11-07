package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.auth.forgot.ForgotPasswordScreen
import com.dmb.chantiertracker.presentation.auth.forgot.ForgotPasswordViewModel
import com.dmb.chantiertracker.presentation.auth.login.LoginScreen
import com.dmb.chantiertracker.presentation.auth.login.LoginViewModel
import com.dmb.chantiertracker.presentation.auth.register.RegisterScreen
import com.dmb.chantiertracker.presentation.auth.register.RegisterViewModel
import com.dmb.chantiertracker.presentation.auth.reset.ResetPasswordScreen
import com.dmb.chantiertracker.presentation.auth.reset.ResetPasswordViewModel
import com.dmb.chantiertracker.presentation.auth.verify.VerifyEmailScreen
import com.dmb.chantiertracker.presentation.auth.verify.VerifyEmailViewModel
import com.dmb.chantiertracker.presentation.auth.welcome.WelcomeScreen
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.navigation.LoginNotice
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeBillingRepository
import com.dmb.chantiertracker.support.FakeCheckoutLauncher
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AuthDarkContrastUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance().toDouble(), b.luminance().toDouble()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun ComposeUiTest.assertEveryTextReadsAtNight(name: String, screen: @Composable () -> Unit) {
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme(darkTheme = true) {
                    CompositionLocalProvider(LocalDensity provides Density(2f)) {
                        AppSurface { Box(Modifier.size(412.dp, 1400.dp)) { screen() } }
                    }
                }
            }
        }
        waitForIdle()

        val page = onRoot().captureToImage().toPixelMap()
        val pageBounds = Rect(0f, 0f, page.width.toFloat(), page.height.toFloat())
        val texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
        val failures = texts.mapNotNull { node ->
            val label = node.config[SemanticsProperties.Text].joinToString { it.text }
            val area = node.boundsInRoot.intersect(pageBounds)
            if (label.isBlank() || area.width < 2f || area.height < 2f) return@mapNotNull null
            val pixels = (area.top.roundToInt() until area.bottom.roundToInt().coerceAtMost(page.height)).flatMap { y ->
                (area.left.roundToInt() until area.right.roundToInt().coerceAtMost(page.width)).map { x -> page[x, y] }
            }
            val background = pixels.groupingBy { it }.eachCount().maxBy { it.value }.key
            val ink = pixels.maxBy { contrast(it, background) }
            val ratio = contrast(ink, background)
            if (ratio < 4.5) "« $label » ${"%.2f".format(ratio)}:1" else null
        }
        assertTrue(texts.size > 3, "$name: no text found")
        assertTrue(failures.isEmpty(), "$name, sombre, sous AA : $failures")
    }

    private val repo = FakeAuthRepository()

    @Test
    fun the_welcome_screen_reads_at_night() = runComposeUiTest {
        assertEveryTextReadsAtNight("accueil") { WelcomeScreen(onCreateAccount = {}, onSignIn = {}, onDiscoverPlans = {}, onOpenLegalDocument = {}) }
    }

    @Test
    fun the_login_screen_reads_at_night() = runComposeUiTest {
        assertEveryTextReadsAtNight("connexion") {
            LoginScreen(
                onNavigateToRegister = {},
                onNavigateToForgotPassword = {},
                onOpenLegalDocument = {},
                onBack = {},
                notice = LoginNotice.AccountActivated,
                viewModel = LoginViewModel(repo, FakeCheckoutLauncher(), FakeBillingRepository()),
            )
        }
    }

    @Test
    fun the_register_screen_reads_at_night() = runComposeUiTest {
        assertEveryTextReadsAtNight("inscription") {
            RegisterScreen(onRegistered = {}, onBackToLogin = {}, onOpenLegalDocument = {}, onBack = {}, viewModel = RegisterViewModel(repo))
        }
    }

    @Test
    fun the_verification_screen_reads_at_night() = runComposeUiTest {
        assertEveryTextReadsAtNight("vérification") {
            VerifyEmailScreen(email = "jean@chantier.dev", onVerified = {}, onCancelVerification = {}, onBack = {}, viewModel = VerifyEmailViewModel(repo))
        }
    }

    @Test
    fun the_forgot_password_screen_reads_at_night() = runComposeUiTest {
        assertEveryTextReadsAtNight("mot de passe oublié") { ForgotPasswordScreen(onCodeSent = {}, onBack = {}, viewModel = ForgotPasswordViewModel(repo)) }
    }

    @Test
    fun the_reset_password_screen_reads_at_night() = runComposeUiTest {
        assertEveryTextReadsAtNight("nouveau mot de passe") {
            ResetPasswordScreen(email = "jean@chantier.dev", onReset = {}, onBackToLogin = {}, onBack = {}, viewModel = ResetPasswordViewModel(repo))
        }
    }
}
