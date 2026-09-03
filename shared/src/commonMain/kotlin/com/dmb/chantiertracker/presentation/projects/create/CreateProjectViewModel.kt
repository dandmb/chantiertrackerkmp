package com.dmb.chantiertracker.presentation.projects.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.core.deviceTimeZoneId
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import com.dmb.chantiertracker.presentation.auth.validateName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

private val SUGGESTED_TIMEZONES = listOf(
    "Africa/Douala",
    "Africa/Abidjan",
    "Africa/Casablanca",
    "Europe/Paris",
    "Europe/London",
    "America/New_York",
)

data class CreateProjectUiState(
    val name: String = "",
    val description: String = "",
    val location: String = "",
    val currency: String = "",
    val timezone: String = "",
    val timezoneOptions: List<String> = emptyList(),
    val nameError: StringResource? = null,
    val formError: DomainException? = null,
    val isSubmitting: Boolean = false,
    val created: Boolean = false,
)

class CreateProjectViewModel(
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState())
    val state = _state.asStateFlow()

    private fun initialState(): CreateProjectUiState {
        val device = runCatching { deviceTimeZoneId() }.getOrNull()
        val options = when {
            device == null || device in SUGGESTED_TIMEZONES -> SUGGESTED_TIMEZONES
            else -> listOf(device) + SUGGESTED_TIMEZONES
        }
        return CreateProjectUiState(
            timezone = device?.takeIf { it in options } ?: options.first(),
            timezoneOptions = options,
        )
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null, formError = null) }
    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value, formError = null) }
    fun onLocationChange(value: String) = _state.update { it.copy(location = value, formError = null) }
    fun onCurrencyChange(value: String) = _state.update { it.copy(currency = value, formError = null) }
    fun onTimezoneChange(value: String) = _state.update { it.copy(timezone = value, formError = null) }

    fun submit() {
        val current = _state.value
        val nameError = validateName(current.name.trim())
        if (nameError != null) {
            _state.update { it.copy(nameError = nameError) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, formError = null) }
            try {
                // Written to the local store and returned immediately; the server sync runs in the background.
                projectRepository.createProject(
                    CreateProjectInput(
                        name = current.name.trim(),
                        description = current.description.trim().ifBlank { null },
                        location = current.location.trim().ifBlank { null },
                        currency = current.currency.trim().ifBlank { null },
                        timezone = current.timezone,
                    ),
                )
                _state.update { it.copy(isSubmitting = false, created = true) }
            } catch (e: DomainException) {
                _state.update { it.copy(isSubmitting = false, formError = e) }
            } catch (e: Throwable) {
                _state.update { it.copy(isSubmitting = false, formError = DomainException.Unexpected) }
            }
        }
    }
}
