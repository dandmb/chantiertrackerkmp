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
import androidx.compose.material3.LinearProgressIndicator
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
import com.dmb.chantiertracker.presentation.billing.FounderBadge
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
import com.dmb.chantiertracker.resources.offer_limits_section
import com.dmb.chantiertracker.resources.offer_never_loaded
import com.dmb.chantiertracker.resources.offer_plan_section
import com.dmb.chantiertracker.resources.offer_usage_photos
import com.dmb.chantiertracker.resources.offer_usage_projects
import com.dmb.chantiertracker.resources.offer_usage_supervisors
import com.dmb.chantiertracker.resources.offer_usage_videos
import com.dmb.chantiertracker.resources.offer_usage_videos_excluded
import com.dmb.chantiertracker.resources.offer_video_duration
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
            LimitsSection(usage)
        }
    }
}

@Composable
private fun PlanSection(usage: PlanUsage, billingStatus: BillingAvailability) {
    SectionCard(stringResource(Res.string.offer_plan_section), tone = SectionTone.Yellow) {
        Text(stringResource(usage.plan.labelRes()), style = MaterialTheme.typography.headlineSmall)
        if (usage.isFounder) FounderBadge()
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
private fun LimitsSection(usage: PlanUsage) {
    SectionCard(stringResource(Res.string.offer_limits_section)) {
        LimitRow(
            usage.projectsUsed, usage.projectsLimit,
            usage.projectsLimit?.let { stringResource(Res.string.offer_usage_projects, usage.projectsUsed, it) }
                ?: stringResource(Res.string.billing_usage_projects_unlimited),
        )
        LimitRow(
            usage.photosUsed, usage.photosLimit,
            usage.photosLimit?.let { stringResource(Res.string.offer_usage_photos, usage.photosUsed, it) }
                ?: stringResource(Res.string.billing_usage_photos_unlimited),
        )
        if (usage.videosLimit > 0) {
            LimitRow(usage.videosUsed, usage.videosLimit, stringResource(Res.string.offer_usage_videos, usage.videosUsed, usage.videosLimit))
            Note(stringResource(Res.string.offer_video_duration, formatVideoDuration(usage.videoDurationLimitSeconds)))
        } else {
            Note(stringResource(Res.string.offer_usage_videos_excluded))
        }
        LimitRow(
            usage.supervisorsUsed, usage.supervisorsLimit,
            usage.supervisorsLimit?.let { stringResource(Res.string.offer_usage_supervisors, usage.supervisorsUsed, it) }
                ?: stringResource(Res.string.billing_usage_supervisors_unlimited),
        )
        usage.maxHistoryDays()?.let { Note(historyKeptLabel(it)) }
        Note(stringResource(Res.string.offer_limits_owner_note))
    }
}

@Composable
private fun LimitRow(used: Int, limit: Int?, label: String) {
    val progress = usageProgress(used, limit)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                color = if (usageAtLimit(used, limit)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

internal fun formatVideoDuration(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
