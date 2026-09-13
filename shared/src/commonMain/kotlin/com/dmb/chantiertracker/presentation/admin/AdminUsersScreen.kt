package com.dmb.chantiertracker.presentation.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.AdminUser
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.PlanSource
import com.dmb.chantiertracker.presentation.auth.components.ErrorBanner
import com.dmb.chantiertracker.presentation.formatIsoDateTime
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.presentation.main.CreditCardIcon
import com.dmb.chantiertracker.presentation.main.DeleteIcon
import com.dmb.chantiertracker.presentation.main.EditIcon
import com.dmb.chantiertracker.presentation.main.KeyIcon
import com.dmb.chantiertracker.presentation.main.MoreVertIcon
import com.dmb.chantiertracker.presentation.main.SendIcon
import com.dmb.chantiertracker.presentation.main.labelRes
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.action_ok
import com.dmb.chantiertracker.resources.admin_users_action_assign_plan
import com.dmb.chantiertracker.resources.admin_users_action_delete
import com.dmb.chantiertracker.resources.admin_users_action_rename
import com.dmb.chantiertracker.resources.admin_users_action_resend_activation
import com.dmb.chantiertracker.resources.admin_users_action_reset_password
import com.dmb.chantiertracker.resources.admin_users_actions
import com.dmb.chantiertracker.resources.admin_users_created_at
import com.dmb.chantiertracker.resources.admin_users_empty
import com.dmb.chantiertracker.resources.admin_users_next
import com.dmb.chantiertracker.resources.admin_users_page
import com.dmb.chantiertracker.resources.admin_users_plan_expires
import com.dmb.chantiertracker.resources.admin_users_plan_source_admin_granted
import com.dmb.chantiertracker.resources.admin_users_plan_source_stripe
import com.dmb.chantiertracker.resources.admin_users_prev
import com.dmb.chantiertracker.resources.admin_users_projects_count
import com.dmb.chantiertracker.resources.admin_users_resend_activation_body
import com.dmb.chantiertracker.resources.admin_users_resend_activation_title
import com.dmb.chantiertracker.resources.admin_users_reset_password_body
import com.dmb.chantiertracker.resources.admin_users_reset_password_title
import com.dmb.chantiertracker.resources.admin_users_retry
import com.dmb.chantiertracker.resources.admin_users_role_super_admin
import com.dmb.chantiertracker.resources.admin_users_status_active
import com.dmb.chantiertracker.resources.admin_users_status_inactive
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private sealed class PendingAction {
    data class Rename(val user: AdminUser) : PendingAction()
    data class ResetPassword(val user: AdminUser) : PendingAction()
    data class ResendActivation(val user: AdminUser) : PendingAction()
    data class AssignPlan(val user: AdminUser) : PendingAction()
    data class Delete(val user: AdminUser) : PendingAction()
}

