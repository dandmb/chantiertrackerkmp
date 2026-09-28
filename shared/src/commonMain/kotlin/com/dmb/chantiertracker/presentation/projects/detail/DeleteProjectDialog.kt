package com.dmb.chantiertracker.presentation.projects.detail

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
import com.dmb.chantiertracker.resources.create_name_label
import com.dmb.chantiertracker.resources.delete_project_confirm_button
import com.dmb.chantiertracker.resources.delete_project_confirm_prompt
import com.dmb.chantiertracker.resources.delete_project_title
import com.dmb.chantiertracker.resources.delete_project_warning
import org.jetbrains.compose.resources.stringResource

@Composable
fun DeleteProjectDialog(projectName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val matches = typed == projectName

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.delete_project_title, projectName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(Res.string.delete_project_warning),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(Res.string.delete_project_confirm_prompt, projectName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(Res.string.create_name_label)) },
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
                Text(stringResource(Res.string.delete_project_confirm_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
