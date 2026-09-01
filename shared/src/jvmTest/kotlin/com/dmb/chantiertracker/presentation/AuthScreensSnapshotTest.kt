package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
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
import androidx.compose.foundation.pager.rememberPagerState
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.navigation.LoginNotice
import com.dmb.chantiertracker.presentation.onboarding.ONBOARDING_PAGES
import com.dmb.chantiertracker.presentation.onboarding.OnboardingScreenContent
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AuthScreensSnapshotTest {

    private val outDir = File("build/auth-snapshots").apply { mkdirs() }

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private fun snapshot(name: String, locale: String, dark: Boolean = false, screen: @Composable () -> Unit) =
        runComposeUiTest {
            setContent {
                customAppLocale = locale
                AppEnvironment {
                    AppTheme(darkTheme = dark) {
                        Box(Modifier.size(412.dp, 892.dp)) { screen() }
                    }
                }
            }
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(outDir, "$name-$locale.png"))
        }

    @Test
    fun capture_all_auth_screens_in_french_and_english() {
        val repo = FakeAuthRepository()

        for (locale in listOf("fr", "en")) {
            for (page in 0 until ONBOARDING_PAGES) {
                snapshot("00-onboarding-${page + 1}", locale) {
                    val pagerState = rememberPagerState(initialPage = page, pageCount = { ONBOARDING_PAGES })
                    OnboardingScreenContent(pagerState = pagerState, loop = 0.3f, onFinish = {})
                }
            }
            snapshot("00-onboarding-1-dark", locale, dark = true) {
                val pagerState = rememberPagerState(initialPage = 0, pageCount = { ONBOARDING_PAGES })
                OnboardingScreenContent(pagerState = pagerState, loop = 0.3f, onFinish = {})
            }
            snapshot("00-welcome", locale) {
                WelcomeScreen(onCreateAccount = {}, onSignIn = {})
            }
            snapshot("01-login", locale) {
                LoginScreen(
                    onNavigateToRegister = {},
                    onNavigateToForgotPassword = {},
                    onBack = {},
                    notice = LoginNotice.AccountActivated,
                    viewModel = LoginViewModel(repo),
                )
            }
            snapshot("02-register", locale) {
                RegisterScreen(onRegistered = {}, onBackToLogin = {}, onBack = {}, viewModel = RegisterViewModel(repo))
            }
            snapshot("03-verify", locale) {
                VerifyEmailScreen(
                    email = "jean@chantier.dev",
                    onVerified = {},
                    onCancelVerification = {},
                    onBack = {},
                    viewModel = VerifyEmailViewModel(repo),
                )
            }
            snapshot("04-forgot", locale) {
                ForgotPasswordScreen(onCodeSent = {}, onBack = {}, viewModel = ForgotPasswordViewModel(repo))
            }
            snapshot("05-reset", locale) {
                ResetPasswordScreen(
                    email = "jean@chantier.dev",
                    onReset = {},
                    onBackToLogin = {},
                    onBack = {},
                    viewModel = ResetPasswordViewModel(repo),
                )
            }
            snapshot("06-login-dark", locale, dark = true) {
                LoginScreen(onNavigateToRegister = {}, onNavigateToForgotPassword = {}, viewModel = LoginViewModel(repo))
            }
            snapshot("07-register-dark", locale, dark = true) {
                RegisterScreen(onRegistered = {}, onBackToLogin = {}, viewModel = RegisterViewModel(repo))
            }
            snapshot("08-welcome-dark", locale, dark = true) {
                WelcomeScreen(onCreateAccount = {}, onSignIn = {})
            }
        }
    }
}
