package com.dmb.chantiertracker.presentation.offer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.PlanUsage
import com.dmb.chantiertracker.domain.model.SyncIssueTarget
import com.dmb.chantiertracker.presentation.auth.components.AuthInlineLink
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.billing.paidPlansAreOffered
import com.dmb.chantiertracker.presentation.billing.usageAtLimit
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.invite_member_limit
import com.dmb.chantiertracker.resources.invite_member_limit_no_upgrade
import com.dmb.chantiertracker.resources.plan_limit_export
import com.dmb.chantiertracker.resources.plan_limit_media
import com.dmb.chantiertracker.resources.plan_limit_see_offer
import com.dmb.chantiertracker.resources.plan_limit_usage
import com.dmb.chantiertracker.resources.plan_reminder_projects
import org.jetbrains.compose.resources.stringResource

// PLAN_LIMIT_EXCEEDED does not say which limit was hit: the action that was refused does.
enum class PlanLimitKind { PROJECTS, SUPERVISORS, MEDIA, PDF_EXPORT }

fun SyncIssueTarget.planLimitKind(): PlanLimitKind? = when (this) {
    SyncIssueTarget.PROJECT -> PlanLimitKind.PROJECTS
    SyncIssueTarget.ATTACHMENT -> PlanLimitKind.MEDIA
    else -> null
}

// The figures are the caller's own: on someone else's project the owner's limit was hit, and only
// a counter really at its limit can be the one that refused.
fun PlanUsage.reachedCount(kind: PlanLimitKind): Pair<Int, Int>? {
    val counters = when (kind) {
        PlanLimitKind.PROJECTS -> listOf(projectsUsed to projectsLimit)
        PlanLimitKind.SUPERVISORS -> listOf(supervisorsUsed to supervisorsLimit)
        PlanLimitKind.MEDIA -> listOf(photosUsed to photosLimit, videosUsed to videosLimit)
        PlanLimitKind.PDF_EXPORT -> emptyList()
    }
    return counters.firstNotNullOfOrNull { (used, limit) -> limit?.takeIf { usageAtLimit(used, it) }?.let { used to it } }
}

val LocalPlanUsage = compositionLocalOf<PlanUsage?> { null }

val LocalOpenMyOffer = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
fun PlanLimitNotice(kind: PlanLimitKind, modifier: Modifier = Modifier) {
    val sentence = when (kind) {
        PlanLimitKind.PROJECTS -> DomainException.PlanLimitReached.localizedText()
        PlanLimitKind.SUPERVISORS -> stringResource(
            if (paidPlansAreOffered()) Res.string.invite_member_limit else Res.string.invite_member_limit_no_upgrade,
        )
        PlanLimitKind.MEDIA -> stringResource(Res.string.plan_limit_media)
        PlanLimitKind.PDF_EXPORT -> stringResource(Res.string.plan_limit_export)
    }
    val usage = LocalPlanUsage.current?.reachedCount(kind)?.let { (used, limit) -> stringResource(Res.string.plan_limit_usage, used, limit) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ErrorBanner(listOfNotNull(sentence, usage).joinToString(" "))
        SeeMyOfferLink()
    }
}

@Composable
fun PlanLimitDetails(kind: PlanLimitKind) {
    val usage = LocalPlanUsage.current?.reachedCount(kind)?.let { (used, limit) -> stringResource(Res.string.plan_limit_usage, used, limit) }
    if (usage != null) {
        Text(usage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SeeMyOfferLink()
}

@Composable
fun SeeMyOfferLink() {
    LocalOpenMyOffer.current?.let { open -> AuthInlineLink(stringResource(Res.string.plan_limit_see_offer), open) }
}

@Composable
fun ProjectLimitReminder() {
    val usage = LocalPlanUsage.current ?: return
    val limit = usage.projectsLimit ?: return
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.plan_reminder_projects, usage.projectsUsed, limit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SeeMyOfferLink()
    }
}
