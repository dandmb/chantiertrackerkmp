package com.dmb.chantiertracker.presentation.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.action_cancel
import com.dmb.chantiertracker.resources.logout_anyway
import com.dmb.chantiertracker.resources.logout_blocked_message
import com.dmb.chantiertracker.resources.logout_blocked_title
import com.dmb.chantiertracker.resources.logout_refused_message
import com.dmb.chantiertracker.resources.logout_refused_title
import com.dmb.chantiertracker.resources.logout_retry
import com.dmb.chantiertracker.resources.logout_sending
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// ADR-69 — a voluntary sign-out never strands writes that can still be sent (A-1, decision a).
// Blocked: offline / server down, retry once back online. RefusedWritesLeft: the sync worked and
// these still did not leave; signing out is allowed, but the user is told they will be erased if
// another account signs in on this device.
@Composable
fun LogoutPromptDialog(
    prompt: LogoutPrompt,
    onRetry: () -> Unit,
    onLogoutAnyway: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (prompt) {
        LogoutPrompt.Sending -> AlertDialog(
            onDismissRequest = {},
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text(stringResource(Res.string.logout_sending), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {},
        )
        is LogoutPrompt.Blocked -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.logout_blocked_title)) },
            text = {
                Text(
                    pluralStringResource(Res.plurals.logout_blocked_message, prompt.unsentCount, prompt.unsentCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = onRetry) { Text(stringResource(Res.string.logout_retry)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
        )
        is LogoutPrompt.RefusedWritesLeft -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.logout_refused_title)) },
            text = {
                Text(
                    pluralStringResource(Res.plurals.logout_refused_message, prompt.count, prompt.count),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onLogoutAnyway,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.logout_anyway)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}
