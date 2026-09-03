package com.dmb.chantiertracker.presentation.stages.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmb.chantiertracker.domain.model.StageDetail
import com.dmb.chantiertracker.domain.repository.ProjectRepository
import com.dmb.chantiertracker.domain.repository.StageRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StageDetailUiState(
    val isLoading: Boolean = true,
    val detail: StageDetail? = null,
    val currency: String? = null,
) {
    val isMissing: Boolean get() = !isLoading && detail == null
}

@OptIn(ExperimentalCoroutinesApi::class)
class StageDetailViewModel(
    private val stageRepository: StageRepository,
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StageDetailUiState())
    val state = _state.asStateFlow()

    private var localId: String? = null

    fun load(stageLocalId: String) {
        if (localId == stageLocalId) return
        localId = stageLocalId

        viewModelScope.launch {
            stageRepository.observeStage(stageLocalId)
                .flatMapLatest { detail ->
                    val currency = detail?.projectLocalId
                        ?.let { projectRepository.observeProject(it) }
                        ?: flowOf(null)
                    currency.map { project -> detail to project?.currency }
                }
                .collect { (detail, currency) ->
                    _state.update { it.copy(isLoading = false, detail = detail, currency = currency) }
                }
        }
        viewModelScope.launch { stageRepository.refreshStage(stageLocalId) }
    }

    fun retry() {
        localId?.let { id -> viewModelScope.launch { stageRepository.refreshStage(id) } }
    }
}
