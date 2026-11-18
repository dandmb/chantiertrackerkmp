package com.dmb.chantiertracker.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dmb.chantiertracker.presentation.main.ExitPrompt
import com.dmb.chantiertracker.presentation.main.SignOutBackDialog

@Composable
private fun NavHostController.isOnRootScreen(): Boolean {
    val entry by currentBackStackEntryAsState()
    return entry != null && previousBackStackEntry == null
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SignedInRootBackGuard(
    navController: NavHostController,
    prompt: ExitPrompt?,
    onRequest: () -> Unit,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit,
) {
    BackHandler(enabled = navController.isOnRootScreen() && prompt == null, onBack = onRequest)
    if (prompt != null) {
        SignOutBackDialog(
            unsentChanges = prompt.unsentChanges,
            onSignOut = {
                onDismiss()
                onSignOut()
            },
            onDismiss = onDismiss,
        )
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SignedOutRootBackExit(navController: NavHostController, onExit: () -> Unit = rememberExitApp()) {
    BackHandler(enabled = navController.isOnRootScreen(), onBack = onExit)
}
