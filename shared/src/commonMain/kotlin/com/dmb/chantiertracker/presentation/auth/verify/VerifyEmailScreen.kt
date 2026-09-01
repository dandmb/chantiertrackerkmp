package com.dmb.chantiertracker.presentation.auth.verify

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.presentation.auth.components.AuthLink
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.auth.components.AuthScreenLayout
import com.dmb.chantiertracker.presentation.auth.components.AuthSecondaryButton
import com.dmb.chantiertracker.presentation.auth.components.CodeField
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.auth.components.InfoBanner
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.verify_cancel
import com.dmb.chantiertracker.resources.verify_code_resent
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
    viewModel: VerifyEmailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.verified) {
        if (state.verified) onVerified()
    }

    AuthScreenLayout(
        title = stringResource(Res.string.verify_title),
        subtitle = stringResource(Res.string.verify_subtitle, email),
        onBack = onBack,
    ) {
        state.formError?.let { ErrorBanner(it.localizedText()) }
        if (state.codeResent) InfoBanner(stringResource(Res.string.verify_code_resent))

        CodeField(
            value = state.code,
            onValueChange = viewModel::onCodeChange,
            error = state.codeError?.let { stringResource(it) },
            enabled = !state.isSubmitting,
        )

        AuthPrimaryButton(
            text = stringResource(Res.string.verify_submit),
            onClick = { viewModel.submit(email) },
            loading = state.isSubmitting,
        )
        AuthSecondaryButton(
            text = stringResource(if (state.isResending) Res.string.verify_resending else Res.string.verify_resend),
            onClick = { viewModel.resendCode(email) },
            enabled = !state.isSubmitting && !state.isResending,
        )
        AuthLink(text = stringResource(Res.string.verify_cancel), onClick = onCancelVerification)
    }
}
