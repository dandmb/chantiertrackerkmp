package com.dmb.chantiertracker.presentation.billing

import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.billing_feature_liberte_export
import com.dmb.chantiertracker.resources.billing_feature_liberte_history
import com.dmb.chantiertracker.resources.billing_feature_liberte_photos
import com.dmb.chantiertracker.resources.billing_feature_liberte_projects
import com.dmb.chantiertracker.resources.billing_feature_liberte_supervisors
import com.dmb.chantiertracker.resources.billing_feature_liberte_videos
import com.dmb.chantiertracker.resources.billing_feature_semi_flex_export
import com.dmb.chantiertracker.resources.billing_feature_semi_flex_history
import com.dmb.chantiertracker.resources.billing_feature_semi_flex_photos
import com.dmb.chantiertracker.resources.billing_feature_semi_flex_projects
import com.dmb.chantiertracker.resources.billing_feature_semi_flex_supervisors
import com.dmb.chantiertracker.resources.billing_feature_semi_flex_videos
import com.dmb.chantiertracker.resources.billing_price_liberte_monthly
import com.dmb.chantiertracker.resources.billing_price_liberte_monthly_note
import com.dmb.chantiertracker.resources.billing_price_liberte_yearly
import com.dmb.chantiertracker.resources.billing_price_liberte_yearly_note
import com.dmb.chantiertracker.resources.billing_price_semi_flex_monthly
import com.dmb.chantiertracker.resources.billing_price_semi_flex_monthly_note
import com.dmb.chantiertracker.resources.billing_price_semi_flex_yearly
import com.dmb.chantiertracker.resources.billing_price_semi_flex_yearly_note
import org.jetbrains.compose.resources.StringResource

// Static pricing content, no server endpoint for it (mirrors the web's
// PaidPlanPricingCard SEMI_FLEX_CONFIG/LIBERTE_CONFIG — prices/features are
// hardcoded there too). Only the two paid, purchasable tiers are here; FREE
// has no checkout.
data class PricingTier(
    val plan: Plan,
    val monthlyPriceRes: StringResource,
    val monthlyNoteRes: StringResource,
    val yearlyPriceRes: StringResource,
    val yearlyNoteRes: StringResource,
    val featureRes: List<StringResource>,
)

val PRICING_TIERS: Map<Plan, PricingTier> = mapOf(
    Plan.SEMI_FLEX to PricingTier(
        plan = Plan.SEMI_FLEX,
        monthlyPriceRes = Res.string.billing_price_semi_flex_monthly,
        monthlyNoteRes = Res.string.billing_price_semi_flex_monthly_note,
        yearlyPriceRes = Res.string.billing_price_semi_flex_yearly,
        yearlyNoteRes = Res.string.billing_price_semi_flex_yearly_note,
        featureRes = listOf(
            Res.string.billing_feature_semi_flex_projects,
            Res.string.billing_feature_semi_flex_photos,
            Res.string.billing_feature_semi_flex_videos,
            Res.string.billing_feature_semi_flex_supervisors,
            Res.string.billing_feature_semi_flex_export,
            Res.string.billing_feature_semi_flex_history,
        ),
    ),
    Plan.LIBERTE to PricingTier(
        plan = Plan.LIBERTE,
        monthlyPriceRes = Res.string.billing_price_liberte_monthly,
        monthlyNoteRes = Res.string.billing_price_liberte_monthly_note,
        yearlyPriceRes = Res.string.billing_price_liberte_yearly,
        yearlyNoteRes = Res.string.billing_price_liberte_yearly_note,
        featureRes = listOf(
            Res.string.billing_feature_liberte_projects,
            Res.string.billing_feature_liberte_photos,
            Res.string.billing_feature_liberte_videos,
            Res.string.billing_feature_liberte_supervisors,
            Res.string.billing_feature_liberte_export,
            Res.string.billing_feature_liberte_history,
        ),
    ),
)
