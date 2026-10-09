package com.dmb.chantiertracker.presentation.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.dmb.chantiertracker.resources.sync_issues_open
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.dmb.chantiertracker.resources.unsent_attachments
import com.dmb.chantiertracker.resources.unsent_lines
import com.dmb.chantiertracker.resources.unsent_entries
import com.dmb.chantiertracker.resources.unsent_materials
import com.dmb.chantiertracker.resources.unsent_stages
import com.dmb.chantiertracker.resources.unsent_projects
import com.dmb.chantiertracker.resources.list_separator
import com.dmb.chantiertracker.resources.list_last_separator
import com.dmb.chantiertracker.domain.model.UnsentWrites

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
    onSeeDetails: () -> Unit = {},
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
                    stringResource(Res.string.logout_blocked_message, unsentSummary(prompt.unsent)),
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
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(Res.string.logout_refused_message, unsentSummary(prompt.unsent)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = onSeeDetails,
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) { Text(stringResource(Res.string.sync_issues_open)) }
                }
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

@Composable
fun unsentSummary(unsent: UnsentWrites): String {
    val parts = listOf(
        Res.plurals.unsent_projects to unsent.projects,
        Res.plurals.unsent_stages to unsent.stages,
        Res.plurals.unsent_materials to unsent.materials,
        Res.plurals.unsent_entries to unsent.entries,
        Res.plurals.unsent_lines to unsent.lines,
        Res.plurals.unsent_attachments to unsent.attachments,
    ).filter { (_, count) -> count > 0 }.map { (plural, count) -> pluralStringResource(plural, count, count) }
    if (parts.size < 2) return parts.joinToString()
    val separator = stringResource(Res.string.list_separator) + " "
    return parts.dropLast(1).joinToString(separator) + " " + stringResource(Res.string.list_last_separator) + " " + parts.last()
}

