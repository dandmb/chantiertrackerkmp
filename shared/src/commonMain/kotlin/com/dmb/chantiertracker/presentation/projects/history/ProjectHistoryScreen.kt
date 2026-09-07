package com.dmb.chantiertracker.presentation.projects.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.HistoryActionType
import com.dmb.chantiertracker.domain.model.HistorySort
import com.dmb.chantiertracker.domain.model.ModificationHistoryItem
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.presentation.formatIsoDateTime
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.history_action_creation
import com.dmb.chantiertracker.resources.history_action_deletion
import com.dmb.chantiertracker.resources.history_action_modification
import com.dmb.chantiertracker.resources.history_empty
import com.dmb.chantiertracker.resources.history_limit_free
import com.dmb.chantiertracker.resources.history_limit_semi_flex
import com.dmb.chantiertracker.resources.history_next
import com.dmb.chantiertracker.resources.history_page
import com.dmb.chantiertracker.resources.history_prev
import com.dmb.chantiertracker.resources.history_retry
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ProjectHistoryScreen(
    projectLocalId: String,
    modifier: Modifier = Modifier,
    // Driven from the TopAppBar in MainScreen — a global control that stays
    // reachable while the list scrolls (Material 3 convention).
    sort: HistorySort = HistorySort.NEWEST_FIRST,
    viewModel: ProjectHistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(projectLocalId) { viewModel.load(projectLocalId) }
    LaunchedEffect(sort) { viewModel.setSort(sort) }

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading && state.items.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.error != null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
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

                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                    itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                        HistoryRow(item)
                        if (index < state.items.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
        }

        if (state.showPagination) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = viewModel::previousPage, enabled = !state.isFirst && !state.isLoading) {
                    Text(stringResource(Res.string.history_prev))
                }
                Text(
                    text = stringResource(Res.string.history_page, state.page + 1, state.totalPages),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = viewModel::nextPage, enabled = !state.isLast && !state.isLoading) {
                    Text(stringResource(Res.string.history_next))
                }
            }
        }

        retentionNotice(state.ownerPlan)?.let { notice ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = stringResource(notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun HistoryRow(item: ModificationHistoryItem) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = formatIsoDateTime(item.modifiedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = item.description?.let { historyLine(it) }
                ?: AnnotatedString(stringResource(actionLabel(item.actionType))),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// Turns the plain server sentence into a lightly emphasised line: the "how much"
// in bold, the "where" (l'étape / le projet …) in the accent colour, so an admin
// scanning the log spots WHAT changed and WHERE without reading word by word.
// A shape the parser doesn't recognise stays plain (see HistoryDescription.kt).
@Composable
private fun historyLine(description: String): AnnotatedString {
    val accent = MaterialTheme.colorScheme.primary
    return remember(description, accent) {
        buildAnnotatedString {
            for (segment in parseHistoryDescription(description)) {
                when (segment.kind) {
                    HistorySegmentKind.PLAIN -> append(segment.text)
                    HistorySegmentKind.VALUE ->
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(segment.text) }
                    HistorySegmentKind.TARGET ->
                        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Medium)) { append(segment.text) }
                }
            }
        }
    }
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
