package com.dmb.chantiertracker.presentation.auth.register

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.presentation.auth.components.AuthFooterPrompt
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.auth.components.AuthScreenLayout
import com.dmb.chantiertracker.presentation.auth.components.EmailField
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.auth.components.NameField
import com.dmb.chantiertracker.presentation.auth.components.PasswordField
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.register_have_account_action
import com.dmb.chantiertracker.resources.register_have_account_prompt
import com.dmb.chantiertracker.resources.register_submit
import com.dmb.chantiertracker.resources.register_subtitle
import com.dmb.chantiertracker.resources.register_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RegisterScreen(
    onRegistered: (email: String) -> Unit,
    onBackToLogin: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: RegisterViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.registeredEmail) {
        state.registeredEmail?.let(onRegistered)
    }

    AuthScreenLayout(
        title = stringResource(Res.string.register_title),
        subtitle = stringResource(Res.string.register_subtitle),
        onBack = onBack,
    ) {
        state.formError?.let { ErrorBanner(it.localizedText()) }

        NameField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            error = state.nameError?.let { stringResource(it) },
            enabled = !state.isSubmitting,
        )
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
            text = stringResource(Res.string.register_submit),
            onClick = viewModel::submit,
            loading = state.isSubmitting,
        )

        Spacer(Modifier.height(4.dp))
        AuthFooterPrompt(
            prompt = stringResource(Res.string.register_have_account_prompt),
            action = stringResource(Res.string.register_have_account_action),
            onClick = onBackToLogin,
        )
    }
}