@Composable
fun AdminUsersScreen(
    modifier: Modifier = Modifier,
    viewModel: AdminUsersViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<PendingAction?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    // This screen always has a floating "create user" FAB (MainScreen.kt) —
    // Scaffold floats it over the content instead of reserving room for it in
    // the padding it hands down, so without this the pagination row's
    // right-aligned "Next" button sat directly under it, permanently
    // unreachable (found by inspection, not a snapshot: the shared harness
    // never renders this screen's own FAB alongside it in the same frame).
    // 56dp default FAB + Scaffold's 16dp margin = 72dp clearance, +16dp so
    // the row doesn't visually hug the button either.
    Column(modifier.fillMaxSize().padding(bottom = 88.dp)) {
        state.actionError?.let {
            ErrorBanner(
                message = it.localizedText(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading && state.items.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.error != null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = state.error!!.localizedText(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(onClick = viewModel::retry) {
                        Text(stringResource(Res.string.admin_users_retry))
                    }
                }

                state.items.isEmpty() -> Text(
                    text = stringResource(Res.string.admin_users_empty),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(state.items, key = AdminUser::id) { user ->
                        AdminUserRow(
                            user = user,
                            isSelf = user.id == state.currentUserId,
                            isProcessing = user.id in state.processingIds,
                            onRename = { pending = PendingAction.Rename(user) },
                            onResetPassword = { pending = PendingAction.ResetPassword(user) },
                            onResendActivation = { pending = PendingAction.ResendActivation(user) },
                            onAssignPlan = { pending = PendingAction.AssignPlan(user) },
                            onDelete = { pending = PendingAction.Delete(user) },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }

        if (state.showPagination) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = viewModel::previousPage, enabled = !state.isFirst && !state.isLoading) {
                    Text(stringResource(Res.string.admin_users_prev))
                }
                Text(
                    text = stringResource(Res.string.admin_users_page, state.page + 1, state.totalPages),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = viewModel::nextPage, enabled = !state.isLast && !state.isLoading) {
                    Text(stringResource(Res.string.admin_users_next))
                }
            }
        }
    }

    state.actionMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearActionMessage,
            title = null,
            text = { Text(message.resolveText()) },
            confirmButton = {
                TextButton(onClick = viewModel::clearActionMessage) { Text(stringResource(Res.string.action_ok)) }
            },
        )
    }

    when (val action = pending) {
        null -> Unit
        is PendingAction.Rename -> RenameAdminUserDialog(
            currentName = action.user.name,
            onDismiss = { pending = null },
            onConfirm = { newName ->
                pending = null
                viewModel.updateName(action.user.id, newName)
            },
        )
        is PendingAction.ResetPassword -> AdminConfirmActionDialog(
            title = stringResource(Res.string.admin_users_reset_password_title),
            body = stringResource(Res.string.admin_users_reset_password_body, action.user.email),
            confirmLabel = stringResource(Res.string.admin_users_action_reset_password),
            onDismiss = { pending = null },
            onConfirm = {
                pending = null
                viewModel.resetPassword(action.user.id, action.user.email)
            },
        )
        is PendingAction.ResendActivation -> AdminConfirmActionDialog(
            title = stringResource(Res.string.admin_users_resend_activation_title),
            body = stringResource(Res.string.admin_users_resend_activation_body, action.user.email),
            confirmLabel = stringResource(Res.string.admin_users_action_resend_activation),
            onDismiss = { pending = null },
            onConfirm = {
                pending = null
                viewModel.resendActivation(action.user.id, action.user.email)
            },
        )
        is PendingAction.AssignPlan -> AssignPlanDialog(
            user = action.user,
            onDismiss = { pending = null },
            onConfirm = { plan, expiresAt ->
                pending = null
                viewModel.updatePlan(action.user.id, plan, expiresAt)
            },
        )
        is PendingAction.Delete -> DeleteAdminUserDialog(
            email = action.user.email,
            onDismiss = { pending = null },
            onConfirm = {
                pending = null
                viewModel.deleteUser(action.user.id)
            },
        )
    }
}

