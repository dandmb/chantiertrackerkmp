package com.dmb.chantiertracker.presentation.auth.welcome

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.dmb.chantiertracker.presentation.auth.components.AuthHighlightCard
import com.dmb.chantiertracker.presentation.auth.components.AuthLegalFooter
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.auth.components.AuthScreenLayout
import com.dmb.chantiertracker.presentation.auth.components.AuthSecondaryButton
import com.dmb.chantiertracker.presentation.legal.LegalDocument
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.presentation_open
import com.dmb.chantiertracker.resources.welcome_create_account
import com.dmb.chantiertracker.resources.welcome_discover_plans
import com.dmb.chantiertracker.resources.welcome_discover_plans_teaser
import com.dmb.chantiertracker.resources.welcome_sign_in
import com.dmb.chantiertracker.resources.welcome_subtitle
import com.dmb.chantiertracker.resources.welcome_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun WelcomeScreen(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onDiscoverPlans: (() -> Unit)?,
    onOpenLegalDocument: (LegalDocument) -> Unit,
    presentationOpenAtStart: Boolean = false,
) {
    var presentationOpen by rememberSaveable { mutableStateOf(presentationOpenAtStart) }

    AuthScreenLayout(
        title = stringResource(Res.string.welcome_title),
        subtitle = stringResource(Res.string.welcome_subtitle),
        pinnedFooter = {
            AuthPrimaryButton(text = stringResource(Res.string.welcome_create_account), onClick = onCreateAccount)
            AuthSecondaryButton(text = stringResource(Res.string.welcome_sign_in), onClick = onSignIn)
            AuthSecondaryButton(
                text = stringResource(Res.string.presentation_open),
                onClick = { presentationOpen = true },
                contentColor = MaterialTheme.colorScheme.onBackground,
            )
            AuthLegalFooter(onOpenDocument = onOpenLegalDocument)
        },
    ) {
        if (onDiscoverPlans != null) {
            AuthHighlightCard(
                title = stringResource(Res.string.welcome_discover_plans),
                subtitle = stringResource(Res.string.welcome_discover_plans_teaser),
                onClick = onDiscoverPlans,
            )
        }
    }

    if (presentationOpen) {
        ProductPresentationSheet(onDismiss = { presentationOpen = false })
    }
}
