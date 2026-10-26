package com.dmb.chantiertracker.presentation.legal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.data.sync.ConnectivityObserver
import com.dmb.chantiertracker.domain.model.LegalLanguage
import com.dmb.chantiertracker.domain.model.LegalText
import com.dmb.chantiertracker.domain.repository.LegalDocumentRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed interface LegalDocumentUiState {
    data object Loading : LegalDocumentUiState
    data class Shown(val text: LegalText, val blocks: List<LegalBlock>, val savedCopyOnly: Boolean) : LegalDocumentUiState
    data class Unavailable(val offline: Boolean) : LegalDocumentUiState
}

enum class LegalLinkProblem { Offline, CannotOpen }

data class LegalLinkNotice(val link: LegalLink, val problem: LegalLinkProblem)

private sealed interface RefreshOutcome {
    data object Running : RefreshOutcome
    data object Received : RefreshOutcome
    data class Failed(val offline: Boolean) : RefreshOutcome
}

class LegalDocumentViewModel(
    private val repository: LegalDocumentRepository,
    private val connectivity: ConnectivityObserver,
) : ViewModel() {

    private val _state = MutableStateFlow<LegalDocumentUiState>(LegalDocumentUiState.Loading)
    val state: StateFlow<LegalDocumentUiState> = _state.asStateFlow()

    private val _linkNotice = MutableStateFlow<LegalLinkNotice?>(null)
    val linkNotice: StateFlow<LegalLinkNotice?> = _linkNotice.asStateFlow()

    private val outcome = MutableStateFlow<RefreshOutcome>(RefreshOutcome.Running)
    private var loaded: Pair<LegalDocument, LegalLanguage>? = null
    private var observing: Job? = null
    private var parsed: Pair<LegalText, List<LegalBlock>>? = null

    fun load(document: LegalDocument, language: LegalLanguage) {
        if (loaded == document to language) return
        loaded = document to language
        observing?.cancel()
        observing = viewModelScope.launch {
            combine(repository.observe(document.slug, language), outcome) { text, outcome -> stateFor(text, outcome) }
                .collect { _state.value = it }
        }
        refresh()
    }

    fun retry() {
        if (outcome.value == RefreshOutcome.Running) return
        refresh()
    }

    private fun refresh() {
        val (document, language) = loaded ?: return
        outcome.value = RefreshOutcome.Running
        viewModelScope.launch {
            outcome.value = if (repository.refresh(document.slug, language)) {
                RefreshOutcome.Received
            } else {
                RefreshOutcome.Failed(offline = !connectivity.isOnline())
            }
        }
    }

    private fun stateFor(text: LegalText?, outcome: RefreshOutcome): LegalDocumentUiState = when {
        text != null -> LegalDocumentUiState.Shown(text, blocksOf(text), savedCopyOnly = (outcome as? RefreshOutcome.Failed)?.offline == true)
        outcome is RefreshOutcome.Failed -> LegalDocumentUiState.Unavailable(outcome.offline)
        else -> LegalDocumentUiState.Loading
    }

    private fun blocksOf(text: LegalText): List<LegalBlock> =
        parsed?.takeIf { it.first == text }?.second ?: parseLegalMarkdown(text.markdown).also { parsed = text to it }

    fun openLink(link: LegalLink, webOpener: LegalPageOpener, mailOpener: LegalPageOpener) {
        viewModelScope.launch {
            if (link is LegalLink.Web && !connectivity.isOnline()) {
                _linkNotice.value = LegalLinkNotice(link, LegalLinkProblem.Offline)
                return@launch
            }
            val opener = if (link is LegalLink.Mail) mailOpener else webOpener
            runCatching { opener.open(link.target) }
                .onFailure { _linkNotice.value = LegalLinkNotice(link, LegalLinkProblem.CannotOpen) }
        }
    }

    fun dismissLinkNotice() {
        _linkNotice.value = null
    }
}
