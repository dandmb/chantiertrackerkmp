package com.dmb.chantiertracker.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.repository.ReportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportEntryUiState(
    val message: String = "",
    val isSubmitting: Boolean = false,
    val sent: Boolean = false,
    val error: DomainException? = null,
) {
    val canSend: Boolean get() = message.isNotBlank()
}

// Online only (ADR-47). No entry observation: the screen is opened from a
// visible entry, and createReport resolves the server id itself (NotFound if the
// entry has never synced — surfaced as an error under the field, no dedicated
// missing state for such a rare edge).
class ReportEntryViewModel(
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReportEntryUiState())
    val state = _state.asStateFlow()

    private var entryLocalId: String? = null

    fun load(entryLocalId: String) {
        if (this.entryLocalId == entryLocalId) return
        this.entryLocalId = entryLocalId
    }

    fun onMessageChange(value: String) = _state.update { it.copy(message = value, error = null) }

    fun submit() {
        val id = entryLocalId ?: return
        val current = _state.value
        if (!current.canSend || current.isSubmitting) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            try {
                reportRepository.createReport(id, current.message.trim())
                _state.update { it.copy(isSubmitting = false, sent = true) }
            } catch (e: DomainException) {
                _state.update { it.copy(isSubmitting = false, error = e) }
            } catch (e: Throwable) {
                _state.update { it.copy(isSubmitting = false, error = DomainException.Unexpected) }
            }
        }
    }
}
