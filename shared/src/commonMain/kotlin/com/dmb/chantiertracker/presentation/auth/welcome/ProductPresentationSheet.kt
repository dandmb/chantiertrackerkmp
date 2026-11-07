package com.dmb.chantiertracker.presentation.auth.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.domain.model.BillingAvailability
import com.dmb.chantiertracker.presentation.CapitalTitle
import com.dmb.chantiertracker.presentation.SectionCard
import com.dmb.chantiertracker.presentation.SectionTone
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.billing.LocalFounderOffer
import com.dmb.chantiertracker.presentation.billing.LocalServerBillingStatus
import com.dmb.chantiertracker.presentation.main.ConstructionIcon
import com.dmb.chantiertracker.presentation.main.PhotoCameraIcon
import com.dmb.chantiertracker.presentation.main.ProjectsIcon
import com.dmb.chantiertracker.presentation.main.ShoppingCartIcon
import com.dmb.chantiertracker.presentation.main.CheckIcon
import com.dmb.chantiertracker.presentation.offer.FounderOfferFigures
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.presentation_close
import com.dmb.chantiertracker.resources.presentation_offline
import com.dmb.chantiertracker.resources.welcome_benefit_photos_body
import com.dmb.chantiertracker.resources.welcome_benefit_photos_title
import com.dmb.chantiertracker.resources.welcome_benefit_purchases_body
import com.dmb.chantiertracker.resources.welcome_benefit_purchases_title
import com.dmb.chantiertracker.resources.welcome_benefit_stages_body
import com.dmb.chantiertracker.resources.welcome_benefit_stages_title
import com.dmb.chantiertracker.resources.welcome_benefit_stock_body
import com.dmb.chantiertracker.resources.welcome_benefit_stock_title
import com.dmb.chantiertracker.resources.welcome_benefits_section
import com.dmb.chantiertracker.resources.welcome_founder_body
import com.dmb.chantiertracker.resources.welcome_founder_title
import com.dmb.chantiertracker.resources.welcome_subtitle
import com.dmb.chantiertracker.resources.welcome_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductPresentationSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ProductPresentationContent(
            onClose = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            },
        )
    }
}

@Composable
fun ProductPresentationContent(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CapitalTitle(
            text = stringResource(Res.string.welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            isHeading = true,
        )
        Text(
            stringResource(Res.string.welcome_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionCard(stringResource(Res.string.welcome_benefits_section)) {
            Benefit(ProjectsIcon, Res.string.welcome_benefit_stages_title, Res.string.welcome_benefit_stages_body)
            Benefit(ShoppingCartIcon, Res.string.welcome_benefit_purchases_title, Res.string.welcome_benefit_purchases_body)
            Benefit(ConstructionIcon, Res.string.welcome_benefit_stock_title, Res.string.welcome_benefit_stock_body)
            Benefit(PhotoCameraIcon, Res.string.welcome_benefit_photos_title, Res.string.welcome_benefit_photos_body)
        }

        if (LocalServerBillingStatus.current == BillingAvailability.CLOSED) {
            SectionCard(stringResource(Res.string.welcome_founder_title), tone = SectionTone.Orange) {
                Text(stringResource(Res.string.welcome_founder_body), style = MaterialTheme.typography.bodyMedium)
                LocalFounderOffer.current?.let { FounderOfferFigures(it) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(CheckIcon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(Res.string.presentation_offline), style = MaterialTheme.typography.bodyMedium)
        }

        AuthPrimaryButton(text = stringResource(Res.string.presentation_close), onClick = onClose)
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
