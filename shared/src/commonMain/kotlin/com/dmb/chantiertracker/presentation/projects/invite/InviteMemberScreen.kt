package com.dmb.chantiertracker.presentation.projects.invite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.auth.components.InfoBanner
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.invite_member_email_label
import com.dmb.chantiertracker.resources.invite_member_hint
import com.dmb.chantiertracker.resources.invite_member_limit
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.atSupervisorLimit) {
            InfoBanner(stringResource(Res.string.invite_member_limit))
        }
        state.formError?.let { ErrorBanner(it.localizedText()) }

        OutlinedTextField(
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
            enabled = !state.atSupervisorLimit,
        )
    }
}
