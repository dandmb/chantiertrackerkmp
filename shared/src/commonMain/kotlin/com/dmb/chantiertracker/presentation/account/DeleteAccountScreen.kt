package com.dmb.chantiertracker.presentation.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.AccountDeletionPreview
import com.dmb.chantiertracker.domain.model.DeletionBlocker
import com.dmb.chantiertracker.domain.model.MemberLosingAccess
import com.dmb.chantiertracker.domain.model.ProjectRole
import com.dmb.chantiertracker.presentation.AppOutlinedButton
import com.dmb.chantiertracker.presentation.DetailSection
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.billing.LocalInAppPaymentsAllowed
import com.dmb.chantiertracker.presentation.auth.components.PasswordField
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.action_cancel
import com.dmb.chantiertracker.resources.delete_account_blocked_title
import com.dmb.chantiertracker.resources.delete_account_confirm_title
import com.dmb.chantiertracker.resources.delete_account_contributions
import com.dmb.chantiertracker.resources.delete_account_founder
import com.dmb.chantiertracker.resources.delete_account_good_to_know
import com.dmb.chantiertracker.resources.delete_account_invitations
import com.dmb.chantiertracker.resources.delete_account_left_intro
import com.dmb.chantiertracker.resources.delete_account_left_title
import com.dmb.chantiertracker.resources.delete_account_list_and
import com.dmb.chantiertracker.resources.delete_account_lost_access
import com.dmb.chantiertracker.resources.delete_account_manage_subscription
import com.dmb.chantiertracker.resources.delete_account_manage_users
import com.dmb.chantiertracker.resources.delete_account_member_with_role
import com.dmb.chantiertracker.resources.delete_account_no_project
import com.dmb.chantiertracker.resources.delete_account_offline
import com.dmb.chantiertracker.resources.delete_account_password_prompt
import com.dmb.chantiertracker.resources.delete_account_projects_intro
import com.dmb.chantiertracker.resources.delete_account_projects_title
import com.dmb.chantiertracker.resources.delete_account_role_admin
import com.dmb.chantiertracker.resources.delete_account_role_supervisor
import com.dmb.chantiertracker.resources.delete_account_sessions
import com.dmb.chantiertracker.resources.delete_account_submit
import com.dmb.chantiertracker.resources.delete_account_submitting
import com.dmb.chantiertracker.resources.delete_account_understood
import com.dmb.chantiertracker.resources.delete_account_wrong_password
import com.dmb.chantiertracker.resources.error_deletion_last_super_admin
import com.dmb.chantiertracker.resources.error_deletion_subscription_renewing
import com.dmb.chantiertracker.resources.error_deletion_subscription_renewing_outside_app
import com.dmb.chantiertracker.resources.projects_retry
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DeleteAccountScreen(
    onCancel: () -> Unit,
    onManageUsers: () -> Unit,
    onManageSubscription: () -> Unit,
    viewModel: DeleteAccountViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (!state.online) ErrorBanner(stringResource(Res.string.delete_account_offline))
            val preview = state.preview
            when {
                preview != null -> {
                    if (preview.blockers.isNotEmpty()) Blockers(preview.blockers, onManageUsers, onManageSubscription)
                    PreviewSections(preview)
                    if (preview.canDelete) Confirmation(state, viewModel, onCancel)
                }
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                state.loadError != null -> {
                    ErrorBanner(state.loadError!!.localizedText())
                    AppOutlinedButton(onClick = viewModel::load, enabled = state.online) { Text(stringResource(Res.string.projects_retry)) }
                }
            }
        }
    }
}

