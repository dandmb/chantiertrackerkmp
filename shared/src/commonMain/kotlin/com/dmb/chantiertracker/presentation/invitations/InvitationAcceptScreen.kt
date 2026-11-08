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
import com.dmb.chantiertracker.presentation.CapitalTitle
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.presentation.ConfirmActionDialog
import com.dmb.chantiertracker.presentation.auth.components.AuthSecondaryButton
import com.dmb.chantiertracker.resources.incoming_invitation_decline_confirm_body
import com.dmb.chantiertracker.resources.incoming_invitation_decline_confirm_title
import com.dmb.chantiertracker.resources.invitation_declined_body
import com.dmb.chantiertracker.resources.invitation_declined_title
import com.dmb.chantiertracker.resources.invitation_join_button
import com.dmb.chantiertracker.resources.invitation_join_decline
import com.dmb.chantiertracker.resources.invitation_join_intro
import com.dmb.chantiertracker.resources.invitation_join_invited_by
import com.dmb.chantiertracker.resources.invitation_join_role
import com.dmb.chantiertracker.resources.role_admin
import com.dmb.chantiertracker.resources.role_supervisor
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

    var confirmDecline by remember { mutableStateOf(false) }

    LaunchedEffect(token) { viewModel.load(token) }

    val readyForDecline = status as? InvitationAcceptStatus.Ready
    if (confirmDecline && readyForDecline != null) {
        ConfirmActionDialog(
            title = stringResource(Res.string.incoming_invitation_decline_confirm_title),
            body = stringResource(Res.string.incoming_invitation_decline_confirm_body, readyForDecline.projectName),
            confirmLabel = stringResource(Res.string.invitation_join_decline),
            destructive = false,
            onDismiss = { confirmDecline = false },
            onConfirm = {
                confirmDecline = false
                viewModel.decline()
            },
        )
    }

    ResponsiveContent(modifier, maxContentWidth = 480.dp) {
        Box(Modifier.fillMaxSize()) {
            when (val current = status) {
                InvitationAcceptStatus.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is InvitationAcceptStatus.Ready -> ReadyColumn(
                    ready = current,
                    onJoin = viewModel::join,
                    onDecline = { confirmDecline = true },
                )
                InvitationAcceptStatus.Declined -> ResultColumn {
                    CapitalTitle(
                        text = stringResource(Res.string.invitation_declined_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(Res.string.invitation_declined_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    AuthPrimaryButton(
                        text = stringResource(Res.string.invitation_accept_go_to_projects),
                        onClick = { onAccepted(null) },
                    )
                }
                is InvitationAcceptStatus.Accepted -> ResultColumn {
                    Icon(
                        CheckIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    CapitalTitle(
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
                    CapitalTitle(
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
                    CapitalTitle(
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
private fun BoxScope.ReadyColumn(
    ready: InvitationAcceptStatus.Ready,
    onJoin: () -> Unit,
    onDecline: () -> Unit,
) {
    Column(
        modifier = Modifier.align(Alignment.Center).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.invitation_join_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        CapitalTitle(
            text = ready.projectName,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        ready.invitedByName?.let {
            Text(
                text = stringResource(Res.string.invitation_join_invited_by, it),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        ready.role?.roleLabel()?.let {
            Text(
                text = stringResource(Res.string.invitation_join_role, it),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(12.dp))
        AuthPrimaryButton(
            text = stringResource(Res.string.invitation_join_button),
            onClick = onJoin,
            loading = ready.working,
        )
        AuthSecondaryButton(
            text = stringResource(Res.string.invitation_join_decline),
            onClick = onDecline,
            enabled = !ready.working,
        )
    }
}

@Composable
private fun ProjectRole.roleLabel(): String? = when (this) {
    ProjectRole.ADMIN -> stringResource(Res.string.role_admin)
    ProjectRole.SUPERVISOR -> stringResource(Res.string.role_supervisor)
    ProjectRole.UNKNOWN -> null
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
