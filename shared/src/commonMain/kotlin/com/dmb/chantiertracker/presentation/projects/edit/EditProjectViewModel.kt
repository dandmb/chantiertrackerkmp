package com.dmb.chantiertracker.presentation.projects.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.ProjectDetail
import com.dmb.chantiertracker.domain.model.UpdateProjectInput
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import com.dmb.chantiertracker.presentation.auth.validateName
import com.dmb.chantiertracker.presentation.projects.timezoneOptionsWith
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

data class EditProjectUiState(
    val prefilled: Boolean = false,
    val name: String = "",
    val description: String = "",
    val location: String = "",
    val currency: String = "",
    val timezone: String = "",
    val timezoneOptions: List<String> = emptyList(),
    val nameError: StringResource? = null,
    val formError: DomainException? = null,
    val isSubmitting: Boolean = false,
    val saved: Boolean = false,
    val isMissing: Boolean = false,
)

class EditProjectViewModel(
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EditProjectUiState())
    val state = _state.asStateFlow()

    private var localId: String? = null
    private var currentStatus = com.dmb.chantiertracker.domain.model.ProjectStatus.IN_PROGRESS

    fun load(projectLocalId: String) {
        if (localId == projectLocalId) return
        localId = projectLocalId

        viewModelScope.launch {
            projectRepository.observeProject(projectLocalId).collect { detail ->
                if (detail == null) {
                    if (!_state.value.prefilled) _state.update { it.copy(isMissing = true) }
                    return@collect
                }
                // Prefill once — later background syncs must not clobber the user's edits.
                if (!_state.value.prefilled) prefill(detail)
                currentStatus = detail.status
            }
        }
    }

    private fun prefill(detail: ProjectDetail) {
        _state.update {
            it.copy(
                prefilled = true,
                isMissing = false,
                name = detail.name,
                description = detail.description.orEmpty(),
                location = detail.location.orEmpty(),
                currency = detail.currency,
                timezone = detail.timezone,
                timezoneOptions = timezoneOptionsWith(detail.timezone),
            )
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null, formError = null) }
    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value, formError = null) }
    fun onLocationChange(value: String) = _state.update { it.copy(location = value, formError = null) }
    fun onCurrencyChange(value: String) = _state.update { it.copy(currency = value, formError = null) }
    fun onTimezoneChange(value: String) = _state.update { it.copy(timezone = value, formError = null) }

    fun submit() {
        val current = _state.value
        val id = localId ?: return
        if (!current.prefilled) return

        val nameError = validateName(current.name.trim())
        if (nameError != null) {
            _state.update { it.copy(nameError = nameError) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, formError = null) }
            try {
                projectRepository.updateProject(
                    id,
                    UpdateProjectInput(
                        name = current.name.trim(),
                        description = current.description.trim().ifBlank { null },
                        location = current.location.trim().ifBlank { null },
                        currency = current.currency.trim(),
                        timezone = current.timezone,
                        status = currentStatus,
                    ),
                )
                _state.update { it.copy(isSubmitting = false, saved = true) }
            } catch (e: DomainException) {
                _state.update { it.copy(isSubmitting = false, formError = e) }
            } catch (e: Throwable) {
                _state.update { it.copy(isSubmitting = false, formError = DomainException.Unexpected) }
            }
        }
    }
}
