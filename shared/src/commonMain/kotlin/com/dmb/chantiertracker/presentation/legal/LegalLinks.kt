package com.dmb.chantiertracker.presentation.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.data.sync.ConnectivityObserver
import com.dmb.chantiertracker.presentation.CapitalTitle
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.action_ok
import com.dmb.chantiertracker.resources.legal_page_cannot_open_message
import com.dmb.chantiertracker.resources.legal_page_cannot_open_title
import com.dmb.chantiertracker.resources.legal_page_offline_message
import com.dmb.chantiertracker.resources.legal_page_offline_title
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

fun interface LegalPageOpener {
    fun open(url: String)
}

// Custom Tabs on Android, SFSafariViewController on iOS, the browser on Desktop.
@Composable
expect fun platformLegalPageOpener(): LegalPageOpener

val LocalLegalPageOpener = staticCompositionLocalOf<LegalPageOpener?> { null }

enum class LegalPageProblem { Offline, CannotOpen }

data class LegalPageNotice(val document: LegalDocument, val problem: LegalPageProblem)

class LegalLinksViewModel(private val connectivity: ConnectivityObserver) : ViewModel() {

    private val _notice = MutableStateFlow<LegalPageNotice?>(null)
    val notice: StateFlow<LegalPageNotice?> = _notice.asStateFlow()

    fun open(document: LegalDocument, opener: LegalPageOpener) {
        viewModelScope.launch {
            if (!connectivity.isOnline()) {
                _notice.value = LegalPageNotice(document, LegalPageProblem.Offline)
                return@launch
            }
            runCatching { opener.open(document.url) }
                .onFailure { _notice.value = LegalPageNotice(document, LegalPageProblem.CannotOpen) }
        }
    }

    fun dismissNotice() {
        _notice.value = null
    }
}

@Composable
fun rememberOpenLegalDocument(viewModel: LegalLinksViewModel = koinViewModel()): (LegalDocument) -> Unit {
    val opener = LocalLegalPageOpener.current ?: platformLegalPageOpener()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    notice?.let { LegalPageNoticeDialog(it, onDismiss = viewModel::dismissNotice) }
    return remember(viewModel, opener) { { document -> viewModel.open(document, opener) } }
}

@Composable
private fun LegalPageNoticeDialog(notice: LegalPageNotice, onDismiss: () -> Unit) {
    val (title, message) = when (notice.problem) {
        LegalPageProblem.Offline -> Res.string.legal_page_offline_title to Res.string.legal_page_offline_message
        LegalPageProblem.CannotOpen -> Res.string.legal_page_cannot_open_title to Res.string.legal_page_cannot_open_message
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { CapitalTitle(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(message, stringResource(notice.document.label)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                SelectionContainer {
                    Text(notice.document.url, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_ok)) } },
    )
}
