package com.dmb.chantiertracker.presentation.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.repository.DailyLogRepository
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.entry_summary_required_work
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

data class EntrySummaryUiState(
    val prefilled: Boolean = false,
    val type: EntryType = EntryType.PURCHASE,
    val summary: String = "",
    val error: StringResource? = null,
    val isSubmitting: Boolean = false,
    val saved: Boolean = false,
    val isMissing: Boolean = false,
)

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

    fun onSummaryChange(value: String) = _state.update { it.copy(summary = value, error = null) }

    fun submit() {
        val id = entryLocalId ?: return
        val current = _state.value
        if (!current.prefilled) return
        if (current.type == EntryType.WORK && current.summary.isBlank()) {
            _state.update { it.copy(error = Res.string.entry_summary_required_work) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            dailyLogRepository.updateEntry(id, current.summary.trim())
            _state.update { it.copy(isSubmitting = false, saved = true) }
        }
    }
}
