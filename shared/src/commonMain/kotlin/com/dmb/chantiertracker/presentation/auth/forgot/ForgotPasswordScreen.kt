package com.dmb.chantiertracker.presentation.auth.forgot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.auth.components.AuthScreenLayout
import com.dmb.chantiertracker.presentation.auth.components.EmailField
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.forgot_submit
import com.dmb.chantiertracker.resources.forgot_subtitle
import com.dmb.chantiertracker.resources.forgot_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ForgotPasswordScreen(
    onCodeSent: (email: String) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: ForgotPasswordViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.submittedEmail) {
        state.submittedEmail?.let(onCodeSent)
    }

    AuthScreenLayout(
        title = stringResource(Res.string.forgot_title),
        subtitle = stringResource(Res.string.forgot_subtitle),
        onBack = onBack,
        centerContentVertically = true,
    ) {
        state.formError?.let { ErrorBanner(it.localizedText()) }

        EmailField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            error = state.emailError?.let { stringResource(it) },
            enabled = !state.isSubmitting,
            imeAction = ImeAction.Done,
        )

        AuthPrimaryButton(
            text = stringResource(Res.string.forgot_submit),
            onClick = viewModel::submit,
            loading = state.isSubmitting,
        )
    }
}
