package com.dmb.chantiertracker.presentation.invitations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.presentation.ResponsiveContent
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.presentation.main.CheckIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.invitation_accept_error_title
import com.dmb.chantiertracker.resources.invitation_accept_go_to_project
import com.dmb.chantiertracker.resources.invitation_accept_go_to_projects
import com.dmb.chantiertracker.resources.invitation_accept_invalid_body
import com.dmb.chantiertracker.resources.invitation_accept_invalid_title
import com.dmb.chantiertracker.resources.invitation_accept_retry
import com.dmb.chantiertracker.resources.invitation_accept_welcome
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun InvitationAcceptScreen(
    token: String,
    onAccepted: (projectLocalId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InvitationAcceptViewModel = koinViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()

    LaunchedEffect(token) { viewModel.load(token) }

    ResponsiveContent(modifier, maxContentWidth = 480.dp) {
        Box(Modifier.fillMaxSize()) {
            when (val current = status) {
                InvitationAcceptStatus.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is InvitationAcceptStatus.Accepted -> ResultColumn {
                    Icon(
                        CheckIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = stringResource(Res.string.invitation_accept_welcome, current.projectName),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    AuthPrimaryButton(
                        text = stringResource(
                            if (current.projectLocalId != null) {
                                Res.string.invitation_accept_go_to_project
                            } else {
                                Res.string.invitation_accept_go_to_projects
                            },
                        ),
                        onClick = { onAccepted(current.projectLocalId) },
                    )
                }
                InvitationAcceptStatus.InvalidLink -> ResultColumn {
                    Text(
                        text = stringResource(Res.string.invitation_accept_invalid_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(Res.string.invitation_accept_invalid_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    AuthPrimaryButton(
                        text = stringResource(Res.string.invitation_accept_go_to_projects),
                        onClick = { onAccepted(null) },
                    )
                }
                is InvitationAcceptStatus.Failed -> ResultColumn {
                    Text(
                        text = stringResource(Res.string.invitation_accept_error_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = current.error.localizedText(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    AuthPrimaryButton(
                        text = stringResource(Res.string.invitation_accept_retry),
                        onClick = viewModel::retry,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ResultColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
    }
}
