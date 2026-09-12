package com.dmb.chantiertracker.presentation.projects.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.presentation.projects.TimezoneField
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.create_currency_hint
import com.dmb.chantiertracker.resources.create_currency_label
import com.dmb.chantiertracker.resources.create_description_label
import com.dmb.chantiertracker.resources.create_location_label
import com.dmb.chantiertracker.resources.create_name_label
import com.dmb.chantiertracker.resources.create_submit
import com.dmb.chantiertracker.resources.create_super_admin_blocked
import com.dmb.chantiertracker.resources.create_timezone_help
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CreateProjectScreen(
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateProjectViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locked = state.isSubmitting || state.atProjectLimit || state.isSuperAdmin

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
        if (state.atProjectLimit) {
            ErrorBanner(DomainException.PlanLimitReached.localizedText())
        }
        if (state.isSuperAdmin) {
            ErrorBanner(stringResource(Res.string.create_super_admin_blocked))
        }
        state.formError?.let { ErrorBanner(it.localizedText()) }

        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.create_name_label)) },
            singleLine = true,
            isError = state.nameError != null,
            supportingText = state.nameError?.let { { Text(stringResource(it)) } },
            enabled = !locked,
        )

        OutlinedTextField(
            value = state.description,
            onValueChange = viewModel::onDescriptionChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.create_description_label)) },
            minLines = 3,
            enabled = !locked,
        )

        OutlinedTextField(
            value = state.location,
            onValueChange = viewModel::onLocationChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.create_location_label)) },
            singleLine = true,
            enabled = !locked,
        )

        OutlinedTextField(
            value = state.currency,
            onValueChange = viewModel::onCurrencyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.create_currency_label)) },
            placeholder = { Text(stringResource(Res.string.create_currency_hint)) },
            singleLine = true,
            enabled = !locked,
        )

        TimezoneField(
            value = state.timezone,
            options = state.timezoneOptions,
            enabled = !locked,
            onSelect = viewModel::onTimezoneChange,
        )
        Text(
            text = stringResource(Res.string.create_timezone_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AuthPrimaryButton(
            text = stringResource(Res.string.create_submit),
            onClick = viewModel::submit,
            loading = state.isSubmitting,
            enabled = !state.atProjectLimit && !state.isSuperAdmin,
        )
    }
}
