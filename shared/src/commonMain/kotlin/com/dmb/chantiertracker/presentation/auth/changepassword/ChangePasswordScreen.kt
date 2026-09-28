package com.dmb.chantiertracker.presentation.auth.changepassword

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.presentation.auth.components.AuthLink
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.auth.components.AuthScreenLayout
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.auth.components.PasswordField
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.change_password_logout
import com.dmb.chantiertracker.resources.change_password_submit
import com.dmb.chantiertracker.resources.change_password_subtitle
import com.dmb.chantiertracker.resources.change_password_title
import com.dmb.chantiertracker.resources.field_current_password_label
import com.dmb.chantiertracker.resources.field_new_password_label
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

// Reachable only via RootNavHost's AuthState.MustChangePassword branch — no
// onBack (mirrors the web's ChangePasswordRequiredPage: "no cancel button,
// no link elsewhere", every other endpoint stays 403'd until this succeeds).
// The one deliberate addition over the web is the logout link at the bottom
// — see ChangePasswordViewModel.logout for why mobile needs it and web doesn't.
@Composable
fun ChangePasswordScreen(
    email: String,
    viewModel: ChangePasswordViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AuthScreenLayout(
        title = stringResource(Res.string.change_password_title),
        subtitle = stringResource(Res.string.change_password_subtitle),
        centerContentVertically = true,
    ) {
        state.formError?.let { ErrorBanner(it.localizedText()) }

        PasswordField(
            value = state.currentPassword,
            onValueChange = viewModel::onCurrentPasswordChange,
            label = stringResource(Res.string.field_current_password_label),
            error = state.currentPasswordError?.let { stringResource(it) },
            enabled = !state.isSubmitting,
        )
        PasswordField(
            value = state.newPassword,
            onValueChange = viewModel::onNewPasswordChange,
            label = stringResource(Res.string.field_new_password_label),
            error = state.newPasswordError?.let { stringResource(it) },
            enabled = !state.isSubmitting,
            imeAction = ImeAction.Done,
        )

        AuthPrimaryButton(
            text = stringResource(Res.string.change_password_submit),
            onClick = { viewModel.submit(email) },
            loading = state.isSubmitting,
        )

        Spacer(Modifier.height(4.dp))
        AuthLink(
            text = stringResource(Res.string.change_password_logout),
            onClick = viewModel::logout,
            enabled = !state.isSubmitting,
        )
    }
}
