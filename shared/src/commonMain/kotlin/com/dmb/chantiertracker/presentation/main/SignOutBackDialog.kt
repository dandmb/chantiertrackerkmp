package com.dmb.chantiertracker.presentation.main

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.dmb.chantiertracker.presentation.CapitalTitle
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.back_signout_confirm
import com.dmb.chantiertracker.resources.back_signout_stay
import com.dmb.chantiertracker.resources.back_signout_title
import com.dmb.chantiertracker.resources.back_signout_understood
import com.dmb.chantiertracker.resources.back_signout_unsent
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SignOutBackDialog(unsentChanges: Int, onSignOut: () -> Unit, onDismiss: () -> Unit) {
    if (unsentChanges > 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { CapitalTitle(stringResource(Res.string.back_signout_title)) },
            text = { Text(pluralStringResource(Res.plurals.back_signout_unsent, unsentChanges, unsentChanges), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { Button(onClick = onDismiss) { Text(stringResource(Res.string.back_signout_understood)) } },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { CapitalTitle(stringResource(Res.string.back_signout_title)) },
            confirmButton = { Button(onClick = onSignOut) { Text(stringResource(Res.string.back_signout_confirm)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.back_signout_stay)) } },
        )
    }
}
