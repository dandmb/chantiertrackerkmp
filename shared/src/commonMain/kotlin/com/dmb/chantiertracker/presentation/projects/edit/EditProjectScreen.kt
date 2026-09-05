package com.dmb.chantiertracker.presentation.projects.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.dmb.chantiertracker.resources.create_timezone_help
import com.dmb.chantiertracker.resources.edit_project_submit
import com.dmb.chantiertracker.resources.error_not_found
import com.dmb.chantiertracker.resources.projects_retry
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EditProjectScreen(
    projectLocalId: String,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditProjectViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(projectLocalId) { viewModel.load(projectLocalId) }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Box(modifier.fillMaxSize()) {
        when {
            state.isMissing -> Column(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.error_not_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(onClick = onBack) { Text(stringResource(Res.string.projects_retry)) }
            }
            !state.prefilled -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            else -> EditForm(state, viewModel)
        }
    }
}

@Composable
private fun EditForm(state: EditProjectUiState, viewModel: EditProjectViewModel) {
    val locked = state.isSubmitting
    Column(
        modifier = Modifier
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
            text = stringResource(Res.string.edit_project_submit),
            onClick = viewModel::submit,
            loading = state.isSubmitting,
        )
    }
}
