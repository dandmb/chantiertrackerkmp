package com.dmb.chantiertracker.presentation.legal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.EditorIdentity
import com.dmb.chantiertracker.domain.repository.EditorIdentityRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// The last-known identity shows at once (offline included); each opening of a
// legal document asks the server again, best effort.
class LegalDocumentViewModel(private val editorIdentityRepository: EditorIdentityRepository) : ViewModel() {

    val editorIdentity: StateFlow<EditorIdentity?> =
        editorIdentityRepository.observe().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch { editorIdentityRepository.refresh() }
    }
}
