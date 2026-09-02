package com.dmb.chantiertracker.presentation.projects.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.create_currency_hint
import com.dmb.chantiertracker.resources.create_currency_label
import com.dmb.chantiertracker.resources.create_description_label
import com.dmb.chantiertracker.resources.create_location_label
import com.dmb.chantiertracker.resources.create_name_label
import com.dmb.chantiertracker.resources.create_submit
import com.dmb.chantiertracker.resources.create_timezone_help
import com.dmb.chantiertracker.resources.create_timezone_label
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CreateProjectScreen(
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateProjectViewModel = koinViewModel(),
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

        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.create_name_label)) },
            singleLine = true,
            isError = state.nameError != null,
            supportingText = state.nameError?.let { { Text(stringResource(it)) } },
            enabled = !state.isSubmitting,
        )

        OutlinedTextField(
            value = state.description,
            onValueChange = viewModel::onDescriptionChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.create_description_label)) },
            minLines = 3,
            enabled = !state.isSubmitting,
        )

        OutlinedTextField(
            value = state.location,
            onValueChange = viewModel::onLocationChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.create_location_label)) },
            singleLine = true,
            enabled = !state.isSubmitting,
        )

        OutlinedTextField(
            value = state.currency,
            onValueChange = viewModel::onCurrencyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.create_currency_label)) },
            placeholder = { Text(stringResource(Res.string.create_currency_hint)) },
            singleLine = true,
            enabled = !state.isSubmitting,
        )

        TimezoneField(
            value = state.timezone,
            options = state.timezoneOptions,
            enabled = !state.isSubmitting,
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
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimezoneField(
    value: String,
    options: List<String>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            label = { Text(stringResource(Res.string.create_timezone_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
