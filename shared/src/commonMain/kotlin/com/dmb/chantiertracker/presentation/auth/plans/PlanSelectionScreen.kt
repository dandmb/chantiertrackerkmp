package com.dmb.chantiertracker.presentation.auth.plans

import androidx.compose.runtime.Composable
import com.dmb.chantiertracker.domain.model.BillingCycle
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.presentation.auth.components.AuthScreenLayout
import com.dmb.chantiertracker.presentation.billing.PRICING_TIERS
import com.dmb.chantiertracker.presentation.billing.PricingCard
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.plan_selection_choose
import com.dmb.chantiertracker.resources.plan_selection_subtitle
import com.dmb.chantiertracker.resources.plan_selection_title
import org.jetbrains.compose.resources.stringResource

// Reached from WelcomeScreen (ADR-50) — the mobile equivalent of the web's
// landing-page pricing section, the only point before an account exists
// where a paid tier is offered. Reuses PricingTiers/PricingCard as-is
// (same config and card the billing screen shows post-signup) — only the
// action differs: here it carries the choice into registration instead of
// starting a Stripe checkout directly (no account, no Bearer token yet).
@Composable
fun PlanSelectionScreen(
    onSelectPlan: (Plan, BillingCycle) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val chooseLabel = stringResource(Res.string.plan_selection_choose)
    AuthScreenLayout(
        title = stringResource(Res.string.plan_selection_title),
        subtitle = stringResource(Res.string.plan_selection_subtitle),
        onBack = onBack,
    ) {
        PRICING_TIERS.values.forEach { tier ->
            PricingCard(
                tier = tier,
                actionLabel = chooseLabel,
                onAction = { cycle -> onSelectPlan(tier.plan, cycle) },
            )
        }
    }
}
