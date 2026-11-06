package com.dmb.chantiertracker.presentation.projects.invite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.presentation.AppOutlinedTextField
import com.dmb.chantiertracker.presentation.ResponsiveContent
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.auth.components.AuthSecondaryButton
import com.dmb.chantiertracker.presentation.auth.components.FormErrorSnackbarHost
import com.dmb.chantiertracker.presentation.i18n.snackbarText
import com.dmb.chantiertracker.presentation.i18n.rememberWaitSecondsLeft
import com.dmb.chantiertracker.presentation.offer.PlanLimitKind
import com.dmb.chantiertracker.presentation.offer.PlanLimitNotice
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.invite_member_email_label
import com.dmb.chantiertracker.resources.invite_member_hint
import com.dmb.chantiertracker.resources.invite_member_replace_pending
import com.dmb.chantiertracker.resources.invite_member_submit
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun InviteMemberScreen(
    projectLocalId: String,
    onInvited: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InviteMemberViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(projectLocalId) { viewModel.load(projectLocalId) }
    LaunchedEffect(state.invited) { if (state.invited) onInvited() }

    val locked = state.isSubmitting || state.atSupervisorLimit

    val error = state.formError
    val errorMessage = error?.takeUnless { it == DomainException.PlanLimitReached }?.snackbarText()
    Box(modifier.fillMaxSize()) {
    ResponsiveContent(Modifier, maxContentWidth = 480.dp) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.atSupervisorLimit) {
                PlanLimitNotice(PlanLimitKind.SUPERVISORS)
            }
            if (error == DomainException.PlanLimitReached) PlanLimitNotice(PlanLimitKind.SUPERVISORS)

            AppOutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.invite_member_email_label)) },
                singleLine = true,
                isError = state.emailError != null,
                supportingText = state.emailError?.let { { Text(stringResource(it)) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                enabled = !locked,
            )

            Text(
                text = stringResource(Res.string.invite_member_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AuthPrimaryButton(
                text = stringResource(Res.string.invite_member_submit),
                onClick = viewModel::submit,
                loading = state.isSubmitting,
                enabled = state.canSubmit,
                waitSecondsLeft = rememberWaitSecondsLeft(state.formError),
            )
            if (state.formError == DomainException.InvitationAlreadyPending) {
                AuthSecondaryButton(
                    text = stringResource(Res.string.invite_member_replace_pending),
                    onClick = viewModel::cancelExistingAndInvite,
                    enabled = !state.isSubmitting,
                )
            }
        }
    }
    FormErrorSnackbarHost(errorMessage, state.isSubmitting, Modifier.align(Alignment.BottomCenter))
    }
}
