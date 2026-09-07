package com.dmb.chantiertracker.presentation.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.repository.DailyLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EntrySummaryUiState(
    val prefilled: Boolean = false,
    val type: EntryType = EntryType.PURCHASE,
    val summary: String = "",
    val isSubmitting: Boolean = false,
    val saved: Boolean = false,
    val isMissing: Boolean = false,
) {
    // A WORK entry needs a title; a PURCHASE summary is optional. The Save
    // button stays disabled until this holds, so there is never an error to
    // show for the empty case.
    val canSave: Boolean get() = type == EntryType.PURCHASE || summary.isNotBlank()
}

class EntrySummaryViewModel(
    private val dailyLogRepository: DailyLogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EntrySummaryUiState())
    val state = _state.asStateFlow()

    private var entryLocalId: String? = null

    fun load(entryLocalId: String) {
        if (this.entryLocalId == entryLocalId) return
        this.entryLocalId = entryLocalId
        viewModelScope.launch {
            dailyLogRepository.observeEntry(entryLocalId).collect { entry ->
                if (entry == null) {
                    if (!_state.value.prefilled) _state.update { it.copy(isMissing = true) }
                    return@collect
                }
                // Prefill once — a later background sync must not clobber the edit in progress.
                if (!_state.value.prefilled) {
                    _state.update {
                        it.copy(prefilled = true, isMissing = false, type = entry.type, summary = entry.summary.orEmpty())
                    }
                } else {
                    _state.update { it.copy(type = entry.type) }
                }
            }
        }
    }

    fun onSummaryChange(value: String) = _state.update { it.copy(summary = value) }

    fun submit() {
        val id = entryLocalId ?: return
        val current = _state.value
        if (!current.prefilled || !current.canSave) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            dailyLogRepository.updateEntry(id, current.summary.trim())
            _state.update { it.copy(isSubmitting = false, saved = true) }
        }
    }
}
