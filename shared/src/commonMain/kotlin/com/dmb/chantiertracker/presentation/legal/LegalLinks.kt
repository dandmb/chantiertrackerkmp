package com.dmb.chantiertracker.presentation.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.CapitalTitle
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.action_ok
import com.dmb.chantiertracker.resources.legal_link_cannot_open_mail_message
import com.dmb.chantiertracker.resources.legal_link_cannot_open_page_message
import com.dmb.chantiertracker.resources.legal_link_cannot_open_title
import com.dmb.chantiertracker.resources.legal_link_offline_message
import com.dmb.chantiertracker.resources.legal_link_offline_title
import org.jetbrains.compose.resources.stringResource

fun interface LegalPageOpener {
    fun open(url: String)
}

// Custom Tabs on Android, SFSafariViewController on iOS, the browser on Desktop.
@Composable
expect fun platformLegalPageOpener(): LegalPageOpener

val LocalLegalPageOpener = staticCompositionLocalOf<LegalPageOpener?> { null }

@Composable
fun LegalLinkNoticeDialog(notice: LegalLinkNotice, onDismiss: () -> Unit) {
    val (title, message) = when {
        notice.problem == LegalLinkProblem.Offline -> Res.string.legal_link_offline_title to Res.string.legal_link_offline_message
        notice.link is LegalLink.Mail -> Res.string.legal_link_cannot_open_title to Res.string.legal_link_cannot_open_mail_message
        else -> Res.string.legal_link_cannot_open_title to Res.string.legal_link_cannot_open_page_message
    }
    val address = when (val link = notice.link) {
        is LegalLink.Mail -> link.target.substringAfter(":").substringBefore("?")
        is LegalLink.Web, is LegalLink.Document -> link.target
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { CapitalTitle(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
                SelectionContainer {
                    Text(address, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_ok)) } },
    )
}
