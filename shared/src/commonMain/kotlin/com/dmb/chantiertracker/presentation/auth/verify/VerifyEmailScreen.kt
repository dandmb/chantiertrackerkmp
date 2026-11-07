package com.dmb.chantiertracker.presentation.auth.verify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.dmb.chantiertracker.presentation.TextAction
import com.dmb.chantiertracker.presentation.auth.components.EmailField
import com.dmb.chantiertracker.resources.verify_from_login_subtitle
import com.dmb.chantiertracker.resources.reset_back_to_login
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.presentation.auth.components.AuthLink
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.auth.components.AuthScreenLayout
import com.dmb.chantiertracker.presentation.auth.components.AuthSecondaryButton
import com.dmb.chantiertracker.presentation.auth.components.CodeField
import com.dmb.chantiertracker.presentation.auth.components.InfoBanner
import com.dmb.chantiertracker.presentation.auth.components.PasswordField
import com.dmb.chantiertracker.presentation.i18n.snackbarText
import com.dmb.chantiertracker.presentation.i18n.rememberWaitSecondsLeft
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.verify_cancel
import com.dmb.chantiertracker.resources.verify_code_resent
import com.dmb.chantiertracker.resources.verify_existing_account_hint
import com.dmb.chantiertracker.resources.verify_password_hint
import com.dmb.chantiertracker.resources.verify_wrong_code
import com.dmb.chantiertracker.resources.verify_wrong_code_or_password
import com.dmb.chantiertracker.resources.verify_resend
import com.dmb.chantiertracker.resources.verify_resending
import com.dmb.chantiertracker.resources.verify_submit
import com.dmb.chantiertracker.resources.verify_subtitle
import com.dmb.chantiertracker.resources.verify_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun VerifyEmailScreen(
    email: String,
    onVerified: () -> Unit,
    onCancelVerification: () -> Unit,
    onBack: (() -> Unit)? = null,
    unverifiedLogin: Boolean = false,
    viewModel: VerifyEmailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(email) { viewModel.start(email, requestCode = unverifiedLogin) }

    LaunchedEffect(state.verified) {
        if (state.verified) onVerified()
    }

    val errorMessage = state.formError?.let { error ->
        val wrongCode = error == DomainException.InvalidCode
        when {
            wrongCode && (state.asksPassword || unverifiedLogin) -> stringResource(Res.string.verify_wrong_code_or_password)
            wrongCode -> stringResource(Res.string.verify_wrong_code)
            else -> error.snackbarText()
        }
    }
    val waitSecondsLeft = rememberWaitSecondsLeft(state.formError)

    AuthScreenLayout(
        title = stringResource(Res.string.verify_title),
        subtitle = stringResource(Res.string.verify_subtitle, email),
        onBack = onBack,
        errorMessage = errorMessage,
        submitting = state.isSubmitting || state.isResending,
    ) {
        if (state.codeResent) InfoBanner(stringResource(Res.string.verify_code_resent))
        if (!unverifiedLogin) InfoBanner(stringResource(Res.string.verify_existing_account_hint))

        CodeField(
            value = state.code,
            onValueChange = viewModel::onCodeChange,
            error = state.codeError?.let { stringResource(it) },
            enabled = !state.isSubmitting,
        )
        if (state.asksPassword) {
            PasswordField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                hint = stringResource(Res.string.verify_password_hint),
                error = state.passwordError?.let { stringResource(it) },
                enabled = !state.isSubmitting,
            )
        }

        AuthPrimaryButton(
            text = stringResource(Res.string.verify_submit),
            onClick = { viewModel.submit(email) },
            loading = state.isSubmitting,
            waitSecondsLeft = waitSecondsLeft,
        )
        AuthSecondaryButton(
            text = stringResource(if (state.isResending) Res.string.verify_resending else Res.string.verify_resend),
            onClick = { viewModel.resendCode(email) },
            enabled = !state.isSubmitting && !state.isResending && waitSecondsLeft == 0,
        )
        AuthLink(text = stringResource(Res.string.verify_cancel), onClick = onCancelVerification)
    }
}

@Composable
fun VerifyAccountFromLoginScreen(
    prefilledEmail: String?,
    onVerified: (email: String) -> Unit,
    onBackToLogin: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: VerifyEmailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.startFromLogin(prefilledEmail) }

    LaunchedEffect(state.verified) {
        if (state.verified) onVerified(state.email.trim())
    }

    val errorMessage = state.formError?.let { error ->
        if (error == DomainException.InvalidCode) stringResource(Res.string.verify_wrong_code_or_password) else error.snackbarText()
    }
    val waitSecondsLeft = rememberWaitSecondsLeft(state.formError)

    AuthScreenLayout(
        title = stringResource(Res.string.verify_title),
        subtitle = stringResource(Res.string.verify_from_login_subtitle),
        onBack = onBack,
        errorMessage = errorMessage,
        submitting = state.isSubmitting || state.isResending,
    ) {
        if (state.codeResent) InfoBanner(stringResource(Res.string.verify_code_resent))

        EmailField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            error = state.emailError?.let { stringResource(it) },
            enabled = !state.isSubmitting,
        )
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            hint = stringResource(Res.string.verify_password_hint),
            error = state.passwordError?.let { stringResource(it) },
            enabled = !state.isSubmitting,
            imeAction = ImeAction.Next,
        )
        CodeField(
            value = state.code,
            onValueChange = viewModel::onCodeChange,
            error = state.codeError?.let { stringResource(it) },
            enabled = !state.isSubmitting,
        )
        AuthPrimaryButton(
            text = stringResource(Res.string.verify_submit),
            onClick = viewModel::submitFromLogin,
            loading = state.isSubmitting,
            waitSecondsLeft = waitSecondsLeft,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextAction(
                text = stringResource(if (state.isResending) Res.string.verify_resending else Res.string.verify_resend),
                onClick = viewModel::resendFromLogin,
                enabled = !state.isSubmitting && !state.isResending && waitSecondsLeft == 0,
            )
            TextAction(text = stringResource(Res.string.reset_back_to_login), onClick = onBackToLogin)
        }
    }
}