@Composable
private fun Blockers(blockers: List<DeletionBlocker>, onManageUsers: () -> Unit, onManageSubscription: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(Res.string.delete_account_blocked_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            val paymentsAllowed = LocalInAppPaymentsAllowed.current
            blockers.forEach { blocker ->
                val reason = when (blocker) {
                    DeletionBlocker.LAST_SUPER_ADMIN -> Res.string.error_deletion_last_super_admin
                    DeletionBlocker.SUBSCRIPTION_STILL_RENEWING ->
                        if (paymentsAllowed) Res.string.error_deletion_subscription_renewing else Res.string.error_deletion_subscription_renewing_outside_app
                }
                Text(
                    stringResource(reason),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                when (blocker) {
                    DeletionBlocker.LAST_SUPER_ADMIN -> AppOutlinedButton(onClick = onManageUsers) {
                        Text(stringResource(Res.string.delete_account_manage_users))
                    }
                    DeletionBlocker.SUBSCRIPTION_STILL_RENEWING -> if (paymentsAllowed) {
                        AppOutlinedButton(onClick = onManageSubscription) {
                            Text(stringResource(Res.string.delete_account_manage_subscription))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewSections(preview: AccountDeletionPreview) {
    DetailSection(title = stringResource(Res.string.delete_account_projects_title)) {
        if (preview.ownedProjects.isEmpty()) {
            Text(stringResource(Res.string.delete_account_no_project), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(stringResource(Res.string.delete_account_projects_intro), style = MaterialTheme.typography.bodyMedium)
            preview.ownedProjects.forEach { project ->
                Column {
                    Text("• ${project.name}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    if (project.otherMembers.isNotEmpty()) {
                        Text(
                            lostAccessSentence(project.otherMembers),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
    }
    if (preview.memberships.isNotEmpty()) {
        DetailSection(title = stringResource(Res.string.delete_account_left_title)) {
            Text(stringResource(Res.string.delete_account_left_intro), style = MaterialTheme.typography.bodyMedium)
            preview.memberships.forEach { membership ->
                Text(
                    "• " + stringResource(Res.string.delete_account_member_with_role, membership.name, roleWord(membership.role)),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
    DetailSection(title = stringResource(Res.string.delete_account_good_to_know)) {
        if (preview.pendingInvitations > 0) {
            val count = preview.pendingInvitations.toInt()
            Text("• " + pluralStringResource(Res.plurals.delete_account_invitations, count, count), style = MaterialTheme.typography.bodyMedium)
        }
        if (preview.founder) Text("• " + stringResource(Res.string.delete_account_founder), style = MaterialTheme.typography.bodyMedium)
        Text("• " + stringResource(Res.string.delete_account_contributions), style = MaterialTheme.typography.bodyMedium)
        Text("• " + stringResource(Res.string.delete_account_sessions), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Confirmation(state: DeleteAccountUiState, viewModel: DeleteAccountViewModel, onCancel: () -> Unit) {
    DetailSection(title = stringResource(Res.string.delete_account_confirm_title)) {
        state.formError?.let { ErrorBanner(it.localizedText()) }
        Text(stringResource(Res.string.delete_account_password_prompt), style = MaterialTheme.typography.bodyMedium)
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            error = if (state.wrongPassword) stringResource(Res.string.delete_account_wrong_password) else null,
            enabled = !state.submitting,
            imeAction = ImeAction.Done,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = state.understood, enabled = !state.submitting, role = Role.Checkbox, onValueChange = viewModel::onUnderstoodChange),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(checked = state.understood, onCheckedChange = null, modifier = Modifier.padding(top = 12.dp, end = 12.dp))
            Text(stringResource(Res.string.delete_account_understood), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(if (state.submitting) Res.string.delete_account_submitting else Res.string.delete_account_submit))
            }
            AppOutlinedButton(onClick = onCancel, enabled = !state.submitting) { Text(stringResource(Res.string.action_cancel)) }
        }
    }
}

@Composable
private fun roleWord(role: ProjectRole): String = stringResource(
    if (role == ProjectRole.ADMIN) Res.string.delete_account_role_admin else Res.string.delete_account_role_supervisor,
)

// « Yann Leroy (superviseur) et Léa Martin (administrateur) perdront l'accès. »
@Composable
private fun lostAccessSentence(members: List<MemberLosingAccess>): String {
    val names = members.map { stringResource(Res.string.delete_account_member_with_role, it.name, roleWord(it.role)) }
    val list = if (names.size == 1) names.single() else stringResource(Res.string.delete_account_list_and, names.dropLast(1).joinToString(", "), names.last())
    return pluralStringResource(Res.plurals.delete_account_lost_access, members.size, list)
}
