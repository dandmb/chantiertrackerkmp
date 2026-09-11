package com.dmb.chantiertracker.presentation.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import com.dmb.chantiertracker.domain.model.BillingCycle
import com.dmb.chantiertracker.presentation.main.CheckIcon
import com.dmb.chantiertracker.presentation.main.labelRes
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.billing_cycle_monthly
import com.dmb.chantiertracker.resources.billing_cycle_yearly
import org.jetbrains.compose.resources.stringResource

// Shared between BillingScreen (upgrade from the current plan, "S'abonner" ->
// immediate checkout) and PlanSelectionScreen (ADR-50, choosing a plan before
// an account even exists, "Choisir cette formule" -> carries the choice into
// registration) — the action's label and what it does belong to the caller,
// this card only ever renders PricingTiers content + a monthly/yearly toggle.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricingCard(
    tier: PricingTier,
    actionLabel: String,
    onAction: (BillingCycle) -> Unit,
    modifier: Modifier = Modifier,
    actionEnabled: Boolean = true,
) {
    var cycle by remember { mutableStateOf(BillingCycle.MONTHLY) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
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

            Button(onClick = { onAction(cycle) }, enabled = actionEnabled, modifier = Modifier.fillMaxWidth()) {
                Text(actionLabel)
            }
        }
    }
}
