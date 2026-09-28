package com.dmb.chantiertracker.presentation.projects.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.core.deviceTimeZoneId
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.repository.AccountRepository
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import com.dmb.chantiertracker.presentation.auth.validateName
import com.dmb.chantiertracker.presentation.projects.timezoneOptionsWith
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

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
    val atProjectLimit: Boolean = false,
    val isSuperAdmin: Boolean = false,
)

class CreateProjectViewModel(
    private val projectRepository: ProjectRepository,
    private val accountRepository: AccountRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState())
    val state = _state.asStateFlow()

    init {
        val currentUser = (authRepository.authState.value as? AuthState.Authenticated)?.user
        val currentUserId = currentUser?.id
        if (currentUser?.globalRole == GlobalRole.SUPER_ADMIN) {
            _state.update { it.copy(isSuperAdmin = true) }
        }
        if (currentUserId != null) {
            viewModelScope.launch {
                combine(
                    accountRepository.observePlanUsage(),
                    projectRepository.observeActiveProjectCount(currentUserId),
                ) { usage, count -> usage?.isAtProjectLimit(count) == true }
                    .collect { atLimit -> _state.update { it.copy(atProjectLimit = atLimit) } }
            }
        }
        // Best-effort: get the latest plan/limit before the user can hit "Create".
        viewModelScope.launch { accountRepository.refreshPlanUsage() }
    }

    private fun initialState(): CreateProjectUiState {
        val device = runCatching { deviceTimeZoneId() }.getOrNull()
        val options = timezoneOptionsWith(device)
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
        // Plan limit reached, or the caller is a SUPER_ADMIN (never owns or joins
        // a project, backend/web both enforce this — ADR-25 posture) → never
        // touch Room, never nudge the syncer.
        if (current.atProjectLimit || current.isSuperAdmin) return
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
