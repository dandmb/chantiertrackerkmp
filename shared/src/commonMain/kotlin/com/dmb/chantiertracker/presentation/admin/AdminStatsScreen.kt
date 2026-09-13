package com.dmb.chantiertracker.presentation.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.Granularity
import com.dmb.chantiertracker.domain.model.StatsPoint
import com.dmb.chantiertracker.presentation.ClickableListRow
import com.dmb.chantiertracker.presentation.DateField
import com.dmb.chantiertracker.presentation.DetailEmptyHint
import com.dmb.chantiertracker.presentation.DetailInfoRow
import com.dmb.chantiertracker.presentation.DetailSection
import com.dmb.chantiertracker.presentation.DetailSectionDivider
import com.dmb.chantiertracker.presentation.formatIsoDate
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.presentation.main.AccountIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.admin_stats_empty
import com.dmb.chantiertracker.resources.admin_stats_from_label
import com.dmb.chantiertracker.resources.admin_stats_manage_users
import com.dmb.chantiertracker.resources.admin_stats_overview
import com.dmb.chantiertracker.resources.admin_stats_projects
import com.dmb.chantiertracker.resources.admin_stats_projects_created
import com.dmb.chantiertracker.resources.admin_stats_registrations
import com.dmb.chantiertracker.resources.admin_stats_to_label
import com.dmb.chantiertracker.resources.admin_stats_users
import com.dmb.chantiertracker.resources.admin_users_retry
import com.dmb.chantiertracker.resources.date_field_placeholder
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AdminStatsScreen(
    granularity: Granularity,
    onManageUsers: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminStatsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(granularity) { viewModel.setGranularity(granularity) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ClickableListRow(onClick = onManageUsers) {
            Icon(AccountIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = stringResource(Res.string.admin_stats_manage_users),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateField(
                label = stringResource(Res.string.admin_stats_from_label),
                value = state.from,
                onValueChange = viewModel::onFromChange,
                minDate = EARLIEST_STATS_DATE,
                modifier = Modifier.weight(1f),
                placeholder = stringResource(Res.string.date_field_placeholder),
                isError = state.dateRangeError != null,
            )
            DateField(
                label = stringResource(Res.string.admin_stats_to_label),
                value = state.to,
                onValueChange = viewModel::onToChange,
                minDate = EARLIEST_STATS_DATE,
                modifier = Modifier.weight(1f),
                placeholder = stringResource(Res.string.date_field_placeholder),
                isError = state.dateRangeError != null,
            )
        }
        state.dateRangeError?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        when {
            state.isLoading && state.stats == null ->
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp)) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

            state.error != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            ) {
                Text(
                    text = state.error!!.localizedText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(onClick = viewModel::retry) {
                    Text(stringResource(Res.string.admin_users_retry))
                }
            }

            state.stats != null -> {
                val stats = state.stats!!

                DetailSection(stringResource(Res.string.admin_stats_overview)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatCounter(stringResource(Res.string.admin_stats_users), stats.totalUsers)
                        StatCounter(stringResource(Res.string.admin_stats_projects), stats.totalProjects)
                    }
                }
                DetailSectionDivider()

                DetailSection(stringResource(Res.string.admin_stats_registrations)) {
                    StatsPointList(stats.registrations)
                }
                DetailSectionDivider()

                DetailSection(stringResource(Res.string.admin_stats_projects_created)) {
                    StatsPointList(stats.projectsCreated)
                }
            }
        }
    }
}

// Arbitrary — stats have no natural lower bound (an account could be as old
// as the platform itself), unlike every other DateField in this app which
// bounds a real business rule (a stage can't start before today, etc.).
private val EARLIEST_STATS_DATE = LocalDate(2020, 1, 1)

@Composable
private fun StatCounter(label: String, value: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatsPointList(points: List<StatsPoint>) {
    if (points.isEmpty()) {
        DetailEmptyHint(stringResource(Res.string.admin_stats_empty))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        points.forEachIndexed { index, point ->
            DetailInfoRow(formatIsoDate(point.bucket), point.count.toString())
            if (index < points.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
