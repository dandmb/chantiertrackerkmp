package com.dmb.chantiertracker.presentation.auth.verify

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
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.auth.components.InfoBanner
import com.dmb.chantiertracker.presentation.auth.components.PasswordField
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.presentation.i18n.rememberWaitSecondsLeft
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.verify_cancel
import com.dmb.chantiertracker.resources.verify_code_resent
import com.dmb.chantiertracker.resources.verify_existing_account_hint
import com.dmb.chantiertracker.resources.verify_password_hint
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

    AuthScreenLayout(
        title = stringResource(Res.string.verify_title),
        subtitle = stringResource(Res.string.verify_subtitle, email),
        onBack = onBack,
    ) {
        val wrongProof = state.asksPassword && state.formError == DomainException.InvalidCode
        state.formError?.let { ErrorBanner(if (wrongProof) stringResource(Res.string.verify_wrong_code_or_password) else it.localizedText()) }
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
            enabled = rememberWaitSecondsLeft(state.formError) == 0,
        )
        AuthSecondaryButton(
            text = stringResource(if (state.isResending) Res.string.verify_resending else Res.string.verify_resend),
            onClick = { viewModel.resendCode(email) },
            enabled = !state.isSubmitting && !state.isResending && rememberWaitSecondsLeft(state.formError) == 0,
        )
        AuthLink(text = stringResource(Res.string.verify_cancel), onClick = onCancelVerification)
    }
}
