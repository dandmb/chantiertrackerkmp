package com.dmb.chantiertracker.presentation.main

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.dmb.chantiertracker.presentation.CapitalTitle
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.exit_confirm
import com.dmb.chantiertracker.resources.exit_stay
import com.dmb.chantiertracker.resources.exit_title
import com.dmb.chantiertracker.resources.exit_unsent
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ExitAppDialog(unsentChanges: Int, onExit: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { CapitalTitle(stringResource(Res.string.exit_title)) },
        text = if (unsentChanges > 0) {
            { Text(pluralStringResource(Res.plurals.exit_unsent, unsentChanges, unsentChanges), style = MaterialTheme.typography.bodyMedium) }
        } else {
            null
        },
        confirmButton = { TextButton(onClick = onExit) { Text(stringResource(Res.string.exit_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.exit_stay)) } },
    )
}
