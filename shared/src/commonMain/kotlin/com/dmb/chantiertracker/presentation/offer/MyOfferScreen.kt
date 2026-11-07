package com.dmb.chantiertracker.presentation.offer

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.BillingAvailability
import com.dmb.chantiertracker.domain.model.PlanUsage
import com.dmb.chantiertracker.presentation.ResponsiveContent
import com.dmb.chantiertracker.presentation.SectionCard
import com.dmb.chantiertracker.presentation.SectionTone
import com.dmb.chantiertracker.presentation.auth.components.InfoBanner
import com.dmb.chantiertracker.presentation.billing.usageAtLimit
import com.dmb.chantiertracker.presentation.billing.usageProgress
import com.dmb.chantiertracker.presentation.formatEpochMillisDateTime
import com.dmb.chantiertracker.presentation.main.labelRes
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.billing_usage_photos_unlimited
import com.dmb.chantiertracker.resources.billing_usage_projects_unlimited
import com.dmb.chantiertracker.resources.billing_usage_supervisors_unlimited
import com.dmb.chantiertracker.resources.offer_founder_launch
import com.dmb.chantiertracker.resources.offer_free_launch
import com.dmb.chantiertracker.resources.offer_last_known
import com.dmb.chantiertracker.resources.offer_limits_owner_note
import com.dmb.chantiertracker.resources.offer_never_loaded
import com.dmb.chantiertracker.resources.offer_plan_section
import com.dmb.chantiertracker.resources.offer_usage_photos
import com.dmb.chantiertracker.resources.offer_usage_projects
import com.dmb.chantiertracker.resources.offer_usage_supervisors
import com.dmb.chantiertracker.resources.offer_usage_videos
import com.dmb.chantiertracker.resources.offer_usage_videos_excluded
import com.dmb.chantiertracker.resources.offer_video_duration
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import com.dmb.chantiertracker.presentation.CapitalTitle
import com.dmb.chantiertracker.presentation.SectionProgressBar
import com.dmb.chantiertracker.presentation.SectionRow
import com.dmb.chantiertracker.resources.billing_founder_badge
import com.dmb.chantiertracker.resources.founder_offer_pdf
import com.dmb.chantiertracker.resources.offer_row_history
import com.dmb.chantiertracker.resources.offer_row_pdf
import com.dmb.chantiertracker.resources.offer_row_photos
import com.dmb.chantiertracker.resources.offer_row_projects
import com.dmb.chantiertracker.resources.offer_row_supervisors
import com.dmb.chantiertracker.resources.offer_row_videos
import com.dmb.chantiertracker.resources.offer_usage_section
import com.dmb.chantiertracker.resources.offer_value_count
import com.dmb.chantiertracker.resources.offer_value_count_per_project
import com.dmb.chantiertracker.resources.offer_value_days
import com.dmb.chantiertracker.resources.offer_value_included
import com.dmb.chantiertracker.resources.offer_value_months
import com.dmb.chantiertracker.resources.offer_value_not_included
import com.dmb.chantiertracker.resources.offer_value_unlimited
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MyOfferScreen(
    modifier: Modifier = Modifier,
    viewModel: MyOfferViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val usage = state.usage

    ResponsiveContent(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (usage == null) {
                if (state.online) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    InfoBanner(stringResource(Res.string.offer_never_loaded))
                }
                return@Column
            }
            if (!state.online) {
                usage.refreshedAtEpochMillis?.let { InfoBanner(stringResource(Res.string.offer_last_known, formatEpochMillisDateTime(it))) }
            }
            PlanSection(usage, state.billingStatus)
            UsageSection(usage, pdfExportIncluded = usage.isFounder && state.founderOffer?.pdfExport == true)
        }
    }
}

