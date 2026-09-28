package com.dmb.chantiertracker.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.action_cancel
import org.jetbrains.compose.resources.stringResource

// General-purpose "are you sure?" dialog for actions that don't need the
// retype-to-confirm friction of DeleteProjectDialog/DeleteAdminUserDialog
// (those stay dedicated: they carry their own typed-name state). This one
// covers the simpler, one-tap-to-confirm case — a single AlertDialog with an
// error-colored confirm button when the action is destructive (data lost for
// good), a plain one otherwise (e.g. cancelling an invitation is undoable by
// just re-inviting).
@Composable
fun ConfirmActionDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
