package com.dmb.chantiertracker.presentation.stages.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.DailyLog
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.StageDetail
import com.dmb.chantiertracker.presentation.ClickableListRow
import com.dmb.chantiertracker.presentation.DetailEmptyHint
import com.dmb.chantiertracker.presentation.DetailInfoRow
import com.dmb.chantiertracker.presentation.DetailSection
import com.dmb.chantiertracker.presentation.DetailSectionDivider
import com.dmb.chantiertracker.presentation.formatIsoDate
import com.dmb.chantiertracker.presentation.format.formatMoney
import com.dmb.chantiertracker.presentation.main.AddIcon
import com.dmb.chantiertracker.presentation.main.ConstructionIcon
import com.dmb.chantiertracker.presentation.main.ShoppingCartIcon
import com.dmb.chantiertracker.presentation.stages.StageStatusBadge
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.detail_section_info
import com.dmb.chantiertracker.resources.entry_type_purchase
import com.dmb.chantiertracker.resources.entry_type_work
import com.dmb.chantiertracker.resources.error_not_found
import com.dmb.chantiertracker.resources.projects_retry
import com.dmb.chantiertracker.resources.stage_detail_add_entry_today
import com.dmb.chantiertracker.resources.stage_detail_budget
import com.dmb.chantiertracker.resources.stage_detail_dates
import com.dmb.chantiertracker.resources.stage_detail_days_empty
import com.dmb.chantiertracker.resources.stage_detail_days_empty_can_add
import com.dmb.chantiertracker.resources.stage_detail_section_days
import com.dmb.chantiertracker.resources.value_not_set
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun StageDetailScreen(
    stageLocalId: String,
    modifier: Modifier = Modifier,
    onStageNameResolved: (String) -> Unit = {},
    onOpenLog: (dailyLogLocalId: String) -> Unit = {},
    viewModel: StageDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(stageLocalId) { viewModel.load(stageLocalId) }
    LaunchedEffect(state.detail?.name) {
        state.detail?.name?.let(onStageNameResolved)
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
            state.detail != null -> StageDetailContent(
                detail = state.detail!!,
                currency = state.currency,
                logs = state.logs,
                todayDate = state.todayDate,
                canAddToday = state.canAddToday,
                onOpenLog = onOpenLog,
                onAddToday = { type -> viewModel.addTodayEntry(type) },
            )
        }
    }
}

@Composable
private fun StageDetailContent(
    detail: StageDetail,
    currency: String?,
    logs: List<DailyLog>,
    todayDate: String?,
    canAddToday: Boolean,
    onOpenLog: (String) -> Unit,
    onAddToday: suspend (EntryType) -> String?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = detail.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            StageStatusBadge(detail.status)
            if (!detail.description.isNullOrBlank()) {
                Text(
                    text = detail.description,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        DetailSectionDivider()

        val budget = detail.estimatedBudget?.let { formatMoney(it, currency) }
        val dates = formatDateRange(detail.startDate, detail.endDate)
        val notSet = stringResource(Res.string.value_not_set)
        DetailSection(stringResource(Res.string.detail_section_info)) {
            DetailInfoRow(stringResource(Res.string.stage_detail_budget), budget ?: notSet, muted = budget == null)
            DetailInfoRow(stringResource(Res.string.stage_detail_dates), dates ?: notSet, muted = dates == null)
        }

        DetailSectionDivider()

        DaysSection(
            logs = logs,
            todayDate = todayDate,
            canAddToday = canAddToday,
            onOpenLog = onOpenLog,
            onAddToday = onAddToday,
        )
    }
}

@Composable
private fun DaysSection(
    logs: List<DailyLog>,
    todayDate: String?,
    canAddToday: Boolean,
    onOpenLog: (String) -> Unit,
    onAddToday: suspend (EntryType) -> String?,
) {
    val scope = rememberCoroutineScope()
    var choiceMenuOpen by remember { mutableStateOf(false) }
    var isAdding by remember { mutableStateOf(false) }

    fun addAndOpen(type: EntryType) {
        choiceMenuOpen = false
        scope.launch {
            isAdding = true
            val logLocalId = onAddToday(type)
            isAdding = false
            logLocalId?.let(onOpenLog)
        }
    }

    DetailSection(stringResource(Res.string.stage_detail_section_days)) {
        // Full-width primary action on its own row — the label is long and must
        // never be squeezed against the section title (large font / narrow screen).
        Box(Modifier.fillMaxWidth()) {
            Button(
                enabled = todayDate != null && canAddToday && !isAdding,
                onClick = {
                    val todayLogLocalId = logs.firstOrNull { it.date == todayDate }?.localId
                    if (todayLogLocalId != null) onOpenLog(todayLogLocalId) else choiceMenuOpen = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(AddIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(Res.string.stage_detail_add_entry_today),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            DropdownMenu(expanded = choiceMenuOpen, onDismissRequest = { choiceMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.entry_type_purchase)) },
                    onClick = { addAndOpen(EntryType.PURCHASE) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.entry_type_work)) },
                    onClick = { addAndOpen(EntryType.WORK) },
                )
            }
        }

        if (logs.isEmpty()) {
            DetailEmptyHint(
                stringResource(
                    if (canAddToday) Res.string.stage_detail_days_empty_can_add else Res.string.stage_detail_days_empty,
                ),
            )
        } else {
            logs.forEach { log -> LogRow(log, onClick = { onOpenLog(log.localId) }) }
        }
    }
}

@Composable
private fun LogRow(log: DailyLog, onClick: () -> Unit) {
    ClickableListRow(onClick = onClick) {
        Text(
            formatIsoDate(log.date),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (log.hasPurchase) {
            Icon(
                ShoppingCartIcon,
                contentDescription = stringResource(Res.string.entry_type_purchase),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        if (log.hasWork) {
            Icon(
                ConstructionIcon,
                contentDescription = stringResource(Res.string.entry_type_work),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun formatDateRange(start: String?, end: String?): String? = when {
    !start.isNullOrBlank() && !end.isNullOrBlank() -> "${formatIsoDate(start)} → ${formatIsoDate(end)}"
    !start.isNullOrBlank() -> formatIsoDate(start)
    !end.isNullOrBlank() -> formatIsoDate(end)
    else -> null
}