@Composable
private fun AdminUserRow(
    user: AdminUser,
    isSelf: Boolean,
    isProcessing: Boolean,
    onRename: () -> Unit,
    onResetPassword: () -> Unit,
    onResendActivation: () -> Unit,
    onAssignPlan: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(user.email, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(user.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AdminUserActionsMenu(
                isSelf = isSelf,
                isProcessing = isProcessing,
                showResendActivation = !user.active,
                showAssignPlan = user.globalRole != GlobalRole.SUPER_ADMIN,
                onRename = onRename,
                onResetPassword = onResetPassword,
                onResendActivation = onResendActivation,
                onAssignPlan = onAssignPlan,
                onDelete = onDelete,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusPill(active = user.active)
            if (user.globalRole == GlobalRole.SUPER_ADMIN) {
                Pill(
                    text = stringResource(Res.string.admin_users_role_super_admin),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Pill(
                text = stringResource(user.plan.labelRes()),
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            user.planSource?.let { source ->
                Pill(
                    text = stringResource(
                        if (source == PlanSource.STRIPE) {
                            Res.string.admin_users_plan_source_stripe
                        } else {
                            Res.string.admin_users_plan_source_admin_granted
                        },
                    ),
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        user.planExpiresAt?.let { expiresAt ->
            Text(
                text = stringResource(Res.string.admin_users_plan_expires, formatIsoDateTime(expiresAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(Res.string.admin_users_projects_count, user.projectCount.toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.admin_users_created_at, formatIsoDateTime(user.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AdminUserActionsMenu(
    isSelf: Boolean,
    isProcessing: Boolean,
    showResendActivation: Boolean,
    showAssignPlan: Boolean,
    onRename: () -> Unit,
    onResetPassword: () -> Unit,
    onResendActivation: () -> Unit,
    onAssignPlan: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }, enabled = !isProcessing) {
            if (isProcessing) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(MoreVertIcon, contentDescription = stringResource(Res.string.admin_users_actions))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AdminUserActionMenuItems(
                isSelf = isSelf,
                showResendActivation = showResendActivation,
                showAssignPlan = showAssignPlan,
                onRename = { expanded = false; onRename() },
                onResetPassword = { expanded = false; onResetPassword() },
                onResendActivation = { expanded = false; onResendActivation() },
                onAssignPlan = { expanded = false; onAssignPlan() },
                onDelete = { expanded = false; onDelete() },
            )
        }
    }
}

// Extracted so a snapshot test can preview the menu body directly (same
// idiom as ProjectSortMenuItems/AccountMenuBody) — capturing a real
// DropdownMenu popup would need a click-then-capture step the shared
// snapshot() helper doesn't support.
@Composable
fun AdminUserActionMenuItems(
    isSelf: Boolean,
    showResendActivation: Boolean,
    showAssignPlan: Boolean,
    onRename: () -> Unit,
    onResetPassword: () -> Unit,
    onResendActivation: () -> Unit,
    onAssignPlan: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.admin_users_action_rename)) },
        leadingIcon = { Icon(EditIcon, contentDescription = null) },
        onClick = onRename,
    )
    if (showResendActivation) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.admin_users_action_resend_activation)) },
            leadingIcon = { Icon(SendIcon, contentDescription = null) },
            onClick = onResendActivation,
        )
    }
    // Hidden entirely for a SUPER_ADMIN target — mirrors the web exactly:
    // getEffectivePlan() for a SUPER_ADMIN is always LIBERTE server-side
    // regardless of this field, so changing it would have no real effect.
    if (showAssignPlan) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.admin_users_action_assign_plan)) },
            leadingIcon = { Icon(CreditCardIcon, contentDescription = null) },
            onClick = onAssignPlan,
        )
    }
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.admin_users_action_reset_password)) },
        leadingIcon = { Icon(KeyIcon, contentDescription = null) },
        onClick = onResetPassword,
    )
    // A hardcoded error-red color doesn't participate in DropdownMenuItem's
    // automatic disabled-content dimming (that only touches LocalContentColor)
    // — found by snapshot inspection: the disabled self-row still rendered
    // full-opacity red. Faded by hand to the same disabled alpha Material 3
    // uses elsewhere.
    val deleteColor = MaterialTheme.colorScheme.error.copy(alpha = if (isSelf) 0.38f else 1f)
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.admin_users_action_delete), color = deleteColor) },
        leadingIcon = { Icon(DeleteIcon, contentDescription = null, tint = deleteColor) },
        enabled = !isSelf,
        onClick = onDelete,
    )
}

@Composable
private fun StatusPill(active: Boolean) {
    Pill(
        text = stringResource(if (active) Res.string.admin_users_status_active else Res.string.admin_users_status_inactive),
        container = if (active) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
        content = if (active) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Composable
private fun Pill(text: String, container: Color, content: Color) {
    Surface(color = container, shape = RoundedCornerShape(percent = 50)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = content,
        )
    }
}
