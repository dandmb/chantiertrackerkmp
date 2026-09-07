package com.dmb.chantiertracker.presentation.reports

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.Report
import com.dmb.chantiertracker.domain.model.ReportStatus
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.formatIsoDate
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.entry_type_purchase
import com.dmb.chantiertracker.resources.entry_type_work
import com.dmb.chantiertracker.resources.history_next
import com.dmb.chantiertracker.resources.history_page
import com.dmb.chantiertracker.resources.history_prev
import com.dmb.chantiertracker.resources.history_retry
import com.dmb.chantiertracker.resources.report_author_unknown
import com.dmb.chantiertracker.resources.report_flagged_by
import com.dmb.chantiertracker.resources.report_mark_processed
import com.dmb.chantiertracker.resources.report_processed_on
import com.dmb.chantiertracker.resources.report_status_processed
import com.dmb.chantiertracker.resources.reports_empty
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ProjectReportsScreen(
    projectLocalId: String,
    modifier: Modifier = Modifier,
    viewModel: ProjectReportsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(projectLocalId) { viewModel.load(projectLocalId) }

    Column(modifier.fillMaxSize()) {
        state.processError?.let {
            ErrorBanner(
                message = it.localizedText(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

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
                    text = stringResource(Res.string.reports_empty),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                    itemsIndexed(state.items, key = { _, report -> report.id }) { index, report ->
                        ReportRow(
                            report = report,
                            isProcessing = report.id in state.processingIds,
                            onMarkProcessed = { viewModel.markProcessed(report.id) },
                        )
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
    }
}

@Composable
private fun ReportRow(report: Report, isProcessing: Boolean, onMarkProcessed: () -> Unit) {
    val processed = report.status == ReportStatus.PROCESSED

    // The fields are already structured — no parsing (unlike the history). We
    // just mark the author and the report's own date so the screen emphasises
    // them the same two ways the history screen styles TARGET / VALUE: accent
    // colour for the person, weight for the date.
    val metaSegments = reportTextSegments(
        template = stringResource(Res.string.report_flagged_by),
        parts = listOf(
            stringResource(if (report.entryType == EntryType.WORK) Res.string.entry_type_work else Res.string.entry_type_purchase)
                to ReportFieldEmphasis.PLAIN,
            formatIsoDate(report.entryDate) to ReportFieldEmphasis.PLAIN,
            (report.authorName ?: stringResource(Res.string.report_author_unknown)) to ReportFieldEmphasis.PERSON,
            formatIsoDate(report.createdAt.substringBefore('T')) to ReportFieldEmphasis.DATE,
        ),
    )

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = emphasised(metaSegments),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (processed) ProcessedBadge()
        }

        Text(
            text = report.message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (processed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )

        if (processed) {
            report.processedAt?.let { at ->
                val processedSegments = reportTextSegments(
                    template = stringResource(Res.string.report_processed_on),
                    parts = listOf(formatIsoDate(at.substringBefore('T')) to ReportFieldEmphasis.DATE),
                )
                Text(
                    text = emphasised(processedSegments),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            OutlinedButton(onClick = onMarkProcessed, enabled = !isProcessing) {
                Text(stringResource(Res.string.report_mark_processed))
            }
        }
    }
}

// PERSON → accent colour + Medium, DATE → SemiBold + full-contrast colour — the
// same two channels the history screen uses for TARGET / VALUE
// (ProjectHistoryScreen.historyLine), adapted to this smaller (labelSmall,
// muted) line: the date also gets onSurface so the weight actually reads.
@Composable
private fun emphasised(segments: List<ReportTextSegment>): AnnotatedString {
    val person = MaterialTheme.colorScheme.primary
    val date = MaterialTheme.colorScheme.onSurface
    return remember(segments, person, date) {
        buildAnnotatedString {
            for (segment in segments) {
                when (segment.emphasis) {
                    ReportFieldEmphasis.PLAIN -> append(segment.text)
                    ReportFieldEmphasis.PERSON ->
                        withStyle(SpanStyle(color = person, fontWeight = FontWeight.Medium)) { append(segment.text) }
                    ReportFieldEmphasis.DATE ->
                        withStyle(SpanStyle(color = date, fontWeight = FontWeight.SemiBold)) { append(segment.text) }
                }
            }
        }
    }
}

@Composable
private fun ProcessedBadge() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(percent = 50),
    ) {
        Text(
            text = stringResource(Res.string.report_status_processed),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}
