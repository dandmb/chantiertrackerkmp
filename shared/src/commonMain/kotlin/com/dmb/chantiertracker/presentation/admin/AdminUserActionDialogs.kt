package com.dmb.chantiertracker.presentation.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.action_cancel
import com.dmb.chantiertracker.resources.action_save
import com.dmb.chantiertracker.resources.admin_users_activation_resent
import com.dmb.chantiertracker.resources.admin_users_delete_confirm_button
import com.dmb.chantiertracker.resources.admin_users_delete_confirm_prompt
import com.dmb.chantiertracker.resources.admin_users_delete_title
import com.dmb.chantiertracker.resources.admin_users_delete_warning
import com.dmb.chantiertracker.resources.admin_users_rename_title
import com.dmb.chantiertracker.resources.admin_users_reset_password_sent
import com.dmb.chantiertracker.resources.field_email_label
import com.dmb.chantiertracker.resources.field_name_label
import org.jetbrains.compose.resources.stringResource

@Composable
fun RenameAdminUserDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.admin_users_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.field_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank() && name.trim() != currentName,
            ) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

// Same retype-to-confirm pattern as DeleteProjectDialog, keyed by email
// instead of a project name — there is no server-side guard here at all
// (verified against AdminUserService.deleteUser), so this UI friction is the
// only thing standing between a super-admin and an irreversible delete.
@Composable
fun DeleteAdminUserDialog(email: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val matches = typed == email

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.admin_users_delete_title, email)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(Res.string.admin_users_delete_warning),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(Res.string.admin_users_delete_confirm_prompt, email),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(Res.string.field_email_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = matches,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(Res.string.admin_users_delete_confirm_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

// Shared by reset-password and resend-activation — both are a plain
// "confirm, then fire" action with no input, only the wording differs.
@Composable
fun AdminConfirmActionDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

// Reset-password and resend-activation change nothing visible in the row
// (mustChangePassword/verification state aren't part of AdminUser) — unlike
// rename (row updates in place) or delete (row disappears), these two need
// their own explicit confirmation, or a super-admin has no way to tell the
// tap actually did anything.
@Composable
fun AdminUserActionMessage.resolveText(): String = when (this) {
    is AdminUserActionMessage.PasswordResetSent -> stringResource(Res.string.admin_users_reset_password_sent, email)
    is AdminUserActionMessage.ActivationResent -> stringResource(Res.string.admin_users_activation_resent, email)
}
