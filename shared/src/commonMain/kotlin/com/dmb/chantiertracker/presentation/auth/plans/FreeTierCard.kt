package com.dmb.chantiertracker.presentation.auth.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.main.CheckIcon
import com.dmb.chantiertracker.presentation.main.CloseIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.billing_feature_free_export_excluded
import com.dmb.chantiertracker.resources.billing_feature_free_history
import com.dmb.chantiertracker.resources.billing_feature_free_photos
import com.dmb.chantiertracker.resources.billing_feature_free_projects
import com.dmb.chantiertracker.resources.billing_feature_free_supervisors
import com.dmb.chantiertracker.resources.billing_feature_free_videos_excluded
import com.dmb.chantiertracker.resources.billing_price_free
import com.dmb.chantiertracker.resources.plan_selection_continue_free
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// PlanSelectionScreen's third tab (ADR-51 correction) — mirrors the web
// landing page's FREE card (HomePage.tsx PRICING_PLANS[0]): same feature
// order as PricingCard's tiers (projects, photos, videos, supervisors,
// export, history) but each row is either included (green check) or
// excluded (grey cross) — FREE is the only tier with anything excluded,
// which is exactly what makes the paid tiers' full checkmark lists land.
//
// A standalone composable rather than a PricingCard reuse/generalisation:
// PricingCard is built around PricingTier (a purchasable Stripe price +
// monthly/yearly toggle) and PRICING_TIERS is deliberately the "sellable
// tiers only" map that resolveCheckoutIntent/BillingScreen depend on — FREE
// has no price, no billing cycle, and is never a Stripe checkout target, so
// forcing it through that shape would mean fabricating a fake cycle/price
// just to satisfy an API built for something else. The CTA is an
// OutlinedButton rather than a filled Button like PricingCard's — a
// deliberate, honest visual difference (never hidden, never harder to
// tap), not a filled primary action, so the paid tiers keep the stronger
// visual pull as the screen's actual goal.
@Composable
fun FreeTierCard(onContinueFree: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.billing_price_free), style = MaterialTheme.typography.headlineSmall)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FreeFeatureRow(Res.string.billing_feature_free_projects, included = true)
                FreeFeatureRow(Res.string.billing_feature_free_photos, included = true)
                FreeFeatureRow(Res.string.billing_feature_free_videos_excluded, included = false)
                FreeFeatureRow(Res.string.billing_feature_free_supervisors, included = true)
                FreeFeatureRow(Res.string.billing_feature_free_export_excluded, included = false)
                FreeFeatureRow(Res.string.billing_feature_free_history, included = true)
            }

            OutlinedButton(onClick = onContinueFree, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.plan_selection_continue_free))
            }
        }
    }
}

@Composable
private fun FreeFeatureRow(textRes: StringResource, included: Boolean) {
    val tint = if (included) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (included) CheckIcon else CloseIcon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.padding(2.dp),
        )
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyMedium,
            color = if (included) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
