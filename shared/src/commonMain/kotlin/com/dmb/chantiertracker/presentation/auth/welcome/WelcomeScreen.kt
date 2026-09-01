package com.dmb.chantiertracker.presentation.auth.welcome

import androidx.compose.runtime.Composable
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.auth.components.AuthScreenLayout
import com.dmb.chantiertracker.presentation.auth.components.AuthSecondaryButton
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.welcome_create_account
import com.dmb.chantiertracker.resources.welcome_sign_in
import com.dmb.chantiertracker.resources.welcome_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun WelcomeScreen(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
) {
    AuthScreenLayout(
        title = stringResource(Res.string.welcome_title),
        centerContentVertically = true,
    ) {
        AuthPrimaryButton(text = stringResource(Res.string.welcome_create_account), onClick = onCreateAccount)
        AuthSecondaryButton(text = stringResource(Res.string.welcome_sign_in), onClick = onSignIn)
    }
}
