package com.dmb.chantiertracker.presentation.billing

import androidx.compose.runtime.Composable
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.history_limit_30_days
import com.dmb.chantiertracker.resources.history_limit_6_months
import com.dmb.chantiertracker.resources.history_limit_days
import com.dmb.chantiertracker.resources.history_limit_free
import com.dmb.chantiertracker.resources.history_limit_semi_flex
import org.jetbrains.compose.resources.stringResource

private const val FREE_TIER_HISTORY_DAYS = 30
private const val SEMI_FLEX_TIER_HISTORY_DAYS = 180

/**
 * Keyed on the retention window itself, not on a plan: a founder on FREE gets
 * 180 days. `null` (unlimited, or not known yet) shows nothing. The "upgrade"
 * half of the sentence only exists while paid tiers are actually on offer.
 */
@Composable
fun historyRetentionNotice(maxHistoryDays: Int?): String? {
    val days = maxHistoryDays ?: return null
    val upgradeOffered = paidPlansAreOffered()
    return when (days) {
        FREE_TIER_HISTORY_DAYS ->
            stringResource(if (upgradeOffered) Res.string.history_limit_free else Res.string.history_limit_30_days)
        SEMI_FLEX_TIER_HISTORY_DAYS ->
            stringResource(if (upgradeOffered) Res.string.history_limit_semi_flex else Res.string.history_limit_6_months)
        else -> stringResource(Res.string.history_limit_days, days)
    }
}
