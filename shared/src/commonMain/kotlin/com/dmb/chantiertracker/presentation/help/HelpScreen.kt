package com.dmb.chantiertracker.presentation.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.presentation.ClickableListRow
import com.dmb.chantiertracker.presentation.DetailInfoRow
import com.dmb.chantiertracker.presentation.ResponsiveContent
import com.dmb.chantiertracker.presentation.SectionCard
import com.dmb.chantiertracker.presentation.SectionPrimaryButton
import com.dmb.chantiertracker.presentation.SectionTone
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.main.SendIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.settings_contact
import com.dmb.chantiertracker.resources.help_a_admin
import com.dmb.chantiertracker.resources.help_a_delete
import com.dmb.chantiertracker.resources.help_a_export
import com.dmb.chantiertracker.resources.help_a_invite
import com.dmb.chantiertracker.resources.help_a_offline
import com.dmb.chantiertracker.resources.help_a_owner
import com.dmb.chantiertracker.resources.help_a_photos
import com.dmb.chantiertracker.resources.help_a_supervisor
import com.dmb.chantiertracker.resources.help_a_sync_issue
import com.dmb.chantiertracker.resources.help_a_videos
import com.dmb.chantiertracker.resources.help_contact_section
import com.dmb.chantiertracker.resources.help_discover
import com.dmb.chantiertracker.resources.help_faq_section_account
import com.dmb.chantiertracker.resources.help_faq_section_export
import com.dmb.chantiertracker.resources.help_faq_section_invite
import com.dmb.chantiertracker.resources.help_faq_section_media
import com.dmb.chantiertracker.resources.help_faq_section_offline
import com.dmb.chantiertracker.resources.help_faq_section_roles
import com.dmb.chantiertracker.resources.help_intro_section
import com.dmb.chantiertracker.resources.help_mail_body
import com.dmb.chantiertracker.resources.help_mail_subject
import com.dmb.chantiertracker.resources.help_no_mail_app
import com.dmb.chantiertracker.resources.help_q_admin
import com.dmb.chantiertracker.resources.help_q_delete
import com.dmb.chantiertracker.resources.help_q_export
import com.dmb.chantiertracker.resources.help_q_invite
import com.dmb.chantiertracker.resources.help_q_offline
import com.dmb.chantiertracker.resources.help_q_owner
import com.dmb.chantiertracker.resources.help_q_photos
import com.dmb.chantiertracker.resources.help_q_supervisor
import com.dmb.chantiertracker.resources.help_q_sync_issue
import com.dmb.chantiertracker.resources.help_q_videos
import com.dmb.chantiertracker.resources.help_report
import com.dmb.chantiertracker.resources.help_report_hint
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HelpScreen(
    onDiscover: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HelpViewModel = koinViewModel(),
) {
    val noMailApp by viewModel.noMailApp.collectAsStateWithLifecycle()
    val supportEmail by viewModel.supportEmail.collectAsStateWithLifecycle()
    val subject = stringResource(Res.string.help_mail_subject)
    val body = stringResource(Res.string.help_mail_body, viewModel.appVersion, viewModel.deviceModel, viewModel.osVersion)

    ResponsiveContent(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SectionCard(stringResource(Res.string.help_intro_section), tone = SectionTone.Yellow) {
                ClickableListRow(onClick = onDiscover) {
                    Text(stringResource(Res.string.help_discover), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
            FaqSection(
                Res.string.help_faq_section_offline,
                Res.string.help_q_offline to Res.string.help_a_offline,
                Res.string.help_q_sync_issue to Res.string.help_a_sync_issue,
            )
            FaqSection(
                Res.string.help_faq_section_roles,
                Res.string.help_q_owner to Res.string.help_a_owner,
                Res.string.help_q_admin to Res.string.help_a_admin,
                Res.string.help_q_supervisor to Res.string.help_a_supervisor,
            )
            FaqSection(
                Res.string.help_faq_section_invite,
                Res.string.help_q_invite to Res.string.help_a_invite,
            )
            FaqSection(
                Res.string.help_faq_section_export,
                Res.string.help_q_export to Res.string.help_a_export,
            )
            FaqSection(
                Res.string.help_faq_section_media,
                Res.string.help_q_photos to Res.string.help_a_photos,
                Res.string.help_q_videos to Res.string.help_a_videos,
            )
            FaqSection(
                Res.string.help_faq_section_account,
                Res.string.help_q_delete to Res.string.help_a_delete,
            )
            supportEmail?.let { address ->
                SectionCard(stringResource(Res.string.help_contact_section)) {
                    if (noMailApp) ErrorBanner(stringResource(Res.string.help_no_mail_app, address))
                    Text(
                        stringResource(Res.string.help_report_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DetailInfoRow(stringResource(Res.string.settings_contact), address)
                    SectionPrimaryButton(
                        text = stringResource(Res.string.help_report),
                        onClick = { viewModel.report(subject, body) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = SendIcon,
                    )
                }
            }
        }
    }
}

@Composable
private fun FaqSection(title: StringResource, vararg entries: Pair<StringResource, StringResource>) {
    SectionCard(stringResource(title)) {
        entries.forEach { (question, answer) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(question), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(stringResource(answer), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
