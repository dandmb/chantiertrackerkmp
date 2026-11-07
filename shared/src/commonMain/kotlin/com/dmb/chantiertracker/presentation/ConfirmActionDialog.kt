package com.dmb.chantiertracker.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.dmb.chantiertracker.presentation.theme.Brick
import com.dmb.chantiertracker.presentation.theme.DisabledFill
import com.dmb.chantiertracker.presentation.theme.DisabledText
import com.dmb.chantiertracker.presentation.theme.OnBrick
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
fun destructiveButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = Brick,
    contentColor = OnBrick,
    disabledContainerColor = DisabledFill,
    disabledContentColor = DisabledText,
)

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
        title = { CapitalTitle(title) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            Button(onClick = onConfirm, colors = if (destructive) destructiveButtonColors() else ButtonDefaults.buttonColors()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}
