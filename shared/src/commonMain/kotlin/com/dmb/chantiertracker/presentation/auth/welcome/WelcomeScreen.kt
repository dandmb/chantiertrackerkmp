package com.dmb.chantiertracker.presentation.auth.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.BillingAvailability
import com.dmb.chantiertracker.presentation.SectionCard
import com.dmb.chantiertracker.presentation.SectionTone
import com.dmb.chantiertracker.presentation.auth.components.AuthHighlightCard
import com.dmb.chantiertracker.presentation.auth.components.AuthLegalFooter
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.auth.components.AuthScreenLayout
import com.dmb.chantiertracker.presentation.auth.components.AuthSecondaryButton
import com.dmb.chantiertracker.presentation.billing.LocalFounderOffer
import com.dmb.chantiertracker.presentation.billing.LocalServerBillingStatus
import com.dmb.chantiertracker.presentation.offer.FounderOfferFigures
import com.dmb.chantiertracker.presentation.legal.LegalDocument
import com.dmb.chantiertracker.presentation.main.PhotoCameraIcon
import com.dmb.chantiertracker.presentation.main.ProjectsIcon
import com.dmb.chantiertracker.presentation.main.ShoppingCartIcon
import com.dmb.chantiertracker.presentation.main.ConstructionIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.welcome_benefit_photos_body
import com.dmb.chantiertracker.resources.welcome_benefit_photos_title
import com.dmb.chantiertracker.resources.welcome_benefit_purchases_body
import com.dmb.chantiertracker.resources.welcome_benefit_purchases_title
import com.dmb.chantiertracker.resources.welcome_benefit_stages_body
import com.dmb.chantiertracker.resources.welcome_benefit_stages_title
import com.dmb.chantiertracker.resources.welcome_benefit_stock_body
import com.dmb.chantiertracker.resources.welcome_benefit_stock_title
import com.dmb.chantiertracker.resources.welcome_benefits_section
import com.dmb.chantiertracker.resources.welcome_create_account
import com.dmb.chantiertracker.resources.welcome_discover_plans
import com.dmb.chantiertracker.resources.welcome_discover_plans_teaser
import com.dmb.chantiertracker.resources.welcome_founder_body
import com.dmb.chantiertracker.resources.welcome_founder_title
import com.dmb.chantiertracker.resources.welcome_sign_in
import com.dmb.chantiertracker.resources.welcome_subtitle
import com.dmb.chantiertracker.resources.welcome_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// ADR-51 (auth UX audit) — the primary/secondary actions keep top billing
// (this screen's title is literally "welcome back": returning users want
// Sign in first), the plans entry follows as a genuinely highlighted block
// rather than a small text link — real tap target, filled surface, its own
// teaser copy — since it is the only pre-account path into monetization.
//
// ADR-66 — onDiscoverPlans is null whenever paid tiers are not confirmed open:
// the block is then left out entirely, not shown disabled.
@Composable
fun WelcomeScreen(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onDiscoverPlans: (() -> Unit)?,
    onOpenLegalDocument: (LegalDocument) -> Unit,
) {
    AuthScreenLayout(
        title = stringResource(Res.string.welcome_title),
        subtitle = stringResource(Res.string.welcome_subtitle),
        pinnedFooter = {
            AuthPrimaryButton(text = stringResource(Res.string.welcome_create_account), onClick = onCreateAccount)
            AuthSecondaryButton(text = stringResource(Res.string.welcome_sign_in), onClick = onSignIn)
        },
    ) {
        SectionCard(stringResource(Res.string.welcome_benefits_section)) {
            Benefit(ProjectsIcon, Res.string.welcome_benefit_stages_title, Res.string.welcome_benefit_stages_body)
            Benefit(ShoppingCartIcon, Res.string.welcome_benefit_purchases_title, Res.string.welcome_benefit_purchases_body)
            Benefit(ConstructionIcon, Res.string.welcome_benefit_stock_title, Res.string.welcome_benefit_stock_body)
            Benefit(PhotoCameraIcon, Res.string.welcome_benefit_photos_title, Res.string.welcome_benefit_photos_body)
        }

        if (LocalServerBillingStatus.current == BillingAvailability.CLOSED) {
            SectionCard(stringResource(Res.string.welcome_founder_title), tone = SectionTone.Yellow) {
                Text(stringResource(Res.string.welcome_founder_body), style = MaterialTheme.typography.bodyMedium)
                LocalFounderOffer.current?.let { FounderOfferFigures(it) }
            }
        }

        if (onDiscoverPlans != null) {
            Spacer(Modifier.height(8.dp))
            AuthHighlightCard(
                title = stringResource(Res.string.welcome_discover_plans),
                subtitle = stringResource(Res.string.welcome_discover_plans_teaser),
                onClick = onDiscoverPlans,
            )
        }

        Spacer(Modifier.height(4.dp))
        AuthLegalFooter(onOpenDocument = onOpenLegalDocument)
    }
}

@Composable
private fun Benefit(icon: ImageVector, title: StringResource, body: StringResource) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
