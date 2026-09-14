package com.dmb.chantiertracker.presentation.auth.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.presentation.auth.components.AuthFooterPrompt
import com.dmb.chantiertracker.presentation.auth.components.AuthInlineLink
import com.dmb.chantiertracker.presentation.auth.components.AuthLink
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.auth.components.AuthScreenLayout
import com.dmb.chantiertracker.presentation.auth.components.EmailField
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.auth.components.InfoBanner
import com.dmb.chantiertracker.presentation.auth.components.PasswordField
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.presentation.main.labelRes
import com.dmb.chantiertracker.presentation.navigation.LoginNotice
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.login_forgot_password
import com.dmb.chantiertracker.resources.login_no_account_action
import com.dmb.chantiertracker.resources.login_no_account_prompt
import com.dmb.chantiertracker.resources.login_notice_account_activated
import com.dmb.chantiertracker.resources.login_notice_checkout_pending
import com.dmb.chantiertracker.resources.login_notice_password_reset
import com.dmb.chantiertracker.resources.login_resend_verification
import com.dmb.chantiertracker.resources.login_submit
import com.dmb.chantiertracker.resources.login_subtitle
import com.dmb.chantiertracker.resources.login_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onBack: (() -> Unit)? = null,
    prefilledEmail: String? = null,
    notice: LoginNotice? = null,
    checkoutPlan: String? = null,
    checkoutCycle: String? = null,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(prefilledEmail) {
        if (prefilledEmail != null) viewModel.prefillEmail(prefilledEmail)
    }

    LaunchedEffect(checkoutPlan, checkoutCycle) {
        viewModel.setCheckoutIntent(checkoutPlan, checkoutCycle)
    }

    AuthScreenLayout(
        title = stringResource(Res.string.login_title),
        subtitle = stringResource(Res.string.login_subtitle),
        onBack = onBack,
    ) {
        // ADR-50 — a resolved checkout intent takes priority over the ordinary
        // `notice` banner: it can only be reached right after email
        // verification, coming from PlanSelectionScreen, so this always
        // supersedes AccountActivated (same underlying moment, more specific
        // message) and never coexists with PasswordReset in practice.
        val checkoutPlanValue = state.checkoutPlan
        if (checkoutPlanValue != null) {
            InfoBanner(stringResource(Res.string.login_notice_checkout_pending, stringResource(checkoutPlanValue.labelRes())))
        } else {
            when (notice) {
                LoginNotice.AccountActivated -> InfoBanner(stringResource(Res.string.login_notice_account_activated))
                LoginNotice.PasswordReset -> InfoBanner(stringResource(Res.string.login_notice_password_reset))
                null -> Unit
            }
        }
        state.formError?.let { ErrorBanner(it.localizedText()) }

        EmailField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            error = state.emailError?.let { stringResource(it) },
            enabled = !state.isSubmitting,
        )

        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PasswordField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                error = state.passwordError?.let { stringResource(it) },
                enabled = !state.isSubmitting,
                imeAction = ImeAction.Done,
            )
            Box(Modifier.fillMaxWidth()) {
                AuthInlineLink(
                    text = stringResource(Res.string.login_forgot_password),
                    onClick = onNavigateToForgotPassword,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }

        if (state.needsVerification) {
            AuthLink(
                text = stringResource(Res.string.login_resend_verification),
                onClick = viewModel::resendVerificationCode,
            )
        }

        AuthPrimaryButton(
            text = stringResource(Res.string.login_submit),
            onClick = viewModel::submit,
            loading = state.isSubmitting,
        )

        Spacer(Modifier.height(4.dp))
        AuthFooterPrompt(
            prompt = stringResource(Res.string.login_no_account_prompt),
            action = stringResource(Res.string.login_no_account_action),
            onClick = onNavigateToRegister,
        )
    }
}
