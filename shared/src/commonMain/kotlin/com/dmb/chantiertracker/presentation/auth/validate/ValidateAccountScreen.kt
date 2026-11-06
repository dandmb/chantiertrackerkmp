package com.dmb.chantiertracker.presentation.auth.validate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.auth.components.AuthScreenLayout
import com.dmb.chantiertracker.presentation.auth.components.EmailField
import com.dmb.chantiertracker.presentation.auth.components.PasswordField
import com.dmb.chantiertracker.presentation.auth.login.LoginViewModel
import com.dmb.chantiertracker.presentation.i18n.rememberWaitSecondsLeft
import com.dmb.chantiertracker.presentation.i18n.snackbarText
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.validate_account_submit
import com.dmb.chantiertracker.resources.validate_account_subtitle
import com.dmb.chantiertracker.resources.validate_account_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ValidateAccountScreen(
    onVerificationRequired: () -> Unit,
    onBack: (() -> Unit)? = null,
    prefilledEmail: String? = null,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(prefilledEmail) {
        if (!prefilledEmail.isNullOrBlank()) viewModel.prefillEmail(prefilledEmail)
    }
    LaunchedEffect(state.opensVerification) {
        if (state.opensVerification) {
            viewModel.onVerificationOpened()
            onVerificationRequired()
        }
    }

    AuthScreenLayout(
        title = stringResource(Res.string.validate_account_title),
        subtitle = stringResource(Res.string.validate_account_subtitle),
        onBack = onBack,
        errorMessage = state.formError?.snackbarText(),
        submitting = state.isSubmitting,
    ) {
        EmailField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            error = state.emailError?.let { stringResource(it) },
            enabled = !state.isSubmitting,
        )
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            error = state.passwordError?.let { stringResource(it) },
            enabled = !state.isSubmitting,
            imeAction = ImeAction.Done,
        )
        AuthPrimaryButton(
            text = stringResource(Res.string.validate_account_submit),
            onClick = viewModel::submit,
            loading = state.isSubmitting,
            waitSecondsLeft = rememberWaitSecondsLeft(state.formError),
        )
    }
}
