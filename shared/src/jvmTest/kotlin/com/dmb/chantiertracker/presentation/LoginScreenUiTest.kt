package com.dmb.chantiertracker.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.presentation.auth.login.LoginScreen
import com.dmb.chantiertracker.presentation.auth.login.LoginViewModel
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LoginScreenUiTest {

    @BeforeTest
    fun setUp() {
        installTestMainDispatcher()
    }

    @AfterTest
    fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    @Test
    fun renders_login_form_in_french() = runComposeUiTest {
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme {
                    LoginScreen(
                        onNavigateToRegister = {},
                        onNavigateToForgotPassword = {},
                        viewModel = LoginViewModel(FakeAuthRepository()),
                    )
                }
            }
        }

        onNodeWithText("Connexion").assertIsDisplayed()
        onNodeWithText("Se connecter").assertIsDisplayed()
        onNodeWithText("Créer un compte").assertIsDisplayed()
        onNodeWithText("Mot de passe oublié ?").assertIsDisplayed()
    }

    @Test
    fun renders_login_form_in_english() = runComposeUiTest {
        setContent {
            customAppLocale = "en"
            AppEnvironment {
                AppTheme {
                    LoginScreen(
                        onNavigateToRegister = {},
                        onNavigateToForgotPassword = {},
                        viewModel = LoginViewModel(FakeAuthRepository()),
                    )
                }
            }
        }

        onNodeWithText("Forgot password?").assertIsDisplayed()
        onNodeWithText("Create one").assertIsDisplayed()
    }
}
