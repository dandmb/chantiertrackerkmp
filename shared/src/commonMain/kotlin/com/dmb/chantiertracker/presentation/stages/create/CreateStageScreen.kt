package com.dmb.chantiertracker.presentation.stages.create

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.create_description_label
import com.dmb.chantiertracker.resources.create_stage_budget_label
import com.dmb.chantiertracker.resources.create_stage_date_hint
import com.dmb.chantiertracker.resources.create_stage_end_date_label
import com.dmb.chantiertracker.resources.create_stage_name_label
import com.dmb.chantiertracker.resources.create_stage_start_date_label
import com.dmb.chantiertracker.resources.create_stage_submit
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CreateStageScreen(
    projectLocalId: String,
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateStageViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(projectLocalId) { viewModel.start(projectLocalId) }
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
            label = { Text(stringResource(Res.string.create_stage_name_label)) },
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

        if (state.canSetBudget) {
            OutlinedTextField(
                value = state.estimatedBudget,
                onValueChange = viewModel::onBudgetChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.create_stage_budget_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = state.budgetError != null,
                supportingText = state.budgetError?.let { { Text(stringResource(it)) } },
                enabled = !state.isSubmitting,
            )
        }

        OutlinedTextField(
            value = state.startDate,
            onValueChange = viewModel::onStartDateChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.create_stage_start_date_label)) },
            placeholder = { Text(stringResource(Res.string.create_stage_date_hint)) },
            singleLine = true,
            isError = state.startDateError != null,
            supportingText = state.startDateError?.let { { Text(stringResource(it)) } },
            enabled = !state.isSubmitting,
        )

        OutlinedTextField(
            value = state.endDate,
            onValueChange = viewModel::onEndDateChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.create_stage_end_date_label)) },
            placeholder = { Text(stringResource(Res.string.create_stage_date_hint)) },
            singleLine = true,
            isError = state.endDateError != null,
            supportingText = state.endDateError?.let { { Text(stringResource(it)) } },
            enabled = !state.isSubmitting,
        )

        AuthPrimaryButton(
            text = stringResource(Res.string.create_stage_submit),
            onClick = viewModel::submit,
            loading = state.isSubmitting,
        )
    }
}
