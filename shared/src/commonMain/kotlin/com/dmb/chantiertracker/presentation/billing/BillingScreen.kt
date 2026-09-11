package com.dmb.chantiertracker.presentation.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.BillingCycle
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.PlanUsage
import com.dmb.chantiertracker.presentation.DetailSection
import com.dmb.chantiertracker.presentation.DetailSectionDivider
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.formatIsoDateTime
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.presentation.main.CheckIcon
import com.dmb.chantiertracker.presentation.main.labelRes
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.billing_cycle_monthly
import com.dmb.chantiertracker.resources.billing_cycle_yearly
import com.dmb.chantiertracker.resources.billing_manage_subscription
import com.dmb.chantiertracker.resources.billing_next_due
import com.dmb.chantiertracker.resources.billing_no_action
import com.dmb.chantiertracker.resources.billing_plan_section
import com.dmb.chantiertracker.resources.billing_subscribe
import com.dmb.chantiertracker.resources.billing_subscribe_pending
import com.dmb.chantiertracker.resources.billing_upgrade_section
import com.dmb.chantiertracker.resources.billing_usage_photos
import com.dmb.chantiertracker.resources.billing_usage_photos_unlimited
import com.dmb.chantiertracker.resources.billing_usage_projects
import com.dmb.chantiertracker.resources.billing_usage_projects_unlimited
import com.dmb.chantiertracker.resources.billing_usage_section
import com.dmb.chantiertracker.resources.billing_usage_supervisors
import com.dmb.chantiertracker.resources.billing_usage_supervisors_unlimited
import com.dmb.chantiertracker.resources.billing_usage_videos
import com.dmb.chantiertracker.resources.history_limit_free
import com.dmb.chantiertracker.resources.history_limit_semi_flex
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BillingScreen(
    modifier: Modifier = Modifier,
    viewModel: BillingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val usage = state.planUsage

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (usage == null) {
            Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        PlanSection(usage)

        DetailSectionDivider()

        UsageSection(usage)

        DetailSectionDivider()

        ActionsSection(
            usage = usage,
            isProcessing = state.isProcessingAction,
            error = state.actionError,
            onManageSubscription = viewModel::openManageSubscription,
            onSubscribe = viewModel::startCheckout,
        )
    }
}

@Composable
private fun PlanSection(usage: PlanUsage) {
    DetailSection(stringResource(Res.string.billing_plan_section)) {
        Text(stringResource(usage.plan.labelRes()), style = MaterialTheme.typography.headlineSmall)
        usage.planExpiresAt?.let { expiresAt ->
            Text(
                text = stringResource(Res.string.billing_next_due, formatIsoDateTime(expiresAt)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UsageSection(usage: PlanUsage) {
    DetailSection(stringResource(Res.string.billing_usage_section)) {
        CountUsageRow(
            used = usage.projectsUsed,
            limit = usage.projectsLimit,
            label = stringResource(Res.string.billing_usage_projects, usage.projectsUsed, usage.projectsLimit ?: 0),
            unlimitedLabel = stringResource(Res.string.billing_usage_projects_unlimited),
        )
        CountUsageRow(
            used = usage.photosUsed,
            limit = usage.photosLimit,
            label = stringResource(Res.string.billing_usage_photos, usage.photosUsed, usage.photosLimit ?: 0),
            unlimitedLabel = stringResource(Res.string.billing_usage_photos_unlimited),
        )
        if (usage.videosLimit > 0) {
            CountUsageRow(
                used = usage.videosUsed,
                limit = usage.videosLimit,
                label = stringResource(Res.string.billing_usage_videos, usage.videosUsed, usage.videosLimit),
                unlimitedLabel = null,
            )
        }
        CountUsageRow(
            used = usage.supervisorsUsed,
            limit = usage.supervisorsLimit,
            label = stringResource(Res.string.billing_usage_supervisors, usage.supervisorsUsed, usage.supervisorsLimit ?: 0),
            unlimitedLabel = stringResource(Res.string.billing_usage_supervisors_unlimited),
        )
        historyLimitNotice(usage.plan)?.let { notice ->
            Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CountUsageRow(used: Int, limit: Int?, label: String, unlimitedLabel: String?) {
    val progress = usageProgress(used, limit)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (progress == null && unlimitedLabel != null) unlimitedLabel else label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
private fun historyLimitNotice(plan: Plan): String? = when (plan) {
    Plan.FREE -> stringResource(Res.string.history_limit_free)
    Plan.SEMI_FLEX -> stringResource(Res.string.history_limit_semi_flex)
    Plan.LIBERTE, Plan.UNKNOWN -> null
}

@Composable
private fun ActionsSection(
    usage: PlanUsage,
    isProcessing: Boolean,
    error: DomainException?,
    onManageSubscription: () -> Unit,
    onSubscribe: (Plan, BillingCycle) -> Unit,
) {
    val targets = upgradeTargets(usage.plan)
    val visibility = resolveBillingActionsVisibility(usage.plan, usage.hasStripeCustomer, targets.isNotEmpty())

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        error?.let { ErrorBanner(it.localizedText()) }

        if (visibility.showManageButton) {
            Button(onClick = onManageSubscription, enabled = !isProcessing) {
                Text(stringResource(Res.string.billing_manage_subscription))
            }
        }

        if (visibility.showNoActionMessage) {
            Text(
                text = stringResource(Res.string.billing_no_action),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (targets.isNotEmpty()) {
            DetailSection(stringResource(Res.string.billing_upgrade_section)) {
                targets.forEach { plan ->
                    PRICING_TIERS[plan]?.let { tier ->
                        PricingCard(tier = tier, isProcessing = isProcessing, onSubscribe = { cycle -> onSubscribe(plan, cycle) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PricingCard(tier: PricingTier, isProcessing: Boolean, onSubscribe: (BillingCycle) -> Unit) {
    var cycle by remember { mutableStateOf(BillingCycle.MONTHLY) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(tier.plan.labelRes()), style = MaterialTheme.typography.titleMedium)

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                BillingCycle.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == cycle,
                        onClick = { cycle = option },
                        shape = SegmentedButtonDefaults.itemShape(index, BillingCycle.entries.size),
                    ) {
                        Text(stringResource(if (option == BillingCycle.MONTHLY) Res.string.billing_cycle_monthly else Res.string.billing_cycle_yearly))
                    }
                }
            }

            val priceRes = if (cycle == BillingCycle.MONTHLY) tier.monthlyPriceRes else tier.yearlyPriceRes
            val noteRes = if (cycle == BillingCycle.MONTHLY) tier.monthlyNoteRes else tier.yearlyNoteRes
            Text(stringResource(priceRes), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(noteRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tier.featureRes.forEach { featureRes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(CheckIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(2.dp))
                        Text(stringResource(featureRes), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Button(onClick = { onSubscribe(cycle) }, enabled = !isProcessing, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (isProcessing) Res.string.billing_subscribe_pending else Res.string.billing_subscribe))
            }
        }
    }
}
