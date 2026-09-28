package com.dmb.chantiertracker.presentation.auth.reset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.presentation.auth.components.AuthLink
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.auth.components.AuthScreenLayout
import com.dmb.chantiertracker.presentation.auth.components.CodeField
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.auth.components.PasswordField
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.field_new_password_label
import com.dmb.chantiertracker.resources.reset_back_to_login
import com.dmb.chantiertracker.resources.reset_submit
import com.dmb.chantiertracker.resources.reset_subtitle
import com.dmb.chantiertracker.resources.reset_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ResetPasswordScreen(
    email: String,
    onReset: () -> Unit,
    onBackToLogin: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: ResetPasswordViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.reset) {
        if (state.reset) onReset()
    }

    AuthScreenLayout(
        title = stringResource(Res.string.reset_title),
        subtitle = stringResource(Res.string.reset_subtitle, email),
        onBack = onBack,
    ) {
        state.formError?.let { ErrorBanner(it.localizedText()) }

        CodeField(
            value = state.code,
            onValueChange = viewModel::onCodeChange,
            error = state.codeError?.let { stringResource(it) },
            enabled = !state.isSubmitting,
        )
        PasswordField(
            value = state.newPassword,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(Res.string.field_new_password_label),
            error = state.passwordError?.let { stringResource(it) },
            enabled = !state.isSubmitting,
            imeAction = ImeAction.Done,
        )

        AuthPrimaryButton(
            text = stringResource(Res.string.reset_submit),
            onClick = { viewModel.submit(email) },
            loading = state.isSubmitting,
        )
        AuthLink(text = stringResource(Res.string.reset_back_to_login), onClick = onBackToLogin)
    }
}