@Composable
private fun PlanSection(usage: PlanUsage, billingStatus: BillingAvailability) {
    SectionCard(
        title = stringResource(if (usage.isFounder) Res.string.billing_founder_badge else Res.string.offer_plan_section),
        tone = if (usage.isFounder) SectionTone.Orange else SectionTone.Neutral,
    ) {
        CapitalTitle(stringResource(usage.plan.labelRes()), style = MaterialTheme.typography.headlineMedium)
        if (billingStatus == BillingAvailability.CLOSED) {
            Text(
                stringResource(if (usage.isFounder) Res.string.offer_founder_launch else Res.string.offer_free_launch),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UsageSection(usage: PlanUsage, pdfExportIncluded: Boolean) {
    val unlimited = stringResource(Res.string.offer_value_unlimited)
    SectionCard(stringResource(Res.string.offer_usage_section), flush = true) {
        CountRow(
            label = stringResource(Res.string.offer_row_projects),
            used = usage.projectsUsed,
            limit = usage.projectsLimit,
            value = usage.projectsLimit?.let { stringResource(Res.string.offer_value_count, usage.projectsUsed, it) } ?: unlimited,
            spoken = usage.projectsLimit?.let { stringResource(Res.string.offer_usage_projects, usage.projectsUsed, it) }
                ?: stringResource(Res.string.billing_usage_projects_unlimited),
        )
        CountRow(
            label = stringResource(Res.string.offer_row_photos),
            used = usage.photosUsed,
            limit = usage.photosLimit,
            value = usage.photosLimit?.let { stringResource(Res.string.offer_value_count, usage.photosUsed, it) } ?: unlimited,
            spoken = usage.photosLimit?.let { stringResource(Res.string.offer_usage_photos, usage.photosUsed, it) }
                ?: stringResource(Res.string.billing_usage_photos_unlimited),
        )
        if (usage.videosLimit > 0) {
            CountRow(
                label = stringResource(Res.string.offer_row_videos),
                used = usage.videosUsed,
                limit = usage.videosLimit,
                value = stringResource(Res.string.offer_value_count, usage.videosUsed, usage.videosLimit),
                spoken = stringResource(Res.string.offer_usage_videos, usage.videosUsed, usage.videosLimit),
                note = stringResource(Res.string.offer_video_duration, formatVideoDuration(usage.videoDurationLimitSeconds)),
            )
        } else {
            TextRow(
                label = stringResource(Res.string.offer_row_videos),
                value = stringResource(Res.string.offer_value_not_included),
                spoken = stringResource(Res.string.offer_usage_videos_excluded),
            )
        }
        CountRow(
            label = stringResource(Res.string.offer_row_supervisors),
            used = usage.supervisorsUsed,
            limit = usage.supervisorsLimit,
            value = usage.supervisorsLimit?.let { stringResource(Res.string.offer_value_count_per_project, usage.supervisorsUsed, it) } ?: unlimited,
            spoken = usage.supervisorsLimit?.let { stringResource(Res.string.offer_usage_supervisors, usage.supervisorsUsed, it) }
                ?: stringResource(Res.string.billing_usage_supervisors_unlimited),
        )
        if (pdfExportIncluded) {
            TextRow(
                label = stringResource(Res.string.offer_row_pdf),
                value = stringResource(Res.string.offer_value_included),
                spoken = stringResource(Res.string.founder_offer_pdf),
            )
        }
        usage.maxHistoryDays()?.let { days ->
            TextRow(
                label = stringResource(Res.string.offer_row_history),
                value = wholeHistoryMonths(days)?.let { stringResource(Res.string.offer_value_months, it) }
                    ?: stringResource(Res.string.offer_value_days, days),
                spoken = historyKeptLabel(days),
            )
        }
        SectionRow(divider = false) { Note(stringResource(Res.string.offer_limits_owner_note)) }
    }
}

@Composable
private fun LabelAndValue(label: String, value: String, spoken: String) {
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { text = AnnotatedString(spoken) },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End)
    }
}

@Composable
private fun CountRow(label: String, used: Int, limit: Int?, value: String, spoken: String, note: String? = null) {
    SectionRow {
        LabelAndValue(label, value, spoken)
        usageProgress(used, limit)?.let { SectionProgressBar(it, atLimit = usageAtLimit(used, limit)) }
        note?.let { Note(it) }
    }
}

@Composable
private fun TextRow(label: String, value: String, spoken: String) {
    SectionRow { LabelAndValue(label, value, spoken) }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

internal fun formatVideoDuration(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
