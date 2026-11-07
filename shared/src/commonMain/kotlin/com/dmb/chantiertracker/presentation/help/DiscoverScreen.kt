package com.dmb.chantiertracker.presentation.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.ResponsiveContent
import com.dmb.chantiertracker.presentation.SectionCard
import com.dmb.chantiertracker.presentation.billing.LocalFounderOffer
import com.dmb.chantiertracker.presentation.offer.historyKeptLabel
import com.dmb.chantiertracker.presentation.main.AccountIcon
import com.dmb.chantiertracker.presentation.main.CalendarIcon
import com.dmb.chantiertracker.presentation.main.ConstructionIcon
import com.dmb.chantiertracker.presentation.main.DownloadIcon
import com.dmb.chantiertracker.presentation.main.FlagIcon
import com.dmb.chantiertracker.presentation.main.PhotoCameraIcon
import com.dmb.chantiertracker.presentation.main.ProjectsIcon
import com.dmb.chantiertracker.presentation.main.ShoppingCartIcon
import com.dmb.chantiertracker.presentation.main.SyncIssuesIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.discover_at_office
import com.dmb.chantiertracker.resources.discover_days_body
import com.dmb.chantiertracker.resources.discover_days_section
import com.dmb.chantiertracker.resources.discover_export_body
import com.dmb.chantiertracker.resources.discover_export_section
import com.dmb.chantiertracker.resources.discover_history_body
import com.dmb.chantiertracker.resources.discover_history_section
import com.dmb.chantiertracker.resources.discover_intro_body
import com.dmb.chantiertracker.resources.discover_intro_section
import com.dmb.chantiertracker.resources.discover_media_body
import com.dmb.chantiertracker.resources.discover_media_section
import com.dmb.chantiertracker.resources.discover_offline_body
import com.dmb.chantiertracker.resources.discover_offline_section
import com.dmb.chantiertracker.resources.discover_on_site
import com.dmb.chantiertracker.resources.discover_stages_body
import com.dmb.chantiertracker.resources.discover_stages_section
import com.dmb.chantiertracker.resources.discover_stock_body
import com.dmb.chantiertracker.resources.discover_stock_section
import com.dmb.chantiertracker.resources.discover_team_body
import com.dmb.chantiertracker.resources.discover_team_section
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun DiscoverScreen(modifier: Modifier = Modifier) {
    ResponsiveContent(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SectionCard(stringResource(Res.string.discover_intro_section), icon = ConstructionIcon) {
                Body(Res.string.discover_intro_body)
                Body(Res.string.discover_on_site)
                Body(Res.string.discover_at_office)
            }
            Feature(Res.string.discover_stages_section, Res.string.discover_stages_body, ProjectsIcon)
            Feature(Res.string.discover_days_section, Res.string.discover_days_body, CalendarIcon)
            Feature(Res.string.discover_stock_section, Res.string.discover_stock_body, ShoppingCartIcon)
            Feature(Res.string.discover_media_section, Res.string.discover_media_body, PhotoCameraIcon)
            Feature(Res.string.discover_team_section, Res.string.discover_team_body, AccountIcon)
            Feature(Res.string.discover_offline_section, Res.string.discover_offline_body, SyncIssuesIcon)
            SectionCard(stringResource(Res.string.discover_history_section), icon = FlagIcon) {
                Body(Res.string.discover_history_body)
                LocalFounderOffer.current?.historyDays?.let {
                    Text(historyKeptLabel(it), style = MaterialTheme.typography.bodySmall)
                }
            }
            Feature(Res.string.discover_export_section, Res.string.discover_export_body, DownloadIcon)
        }
    }
}

@Composable
private fun Feature(title: StringResource, body: StringResource, icon: ImageVector) {
    SectionCard(stringResource(title), icon = icon) { Body(body) }
}

@Composable
private fun Body(text: StringResource) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
}
