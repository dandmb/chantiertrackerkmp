package com.dmb.chantiertracker.presentation.logs

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
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.action_save
import com.dmb.chantiertracker.resources.entry_summary_edit_title
import com.dmb.chantiertracker.resources.entry_summary_label
import com.dmb.chantiertracker.resources.entry_summary_placeholder
import com.dmb.chantiertracker.resources.entry_title_edit_title
import com.dmb.chantiertracker.resources.entry_title_label
import com.dmb.chantiertracker.resources.entry_title_placeholder
import com.dmb.chantiertracker.resources.error_not_found
import com.dmb.chantiertracker.resources.projects_retry
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EntrySummaryScreen(
    entryLocalId: String,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onTitleResolved: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: EntrySummaryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(entryLocalId) { viewModel.load(entryLocalId) }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    val isWork = state.type == EntryType.WORK
    val title = stringResource(if (isWork) Res.string.entry_title_edit_title else Res.string.entry_summary_edit_title)
    LaunchedEffect(title) { onTitleResolved(title) }

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
            else -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = state.summary,
                    onValueChange = viewModel::onSummaryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(if (isWork) Res.string.entry_title_label else Res.string.entry_summary_label)) },
                    placeholder = { Text(stringResource(if (isWork) Res.string.entry_title_placeholder else Res.string.entry_summary_placeholder)) },
                    minLines = 4,
                    isError = state.error != null,
                    supportingText = state.error?.let { { Text(stringResource(it)) } },
                    enabled = !state.isSubmitting,
                )
                AuthPrimaryButton(
                    text = stringResource(Res.string.action_save),
                    onClick = viewModel::submit,
                    loading = state.isSubmitting,
                )
            }
        }
    }
}
