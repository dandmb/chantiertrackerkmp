package com.dmb.chantiertracker.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import com.dmb.chantiertracker.presentation.main.ExitAppDialog
import com.dmb.chantiertracker.presentation.main.ExitPrompt

@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RootBackExitGuard(
    enabled: Boolean,
    prompt: ExitPrompt?,
    onRequest: () -> Unit,
    onDismiss: () -> Unit,
    onExit: () -> Unit = rememberExitApp(),
) {
    BackHandler(enabled = enabled && prompt == null, onBack = onRequest)
    if (prompt != null) {
        ExitAppDialog(
            unsentChanges = prompt.unsentChanges,
            onExit = {
                onDismiss()
                onExit()
            },
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun SignedOutRootBackExitGuard(enabled: Boolean, onExit: () -> Unit = rememberExitApp()) {
    var asking by rememberSaveable { mutableStateOf(false) }
    RootBackExitGuard(
        enabled = enabled,
        prompt = if (asking) ExitPrompt(unsentChanges = 0) else null,
        onRequest = { asking = true },
        onDismiss = { asking = false },
        onExit = onExit,
    )
}
