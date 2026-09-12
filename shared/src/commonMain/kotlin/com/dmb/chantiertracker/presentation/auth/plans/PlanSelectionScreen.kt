package com.dmb.chantiertracker.presentation.auth.plans

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dmb.chantiertracker.domain.model.BillingCycle
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.presentation.auth.components.AuthScreenLayout
import com.dmb.chantiertracker.presentation.billing.PRICING_TIERS
import com.dmb.chantiertracker.presentation.billing.PricingCard
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.plan_selection_choose
import com.dmb.chantiertracker.resources.plan_selection_subtitle
import com.dmb.chantiertracker.resources.plan_selection_tab_free
import com.dmb.chantiertracker.resources.plan_selection_tab_liberte
import com.dmb.chantiertracker.resources.plan_selection_tab_semi_flex
import com.dmb.chantiertracker.resources.plan_selection_title
import org.jetbrains.compose.resources.stringResource

// Reached from WelcomeScreen (ADR-50) — the mobile equivalent of the web's
// landing-page pricing section, the only point before an account exists
// where every tier — paid or not — is laid out for comparison. Reuses
// PricingTiers/PricingCard as-is for the two paid tiers (same config and
// card the billing screen shows post-signup) — only the action differs:
// here it carries the choice into registration instead of starting a
// Stripe checkout directly (no account, no Bearer token yet).
//
// ADR-51 (auth UX audit) — one tier shown at a time behind a tab switcher,
// rather than the paid PricingCards stacked. Two full cards (6 features
// each) forced a long scroll before either "Choisir cette formule" button
// was even visible, and each carried its own independent monthly/yearly
// toggle — nothing stopped picking "monthly" on one card and "yearly" on
// the other, an inconsistency with no meaning once only one plan is
// actually chosen. A tab switcher shows exactly one card at a time: less
// scrolling, one toggle where it applies, and PricingCard itself is
// unmodified for the paid tiers — so BillingScreen's rendering (verified
// pixel-for-pixel across ADR-49/50) stays untouched.
//
// ADR-51 correction (same day) — FREE added as a third tab. The screen
// initially only offered the two paid tiers, with no way to see what FREE
// actually includes — a user could not tell what they already have or
// would be giving up by not paying. FREE is now a full tab, first in
// reading order (FREE, Semi-Flex, Liberté — the web landing page's own
// left-to-right order, HomePage.tsx PRICING_PLANS), rendered by
// FreeTierCard (not PricingCard — see its doc for why). The **default
// selected tab stays Semi-Flex**, not FREE: comparability is now real (any
// tab is one tap away) without diluting the screen's actual goal, which
// stays steering toward a paid tier — the first thing shown is still a
// paid plan, not the free one.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSelectionScreen(
    onSelectPlan: (Plan, BillingCycle) -> Unit,
    onContinueFree: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var selectedPlan by remember { mutableStateOf(Plan.SEMI_FLEX) }
    val tabs = listOf(Plan.FREE) + PRICING_TIERS.keys
    val chooseLabel = stringResource(Res.string.plan_selection_choose)

    AuthScreenLayout(
        title = stringResource(Res.string.plan_selection_title),
        subtitle = stringResource(Res.string.plan_selection_subtitle),
        onBack = onBack,
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, plan ->
                SegmentedButton(
                    selected = plan == selectedPlan,
                    onClick = { selectedPlan = plan },
                    shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                ) {
                    Text(stringResource(plan.tabLabelRes()))
                }
            }
        }

        if (selectedPlan == Plan.FREE) {
            FreeTierCard(onContinueFree = onContinueFree)
        } else {
            PRICING_TIERS[selectedPlan]?.let { tier ->
                PricingCard(
                    tier = tier,
                    actionLabel = chooseLabel,
                    onAction = { cycle -> onSelectPlan(tier.plan, cycle) },
                    showTitle = false,
                )
            }
        }
    }
}

// Short by design: three tabs share one row on a phone-width screen —
// Plan.labelRes() ("Formule Semi-Flex"/"Semi-Flex plan") wraps or crowds at
// that width, unlike when it labelled only two tabs (ADR-50) or a single
// heading (AppTopBar, BillingScreen).
private fun Plan.tabLabelRes() = when (this) {
    Plan.FREE -> Res.string.plan_selection_tab_free
    Plan.SEMI_FLEX -> Res.string.plan_selection_tab_semi_flex
    Plan.LIBERTE, Plan.UNKNOWN -> Res.string.plan_selection_tab_liberte
}
