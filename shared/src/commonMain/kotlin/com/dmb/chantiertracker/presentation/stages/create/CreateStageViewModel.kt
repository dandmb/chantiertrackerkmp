package com.dmb.chantiertracker.presentation.stages.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.CreateStageInput
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.projectAdmin
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import com.dmb.chantiertracker.domain.repository.StageRepository
import com.dmb.chantiertracker.presentation.auth.validateName
import com.dmb.chantiertracker.presentation.stages.parseAmountOrNull
import com.dmb.chantiertracker.presentation.stages.validateAmountOrBlank
import com.dmb.chantiertracker.presentation.stages.validateIsoDateOrBlank
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

data class CreateStageUiState(
    val name: String = "",
    val description: String = "",
    val estimatedBudget: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val canSetBudget: Boolean = false,
    val nameError: StringResource? = null,
    val budgetError: StringResource? = null,
    val startDateError: StringResource? = null,
    val endDateError: StringResource? = null,
    val formError: DomainException? = null,
    val isSubmitting: Boolean = false,
    val created: Boolean = false,
)

class CreateStageViewModel(
    private val stageRepository: StageRepository,
    private val projectRepository: ProjectRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateStageUiState())
    val state = _state.asStateFlow()

    private var projectLocalId: String? = null

    fun start(projectLocalId: String) {
        if (this.projectLocalId == projectLocalId) return
        this.projectLocalId = projectLocalId

        viewModelScope.launch {
            combine(
                projectRepository.observeProject(projectLocalId),
                projectRepository.observeMembers(projectLocalId),
            ) { detail, members -> detail to members }
                .collect { (detail, members) ->
                    val currentUserId = (authRepository.authState.value as? AuthState.Authenticated)?.user?.id
                    val admin = detail != null && projectAdmin(detail.ownerId, members, currentUserId)
                    _state.update { it.copy(canSetBudget = admin) }
                }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null, formError = null) }
    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value, formError = null) }
    fun onBudgetChange(value: String) = _state.update { it.copy(estimatedBudget = value, budgetError = null, formError = null) }
    fun onStartDateChange(value: String) = _state.update { it.copy(startDate = value, startDateError = null, formError = null) }
    fun onEndDateChange(value: String) = _state.update { it.copy(endDate = value, endDateError = null, formError = null) }

    fun submit() {
        val current = _state.value
        val projectId = projectLocalId ?: return

        val nameError = validateName(current.name.trim())
        val budgetError = if (current.canSetBudget) validateAmountOrBlank(current.estimatedBudget) else null
        val startDateError = validateIsoDateOrBlank(current.startDate)
        val endDateError = validateIsoDateOrBlank(current.endDate)
        if (nameError != null || budgetError != null || startDateError != null || endDateError != null) {
            _state.update {
                it.copy(
                    nameError = nameError,
                    budgetError = budgetError,
                    startDateError = startDateError,
                    endDateError = endDateError,
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, formError = null) }
            try {
                stageRepository.createStage(
                    CreateStageInput(
                        projectLocalId = projectId,
                        name = current.name.trim(),
                        description = current.description.trim().ifBlank { null },
                        estimatedBudget = if (current.canSetBudget) parseAmountOrNull(current.estimatedBudget) else null,
                        startDate = current.startDate.trim().ifBlank { null },
                        endDate = current.endDate.trim().ifBlank { null },
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
