package com.dmb.chantiertracker.presentation.logs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.DailyEntry
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.presentation.main.AddIcon
import com.dmb.chantiertracker.presentation.main.ConstructionIcon
import com.dmb.chantiertracker.presentation.main.ShoppingCartIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.action_cancel
import com.dmb.chantiertracker.resources.entry_add
import com.dmb.chantiertracker.resources.entry_edit
import com.dmb.chantiertracker.resources.entry_edit_title
import com.dmb.chantiertracker.resources.entry_none_yet
import com.dmb.chantiertracker.resources.entry_no_summary
import com.dmb.chantiertracker.resources.entry_no_title
import com.dmb.chantiertracker.resources.entry_summary_label
import com.dmb.chantiertracker.resources.entry_title_label
import com.dmb.chantiertracker.resources.entry_type_purchase
import com.dmb.chantiertracker.resources.entry_type_work
import com.dmb.chantiertracker.resources.error_not_found
import com.dmb.chantiertracker.resources.projects_retry
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DailyLogScreen(
    dailyLogLocalId: String,
    modifier: Modifier = Modifier,
    onDateResolved: (String) -> Unit = {},
    viewModel: DailyLogViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(dailyLogLocalId) { viewModel.load(dailyLogLocalId) }
    LaunchedEffect(state.detail?.date) {
        state.detail?.date?.let(onDateResolved)
    }

    Box(modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
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
                OutlinedButton(onClick = viewModel::retry) {
                    Text(stringResource(Res.string.projects_retry))
                }
            }
            state.detail != null -> DailyLogContent(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun DailyLogContent(state: DailyLogUiState, viewModel: DailyLogViewModel) {
    val detail = state.detail!!
    val purchaseEntry = detail.entries.firstOrNull { it.type == EntryType.PURCHASE }
    val workEntry = detail.entries.firstOrNull { it.type == EntryType.WORK }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = detail.date,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        EntryCard(
            type = EntryType.PURCHASE,
            icon = ShoppingCartIcon,
            entry = purchaseEntry,
            canEdit = state.canEdit,
            onAdd = { viewModel.addEntry(EntryType.PURCHASE) },
            onEdit = { viewModel.startEditingSummary(purchaseEntry!!.localId) },
        )
        EntryCard(
            type = EntryType.WORK,
            icon = ConstructionIcon,
            entry = workEntry,
            canEdit = state.canEdit,
            onAdd = { viewModel.addEntry(EntryType.WORK) },
            onEdit = { viewModel.startEditingSummary(workEntry!!.localId) },
        )
    }

    val editingEntry = detail.entries.firstOrNull { it.localId == state.editingEntryLocalId }
    if (editingEntry != null) {
        EntrySummaryDialog(
            type = editingEntry.type,
            initialSummary = editingEntry.summary.orEmpty(),
            error = state.summaryError,
            isSubmitting = state.isSubmitting,
            onDismiss = viewModel::cancelEditingSummary,
            onSubmit = viewModel::saveSummary,
        )
    }
}

@Composable
private fun EntryCard(
    type: EntryType,
    icon: ImageVector,
    entry: DailyEntry?,
    canEdit: Boolean,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(if (type == EntryType.WORK) Res.string.entry_type_work else Res.string.entry_type_purchase),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (entry != null) {
                val summary = entry.summary?.takeIf { it.isNotBlank() }
                Text(
                    text = summary ?: stringResource(
                        if (type == EntryType.WORK) Res.string.entry_no_title else Res.string.entry_no_summary,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (summary != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (canEdit) {
                    OutlinedButton(onClick = onEdit) { Text(stringResource(Res.string.entry_edit)) }
                }
            } else {
                Text(
                    text = stringResource(Res.string.entry_none_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (canEdit) {
                    Button(onClick = onAdd) {
                        Icon(AddIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(text = stringResource(Res.string.entry_add), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EntrySummaryDialog(
    type: EntryType,
    initialSummary: String,
    error: StringResource?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var value by remember(initialSummary) { mutableStateOf(initialSummary) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.entry_edit_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(if (type == EntryType.WORK) Res.string.entry_title_label else Res.string.entry_summary_label)) },
                isError = error != null,
                supportingText = error?.let { { Text(stringResource(it)) } },
                minLines = 3,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(value) }, enabled = !isSubmitting) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text(stringResource(Res.string.entry_edit))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
