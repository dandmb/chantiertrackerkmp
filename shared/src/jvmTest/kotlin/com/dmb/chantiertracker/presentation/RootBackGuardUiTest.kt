package com.dmb.chantiertracker.presentation

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.ui.platform.findDefaultNavigationEventDispatcherOwner
import androidx.navigation.NavHostController
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.main.ExitPrompt
import com.dmb.chantiertracker.presentation.navigation.SignedInRootBackGuard
import com.dmb.chantiertracker.presentation.navigation.SignedOutRootBackExit
import com.dmb.chantiertracker.presentation.theme.AppTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, InternalComposeApi::class)
class RootBackGuardUiTest {

    @AfterTest fun tearDown() { customAppLocale = null }

    private var exits = 0
    private var signOuts = 0
    private var requests = 0
    private val systemBack = DirectNavigationEventInput()

    @Composable
    private fun TwoScreens(navController: NavHostController) {
        val dispatcher = findDefaultNavigationEventDispatcherOwner()!!.navigationEventDispatcher
        DisposableEffect(dispatcher) {
            dispatcher.addInput(systemBack)
            onDispose { dispatcher.removeInput(systemBack) }
        }
        NavHost(navController, startDestination = "racine") {
            composable("racine") { TextButton(onClick = { navController.navigate("enfant") }) { Text("Écran racine") } }
            composable("enfant") { Text("Écran enfant") }
        }
    }

    private fun ComposeUiTest.mountSignedOut() = setContent {
        customAppLocale = "fr"
        AppEnvironment {
            AppTheme {
                val navController = rememberNavController()
                SignedOutRootBackExit(navController, onExit = { exits++ })
                TwoScreens(navController)
            }
        }
    }

    private fun ComposeUiTest.mountSignedIn(unsent: Int = 0) = setContent {
        customAppLocale = "fr"
        AppEnvironment {
            AppTheme {
                val navController = rememberNavController()
                var prompt by remember { mutableStateOf<ExitPrompt?>(null) }
                SignedInRootBackGuard(
                    navController = navController,
                    prompt = prompt,
                    onRequest = { requests++; prompt = ExitPrompt(unsent) },
                    onDismiss = { prompt = null },
                    onSignOut = { signOuts++ },
                )
                TwoScreens(navController)
            }
        }
    }

    private fun ComposeUiTest.pressBack() {
        runOnIdle { systemBack.backCompleted() }
        waitForIdle()
    }

    private fun ComposeUiTest.absent(text: String) =
        assertEquals(0, onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().size, "« $text » ne doit pas s'afficher")

    @Test
    fun signed_out_back_on_a_root_screen_closes_the_app_without_a_dialog() = runComposeUiTest {
        mountSignedOut()
        onNodeWithText("Écran racine").assertExists()

        pressBack()

        assertEquals(1, exits)
        absent("Quitter")
        absent("Te déconnecter")
    }

    @Test
    fun signed_out_back_on_a_child_screen_simply_returns_to_the_previous_one() = runComposeUiTest {
        mountSignedOut()
        onNodeWithText("Écran racine").performClick()
        waitForIdle()
        onNodeWithText("Écran enfant").assertExists()

        pressBack()

        onNodeWithText("Écran racine").assertExists()
        assertEquals(0, exits)
    }

    @Test
    fun signed_in_back_on_a_root_screen_asks_to_sign_out_and_never_closes_the_app() = runComposeUiTest {
        mountSignedIn()

        pressBack()

        assertEquals(1, requests)
        onNodeWithText("Te déconnecter ?").assertExists()
        onNodeWithText("Me déconnecter").performClick()
        waitForIdle()
        assertEquals(1, signOuts)
        absent("Te déconnecter ?")
    }

    @Test
    fun signed_in_stay_changes_nothing() = runComposeUiTest {
        mountSignedIn()

        pressBack()
        onNodeWithText("Rester").performClick()
        waitForIdle()

        absent("Te déconnecter ?")
        onNodeWithText("Écran racine").assertExists()
        assertEquals(0, signOuts)
    }

    @Test
    fun signed_in_with_unsent_entries_understood_closes_the_dialog_without_signing_out() = runComposeUiTest {
        mountSignedIn(unsent = 2)

        pressBack()
        onNodeWithText("2 saisies ne sont pas encore envoyées.", substring = true).assertExists()
        onNodeWithText("Compris").performClick()
        waitForIdle()

        absent("Te déconnecter ?")
        onNodeWithText("Écran racine").assertExists()
        assertEquals(0, signOuts)
    }

    @Test
    fun signed_in_back_on_a_child_screen_simply_returns_to_the_previous_one() = runComposeUiTest {
        mountSignedIn()
        onNodeWithText("Écran racine").performClick()
        waitForIdle()

        pressBack()

        onNodeWithText("Écran racine").assertExists()
        assertEquals(0, requests)
        absent("Te déconnecter ?")
    }
}
