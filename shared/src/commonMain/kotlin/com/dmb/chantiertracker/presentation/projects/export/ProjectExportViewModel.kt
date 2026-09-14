package com.dmb.chantiertracker.presentation.projects.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.ExportedPdf
import com.dmb.chantiertracker.domain.repository.ExportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectExportUiState(
    val isExporting: Boolean = false,
    val exported: ExportedPdf? = null,
    val error: DomainException? = null,
)

// Online only (ADR-48). Scoped to the ExportSection composable inside
// ProjectDetailScreen, so ProjectDetailViewModel stays untouched. On success
// the PDF is generated once and kept in state — the user then explicitly
// picks open() or share(), rather than one being forced automatically: both
// are legitimate depending on context (checking the file vs. sending it on).
class ProjectExportViewModel(
    private val exportRepository: ExportRepository,
    private val opener: PdfOpener,
    private val sharer: PdfSharer,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectExportUiState())
    val state = _state.asStateFlow()

    fun export(projectLocalId: String) {
        if (_state.value.isExporting) return
        _state.update { it.copy(isExporting = true, exported = null, error = null) }
        viewModelScope.launch {
            try {
                val exported = exportRepository.exportProjectPdf(projectLocalId)
                _state.update { it.copy(isExporting = false, exported = exported) }
            } catch (e: DomainException) {
                _state.update { it.copy(isExporting = false, error = e) }
            } catch (e: Throwable) {
                _state.update { it.copy(isExporting = false, error = DomainException.Unexpected) }
            }
        }
    }

    fun open() {
        val exported = _state.value.exported ?: return
        viewModelScope.launch {
            try {
                opener.open(exported.path)
            } catch (e: Throwable) {
                _state.update { it.copy(error = DomainException.Unexpected) }
            }
        }
    }

    fun share() {
        val exported = _state.value.exported ?: return
        viewModelScope.launch {
            try {
                sharer.share(exported.path)
            } catch (e: Throwable) {
                _state.update { it.copy(error = DomainException.Unexpected) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
