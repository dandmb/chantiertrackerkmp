package com.dmb.chantiertracker.presentation.auth.welcome

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.auth.components.AuthHighlightCard
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.auth.components.AuthScreenLayout
import com.dmb.chantiertracker.presentation.auth.components.AuthSecondaryButton
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.welcome_create_account
import com.dmb.chantiertracker.resources.welcome_discover_plans
import com.dmb.chantiertracker.resources.welcome_discover_plans_teaser
import com.dmb.chantiertracker.resources.welcome_sign_in
import com.dmb.chantiertracker.resources.welcome_subtitle
import com.dmb.chantiertracker.resources.welcome_title
import org.jetbrains.compose.resources.stringResource

// ADR-51 (auth UX audit) — the primary/secondary actions keep top billing
// (this screen's title is literally "welcome back": returning users want
// Sign in first), the plans entry follows as a genuinely highlighted block
// rather than a small text link — real tap target, filled surface, its own
// teaser copy — since it is the only pre-account path into monetization.
@Composable
fun WelcomeScreen(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onDiscoverPlans: () -> Unit,
) {
    AuthScreenLayout(
        title = stringResource(Res.string.welcome_title),
        subtitle = stringResource(Res.string.welcome_subtitle),
        centerContentVertically = true,
    ) {
        AuthPrimaryButton(text = stringResource(Res.string.welcome_create_account), onClick = onCreateAccount)
        AuthSecondaryButton(text = stringResource(Res.string.welcome_sign_in), onClick = onSignIn)

        Spacer(Modifier.height(8.dp))
        AuthHighlightCard(
            title = stringResource(Res.string.welcome_discover_plans),
            subtitle = stringResource(Res.string.welcome_discover_plans_teaser),
            onClick = onDiscoverPlans,
        )
    }
}
