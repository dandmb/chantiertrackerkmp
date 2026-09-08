package com.dmb.chantiertracker.presentation.projects.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.repository.ExportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectExportUiState(
    val isExporting: Boolean = false,
    val error: DomainException? = null,
)

// Online only (ADR-48). Scoped to the ExportSection composable inside
// ProjectDetailScreen, so ProjectDetailViewModel stays untouched. On success
// the fresh PDF is handed straight to the platform share / open mechanism —
// that sheet IS the feedback, no confirmation screen.
class ProjectExportViewModel(
    private val exportRepository: ExportRepository,
    private val sharer: PdfSharer,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectExportUiState())
    val state = _state.asStateFlow()

    fun export(projectLocalId: String) {
        if (_state.value.isExporting) return
        _state.update { it.copy(isExporting = true, error = null) }
        viewModelScope.launch {
            try {
                val exported = exportRepository.exportProjectPdf(projectLocalId)
                sharer.share(exported.path)
                _state.update { it.copy(isExporting = false) }
            } catch (e: DomainException) {
                _state.update { it.copy(isExporting = false, error = e) }
            } catch (e: Throwable) {
                _state.update { it.copy(isExporting = false, error = DomainException.Unexpected) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
