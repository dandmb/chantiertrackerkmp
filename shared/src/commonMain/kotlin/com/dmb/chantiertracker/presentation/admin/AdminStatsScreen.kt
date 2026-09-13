package com.dmb.chantiertracker.presentation.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.Granularity
import com.dmb.chantiertracker.domain.model.StatsPoint
import com.dmb.chantiertracker.presentation.ClickableListRow
import com.dmb.chantiertracker.presentation.DateField
import com.dmb.chantiertracker.presentation.DetailEmptyHint
import com.dmb.chantiertracker.presentation.DetailSection
import com.dmb.chantiertracker.presentation.DetailSectionDivider
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
import io.github.koalaplot.core.Symbol
import io.github.koalaplot.core.line.CubicBezierLinePlot
import io.github.koalaplot.core.style.LineStyle
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.xygraph.CategoryAxisModel
import io.github.koalaplot.core.xygraph.DefaultPoint
import io.github.koalaplot.core.xygraph.LongLinearAxisModel
import io.github.koalaplot.core.xygraph.XYGraph
import io.github.koalaplot.core.xygraph.rememberAxisContent
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
                    StatsLineChart(stats.registrations, granularity)
                }
                DetailSectionDivider()

                DetailSection(stringResource(Res.string.admin_stats_projects_created)) {
                    StatsLineChart(stats.projectsCreated, granularity)
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

// A smoothed time-series line chart, matching the web's TimeSeriesChart.tsx
// (recharts LineChart, type="monotone") — ADR-52 sous-étape 4/4 originally
// chose a plain list here deliberately, but the user later asked for parity
// with the web's real chart (ADR-55). KoalaPlot 0.12.1, chosen and verified
// against this project's exact Kotlin/Compose/AGP pins before adopting
// (see docs/walkthrough/administration-plateforme.md §10).
@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun StatsLineChart(points: List<StatsPoint>, granularity: Granularity) {
    if (points.isEmpty()) {
        DetailEmptyHint(stringResource(Res.string.admin_stats_empty))
        return
    }
    val lineColor = MaterialTheme.colorScheme.primary
    val labels = points.map { formatStatsBucket(it.bucket, granularity) }
    val data = points.mapIndexed { index, point -> DefaultPoint(labels[index], point.count) }
    // count is never negative — a plain 0-based range keeps the line's shape
    // meaningful (a flat low period reads as low, not as filling the whole
    // graph height the way an auto-fit min could make it).
    val maxCount = points.maxOf { it.count }.coerceAtLeast(1L)

    XYGraph(
        xAxisModel = CategoryAxisModel(labels),
        yAxisModel = LongLinearAxisModel(0L..maxCount),
        xAxisContent = rememberAxisContent(labels = { StatsAxisLabel(it) }),
        yAxisContent = rememberAxisContent(labels = { StatsAxisLabel(it.toString()) }),
        modifier = Modifier.fillMaxWidth().height(220.dp),
    ) {
        CubicBezierLinePlot(
            data = data,
            lineStyle = LineStyle(brush = SolidColor(lineColor), strokeWidth = 2.dp),
            symbol = { Symbol(shape = CircleShape, fillBrush = SolidColor(lineColor), size = 6.dp) },
        )
    }
}

@Composable
private fun StatsAxisLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
