package com.dmb.chantiertracker.presentation.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.auth.components.EmailField
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.auth.components.NameField
import com.dmb.chantiertracker.presentation.auth.components.PasswordField
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.admin_create_user_password_help
import com.dmb.chantiertracker.resources.admin_create_user_role_label
import com.dmb.chantiertracker.resources.admin_create_user_submit
import com.dmb.chantiertracker.resources.admin_users_role_super_admin
import com.dmb.chantiertracker.resources.admin_users_role_user
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AdminCreateUserScreen(
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminCreateUserViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.created) {
        if (state.created) onCreated()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
        Text(
            text = stringResource(Res.string.admin_create_user_password_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(Res.string.admin_create_user_role_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RoleChoiceRow(
                selected = state.globalRole,
                onSelect = viewModel::onRoleChange,
                enabled = !state.isSubmitting,
            )
        }

        AuthPrimaryButton(
            text = stringResource(Res.string.admin_create_user_submit),
            onClick = viewModel::submit,
            loading = state.isSubmitting,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleChoiceRow(selected: GlobalRole, onSelect: (GlobalRole) -> Unit, enabled: Boolean) {
    val options = listOf(GlobalRole.USER, GlobalRole.SUPER_ADMIN)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(stringResource(option.roleLabelRes()), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// GlobalRole.UNKNOWN never reaches this screen (only USER/SUPER_ADMIN are
// offered as choices) — the branch exists only so `when` stays exhaustive.
private fun GlobalRole.roleLabelRes(): StringResource = when (this) {
    GlobalRole.USER -> Res.string.admin_users_role_user
    GlobalRole.SUPER_ADMIN -> Res.string.admin_users_role_super_admin
    GlobalRole.UNKNOWN -> Res.string.admin_users_role_user
}
