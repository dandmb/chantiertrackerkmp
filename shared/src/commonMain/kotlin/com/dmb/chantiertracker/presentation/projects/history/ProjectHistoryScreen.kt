package com.dmb.chantiertracker.presentation.projects.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.HistoryActionType
import com.dmb.chantiertracker.domain.model.HistorySort
import com.dmb.chantiertracker.domain.model.ModificationHistoryItem
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.presentation.formatIsoDateTime
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.presentation.main.CheckIcon
import com.dmb.chantiertracker.presentation.main.SortIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.history_action_creation
import com.dmb.chantiertracker.resources.history_action_deletion
import com.dmb.chantiertracker.resources.history_action_modification
import com.dmb.chantiertracker.resources.history_empty
import com.dmb.chantiertracker.resources.history_error
import com.dmb.chantiertracker.resources.history_limit_free
import com.dmb.chantiertracker.resources.history_limit_semi_flex
import com.dmb.chantiertracker.resources.history_next
import com.dmb.chantiertracker.resources.history_page
import com.dmb.chantiertracker.resources.history_prev
import com.dmb.chantiertracker.resources.history_retry
import com.dmb.chantiertracker.resources.history_sort_action
import com.dmb.chantiertracker.resources.history_sort_label
import com.dmb.chantiertracker.resources.history_sort_newest
import com.dmb.chantiertracker.resources.history_sort_oldest
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ProjectHistoryScreen(
    projectLocalId: String,
    modifier: Modifier = Modifier,
    viewModel: ProjectHistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(projectLocalId) { viewModel.load(projectLocalId) }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HistorySortControl(current = state.sort, onSelect = viewModel::setSort)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading && state.items.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.error != null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = state.error!!.localizedText(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(onClick = viewModel::retry) {
                        Text(stringResource(Res.string.history_retry))
                    }
                }

                state.items.isEmpty() -> Text(
                    text = stringResource(Res.string.history_empty),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { item ->
                        HistoryRow(item)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }

        if (state.showPagination) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = viewModel::previousPage, enabled = !state.isFirst && !state.isLoading) {
                    Text(stringResource(Res.string.history_prev))
                }
                Text(
                    text = stringResource(Res.string.history_page, state.page + 1, state.totalPages),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                TextButton(onClick = viewModel::nextPage, enabled = !state.isLast && !state.isLoading) {
                    Text(stringResource(Res.string.history_next))
                }
            }
        }

        retentionNotice(state.ownerPlan)?.let { notice ->
            Text(
                text = stringResource(notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryRow(item: ModificationHistoryItem) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = formatIsoDateTime(item.modifiedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            // The ready-made server sentence; a null description is only ever a
            // legacy row from before the backend recorded one — fall back to the
            // action label rather than an empty line.
            text = item.description ?: stringResource(actionLabel(item.actionType)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun HistorySortControl(current: HistorySort, onSelect: (HistorySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) {
        Icon(SortIcon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            text = stringResource(sortLabel(current)),
            modifier = Modifier.padding(start = 6.dp),
        )
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        HistorySort.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(stringResource(sortLabel(option))) },
                onClick = {
                    open = false
                    onSelect(option)
                },
                trailingIcon = {
                    if (option == current) {
                        Icon(
                            CheckIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
        }
    }
}

private fun sortLabel(sort: HistorySort): StringResource = when (sort) {
    HistorySort.NEWEST_FIRST -> Res.string.history_sort_newest
    HistorySort.OLDEST_FIRST -> Res.string.history_sort_oldest
    HistorySort.BY_ACTION -> Res.string.history_sort_action
}

private fun actionLabel(action: HistoryActionType): StringResource = when (action) {
    HistoryActionType.CREATION -> Res.string.history_action_creation
    HistoryActionType.MODIFICATION, HistoryActionType.UNKNOWN -> Res.string.history_action_modification
    HistoryActionType.DELETION -> Res.string.history_action_deletion
}

// Mirrors the backend's PlanLimitService.maxHistoryDays / the web's
// formatHistoryLimitNotice — LIBERTE (and an unknown/absent plan) shows nothing.
private fun retentionNotice(ownerPlan: Plan?): StringResource? = when (ownerPlan) {
    Plan.FREE -> Res.string.history_limit_free
    Plan.SEMI_FLEX -> Res.string.history_limit_semi_flex
    Plan.LIBERTE, Plan.UNKNOWN, null -> null
}
